package com.nstut.economy.trading;

import com.nstut.Economy;
import com.nstut.economy.api.CommodityPayload;
import com.nstut.economy.api.EconomyApi;
import com.nstut.economy.api.EconomyId;
import com.nstut.economy.api.IAccountManager;
import com.nstut.economy.api.IBankAccount;
import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.IOrder;
import com.nstut.economy.api.IStorageProvider;
import com.nstut.economy.api.StorageDeliveryResult;
import com.nstut.economy.api.StorageReservation;
import com.nstut.economy.blocks.TankManager;
import com.nstut.economy.blocks.VaultInventoryOps;
import com.nstut.economy.blocks.VaultManager;
import com.nstut.economy.core.TransactionContext;
import com.nstut.economy.data.EconomyOrderData;
import com.nstut.economy.data.TradeLedger;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Internal default implementation of the stable {@link IOrder} contract. */
public class Order implements IOrder {
    private static final String QUARANTINE_REASON = "economy:quarantine_reason";
    private static final String COMPENSATION_DEBTOR = "economy:compensation_debtor";
    private static final String COMPENSATION_CREDITOR = "economy:compensation_creditor";
    private static final String COMPENSATION_AMOUNT = "economy:compensation_amount";
    private final UUID orderId;
    private final UUID owner;
    private final ICommodity commodity;
    private int quantity;
    private int initialQuantity;
    private BigDecimal pricePerUnit;
    private final OrderType type;
    private final Instant createdAt;
    private final Instant expiresAt;
    private boolean cancelled;
    private boolean serverOrder;
    private boolean infinite;
    private final NonNullList<ItemStack> reservedItems;
    private final List<EconomyFluidStack> reservedFluids;
    private StorageReservation externalReservation;
    private Map<String, String> addonMetadata = Map.of();
    private EconomyId persistedTypeId;
    private CommodityPayload persistedPayload;

    public Order(UUID owner, ICommodity commodity, int quantity, BigDecimal pricePerUnit, OrderType type, Instant expiresAt) {
        this(owner, commodity, quantity, quantity, pricePerUnit, type, expiresAt, NonNullList.create(), new ArrayList<>(), false);
    }
    public Order(UUID owner, ICommodity commodity, int quantity, BigDecimal pricePerUnit, OrderType type, Instant expiresAt,
                 NonNullList<ItemStack> reservedItems) {
        this(owner, commodity, quantity, quantity, pricePerUnit, type, expiresAt, reservedItems, new ArrayList<>(), false);
    }
    public Order(UUID owner, ICommodity commodity, int quantity, int initialQuantity, BigDecimal pricePerUnit, OrderType type,
                 Instant expiresAt, NonNullList<ItemStack> reservedItems) {
        this(owner, commodity, quantity, initialQuantity, pricePerUnit, type, expiresAt, reservedItems, new ArrayList<>(), false);
    }
    public Order(UUID owner, ICommodity commodity, int quantity, int initialQuantity, BigDecimal pricePerUnit, OrderType type,
                 Instant expiresAt, NonNullList<ItemStack> reservedItems, boolean infinite) {
        this(owner, commodity, quantity, initialQuantity, pricePerUnit, type, expiresAt, reservedItems, new ArrayList<>(), infinite);
    }
    public Order(UUID owner, ICommodity commodity, int quantity, int initialQuantity, BigDecimal pricePerUnit, OrderType type,
                 Instant expiresAt, NonNullList<ItemStack> reservedItems, List<EconomyFluidStack> reservedFluids, boolean infinite) {
        this(UUID.randomUUID(), owner, commodity, quantity, initialQuantity, pricePerUnit, type, Instant.now(), expiresAt,
                reservedItems, reservedFluids, infinite, null, Map.of(), null, null);
    }

    private Order(UUID orderId, UUID owner, ICommodity commodity, int quantity, int initialQuantity,
                  BigDecimal pricePerUnit, OrderType type, Instant createdAt, Instant expiresAt,
                  NonNullList<ItemStack> reservedItems, List<EconomyFluidStack> reservedFluids, boolean infinite,
                  StorageReservation externalReservation, Map<String, String> addonMetadata,
                  EconomyId persistedTypeId, CommodityPayload persistedPayload) {
        this.orderId = orderId;
        this.owner = owner;
        this.commodity = commodity;
        this.quantity = quantity;
        this.initialQuantity = initialQuantity > 0 ? initialQuantity : quantity;
        this.pricePerUnit = pricePerUnit;
        this.type = type;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.reservedItems = reservedItems != null ? reservedItems : NonNullList.create();
        this.reservedFluids = reservedFluids != null ? reservedFluids : new ArrayList<>();
        this.infinite = infinite;
        this.externalReservation = externalReservation;
        this.addonMetadata = addonMetadata == null ? Map.of() : Map.copyOf(addonMetadata);
        this.persistedTypeId = persistedTypeId != null ? persistedTypeId : commodity.getTypeId();
        CommodityPayload initialPayload = persistedPayload != null ? persistedPayload : encodeSafely(commodity);
        this.persistedPayload = initialPayload != null ? initialPayload : CommodityPayload.empty(1);
    }

