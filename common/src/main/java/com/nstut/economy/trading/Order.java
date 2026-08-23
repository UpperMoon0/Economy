package com.nstut.economy.trading;

import com.nstut.Economy;
import com.nstut.economy.api.IAccountManager;
import com.nstut.economy.api.IBankAccount;
import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.IOrder;
import com.nstut.economy.blocks.TankManager;
import com.nstut.economy.blocks.VaultInventoryOps;
import com.nstut.economy.blocks.VaultManager;
import com.nstut.economy.core.TransactionContext;
import com.nstut.economy.data.EconomyOrderData;
import com.nstut.economy.data.TradeLedger;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Order implements IOrder {

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
    private boolean isInfinite;
    private final NonNullList<ItemStack> reservedItems;
    private final List<FluidStack> reservedFluids;

    public Order(UUID owner, ICommodity commodity, int quantity,
                 BigDecimal pricePerUnit, OrderType type, Instant expiresAt) {
        this(owner, commodity, quantity, quantity, pricePerUnit, type, expiresAt, NonNullList.create(), new ArrayList<>(), false);
    }

    public Order(UUID owner, ICommodity commodity, int quantity,
                 BigDecimal pricePerUnit, OrderType type, Instant expiresAt,
                 NonNullList<ItemStack> reservedItems) {
        this(owner, commodity, quantity, quantity, pricePerUnit, type, expiresAt, reservedItems, new ArrayList<>(), false);
    }

    public Order(UUID owner, ICommodity commodity, int quantity, int initialQuantity,
                 BigDecimal pricePerUnit, OrderType type, Instant expiresAt,
                 NonNullList<ItemStack> reservedItems) {
        this(owner, commodity, quantity, initialQuantity, pricePerUnit, type, expiresAt, reservedItems, new ArrayList<>(), false);
    }

    public Order(UUID owner, ICommodity commodity, int quantity, int initialQuantity,
                 BigDecimal pricePerUnit, OrderType type, Instant expiresAt,
                 NonNullList<ItemStack> reservedItems, boolean isInfinite) {
        this(owner, commodity, quantity, initialQuantity, pricePerUnit, type, expiresAt, reservedItems, new ArrayList<>(), isInfinite);
    }

    public Order(UUID owner, ICommodity commodity, int quantity, int initialQuantity,
                 BigDecimal pricePerUnit, OrderType type, Instant expiresAt,
                 NonNullList<ItemStack> reservedItems, List<FluidStack> reservedFluids, boolean isInfinite) {
        this.orderId = UUID.randomUUID();
        this.owner = owner;
        this.commodity = commodity;
        this.quantity = quantity;
        this.initialQuantity = initialQuantity > 0 ? initialQuantity : quantity;
        this.pricePerUnit = pricePerUnit;
        this.type = type;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
        this.cancelled = false;
        this.reservedItems = reservedItems;
        this.reservedFluids = reservedFluids != null ? reservedFluids : new ArrayList<>();
        this.isInfinite = isInfinite;
    }

    public static Order fromSnapshot(EconomyOrderData.OrderSnapshot snap) {
        ResourceLocation rl = new ResourceLocation(snap.itemId);
        ICommodity commodity;
        boolean isFluid = "FLUID".equals(snap.commodityType)
                || (!snap.reservedFluids.isEmpty()
                && BuiltInRegistries.FLUID.get(rl) != net.minecraft.world.level.material.Fluids.EMPTY);

        if (isFluid) {
            Fluid fluid = BuiltInRegistries.FLUID.get(rl);
            commodity = new FluidCommodity(rl, fluid, BigDecimal.ZERO);
        } else {
            Item item = BuiltInRegistries.ITEM.get(rl);
            commodity = new ItemCommodity(rl, item, BigDecimal.ZERO);
        }
        Instant expires = snap.hasExpiry ? Instant.ofEpochMilli(snap.expiresAt) : null;
        Order order = new Order(snap.owner, commodity, snap.quantity, snap.initialQuantity,
            new BigDecimal(snap.pricePerUnit),
            snap.type.equals("SELL") ? OrderType.SELL : OrderType.BUY,
            expires, snap.reservedItems, snap.reservedFluids, snap.isInfinite);
        setField(order, "orderId", snap.orderId);
        setField(order, "createdAt", Instant.ofEpochMilli(snap.createdAt));
        if (snap.isServerOrder) {
            order.serverOrder = true;
        }
        return order;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = Order.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception ignored) {
        }
    }

    public EconomyOrderData.OrderSnapshot toSnapshot() {
        String typeStr = commodity instanceof FluidCommodity ? "FLUID" : "ITEM";
        return new EconomyOrderData.OrderSnapshot(
            orderId, owner, commodity.getId().toString(),
            quantity, initialQuantity, pricePerUnit.toPlainString(),
            type.name(), createdAt.toEpochMilli(),
            expiresAt != null ? expiresAt.toEpochMilli() : 0,
            expiresAt != null, reservedItems, reservedFluids, serverOrder, isInfinite, typeStr
        );
    }

    public boolean isServerOrder() {
        return serverOrder;
    }

    public void setServerOrder(boolean serverOrder) {
        this.serverOrder = serverOrder;
    }

    @Override
    public UUID getOrderId() {
        return orderId;
    }

    @Override
    public UUID getOwner() {
        return owner;
    }

    @Override
    public ICommodity getCommodity() {
        return commodity;
    }

    @Override
    public int getQuantity() {
        return quantity;
    }

    public int getInitialQuantity() {
        return initialQuantity;
    }

    @Override
    public BigDecimal getPricePerUnit() {
        return pricePerUnit;
    }

    @Override
    public OrderType getType() {
        return type;
    }

    @Override
    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isInfinite() {
        return isInfinite;
    }

    public void setInfinite(boolean isInfinite) {
        this.isInfinite = isInfinite;
    }

    public void setPricePerUnit(BigDecimal pricePerUnit) {
        this.pricePerUnit = pricePerUnit;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setInitialQuantity(int initialQuantity) {
        this.initialQuantity = initialQuantity;
    }

    @Override
    public boolean isValid() {
        if (cancelled) {
            return false;
        }
        if (!isInfinite && quantity <= 0) {
            return false;
        }
        if (pricePerUnit == null || pricePerUnit.signum() <= 0) {
            return false;
        }
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return false;
        }
        if (commodity instanceof ItemCommodity ic && ic.getItem() == net.minecraft.world.item.Items.AIR) {
            return false;
        }
        if (commodity instanceof FluidCommodity fc && fc.getFluid() == net.minecraft.world.level.material.Fluids.EMPTY) {
            return false;
        }
        return true;
    }

    @Override
    public boolean canExecute(UUID trader) {
        if (!isValid()) {
            return false;
        }
        if (owner.equals(trader)) {
            return false;
        }

        IAccountManager accounts = IAccountManager.getInstance();
        IBankAccount traderAccount = accounts.getOrCreatePlayerAccount(trader);

        if (type == OrderType.SELL) {
            return traderAccount.hasSufficientFunds(getTotalPrice());
        } else {
            if (serverOrder) return true;
            IBankAccount ownerAccount = accounts.getOrCreatePlayerAccount(owner);
            return ownerAccount.hasSufficientFunds(getTotalPrice());
        }
    }

    @Override
    public TransactionResult execute(UUID trader) {
        return execute(trader, null);
    }

    @Override
    public TransactionResult execute(UUID trader, ServerLevel level) {
        if (!canExecute(trader)) {
            return TransactionResult.failure("Cannot execute this order");
        }

        IAccountManager accounts = IAccountManager.getInstance();
        Item item = null;
        if (commodity instanceof ItemCommodity ic) {
            item = ic.getItem();
        }

        if (type == OrderType.SELL) {
            return executeSell(trader, accounts, item, level);
        } else {
            return executeBuy(trader, accounts, item, level);
        }
    }

    private TransactionResult executeSell(UUID buyer, IAccountManager accounts, Item item, ServerLevel level) {
        IBankAccount sellerAccount = accounts.getOrCreatePlayerAccount(owner);
        IBankAccount buyerAccount = accounts.getOrCreatePlayerAccount(buyer);

        int tradeQty = this.quantity;
        NonNullList<ItemStack> deliverItems = NonNullList.create();
        List<FluidStack> deliverFluids = new ArrayList<>();

        if (level != null && commodity instanceof FluidCommodity fc) {
            if (!serverOrder && getReservedFluidAmount() < quantity) {
                return TransactionResult.failure("Sell order does not have enough reserved fluid");
            }
            if (!serverOrder) {
                tradeQty = Math.min(tradeQty, getReservedFluidAmount());
            }
            if (tradeQty <= 0) {
                return TransactionResult.failure("Sell order has no reserved fluid");
            }
            deliverFluids = buildFluidDelivery(fc.getFluid(), tradeQty);
            FluidStack merged = TankManager.mergeFluids(deliverFluids);
            int tankSpace = TankManager.hasTank(buyer)
                    ? TankManager.simulateInsertFluidToTanks(level, buyer, merged)
                    : 0;
            if (tankSpace < tradeQty) {
                if (tankSpace <= 0) {
                    return TransactionResult.failure("Buyer does not have enough compatible Tank space");
                }
                tradeQty = tankSpace;
                deliverFluids = buildFluidDelivery(fc.getFluid(), tradeQty);
            }
        } else if (level != null && commodity instanceof ItemCommodity ic && ic.getItem() != null) {
            deliverItems = buildItemDelivery(ic.getItem(), tradeQty);
            if (VaultInventoryOps.total(deliverItems) < tradeQty) {
                return TransactionResult.failure("Sell order does not have enough reserved items");
            }
            int vaultSpace = VaultManager.hasVault(buyer)
                    ? VaultManager.countMaxAcceptableItems(level, buyer, deliverItems)
                    : 0;
            if (vaultSpace < tradeQty) {
                if (vaultSpace <= 0) {
                    return TransactionResult.failure("Buyer does not have a Vault block to receive items");
                }
                tradeQty = vaultSpace;
                deliverItems = buildItemDelivery(ic.getItem(), tradeQty);
            }
        }

        BigDecimal totalPrice = pricePerUnit.multiply(BigDecimal.valueOf(tradeQty));

        if (!buyerAccount.transferTo(sellerAccount, totalPrice,
                TransactionContext.transfer("Purchase of " + commodity.getDisplayName().getString(), buyer))) {
            return TransactionResult.failure("Payment failed");
        }

        int delivered = tradeQty;
        if (level != null) {
            delivered = commitDelivery(level, buyer, deliverItems, deliverFluids, tradeQty);
            if (delivered < tradeQty) {
                BigDecimal shortfallRefund = pricePerUnit.multiply(BigDecimal.valueOf((long) tradeQty - delivered));
                sellerAccount.transferTo(buyerAccount, shortfallRefund,
                        TransactionContext.transfer("Refund - partial delivery", buyer));
                Economy.LOGGER.warn("Partial delivery on sell order {}: {}/{} units delivered; refunded {}",
                        orderId, delivered, tradeQty, shortfallRefund.toPlainString());
            }
        }

        consumeEscrow(delivered);
        this.quantity -= delivered;

        BigDecimal chargedTotal = pricePerUnit.multiply(BigDecimal.valueOf(delivered));
        TradeLedger.recordTrade(commodity.getId().toString(), pricePerUnit, delivered, buyer, owner);
        notifyPlayerTrade(level, buyer, owner, true, commodity.getDisplayName().getString(), commodity instanceof FluidCommodity, delivered, pricePerUnit, chargedTotal);
        notifyPlayerTrade(level, owner, buyer, false, commodity.getDisplayName().getString(), commodity instanceof FluidCommodity, delivered, pricePerUnit, chargedTotal);
        return TransactionResult.success("Purchase successful", chargedTotal, delivered);
    }

    private int commitDelivery(ServerLevel level, UUID receiver,
                               NonNullList<ItemStack> deliverItems, List<FluidStack> deliverFluids,
                               int requestedQty) {
        if (deliverItems.isEmpty() && deliverFluids.isEmpty()) {
            return requestedQty;
        }
        int delivered = 0;
        if (!deliverItems.isEmpty()) {
            NonNullList<ItemStack> leftover = VaultManager.insertItemStacksToVaults(level, receiver, deliverItems);
            delivered = VaultInventoryOps.total(deliverItems) - VaultInventoryOps.total(leftover);
        } else {
            for (FluidStack fs : deliverFluids) {
                delivered += TankManager.insertFluidToTanks(level, receiver, fs);
            }
        }
        return Math.min(delivered, requestedQty);
    }

    private TransactionResult executeBuy(UUID seller, IAccountManager accounts, Item item, ServerLevel level) {
        IBankAccount buyerAccount = serverOrder
                ? accounts.getServerAccount()
                : accounts.getOrCreatePlayerAccount(owner);
        IBankAccount sellerAccount = accounts.getOrCreatePlayerAccount(seller);

        int tradeQty = this.quantity;

        if (level != null && commodity instanceof ItemCommodity ic && ic.getItem() != null) {
            Item it = ic.getItem();
            if (!VaultManager.hasVault(seller)) {
                return TransactionResult.failure("You do not have a Vault block with the required items");
            }
            if (VaultManager.countItemInVaults(level, seller, it) < tradeQty) {
                return TransactionResult.failure("Not enough items in your vault(s)");
            }
            if (!serverOrder) {
                int buyerSpace = VaultManager.hasVault(owner)
                        ? VaultManager.countMaxAcceptableItems(level, owner, generateItemStacks(it, tradeQty))
                        : 0;
                if (buyerSpace <= 0) {
                    return TransactionResult.failure("Buyer's vault is full or missing");
                }
                tradeQty = Math.min(tradeQty, buyerSpace);
            }
        } else if (level != null && commodity instanceof FluidCommodity fc) {
            Fluid f = fc.getFluid();
            if (!TankManager.hasTank(seller)) {
                return TransactionResult.failure("You do not have a Tank block with the required fluid");
            }
            if (TankManager.countFluidInTanks(level, seller, f) < tradeQty) {
                return TransactionResult.failure("Not enough fluid in your tank(s)");
            }
            if (!serverOrder) {
                int buyerSpace = TankManager.hasTank(owner)
                        ? TankManager.simulateInsertFluidToTanks(level, owner, new FluidStack(f, tradeQty))
                        : 0;
                if (buyerSpace <= 0) {
                    return TransactionResult.failure("Buyer's tank is full or missing");
                }
                tradeQty = Math.min(tradeQty, buyerSpace);
            }
        }

        BigDecimal totalPrice = pricePerUnit.multiply(BigDecimal.valueOf(tradeQty));

        if (!serverOrder && !buyerAccount.hasSufficientFunds(totalPrice)) {
            tradeQty = capByFunds(tradeQty, buyerAccount);
            if (tradeQty <= 0) {
                return TransactionResult.failure("Buyer has insufficient funds");
            }
            totalPrice = pricePerUnit.multiply(BigDecimal.valueOf(tradeQty));
        }

        int delivered;
        if (level == null || serverOrder) {
            delivered = tradeQty;
        } else if (commodity instanceof ItemCommodity ic && ic.getItem() != null) {
            NonNullList<ItemStack> extracted = NonNullList.create();
            if (!VaultManager.extractItemFromVaults(level, seller, ic.getItem(), tradeQty, extracted)) {
                return TransactionResult.failure("Failed to extract items from vault(s)");
            }
            NonNullList<ItemStack> leftover = VaultManager.insertItemStacksToVaults(level, owner, extracted);
            delivered = VaultInventoryOps.total(extracted) - VaultInventoryOps.total(leftover);
            if (!leftover.isEmpty()) {
                VaultManager.insertItemStacksToVaults(level, seller, leftover);
            }
        } else if (commodity instanceof FluidCommodity fc) {
            List<FluidStack> extracted = new ArrayList<>();
            int drained = TankManager.extractFluidFromTanks(level, seller, fc.getFluid(), tradeQty, extracted);
            if (drained < tradeQty) {
                for (FluidStack fs : extracted) {
                    TankManager.restoreFluidToTanks(level, seller, fs);
                }
                return TransactionResult.failure("Failed to extract fluid from tank(s)");
            }
            delivered = 0;
            for (FluidStack fs : extracted) {
                delivered += TankManager.insertFluidToTanks(level, owner, fs);
            }
            if (delivered < drained) {
                TankManager.restoreFluidToTanks(level, seller, new FluidStack(fc.getFluid(), drained - delivered));
            }
        } else {
            delivered = tradeQty;
        }

        BigDecimal chargeTotal = pricePerUnit.multiply(BigDecimal.valueOf(delivered));
        if (delivered <= 0) {
            return TransactionResult.failure("Nothing could be delivered");
        }

        if (!buyerAccount.transferTo(sellerAccount, chargeTotal,
                TransactionContext.transfer("Sale of " + commodity.getDisplayName().getString(), owner))) {
            Economy.LOGGER.error("Payment failed after goods were delivered for buy order {}; attempting rollback", orderId);
            rollbackDelivery(level, seller, owner, commodity, delivered);
            return TransactionResult.failure("Payment failed");
        }

        if (!isInfinite) {
            this.quantity -= delivered;
        }
        TradeLedger.recordTrade(commodity.getId().toString(), pricePerUnit, delivered, owner, seller);
        notifyPlayerTrade(level, owner, seller, true, commodity.getDisplayName().getString(), commodity instanceof FluidCommodity, delivered, pricePerUnit, chargeTotal);
        notifyPlayerTrade(level, seller, owner, false, commodity.getDisplayName().getString(), commodity instanceof FluidCommodity, delivered, pricePerUnit, chargeTotal);
        return TransactionResult.success("Sale successful", chargeTotal, delivered);
    }

    private void rollbackDelivery(ServerLevel level, UUID seller, UUID buyer, ICommodity commodity, int qty) {
        if (level == null || qty <= 0) return;
        if (commodity instanceof ItemCommodity ic && ic.getItem() != null) {
            NonNullList<ItemStack> clawedBack = NonNullList.create();
            if (VaultManager.extractItemFromVaults(level, buyer, ic.getItem(), qty, clawedBack)) {
                VaultManager.insertItemStacksToVaults(level, seller, clawedBack);
            } else {
                Economy.LOGGER.error("Could not claw back {} x item {} from buyer {} after failed payment for order {}",
                        qty, ic.getId(), buyer, orderId);
            }
        } else if (commodity instanceof FluidCommodity fc) {
            List<FluidStack> drained = new ArrayList<>();
            int amount = TankManager.extractFluidFromTanks(level, buyer, fc.getFluid(), qty, drained);
            if (amount > 0) {
                TankManager.restoreFluidToTanks(level, seller, new FluidStack(fc.getFluid(), amount));
            } else {
                Economy.LOGGER.error("Could not claw back {} mB of {} from buyer {} after failed payment for order {}",
                        qty, fc.getId(), buyer, orderId);
            }
        }
    }

    public TransactionResult executePartial(UUID trader, int amountToTrade, ServerLevel level) {
        if (!isValid() || owner.equals(trader) || amountToTrade <= 0) {
            return TransactionResult.failure("Invalid partial execution request");
        }
        int tradeQty = isInfinite ? amountToTrade : Math.min(this.quantity, amountToTrade);
        if (tradeQty <= 0) return TransactionResult.failure("Nothing to trade");

        IAccountManager accounts = IAccountManager.getInstance();
        boolean isItem = commodity instanceof ItemCommodity;
        boolean isFluid = commodity instanceof FluidCommodity;
        Item item = isItem ? ((ItemCommodity) commodity).getItem() : null;
        Fluid fluid = isFluid ? ((FluidCommodity) commodity).getFluid() : null;

        if (type == OrderType.SELL) {
            return executePartialSell(trader, level, accounts, tradeQty, isItem, isFluid, item, fluid);
        } else {
            return executePartialBuy(trader, level, accounts, tradeQty, isItem, isFluid, item, fluid);
        }
    }

    private TransactionResult executePartialSell(UUID trader, ServerLevel level, IAccountManager accounts,
                                                 int tradeQty, boolean isItem, boolean isFluid,
                                                 Item item, Fluid fluid) {
        boolean isServerBuyer = OrderManager.SERVER_ID.equals(trader);
        IBankAccount sellerAccount = accounts.getOrCreatePlayerAccount(owner);
        IBankAccount buyerAccount = isServerBuyer ? accounts.getServerAccount() : accounts.getOrCreatePlayerAccount(trader);

        if (!isServerBuyer) {
            tradeQty = capByFunds(tradeQty, buyerAccount);
            if (tradeQty <= 0) return TransactionResult.failure("Buyer has insufficient funds");
        }
        if (isFluid && !serverOrder) {
            tradeQty = Math.min(tradeQty, getReservedFluidAmount());
            if (tradeQty <= 0) return TransactionResult.failure("Sell order has no reserved fluid");
        }

        NonNullList<ItemStack> deliverItems = NonNullList.create();
        List<FluidStack> deliverFluids = new ArrayList<>();

        if (!isServerBuyer && level != null) {
            if (isItem && item != null) {
                deliverItems = buildItemDelivery(item, tradeQty);
                if (VaultInventoryOps.total(deliverItems) < tradeQty) {
                    return TransactionResult.failure("Sell order does not have enough reserved items");
                }
                int vaultSpace = VaultManager.hasVault(trader)
                        ? VaultManager.countMaxAcceptableItems(level, trader, deliverItems)
                        : 0;
                if (vaultSpace < tradeQty) {
                    if (vaultSpace <= 0) return TransactionResult.failure("Buyer has no Vault block");
                    tradeQty = vaultSpace;
                    deliverItems = buildItemDelivery(item, tradeQty);
                }
            } else if (isFluid && fluid != null) {
                deliverFluids = buildFluidDelivery(fluid, tradeQty);
                int tankSpace = TankManager.hasTank(trader)
                        ? TankManager.simulateInsertFluidToTanks(level, trader, TankManager.mergeFluids(deliverFluids))
                        : 0;
                if (tankSpace < tradeQty) {
                    if (tankSpace <= 0) return TransactionResult.failure("Buyer has no Tank block");
                    tradeQty = tankSpace;
                    deliverFluids = buildFluidDelivery(fluid, tradeQty);
                }
            }
        } else if (!isServerBuyer && level == null && !reservedItems.isEmpty()) {
            tradeQty = Math.min(tradeQty, VaultInventoryOps.total(reservedItems));
            if (tradeQty <= 0) return TransactionResult.failure("Sell order has no reserved items");
        }

        if (tradeQty <= 0) return TransactionResult.failure("Nothing to trade");

        BigDecimal totalPrice = pricePerUnit.multiply(BigDecimal.valueOf(tradeQty));

        if (!buyerAccount.transferTo(sellerAccount, totalPrice,
                TransactionContext.transfer("Purchase of " + commodity.getDisplayName().getString(), trader))) {
            return TransactionResult.failure("Payment failed");
        }

        int delivered = tradeQty;
        if (!isServerBuyer && level != null) {
            delivered = commitDelivery(level, trader, deliverItems, deliverFluids, tradeQty);
            if (delivered < tradeQty) {
                BigDecimal shortfallRefund = pricePerUnit.multiply(BigDecimal.valueOf((long) tradeQty - delivered));
                sellerAccount.transferTo(buyerAccount, shortfallRefund,
                        TransactionContext.transfer("Refund - partial delivery", trader));
                Economy.LOGGER.warn("Partial delivery on sell order {}: {}/{} units delivered; refunded {}",
                        orderId, delivered, tradeQty, shortfallRefund.toPlainString());
            }
        }

        consumeEscrow(delivered);

        this.quantity -= delivered;
        BigDecimal chargedTotal = pricePerUnit.multiply(BigDecimal.valueOf(delivered));
        TradeLedger.recordTrade(commodity.getId().toString(), pricePerUnit, delivered, trader, owner);
        notifyPlayerTrade(level, trader, owner, true, commodity.getDisplayName().getString(), commodity instanceof FluidCommodity, delivered, pricePerUnit, chargedTotal);
        notifyPlayerTrade(level, owner, trader, false, commodity.getDisplayName().getString(), commodity instanceof FluidCommodity, delivered, pricePerUnit, chargedTotal);
        if (level != null) {
            com.nstut.economy.data.EconomyAccountData.recordSnapshot(trader, level);
            com.nstut.economy.data.EconomyAccountData.recordSnapshot(owner, level);
        }
        return TransactionResult.success("Purchase successful", chargedTotal, delivered);
    }

    private TransactionResult executePartialBuy(UUID trader, ServerLevel level, IAccountManager accounts,
                                                int tradeQty, boolean isItem, boolean isFluid,
                                                Item item, Fluid fluid) {
        IBankAccount buyerAccount = serverOrder ? accounts.getServerAccount() : accounts.getOrCreatePlayerAccount(owner);
        IBankAccount sellerAccount = accounts.getOrCreatePlayerAccount(trader);

        if (level != null) {
            if (isItem && item != null) {
                int availableInVault = VaultManager.countItemInVaults(level, trader, item);
                if (availableInVault <= 0) return TransactionResult.failure("No items in seller vault(s)");
                tradeQty = Math.min(tradeQty, availableInVault);
                if (!serverOrder) {
                    int buyerSpace = VaultManager.hasVault(owner)
                            ? VaultManager.countMaxAcceptableItems(level, owner, generateItemStacks(item, tradeQty))
                            : 0;
                    if (buyerSpace <= 0) return TransactionResult.failure("Buyer has no Vault block");
                    tradeQty = Math.min(tradeQty, buyerSpace);
                }
            } else if (isFluid && fluid != null) {
                int availableInTank = TankManager.countFluidInTanks(level, trader, fluid);
                if (availableInTank <= 0) return TransactionResult.failure("No fluid in seller tank(s)");
                tradeQty = Math.min(tradeQty, availableInTank);
                if (!serverOrder) {
                    int buyerSpace = TankManager.hasTank(owner)
                            ? TankManager.simulateInsertFluidToTanks(level, owner, new FluidStack(fluid, tradeQty))
                            : 0;
                    if (buyerSpace <= 0) return TransactionResult.failure("Buyer has no compatible Tank space");
                    tradeQty = Math.min(tradeQty, buyerSpace);
                }
            }
        }

        if (!serverOrder && !buyerAccount.hasSufficientFunds(pricePerUnit.multiply(BigDecimal.valueOf(tradeQty)))) {
            tradeQty = capByFunds(tradeQty, buyerAccount);
            if (tradeQty <= 0) return TransactionResult.failure("Buyer has insufficient funds");
        }
        if (tradeQty <= 0) return TransactionResult.failure("Nothing to trade");

        BigDecimal totalPrice = pricePerUnit.multiply(BigDecimal.valueOf(tradeQty));

        if (!buyerAccount.transferTo(sellerAccount, totalPrice,
                TransactionContext.transfer("Sale of " + commodity.getDisplayName().getString(), owner))) {
            return TransactionResult.failure("Payment failed");
        }

        int delivered = tradeQty;
        if (level != null) {
            if (isItem && item != null) {
                NonNullList<ItemStack> extracted = NonNullList.create();
                if (!VaultManager.extractItemFromVaults(level, trader, item, tradeQty, extracted)) {
                    refund(buyerAccount, sellerAccount, trader, totalPrice, "Refund - extraction failed");
                    return TransactionResult.failure("Failed to extract items from seller vault(s)");
                }
                if (serverOrder) {
                    delivered = tradeQty;
                } else {
                    NonNullList<ItemStack> leftover = VaultManager.insertItemStacksToVaults(level, owner, extracted);
                    delivered = VaultInventoryOps.total(extracted) - VaultInventoryOps.total(leftover);
                    if (!leftover.isEmpty()) {
                        VaultManager.insertItemStacksToVaults(level, trader, leftover);
                    }
                }
            } else if (isFluid && fluid != null) {
                List<FluidStack> extracted = new ArrayList<>();
                int drained = TankManager.extractFluidFromTanks(level, trader, fluid, tradeQty, extracted);
                if (drained < tradeQty) {
                    for (FluidStack fs : extracted) {
                        TankManager.restoreFluidToTanks(level, trader, fs);
                    }
                    refund(buyerAccount, sellerAccount, trader, totalPrice, "Refund - extraction failed");
                    return TransactionResult.failure("Failed to extract fluid from seller tank(s)");
                }
                if (serverOrder) {
                    delivered = drained;
                } else {
                    delivered = 0;
                    for (FluidStack fs : extracted) {
                        delivered += TankManager.insertFluidToTanks(level, owner, fs);
                    }
                    if (delivered < drained) {
                        TankManager.restoreFluidToTanks(level, trader, new FluidStack(fluid, drained - delivered));
                    }
                }
            }

            if (delivered < tradeQty) {
                BigDecimal shortfallRefund = pricePerUnit.multiply(BigDecimal.valueOf((long) tradeQty - delivered));
                refund(buyerAccount, sellerAccount, trader, shortfallRefund, "Refund - partial delivery");
                Economy.LOGGER.warn("Partial delivery on buy order {}: {}/{} units delivered; refunded {}",
                        orderId, delivered, tradeQty, shortfallRefund.toPlainString());
            }
        }

        if (delivered <= 0) {
            return TransactionResult.failure("Nothing could be delivered");
        }

        if (!isInfinite) {
            this.quantity -= delivered;
        }
        BigDecimal chargedTotal = pricePerUnit.multiply(BigDecimal.valueOf(delivered));
        TradeLedger.recordTrade(commodity.getId().toString(), pricePerUnit, delivered, owner, trader);
        notifyPlayerTrade(level, owner, trader, true, commodity.getDisplayName().getString(), commodity instanceof FluidCommodity, delivered, pricePerUnit, chargedTotal);
        notifyPlayerTrade(level, trader, owner, false, commodity.getDisplayName().getString(), commodity instanceof FluidCommodity, delivered, pricePerUnit, chargedTotal);
        if (level != null) {
            com.nstut.economy.data.EconomyAccountData.recordSnapshot(owner, level);
            com.nstut.economy.data.EconomyAccountData.recordSnapshot(trader, level);
        }
        return TransactionResult.success("Sale successful", chargedTotal, delivered);
    }

    private void refund(IBankAccount from, IBankAccount to, UUID initiator, BigDecimal amount, String reason) {
        if (amount.signum() <= 0) return;
        if (!from.transferTo(to, amount, TransactionContext.transfer(reason, initiator))) {
            Economy.LOGGER.error("FAILED TO REFUND {} coins ({}) on order {} from {} to {}",
                    amount.toPlainString(), reason, orderId, from, to);
        }
    }

    private static void notifyPlayerTrade(ServerLevel level, UUID playerUUID, UUID counterpartyUUID,
                                          boolean isBuy, String itemName, boolean isFluid, int qty,
                                          BigDecimal pricePerUnit, BigDecimal totalPrice) {
        if (level == null || playerUUID == null) return;
        net.minecraft.server.level.ServerPlayer p = level.getServer().getPlayerList().getPlayer(playerUUID);
        if (p != null) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(),
                com.nstut.economy.sound.SoundRegistries.MONEY.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.0F);

            String counterpartyName = getPlayerName(level, counterpartyUUID);
            String action = isBuy ? "Bought" : "Sold";
            String prep = isBuy ? "from" : "to";
            String formattedTotal = com.nstut.economy.util.EconomyFormatUtil.formatCompact(totalPrice);
            String formattedUnit = com.nstut.economy.util.EconomyFormatUtil.formatCompact(pricePerUnit);
            String formattedQuantity = com.nstut.economy.util.EconomyFormatUtil
                    .formatCommodityQuantity(qty, isFluid);

            String costDetails = (qty > 1)
                ? ("§e" + formattedUnit + (isFluid ? " §fcoins per mB" : " §fcoins each")
                    + " (Total: §e" + formattedTotal + " §fcoins)")
                : ("§e" + formattedTotal + " §fcoins");

            net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.literal(
                "§2[Market] §aOrder Matched! §f" + action + " §e" + formattedQuantity + " of "
                    + itemName + " §ffor " + costDetails + " " + prep + " §b" + counterpartyName + "§f."
            );
            p.sendSystemMessage(msg);
        }
    }

    private static String getPlayerName(ServerLevel level, UUID uuid) {
        if (uuid == null || OrderManager.SERVER_ID.equals(uuid)) {
            return "Server";
        }
        net.minecraft.server.level.ServerPlayer p = level.getServer().getPlayerList().getPlayer(uuid);
        if (p != null) {
            return p.getName().getString();
        }
        if (level.getServer() != null && level.getServer().getProfileCache() != null) {
            var profileOpt = level.getServer().getProfileCache().get(uuid);
            if (profileOpt != null && profileOpt.isPresent()) {
                return profileOpt.get().getName();
            }
        }
        return "Server";
    }

    private int capByFunds(int tradeQty, IBankAccount account) {
        if (pricePerUnit == null || pricePerUnit.signum() <= 0) return 0;
        BigDecimal balance = account.getBalance();
        if (balance.signum() <= 0) return 0;
        BigDecimal affordable = balance.divide(pricePerUnit, 0, java.math.RoundingMode.DOWN);
        return Math.min(tradeQty, Math.max(0, affordable.intValue()));
    }

    private int getReservedFluidAmount() {
        int total = 0;
        for (FluidStack stack : reservedFluids) {
            if (!stack.isEmpty()) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /**
     * Builds the exact stacks that would be delivered for {@code count} units
     * without mutating escrow. Player orders deliver from reserved items;
     * server orders generate fresh stacks.
     */
    private NonNullList<ItemStack> buildItemDelivery(Item item, int count) {
        NonNullList<ItemStack> delivery = NonNullList.create();
        if (!reservedItems.isEmpty()) {
            int needed = count;
            for (ItemStack stack : reservedItems) {
                if (needed <= 0) break;
                if (stack == null || stack.isEmpty()) continue;
                int take = Math.min(needed, stack.getCount());
                ItemStack part = stack.copy();
                part.setCount(take);
                delivery.add(part);
                needed -= take;
            }
        } else if (serverOrder && item != null) {
            delivery = generateItemStacks(item, count);
        }
        return delivery;
    }

    private List<FluidStack> buildFluidDelivery(Fluid fluid, int count) {
        List<FluidStack> delivery = new ArrayList<>();
        if (!reservedFluids.isEmpty()) {
            int needed = count;
            for (FluidStack fs : reservedFluids) {
                if (needed <= 0) break;
                if (fs == null || fs.isEmpty()) continue;
                int take = Math.min(needed, fs.getAmount());
                FluidStack part = fs.copy();
                part.setAmount(take);
                delivery.add(part);
                needed -= take;
            }
        } else if (fluid != null && count > 0) {
            delivery.add(new FluidStack(fluid, count));
        }
        return delivery;
    }

    /**
     * Removes exactly {@code qty} units from reserved escrow (items first, then
     * fluids). Called only after the corresponding goods were verifiably
     * delivered or consumed, so escrow can never exceed remaining obligations.
     */
    public void consumeEscrow(int qty) {
        int itemsNeeded = qty;
        var itemIt = reservedItems.iterator();
        while (itemIt.hasNext() && itemsNeeded > 0) {
            ItemStack stack = itemIt.next();
            if (stack == null || stack.isEmpty()) {
                itemIt.remove();
                continue;
            }
            int take = Math.min(itemsNeeded, stack.getCount());
            stack.shrink(take);
            itemsNeeded -= take;
            if (stack.isEmpty()) itemIt.remove();
        }
        int fluidNeeded = itemsNeeded > 0 ? itemsNeeded : 0;
        var fluidIt = reservedFluids.iterator();
        while (fluidIt.hasNext() && fluidNeeded > 0) {
            FluidStack fs = fluidIt.next();
            if (fs == null || fs.isEmpty()) {
                fluidIt.remove();
                continue;
            }
            int take = Math.min(fluidNeeded, fs.getAmount());
            fs.shrink(take);
            fluidNeeded -= take;
            if (fs.isEmpty()) fluidIt.remove();
        }
    }

    public int getEscrowedItemCount() {
        return com.nstut.economy.blocks.VaultInventoryOps.total(reservedItems);
    }

    private static NonNullList<ItemStack> generateItemStacks(Item item, int count) {
        NonNullList<ItemStack> stacks = NonNullList.create();
        int maxStack = item.getMaxStackSize();
        int remaining = count;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, maxStack);
            stacks.add(new ItemStack(item, stackSize));
            remaining -= stackSize;
        }
        return stacks;
    }

    public NonNullList<ItemStack> getReservedItems() {
        return reservedItems;
    }

    public List<FluidStack> getReservedFluids() {
        return reservedFluids;
    }

    @Override
    public boolean cancel() {
        if (cancelled || quantity <= 0) {
            return false;
        }
        this.cancelled = true;
        this.quantity = 0;
        return true;
    }

    public void reduceQuantity(int amount) {
        this.quantity = Math.max(0, this.quantity - amount);
    }
}
