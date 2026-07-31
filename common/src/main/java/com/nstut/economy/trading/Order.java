package com.nstut.economy.trading;

import com.nstut.economy.api.IAccountManager;
import com.nstut.economy.api.IBankAccount;
import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.IOrder;
import com.nstut.economy.blocks.TankManager;
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
        BigDecimal totalPrice = getTotalPrice();

        if (level != null && commodity instanceof FluidCommodity fc) {
            if (!serverOrder && getReservedFluidAmount() < quantity) {
                return TransactionResult.failure("Sell order does not have enough reserved fluid");
            }
            if (TankManager.countAvailableFluidSpaceInTanks(level, buyer, fc.getFluid()) < quantity) {
                return TransactionResult.failure("Buyer does not have enough compatible Tank space");
            }
        }

        if (!buyerAccount.transferTo(sellerAccount, totalPrice,
                TransactionContext.transfer("Purchase of " + commodity.getDisplayName().getString(), buyer))) {
            return TransactionResult.failure("Payment failed");
        }

        if (level != null) {
            if (commodity instanceof ItemCommodity ic && ic.getItem() != null) {
                NonNullList<ItemStack> deliverItems = getItemsToDeliver(ic.getItem(), quantity);
                if (!deliverItems.isEmpty()) {
                    if (!VaultManager.hasVault(buyer)) {
                        sellerAccount.transferTo(buyerAccount, totalPrice,
                                TransactionContext.transfer("Refund - buyer has no vault", buyer));
                        return TransactionResult.failure("Buyer does not have a Vault block to receive items");
                    }
                    if (!VaultManager.insertItemStacksToVaults(level, buyer, deliverItems)) {
                        sellerAccount.transferTo(buyerAccount, totalPrice,
                                TransactionContext.transfer("Refund - buyer vault full", buyer));
                        return TransactionResult.failure("Buyer's vault is full");
                    }
                }
            } else if (commodity instanceof FluidCommodity fc) {
                int delivered = 0;
                int remaining = quantity;
                if (serverOrder) {
                    delivered = TankManager.insertFluidToTanks(level, buyer, new FluidStack(fc.getFluid(), quantity));
                    remaining -= delivered;
                } else {
                    for (FluidStack fs : reservedFluids) {
                        int take = Math.min(remaining, fs.getAmount());
                        FluidStack toDeliver = fs.copy();
                        toDeliver.setAmount(take);
                        int inserted = TankManager.insertFluidToTanks(level, buyer, toDeliver);
                        delivered += inserted;
                        remaining -= inserted;
                        if (remaining <= 0) break;
                    }
                }
                if (delivered < quantity) {
                    sellerAccount.transferTo(buyerAccount, totalPrice,
                            TransactionContext.transfer("Refund - buyer tank full", buyer));
                    return TransactionResult.failure("Buyer's tank is full");
                }
            }
        }

        int tradedQty = this.quantity;
        this.quantity = 0;
        TradeLedger.recordTrade(commodity.getId().toString(), pricePerUnit, tradedQty, buyer, owner);
        notifyPlayerTrade(level, buyer, owner, true, commodity.getDisplayName().getString(), commodity instanceof FluidCommodity, tradedQty, pricePerUnit, totalPrice);
        notifyPlayerTrade(level, owner, buyer, false, commodity.getDisplayName().getString(), commodity instanceof FluidCommodity, tradedQty, pricePerUnit, totalPrice);
        return TransactionResult.success("Purchase successful", totalPrice, tradedQty);
    }

    private TransactionResult executeBuy(UUID seller, IAccountManager accounts, Item item, ServerLevel level) {
        IBankAccount buyerAccount = serverOrder
                ? accounts.getServerAccount()
                : accounts.getOrCreatePlayerAccount(owner);
        IBankAccount sellerAccount = accounts.getOrCreatePlayerAccount(seller);
        BigDecimal totalPrice = getTotalPrice();

        if (level != null) {
            if (commodity instanceof ItemCommodity ic && ic.getItem() != null) {
                Item it = ic.getItem();
                if (!VaultManager.hasVault(seller)) {
                    return TransactionResult.failure("You do not have a Vault block with the required items");
                }
                if (VaultManager.countItemInVaults(level, seller, it) < quantity) {
                    return TransactionResult.failure("Not enough items in your vault(s)");
                }

                NonNullList<ItemStack> extracted = NonNullList.create();
                if (!VaultManager.extractItemFromVaults(level, seller, it, quantity, extracted)) {
                    return TransactionResult.failure("Failed to extract items from vault(s)");
                }

                if (!serverOrder) {
                    if (!VaultManager.hasVault(owner) || !VaultManager.insertItemStacksToVaults(level, owner, copyStacks(extracted))) {
                        VaultManager.insertItemStacksToVaults(level, seller, extracted);
                        return TransactionResult.failure("Buyer's vault is full or missing");
                    }
                }
            } else if (commodity instanceof FluidCommodity fc) {
                Fluid f = fc.getFluid();
                if (!TankManager.hasTank(seller)) {
                    return TransactionResult.failure("You do not have a Tank block with the required fluid");
                }
                if (TankManager.countFluidInTanks(level, seller, f) < quantity) {
                    return TransactionResult.failure("Not enough fluid in your tank(s)");
                }
                if (!serverOrder
                        && TankManager.countAvailableFluidSpaceInTanks(level, owner, f) < quantity) {
                    return TransactionResult.failure("Buyer's tank is full or missing");
                }

                List<FluidStack> extracted = new ArrayList<>();
                int drained = TankManager.extractFluidFromTanks(level, seller, f, quantity, extracted);
                if (drained < quantity) {
                    for (FluidStack fs : extracted) {
                        TankManager.restoreFluidToTanks(level, seller, fs);
                    }
                    return TransactionResult.failure("Failed to extract fluid from tank(s)");
                }

                if (!serverOrder) {
                    if (!TankManager.hasTank(owner)) {
                        for (FluidStack fs : extracted) {
                            TankManager.restoreFluidToTanks(level, seller, fs);
                        }
                        return TransactionResult.failure("Buyer's tank is missing");
                    }
                    int totalInserted = 0;
                    for (FluidStack fs : extracted) {
                        totalInserted += TankManager.insertFluidToTanks(level, owner, fs);
                    }
                    if (totalInserted < drained) {
                        return TransactionResult.failure("Buyer's tank is full");
                    }
                }
            }
        }

        if (!buyerAccount.transferTo(sellerAccount, totalPrice,
                TransactionContext.transfer("Sale of " + commodity.getDisplayName().getString(), owner))) {
            return TransactionResult.failure("Payment failed");
        }

        int tradedQty = this.quantity;
        if (!isInfinite) {
            this.quantity = 0;
        }
        TradeLedger.recordTrade(commodity.getId().toString(), pricePerUnit, tradedQty, owner, seller);
        notifyPlayerTrade(level, owner, seller, true, commodity.getDisplayName().getString(), commodity instanceof FluidCommodity, tradedQty, pricePerUnit, totalPrice);
        notifyPlayerTrade(level, seller, owner, false, commodity.getDisplayName().getString(), commodity instanceof FluidCommodity, tradedQty, pricePerUnit, totalPrice);
        return TransactionResult.success("Sale successful", totalPrice, tradedQty);
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
            if (!isServerBuyer && level != null) {
                if (isItem && item != null) {
                    ItemStack sampleStack = new ItemStack(item);
                    int vaultSpace = VaultManager.hasVault(trader)
                            ? VaultManager.countAvailableSpaceInVaults(level, trader, sampleStack)
                            : 0;
                    if (vaultSpace < tradeQty) {
                        if (vaultSpace <= 0) return TransactionResult.failure("Buyer has no Vault block");
                        tradeQty = vaultSpace;
                    }
                } else if (isFluid && fluid != null) {
                    int tankSpace = TankManager.hasTank(trader)
                            ? TankManager.countAvailableFluidSpaceInTanks(level, trader, fluid)
                            : 0;
                    if (tankSpace < tradeQty) {
                        if (tankSpace <= 0) return TransactionResult.failure("Buyer has no Tank block");
                        tradeQty = tankSpace;
                    }
                }
            }

            BigDecimal totalPrice = pricePerUnit.multiply(BigDecimal.valueOf(tradeQty));

            if (!buyerAccount.transferTo(sellerAccount, totalPrice,
                    TransactionContext.transfer("Purchase of " + commodity.getDisplayName().getString(), trader))) {
                return TransactionResult.failure("Payment failed");
            }

            if (!isServerBuyer && level != null) {
                if (isItem) {
                    NonNullList<ItemStack> itemsToDeliver = NonNullList.create();
                    if (!reservedItems.isEmpty()) {
                        int itemsNeeded = tradeQty;
                        var it = reservedItems.iterator();
                        while (it.hasNext() && itemsNeeded > 0) {
                            ItemStack stack = it.next();
                            if (stack.isEmpty()) continue;
                            int take = Math.min(itemsNeeded, stack.getCount());
                            ItemStack split = stack.split(take);
                            itemsToDeliver.add(split);
                            itemsNeeded -= take;
                            if (stack.isEmpty()) it.remove();
                        }
                    } else if (serverOrder && item != null) {
                        itemsToDeliver = generateItemStacks(item, tradeQty);
                    }
                    if (!itemsToDeliver.isEmpty() && !VaultManager.insertItemStacksToVaults(level, trader, itemsToDeliver)) {
                        sellerAccount.transferTo(buyerAccount, totalPrice,
                                TransactionContext.transfer("Refund - buyer vault full", trader));
                        if (!reservedItems.isEmpty()) {
                            for (ItemStack s : itemsToDeliver) reservedItems.add(s);
                        }
                        return TransactionResult.failure("Buyer's vault is full");
                    }
                } else if (isFluid) {
                    int fluidToDeliver = tradeQty;
                    if (serverOrder && reservedFluids.isEmpty() && fluid != null) {
                        fluidToDeliver -= TankManager.insertFluidToTanks(
                                level, trader, new FluidStack(fluid, tradeQty));
                    } else if (!reservedFluids.isEmpty()) {
                        var it = reservedFluids.iterator();
                        while (it.hasNext() && fluidToDeliver > 0) {
                            FluidStack fs = it.next();
                            int take = Math.min(fluidToDeliver, fs.getAmount());
                            FluidStack toDeliver = fs.copy();
                            toDeliver.setAmount(take);
                            int inserted = TankManager.insertFluidToTanks(level, trader, toDeliver);
                            fluidToDeliver -= inserted;
                            if (inserted > 0) {
                                fs.shrink(inserted);
                                if (fs.isEmpty()) it.remove();
                            }
                        }
                    }
                    if (fluidToDeliver > 0) {
                        sellerAccount.transferTo(buyerAccount, totalPrice,
                                TransactionContext.transfer("Refund - buyer tank full", trader));
                        return TransactionResult.failure("Buyer's tank is full");
                    }
                }
            } else if (isServerBuyer && !reservedItems.isEmpty()) {
                int itemsNeeded = tradeQty;
                var it = reservedItems.iterator();
                while (it.hasNext() && itemsNeeded > 0) {
                    ItemStack stack = it.next();
                    if (stack.isEmpty()) continue;
                    int take = Math.min(itemsNeeded, stack.getCount());
                    stack.shrink(take);
                    itemsNeeded -= take;
                    if (stack.isEmpty()) it.remove();
                }
            } else if (isServerBuyer && !reservedFluids.isEmpty()) {
                int fluidNeeded = tradeQty;
                var it = reservedFluids.iterator();
                while (it.hasNext() && fluidNeeded > 0) {
                    FluidStack fs = it.next();
                    int take = Math.min(fluidNeeded, fs.getAmount());
                    fs.shrink(take);
                    fluidNeeded -= take;
                    if (fs.isEmpty()) it.remove();
                }
            }

            this.quantity -= tradeQty;
            TradeLedger.recordTrade(commodity.getId().toString(), pricePerUnit, tradeQty, trader, owner);
            notifyPlayerTrade(level, trader, owner, true, commodity.getDisplayName().getString(), commodity instanceof FluidCommodity, tradeQty, pricePerUnit, totalPrice);
            notifyPlayerTrade(level, owner, trader, false, commodity.getDisplayName().getString(), commodity instanceof FluidCommodity, tradeQty, pricePerUnit, totalPrice);
            if (level != null) {
                com.nstut.economy.data.EconomyAccountData.recordSnapshot(trader, level);
                com.nstut.economy.data.EconomyAccountData.recordSnapshot(owner, level);
            }
            return TransactionResult.success("Purchase successful", totalPrice, tradeQty);
        } else {
            IBankAccount buyerAccount = serverOrder ? accounts.getServerAccount() : accounts.getOrCreatePlayerAccount(owner);
            IBankAccount sellerAccount = accounts.getOrCreatePlayerAccount(trader);

            if (level != null) {
                if (isItem && item != null) {
                    int availableInVault = VaultManager.countItemInVaults(level, trader, item);
                    if (availableInVault <= 0) return TransactionResult.failure("No items in seller vault(s)");
                    tradeQty = Math.min(tradeQty, availableInVault);
                } else if (isFluid && fluid != null) {
                    int availableInTank = TankManager.countFluidInTanks(level, trader, fluid);
                    if (availableInTank <= 0) return TransactionResult.failure("No fluid in seller tank(s)");
                    tradeQty = Math.min(tradeQty, availableInTank);
                    if (!serverOrder) {
                        int buyerSpace = TankManager.countAvailableFluidSpaceInTanks(level, owner, fluid);
                        if (buyerSpace <= 0) return TransactionResult.failure("Buyer has no compatible Tank space");
                        tradeQty = Math.min(tradeQty, buyerSpace);
                    }
                }
            }

            if (!serverOrder && !buyerAccount.hasSufficientFunds(pricePerUnit.multiply(BigDecimal.valueOf(tradeQty)))) {
                tradeQty = capByFunds(tradeQty, buyerAccount);
                if (tradeQty <= 0) return TransactionResult.failure("Buyer has insufficient funds");
            }

            BigDecimal totalPrice = pricePerUnit.multiply(BigDecimal.valueOf(tradeQty));

            if (level != null) {
                if (isItem && item != null) {
                    NonNullList<ItemStack> extracted = NonNullList.create();
                    if (!VaultManager.extractItemFromVaults(level, trader, item, tradeQty, extracted)) {
                        return TransactionResult.failure("Failed to extract items from seller vault(s)");
                    }
                    if (!serverOrder) {
                        if (!VaultManager.hasVault(owner) || !VaultManager.insertItemStacksToVaults(level, owner, copyStacks(extracted))) {
                            VaultManager.insertItemStacksToVaults(level, trader, extracted);
                            return TransactionResult.failure("Buyer's vault is full or missing");
                        }
                    }
                } else if (isFluid && fluid != null) {
                    List<FluidStack> extracted = new ArrayList<>();
                    int drained = TankManager.extractFluidFromTanks(level, trader, fluid, tradeQty, extracted);
                    if (drained < tradeQty) {
                        for (FluidStack fs : extracted) {
                            TankManager.restoreFluidToTanks(level, trader, fs);
                        }
                        return TransactionResult.failure("Failed to extract fluid from seller tank(s)");
                    }
                    if (!serverOrder) {
                        if (!TankManager.hasTank(owner)) {
                            for (FluidStack fs : extracted) {
                                TankManager.restoreFluidToTanks(level, trader, fs);
                            }
                            return TransactionResult.failure("Buyer has no Tank block");
                        }
                        int totalInserted = 0;
                        for (FluidStack fs : extracted) {
                            totalInserted += TankManager.insertFluidToTanks(level, owner, fs);
                        }
                        if (totalInserted < drained) {
                            return TransactionResult.failure("Buyer's tank is full");
                        }
                    }
                }
            }

            if (!buyerAccount.transferTo(sellerAccount, totalPrice,
                    TransactionContext.transfer("Sale of " + commodity.getDisplayName().getString(), owner))) {
                return TransactionResult.failure("Payment failed");
            }

            if (!isInfinite) {
                this.quantity -= tradeQty;
            }
            TradeLedger.recordTrade(commodity.getId().toString(), pricePerUnit, tradeQty, owner, trader);
            notifyPlayerTrade(level, owner, trader, true, commodity.getDisplayName().getString(), commodity instanceof FluidCommodity, tradeQty, pricePerUnit, totalPrice);
            notifyPlayerTrade(level, trader, owner, false, commodity.getDisplayName().getString(), commodity instanceof FluidCommodity, tradeQty, pricePerUnit, totalPrice);
            if (level != null) {
                com.nstut.economy.data.EconomyAccountData.recordSnapshot(owner, level);
                com.nstut.economy.data.EconomyAccountData.recordSnapshot(trader, level);
            }
            return TransactionResult.success("Sale successful", totalPrice, tradeQty);
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
        BigDecimal balance = account.getBalance();
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

    private static NonNullList<ItemStack> copyStacks(NonNullList<ItemStack> original) {
        NonNullList<ItemStack> copy = NonNullList.create();
        for (ItemStack stack : original) {
            copy.add(stack.copy());
        }
        return copy;
    }

    private NonNullList<ItemStack> getItemsToDeliver(Item item, int count) {
        if (!reservedItems.isEmpty()) {
            return copyStacks(reservedItems);
        }
        if (serverOrder) {
            return generateItemStacks(item, count);
        }
        return NonNullList.create();
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