    public static Order fromSnapshot(EconomyOrderData.OrderSnapshot snap) {
        EconomyId typeId = EconomyId.parse(snap.commodityTypeId);
        EconomyId commodityId = EconomyId.parse(snap.itemId);
        CommodityPayload payload = new CommodityPayload(snap.commodityPayloadVersion, snap.commodityPayload);
        ICommodity commodity = EconomyApi.commodityTypes().require(typeId).decode(commodityId, payload);
        Instant expiry = snap.hasExpiry ? Instant.ofEpochMilli(snap.expiresAt) : null;
        Order order = new Order(snap.orderId, snap.owner, commodity, snap.quantity, snap.initialQuantity,
                new BigDecimal(snap.pricePerUnit), "SELL".equals(snap.type) ? OrderType.SELL : OrderType.BUY,
                Instant.ofEpochMilli(snap.createdAt), expiry, snap.reservedItems, snap.reservedFluids,
                snap.isInfinite, snap.externalReservation, snap.addonMetadata, typeId, payload);
        order.serverOrder = snap.isServerOrder;
        return order;
    }

    public EconomyOrderData.OrderSnapshot toSnapshot() {
        CommodityPayload payload = encodeSafely(commodity);
        if (payload != null) {
            persistedTypeId = commodity.getTypeId();
            persistedPayload = payload;
        }
        String legacyType = commodity.getType() == ICommodity.CommodityType.FLUID ? "FLUID"
                : commodity.getType() == ICommodity.CommodityType.ITEM ? "ITEM" : commodity.getType().name();
        return new EconomyOrderData.OrderSnapshot(orderId, owner, commodity.getId().toString(), quantity, initialQuantity,
                pricePerUnit.toPlainString(), type.name(), createdAt.toEpochMilli(), expiresAt == null ? 0 : expiresAt.toEpochMilli(),
                expiresAt != null, reservedItems, reservedFluids, serverOrder, infinite, legacyType,
                persistedTypeId.toString(), persistedPayload.version(), persistedPayload.values(), externalReservation, addonMetadata);
    }

    private static CommodityPayload encodeSafely(ICommodity commodity) {
        try { return EconomyApi.commodityTypes().handlerFor(commodity).encode(commodity); }
        catch (RuntimeException unavailable) { return null; }
    }

    public boolean isServerOrder() { return serverOrder; }
    public void setServerOrder(boolean serverOrder) { this.serverOrder = serverOrder; }
    public boolean isInfinite() { return infinite; }
    public void setInfinite(boolean infinite) { this.infinite = infinite; }
    public void setPricePerUnit(BigDecimal pricePerUnit) { this.pricePerUnit = pricePerUnit; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setInitialQuantity(int initialQuantity) { this.initialQuantity = initialQuantity; }
    public int getInitialQuantity() { return initialQuantity; }
    public StorageReservation getExternalReservation() { return externalReservation; }
    public void setExternalReservation(StorageReservation reservation) { this.externalReservation = reservation; }
    public Map<String, String> getAddonMetadata() { return addonMetadata; }
    public void setAddonMetadata(Map<String, String> metadata) { addonMetadata = metadata == null ? Map.of() : Map.copyOf(metadata); }
    public void markQuarantined(String reason) {
        HashMap<String, String> metadata = new HashMap<>(addonMetadata);
        metadata.put(QUARANTINE_REASON, reason == null || reason.isBlank() ? "provider escrow requires recovery" : reason);
        addonMetadata = Map.copyOf(metadata);
    }
    public boolean isQuarantined() { return addonMetadata.containsKey(QUARANTINE_REASON); }

    void markCompensationDue(UUID debtor, UUID creditor, BigDecimal amount) {
        HashMap<String, String> metadata = new HashMap<>(addonMetadata);
        metadata.put(COMPENSATION_DEBTOR, debtor == null ? "unknown" : debtor.toString());
        metadata.put(COMPENSATION_CREDITOR, creditor == null ? "unknown" : creditor.toString());
        metadata.put(COMPENSATION_AMOUNT, amount == null ? "0" : amount.toPlainString());
        addonMetadata = Map.copyOf(metadata);
    }

    boolean hasCompensationDue() {
        return addonMetadata.containsKey(COMPENSATION_AMOUNT);
    }

    private void persistRecovery(String reason) {
        try {
            if (EconomyApi.isReady() && EconomyApi.orders() instanceof OrderManager manager) {
                manager.preserveRecoveryOrder(this, reason);
            }
        } catch (RuntimeException unavailable) {
            Economy.LOGGER.error("Could not immediately persist recovery state for order {}", orderId, unavailable);
        }
    }

    void quarantineCompensation(UUID debtor, UUID creditor, BigDecimal amount, String reason) {
        markQuarantined(reason);
        markCompensationDue(debtor, creditor, amount);
        persistRecovery(reason);
    }

    @Override public UUID getOrderId() { return orderId; }
    @Override public UUID getOwner() { return owner; }
    @Override public ICommodity getCommodity() { return commodity; }
    @Override public int getQuantity() { return quantity; }
    @Override public BigDecimal getPricePerUnit() { return pricePerUnit; }
    @Override public OrderType getType() { return type; }
    @Override public Instant getCreatedAt() { return createdAt; }
    @Override public Instant getExpiresAt() { return expiresAt; }

    @Override
    public boolean isValid() {
        if (cancelled || isQuarantined() || (!infinite && quantity <= 0) || pricePerUnit == null || pricePerUnit.signum() <= 0) return false;
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) return false;
        if (commodity instanceof ItemCommodity item && item.getItem() == net.minecraft.world.item.Items.AIR) return false;
        return !(commodity instanceof FluidCommodity fluid) || fluid.getFluid() != net.minecraft.world.level.material.Fluids.EMPTY;
    }

    @Override
    public boolean canExecute(UUID trader) {
        if (!isValid() || owner.equals(trader)) return false;
        IAccountManager accounts = accounts();
        if (type == OrderType.SELL) {
            IBankAccount buyer = OrderManager.SERVER_ID.equals(trader) ? accounts.getServerAccount() : accounts.getOrCreatePlayerAccount(trader);
            return buyer.hasSufficientFunds(pricePerUnit.multiply(BigDecimal.valueOf(Math.max(1, quantity))));
        }
        if (serverOrder) return true;
        return accounts.getOrCreatePlayerAccount(owner).hasSufficientFunds(pricePerUnit.multiply(BigDecimal.valueOf(Math.max(1, quantity))));
    }

    @Override
    public TransactionResult execute(UUID trader) {
        ServerLevel level = EconomyApi.serverLevel().orElse(null);
        if (level == null) return TransactionResult.failure("Order execution requires a running server level");
        return executeAmount(trader, quantity, level);
    }

    @Override
    public TransactionResult execute(UUID trader, ServerLevel level) {
        ServerLevel resolvedLevel = level != null ? level : EconomyApi.serverLevel().orElse(null);
        if (resolvedLevel == null) return TransactionResult.failure("Order execution requires a running server level");
        return executeAmount(trader, quantity, resolvedLevel);
    }

    /** Internal order-book matching hook. Stable addon code must execute through {@link IOrder}. */
    TransactionResult executePartial(UUID trader, int amountToTrade, ServerLevel level) {
        return executeAmount(trader, amountToTrade, level);
    }

    private TransactionResult executeAmount(UUID trader, int requested, ServerLevel level) {
        if (!isValid() || owner.equals(trader) || requested <= 0) return TransactionResult.failure("Invalid execution request");
        int amount = infinite ? requested : Math.min(quantity, requested);
        if (amount <= 0) return TransactionResult.failure("Nothing to trade");
        if (type == OrderType.SELL) return executeSell(trader, amount, level);
        return executeBuy(trader, amount, level);
    }

    private TransactionResult executeSell(UUID buyerId, int requested, ServerLevel level) {
        IAccountManager accounts = accounts();
        boolean serverBuyer = OrderManager.SERVER_ID.equals(buyerId);
        IBankAccount sellerAccount = accounts.getOrCreatePlayerAccount(owner);
        IBankAccount buyerAccount = serverBuyer ? accounts.getServerAccount() : accounts.getOrCreatePlayerAccount(buyerId);
        int amount = serverBuyer ? requested : capByFunds(requested, buyerAccount);
        if (amount <= 0) return TransactionResult.failure("Buyer has insufficient funds");

        if (externalReservation != null) return executeReservedProviderSell(buyerId, sellerAccount, buyerAccount, amount, level, serverBuyer);

        NonNullList<ItemStack> items = NonNullList.create();
        List<EconomyFluidStack> fluids = new ArrayList<>();
        if (commodity instanceof ItemCommodity item) {
            if (!serverOrder) {
                items = buildItemDelivery(item.getItem(), amount);
                boolean virtualServerSettlement = serverBuyer && level == null && reservedItems.isEmpty();
                if (!virtualServerSettlement) {
                    amount = Math.min(amount, VaultInventoryOps.total(items));
                    if (amount <= 0) return TransactionResult.failure("Sell order has no reserved items");
                }
            } else items = generateItemStacks(item.getItem(), amount);
            if (!serverBuyer && level != null) {
                int space = VaultManager.hasVault(buyerId) ? VaultManager.countMaxAcceptableItems(level, buyerId, items) : 0;
                amount = Math.min(amount, space);
                if (amount <= 0) return TransactionResult.failure("Buyer has no compatible Vault space");
                items = buildItemDelivery(item.getItem(), amount);
                if (serverOrder && items.isEmpty()) items = generateItemStacks(item.getItem(), amount);
            }
        } else if (commodity instanceof FluidCommodity fluid) {
            if (!serverOrder) amount = Math.min(amount, getReservedFluidAmount());
            if (amount <= 0) return TransactionResult.failure("Sell order has no reserved fluid");
            fluids = buildFluidDelivery(fluid.getFluid(), amount);
            if (!serverBuyer && level != null) {
                int space = TankManager.hasTank(buyerId)
                        ? TankManager.simulateInsertFluidToTanks(level, buyerId, TankManager.mergeFluids(fluids)) : 0;
                amount = Math.min(amount, space);
                if (amount <= 0) return TransactionResult.failure("Buyer has no compatible Tank space");
                fluids = buildFluidDelivery(fluid.getFluid(), amount);
            }
        } else {
            return TransactionResult.failure("Custom sell order has no storage reservation");
        }

        BigDecimal total = pricePerUnit.multiply(BigDecimal.valueOf(amount));
        if (!accounts.transfer(buyerAccount, sellerAccount, total,
                TransactionContext.transfer("Purchase of " + commodity.getDisplayName().getString(), buyerId))) {
            return TransactionResult.failure("Payment failed");
        }

        int delivered = amount;
        if (!serverBuyer && level != null) delivered = commitBuiltInDelivery(level, buyerId, items, fluids, amount);
        BigDecimal refundAmount = delivered < amount ? totalFor(amount - delivered) : BigDecimal.ZERO;
        boolean compensated = delivered >= amount
                || refund(sellerAccount, buyerAccount, buyerId, refundAmount, "Refund - partial delivery");
        if (!serverOrder) consumeEscrow(delivered);
        reduceAfterFill(delivered);
        if (!compensated) {
            quarantineCompensation(owner, buyerId, refundAmount, "partial built-in SELL delivery refund failed");
        }
        return delivered <= 0 ? TransactionResult.failure("Nothing could be delivered")
                : completeTrade(level, buyerId, owner, delivered, totalFor(delivered));
    }

    private TransactionResult executeReservedProviderSell(UUID buyerId, IBankAccount sellerAccount, IBankAccount buyerAccount,
                                                          int requested, ServerLevel level, boolean serverBuyer) {
        if (level == null) return TransactionResult.failure("Provider-backed orders require a running server level");
        IStorageProvider provider = EconomyApi.storage().provider(externalReservation.providerId()).orElse(null);
        if (provider == null) return TransactionResult.failure("Storage provider unavailable: " + externalReservation.providerId());
        int amount = Math.min(requested, externalReservation.amount());
        if (amount <= 0) return TransactionResult.failure("Nothing to deliver");
        BigDecimal total = totalFor(amount);
        if (!accounts().transfer(buyerAccount, sellerAccount, total,
                TransactionContext.transfer("Purchase of " + commodity.getDisplayName().getString(), buyerId))) {
            return TransactionResult.failure("Payment failed");
        }

        StorageReservation beforeDelivery = externalReservation;
        StorageDeliveryResult delivery;
        try {
            delivery = provider.deliverReserved(level, beforeDelivery,
                    serverBuyer ? OrderManager.SERVER_ID : buyerId, amount).validateAgainst(beforeDelivery, amount);
        } catch (RuntimeException providerFailure) {
            boolean compensated = refund(sellerAccount, buyerAccount, buyerId, total, "Refund - provider delivery failed");
            String reason = "provider delivery failed after payment: " + provider.id();
            markQuarantined(reason);
            if (!compensated) markCompensationDue(owner, buyerId, total);
            persistRecovery(reason);
            Economy.LOGGER.error("Provider {} failed after payment on SELL order {}; payment compensated={}, reservation {} quarantined",
                    provider.id(), orderId, compensated, beforeDelivery.token(), providerFailure);
            return TransactionResult.failure(compensated
                    ? "Storage provider failed; payment refunded and escrow quarantined"
                    : "Storage provider failed; escrow and compensation debt quarantined");
        }

        int delivered = delivery.deliveredAmount();
        externalReservation = delivery.remainingReservation().orElse(null);
        reduceAfterFill(delivered);
        if (delivered < amount) {
            BigDecimal refundAmount = totalFor(amount - delivered);
            if (!refund(sellerAccount, buyerAccount, buyerId, refundAmount, "Refund - partial provider delivery")) {
                quarantineCompensation(owner, buyerId, refundAmount, "partial provider SELL delivery refund failed: " + provider.id());
            }
        }
        return delivered <= 0 ? TransactionResult.failure("Nothing could be delivered")
                : completeTrade(level, buyerId, owner, delivered, totalFor(delivered));
    }

    private TransactionResult executeBuy(UUID sellerId, int requested, ServerLevel level) {
        IAccountManager accounts = accounts();
        IBankAccount buyerAccount = serverOrder ? accounts.getServerAccount() : accounts.getOrCreatePlayerAccount(owner);
        IBankAccount sellerAccount = accounts.getOrCreatePlayerAccount(sellerId);
        int amount = requested;
        if (!serverOrder) amount = capByFunds(amount, buyerAccount);
        if (amount <= 0) return TransactionResult.failure("Buyer has insufficient funds");

        if (!(commodity instanceof ItemCommodity) && !(commodity instanceof FluidCommodity)) {
            return executeProviderBuy(sellerId, buyerAccount, sellerAccount, amount, level);
        }

        if (level != null && commodity instanceof ItemCommodity item) {
            amount = Math.min(amount, VaultManager.countItemInVaults(level, sellerId, item.getItem()));
            if (!serverOrder) amount = Math.min(amount, VaultManager.hasVault(owner)
                    ? VaultManager.countMaxAcceptableItems(level, owner, generateItemStacks(item.getItem(), amount)) : 0);
        } else if (level != null && commodity instanceof FluidCommodity fluid) {
            amount = Math.min(amount, TankManager.countFluidInTanks(level, sellerId, fluid.getFluid()));
            if (!serverOrder) amount = Math.min(amount, TankManager.hasTank(owner)
                    ? TankManager.simulateInsertFluidToTanks(level, owner, new EconomyFluidStack(fluid.getFluid(), amount)) : 0);
        }
        if (amount <= 0) return TransactionResult.failure("Seller lacks goods or buyer lacks storage space");

        BigDecimal total = totalFor(amount);
        if (!accounts.transfer(buyerAccount, sellerAccount, total,
                TransactionContext.transfer("Sale of " + commodity.getDisplayName().getString(), owner))) {
            return TransactionResult.failure("Payment failed");
        }

        int delivered = amount;
        if (level != null && commodity instanceof ItemCommodity item) {
            NonNullList<ItemStack> extracted = NonNullList.create();
            if (!VaultManager.extractItemFromVaults(level, sellerId, item.getItem(), amount, extracted)) delivered = 0;
            else if (!serverOrder) {
                NonNullList<ItemStack> leftover = VaultManager.insertItemStacksToVaults(level, owner, extracted);
                delivered = VaultInventoryOps.total(extracted) - VaultInventoryOps.total(leftover);
                if (!leftover.isEmpty()) VaultManager.insertItemStacksToVaults(level, sellerId, leftover);
            }
        } else if (level != null && commodity instanceof FluidCommodity fluid) {
            List<EconomyFluidStack> extracted = new ArrayList<>();
            int drained = TankManager.extractFluidFromTanks(level, sellerId, fluid.getFluid(), amount, extracted);
            if (drained < amount) { for (EconomyFluidStack stack : extracted) TankManager.restoreFluidToTanks(level, sellerId, stack); delivered = 0; }
            else if (serverOrder) delivered = drained;
            else {
                delivered = 0; for (EconomyFluidStack stack : extracted) delivered += TankManager.insertFluidToTanks(level, owner, stack);
                if (delivered < drained) TankManager.restoreFluidToTanks(level, sellerId, new EconomyFluidStack(fluid.getFluid(), drained - delivered));
            }
        }

        BigDecimal refundAmount = delivered < amount ? totalFor(amount - delivered) : BigDecimal.ZERO;
        boolean compensated = delivered >= amount
                || refund(sellerAccount, buyerAccount, sellerId, refundAmount, "Refund - partial delivery");
        reduceAfterFill(delivered);
        if (!compensated) {
            quarantineCompensation(sellerId, owner, refundAmount, "partial built-in BUY delivery refund failed");
        }
        if (delivered <= 0) return TransactionResult.failure("Nothing could be delivered");
        return completeTrade(level, owner, sellerId, delivered, totalFor(delivered));
    }

    private TransactionResult executeProviderBuy(UUID sellerId, IBankAccount buyerAccount, IBankAccount sellerAccount,
                                                 int requested, ServerLevel level) {
        if (level == null) return TransactionResult.failure("Custom commodities require a running server level");
        StorageReservation reservation = EconomyApi.storage().reserve(level, sellerId, commodity, requested).orElse(null);
        if (reservation == null) return TransactionResult.failure("Seller has no compatible registered storage provider");
        IStorageProvider provider = EconomyApi.storage().provider(reservation.providerId()).orElse(null);
        if (provider == null) {
            preserveProviderReservation(sellerId, reservation, "provider disappeared after reserve");
            return TransactionResult.failure("Storage provider unavailable; reservation quarantined");
        }
        int amount = Math.min(requested, reservation.amount());
        if (amount <= 0) {
            releaseOrPreserve(provider, level, sellerId, reservation, "release failed for zero-sized provider BUY reservation");
            return TransactionResult.failure("Nothing to deliver");
        }
        BigDecimal total = totalFor(amount);
        if (!accounts().transfer(buyerAccount, sellerAccount, total,
                TransactionContext.transfer("Sale of " + commodity.getDisplayName().getString(), owner))) {
            releaseOrPreserve(provider, level, sellerId, reservation, "release failed after provider BUY payment rejection");
            return TransactionResult.failure("Payment failed");
        }

        StorageDeliveryResult delivery;
        try {
            delivery = provider.deliverReserved(level, reservation,
                    serverOrder ? OrderManager.SERVER_ID : owner, amount).validateAgainst(reservation, amount);
        } catch (RuntimeException providerFailure) {
            boolean compensated = refund(sellerAccount, buyerAccount, sellerId, total, "Refund - provider delivery failed");
            if (!compensated) {
                quarantineCompensation(sellerId, owner, total, "provider BUY delivery failed and refund failed: " + provider.id());
            }
            preserveProviderReservation(sellerId, reservation,
                    compensated ? "provider delivery failed after payment"
                            : "provider delivery failed; compensation debt preserved on BUY order");
            Economy.LOGGER.error("Provider {} failed after payment on BUY order {}; payment compensated={}, reservation {} quarantined",
                    provider.id(), orderId, compensated, reservation.token(), providerFailure);
            return TransactionResult.failure(compensated
                    ? "Storage provider failed; payment refunded and escrow quarantined"
                    : "Storage provider failed; escrow and compensation debt quarantined");
        }

        int delivered = delivery.deliveredAmount();
        reduceAfterFill(delivered);
        BigDecimal refundAmount = totalFor(amount - delivered);
        boolean compensated = delivered >= amount || refund(sellerAccount, buyerAccount, sellerId, refundAmount,
                "Refund - partial provider delivery");
        if (!compensated) {
            quarantineCompensation(sellerId, owner, refundAmount, "partial provider BUY delivery refund failed: " + provider.id());
        }
        var remainingReservation = delivery.remainingReservation();
        if (remainingReservation.isPresent()) {
            if (compensated) {
                releaseOrPreserve(provider, level, sellerId, remainingReservation.get(),
                        "provider BUY remainder release failed after partial delivery");
            } else {
                preserveProviderReservation(sellerId, remainingReservation.get(),
                        "provider BUY remainder retained because compensation failed");
            }
        }
        if (delivered <= 0) return TransactionResult.failure("Nothing could be delivered");
        return completeTrade(level, owner, sellerId, delivered, totalFor(delivered));
    }

    private TransactionResult completeTrade(ServerLevel level, UUID buyer, UUID seller, int delivered, BigDecimal total) {
        recordTrade(pricePerUnit, delivered, buyer, seller);
        boolean fluidLike = isFluidLike();
        notifyPlayerTrade(level, buyer, seller, true, commodity.getDisplayName().getString(), fluidLike, delivered, pricePerUnit, total);
        notifyPlayerTrade(level, seller, buyer, false, commodity.getDisplayName().getString(), fluidLike, delivered, pricePerUnit, total);
        if (level != null) {
            com.nstut.economy.data.EconomyAccountData.recordSnapshot(buyer, level);
            com.nstut.economy.data.EconomyAccountData.recordSnapshot(seller, level);
        }
        return TransactionResult.success(type == OrderType.SELL ? "Purchase successful" : "Sale successful", total, delivered);
    }

    private IAccountManager accounts() { return EconomyApi.isReady() ? EconomyApi.accounts() : IAccountManager.getInstance(); }
    private BigDecimal totalFor(int amount) { return pricePerUnit.multiply(BigDecimal.valueOf(amount)); }
    private int capByFunds(int requested, IBankAccount account) {
        if (pricePerUnit == null || pricePerUnit.signum() <= 0 || account.getBalance().signum() <= 0) return 0;
        return Math.min(requested, account.getBalance().divide(pricePerUnit, 0, java.math.RoundingMode.DOWN).max(BigDecimal.ZERO).intValue());
    }
    private void reduceAfterFill(int delivered) { if (!infinite) quantity = Math.max(0, quantity - delivered); }

    private int commitBuiltInDelivery(ServerLevel level, UUID receiver, NonNullList<ItemStack> items,
                                      List<EconomyFluidStack> fluids, int requested) {
        if (!items.isEmpty()) {
            NonNullList<ItemStack> leftover = VaultManager.insertItemStacksToVaults(level, receiver, items);
            return Math.min(requested, VaultInventoryOps.total(items) - VaultInventoryOps.total(leftover));
        }
        if (!fluids.isEmpty()) {
            int delivered = 0; for (EconomyFluidStack stack : fluids) delivered += TankManager.insertFluidToTanks(level, receiver, stack);
            return Math.min(requested, delivered);
        }
        return requested;
    }

    private boolean refund(IBankAccount from, IBankAccount to, UUID initiator, BigDecimal amount, String reason) {
        if (amount.signum() <= 0) return true;
        try {
            boolean success = accounts().transfer(from, to, amount, TransactionContext.transfer(reason, initiator));
            if (!success) Economy.LOGGER.error("FAILED TO REFUND {} coins ({}) on order {}", amount.toPlainString(), reason, orderId);
            return success;
        } catch (RuntimeException failure) {
            Economy.LOGGER.error("REFUND THREW for {} coins ({}) on order {}", amount.toPlainString(), reason, orderId, failure);
            return false;
        }
    }

    private boolean releaseOrPreserve(IStorageProvider provider, ServerLevel level, UUID escrowOwner,
                                      StorageReservation reservation, String reason) {
        boolean released = false;
        try {
            released = provider.release(level, reservation);
        } catch (RuntimeException releaseFailure) {
            Economy.LOGGER.error("Provider {} threw while releasing reservation {}", provider.id(), reservation.token(), releaseFailure);
        }
        if (!released) preserveProviderReservation(escrowOwner, reservation, reason);
        return released;
    }

    private void preserveProviderReservation(UUID escrowOwner, StorageReservation reservation, String reason) {
        try {
            if (EconomyApi.isReady() && EconomyApi.orders() instanceof OrderManager manager) {
                manager.preserveProviderReservation(escrowOwner, commodity, reservation, pricePerUnit, reason);
                return;
            }
        } catch (RuntimeException unavailable) {
            Economy.LOGGER.error("Could not access OrderManager while preserving provider reservation {}", reservation.token(), unavailable);
        }
        externalReservation = reservation;
        markQuarantined(reason);
        Economy.LOGGER.error("Attached provider reservation {} to order {} quarantine because no runtime OrderManager was available",
                reservation.token(), orderId);
    }

    private void recordTrade(BigDecimal price, int amount, UUID buyer, UUID seller) {
        String typeValue = commodity.getType() == ICommodity.CommodityType.ITEM ? "ITEM"
                : commodity.getType() == ICommodity.CommodityType.FLUID ? "FLUID" : commodity.getTypeId().toString();
        TradeLedger.recordTrade(commodity.getId().toString(), typeValue, price, amount, buyer, seller);
    }

    private boolean isFluidLike() {
        try { return EconomyApi.commodityTypes().handlerFor(commodity).fluidLike(); }
        catch (RuntimeException ignored) { return commodity.getType() == ICommodity.CommodityType.FLUID; }
    }

    private int getReservedFluidAmount() {
        int total = 0; for (EconomyFluidStack stack : reservedFluids) if (stack != null && !stack.isEmpty()) total += stack.getAmount(); return total;
    }
    private NonNullList<ItemStack> buildItemDelivery(Item item, int amount) {
        if (reservedItems.isEmpty()) return serverOrder ? generateItemStacks(item, amount) : NonNullList.create();
        NonNullList<ItemStack> result = NonNullList.create(); int remaining = amount;
        for (ItemStack stack : reservedItems) {
            if (remaining <= 0) break; if (stack == null || stack.isEmpty()) continue;
            int take = Math.min(remaining, stack.getCount()); ItemStack copy = stack.copy(); copy.setCount(take); result.add(copy); remaining -= take;
        }
        return result;
    }
    private List<EconomyFluidStack> buildFluidDelivery(Fluid fluid, int amount) {
        List<EconomyFluidStack> result = new ArrayList<>(); int remaining = amount;
        if (!reservedFluids.isEmpty()) {
            for (EconomyFluidStack stack : reservedFluids) {
                if (remaining <= 0) break; if (stack == null || stack.isEmpty()) continue;
                int take = Math.min(remaining, stack.getAmount()); EconomyFluidStack copy = stack.copy(); copy.setAmount(take); result.add(copy); remaining -= take;
            }
        } else if (serverOrder && amount > 0) result.add(new EconomyFluidStack(fluid, amount));
        return result;
    }

    public void consumeEscrow(int amount) {
        int remaining = amount;
        var itemIt = reservedItems.iterator();
        while (itemIt.hasNext() && remaining > 0) {
            ItemStack stack = itemIt.next(); if (stack == null || stack.isEmpty()) { itemIt.remove(); continue; }
            int take = Math.min(remaining, stack.getCount()); stack.shrink(take); remaining -= take; if (stack.isEmpty()) itemIt.remove();
        }
        var fluidIt = reservedFluids.iterator();
        while (fluidIt.hasNext() && remaining > 0) {
            EconomyFluidStack stack = fluidIt.next(); if (stack == null || stack.isEmpty()) { fluidIt.remove(); continue; }
            int take = Math.min(remaining, stack.getAmount()); stack.shrink(take); remaining -= take; if (stack.isEmpty()) fluidIt.remove();
        }
    }

    public int getEscrowedItemCount() { return VaultInventoryOps.total(reservedItems); }
    public NonNullList<ItemStack> getReservedItems() { return reservedItems; }
    public List<EconomyFluidStack> getReservedFluids() { return reservedFluids; }
    public boolean canCancel() { return !cancelled && (quantity > 0 || infinite); }

    @Override
    public boolean cancel() {
        if (!canCancel() || !EconomyApi.isReady()) return false;
        try {
            return EconomyApi.orders().cancelOrder(orderId, owner);
        } catch (RuntimeException unavailable) {
            Economy.LOGGER.error("Could not route legacy cancel for order {} through the active OrderManager", orderId, unavailable);
            return false;
        }
    }

    boolean cancelInternal() {
        if (!canCancel()) return false;
        cancelled = true;
        if (!infinite) quantity = 0;
        return true;
    }

    public void reduceQuantity(int amount) { if (!infinite) quantity = Math.max(0, quantity - amount); }

    private static NonNullList<ItemStack> generateItemStacks(Item item, int amount) {
        NonNullList<ItemStack> result = NonNullList.create(); int max = com.nstut.economy.compat.Compat.maxStackSize(item);
        for (int remaining = amount; remaining > 0;) { int count = Math.min(remaining, max); result.add(new ItemStack(item, count)); remaining -= count; }
        return result;
    }

    private static void notifyPlayerTrade(ServerLevel level, UUID playerUUID, UUID counterpartyUUID, boolean buy,
                                          String name, boolean fluidLike, int amount, BigDecimal unitPrice, BigDecimal total) {
        if (level == null || playerUUID == null || OrderManager.SERVER_ID.equals(playerUUID)) return;
        net.minecraft.server.level.ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerUUID);
        if (player == null) return;
        level.playSound(null, player.getX(), player.getY(), player.getZ(), com.nstut.economy.sound.SoundRegistries.MONEY.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.0F);
        String action = buy ? "Bought" : "Sold"; String prep = buy ? "from" : "to";
        BigDecimal displayUnit = fluidLike ? FluidCommodity.pricePerBucket(unitPrice) : unitPrice;
        String quantity = com.nstut.economy.util.EconomyFormatUtil.formatCommodityQuantity(amount, fluidLike);
        String details = amount > 1
                ? "§e" + com.nstut.economy.util.EconomyFormatUtil.formatMoney(displayUnit)
                    + (fluidLike ? " §fcoins per 1000 mB" : " §fcoins each") + " (Total: §e"
                    + com.nstut.economy.util.EconomyFormatUtil.formatMoney(total) + " §fcoins)"
                : "§e" + com.nstut.economy.util.EconomyFormatUtil.formatMoney(total) + " §fcoins";
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§2[Market] §aOrder Matched! §f" + action + " §e"
                + quantity + " of " + name + " §ffor " + details + " " + prep + " §b" + onlineName(level, counterpartyUUID) + "§f."));
    }

    private static String onlineName(ServerLevel level, UUID id) {
        if (id == null || OrderManager.SERVER_ID.equals(id)) return "Server";
        net.minecraft.server.level.ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
        return player != null ? player.getName().getString() : id.toString();
    }
}
