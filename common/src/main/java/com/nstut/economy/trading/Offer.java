package com.nstut.economy.trading;

import com.nstut.economy.api.IAccountManager;
import com.nstut.economy.api.IBankAccount;
import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.IOffer;
import com.nstut.economy.blocks.VaultBlockEntity;
import com.nstut.economy.blocks.VaultManager;
import com.nstut.economy.core.TransactionContext;
import com.nstut.economy.data.EconomyOfferData;
import com.nstut.economy.data.TradeLedger;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Offer implements IOffer {

    private final UUID offerId;
    private final UUID owner;
    private final ICommodity commodity;
    private int quantity;
    private final BigDecimal pricePerUnit;
    private final OfferType type;
    private final Instant createdAt;
    private final Instant expiresAt;
    private boolean cancelled;
    private boolean serverOrder;
    private final NonNullList<ItemStack> reservedItems;

    public Offer(UUID owner, ICommodity commodity, int quantity,
                 BigDecimal pricePerUnit, OfferType type, Instant expiresAt) {
        this(owner, commodity, quantity, pricePerUnit, type, expiresAt, NonNullList.create());
    }

    public Offer(UUID owner, ICommodity commodity, int quantity,
                 BigDecimal pricePerUnit, OfferType type, Instant expiresAt,
                 NonNullList<ItemStack> reservedItems) {
        this.offerId = UUID.randomUUID();
        this.owner = owner;
        this.commodity = commodity;
        this.quantity = quantity;
        this.pricePerUnit = pricePerUnit;
        this.type = type;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
        this.cancelled = false;
        this.reservedItems = reservedItems;
    }

    public static Offer fromSnapshot(EconomyOfferData.OfferSnapshot snap) {
        ResourceLocation rl = new ResourceLocation(snap.itemId);
        Item item = BuiltInRegistries.ITEM.get(rl);
        ItemCommodity commodity = new ItemCommodity(rl, item, BigDecimal.ZERO);
        Instant expires = snap.hasExpiry ? Instant.ofEpochMilli(snap.expiresAt) : null;
        Offer offer = new Offer(snap.owner, commodity, snap.quantity,
            new BigDecimal(snap.pricePerUnit),
            snap.type.equals("SELL") ? OfferType.SELL : OfferType.BUY,
            expires, snap.reservedItems);
        setField(offer, "offerId", snap.offerId);
        setField(offer, "createdAt", Instant.ofEpochMilli(snap.createdAt));
        if (snap.isServerOrder) {
            offer.serverOrder = true;
        }
        return offer;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = Offer.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception ignored) {
        }
    }

    public EconomyOfferData.OfferSnapshot toSnapshot() {
        return new EconomyOfferData.OfferSnapshot(
            offerId, owner, commodity.getId().toString(),
            quantity, pricePerUnit.toPlainString(),
            type.name(), createdAt.toEpochMilli(),
            expiresAt != null ? expiresAt.toEpochMilli() : 0,
            expiresAt != null, reservedItems, serverOrder
        );
    }

    public boolean isServerOrder() {
        return serverOrder;
    }

    public void setServerOrder(boolean serverOrder) {
        this.serverOrder = serverOrder;
    }

    @Override
    public UUID getOfferId() {
        return offerId;
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

    @Override
    public BigDecimal getPricePerUnit() {
        return pricePerUnit;
    }

    @Override
    public OfferType getType() {
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

    @Override
    public boolean isValid() {
        if (cancelled || quantity <= 0) {
            return false;
        }
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return false;
        }
        if (commodity instanceof ItemCommodity ic && ic.getItem() == net.minecraft.world.item.Items.AIR) {
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

        if (type == OfferType.SELL) {
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
            return TransactionResult.failure("Cannot execute this offer");
        }

        IAccountManager accounts = IAccountManager.getInstance();
        Item item = null;
        if (commodity instanceof ItemCommodity ic) {
            item = ic.getItem();
        }

        if (type == OfferType.SELL) {
            return executeSell(trader, accounts, item, level);
        } else {
            return executeBuy(trader, accounts, item, level);
        }
    }

    private TransactionResult executeSell(UUID buyer, IAccountManager accounts, Item item, ServerLevel level) {
        IBankAccount sellerAccount = accounts.getOrCreatePlayerAccount(owner);
        IBankAccount buyerAccount = accounts.getOrCreatePlayerAccount(buyer);
        BigDecimal totalPrice = getTotalPrice();

        if (!buyerAccount.transferTo(sellerAccount, totalPrice,
                TransactionContext.transfer("Purchase of " + commodity.getDisplayName().getString(), buyer))) {
            return TransactionResult.failure("Payment failed");
        }

        if (level != null && item != null) {
            NonNullList<ItemStack> deliverItems = getItemsToDeliver(item, quantity);
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
        }

        int tradedQty = this.quantity;
        this.quantity = 0;
        TradeLedger.recordTrade(commodity.getId().toString(), pricePerUnit, tradedQty, buyer, owner);
        return TransactionResult.success("Purchase successful", totalPrice, tradedQty);
    }

    private TransactionResult executeBuy(UUID seller, IAccountManager accounts, Item item, ServerLevel level) {
        IBankAccount buyerAccount = accounts.getServerAccount();
        IBankAccount sellerAccount = accounts.getOrCreatePlayerAccount(seller);
        BigDecimal totalPrice = getTotalPrice();

        if (level != null && item != null) {
            if (!VaultManager.hasVault(seller)) {
                return TransactionResult.failure("You do not have a Vault block with the required items");
            }
            if (VaultManager.countItemInVaults(level, seller, item) < quantity) {
                return TransactionResult.failure("Not enough items in your vault(s)");
            }

            NonNullList<ItemStack> extracted = NonNullList.create();
            if (!VaultManager.extractItemFromVaults(level, seller, item, quantity, extracted)) {
                return TransactionResult.failure("Failed to extract items from vault(s)");
            }

            if (!serverOrder) {
                if (!VaultManager.hasVault(owner) || !VaultManager.insertItemStacksToVaults(level, owner, copyStacks(extracted))) {
                    VaultManager.insertItemStacksToVaults(level, seller, extracted);
                    return TransactionResult.failure("Buyer's vault is full or missing");
                }
            }
        }

        if (!buyerAccount.transferTo(sellerAccount, totalPrice,
                TransactionContext.transfer("Sale of " + commodity.getDisplayName().getString(), owner))) {
            return TransactionResult.failure("Payment failed");
        }

        int tradedQty = this.quantity;
        this.quantity = 0;
        TradeLedger.recordTrade(commodity.getId().toString(), pricePerUnit, tradedQty, owner, seller);
        return TransactionResult.success("Sale successful", totalPrice, tradedQty);
    }

    public TransactionResult executePartial(UUID trader, int amountToTrade, ServerLevel level) {
        if (!isValid() || owner.equals(trader) || amountToTrade <= 0) {
            return TransactionResult.failure("Invalid partial execution request");
        }
        int tradeQty = Math.min(this.quantity, amountToTrade);
        if (tradeQty <= 0) return TransactionResult.failure("Nothing to trade");

        IAccountManager accounts = IAccountManager.getInstance();
        Item item = (commodity instanceof ItemCommodity ic) ? ic.getItem() : null;

        if (type == OfferType.SELL) {
            boolean isServerBuyer = OfferManager.SERVER_ID.equals(trader);
            IBankAccount sellerAccount = accounts.getOrCreatePlayerAccount(owner);
            IBankAccount buyerAccount = isServerBuyer ? accounts.getServerAccount() : accounts.getOrCreatePlayerAccount(trader);
            BigDecimal totalPrice = pricePerUnit.multiply(BigDecimal.valueOf(tradeQty));

            if (!isServerBuyer && !buyerAccount.hasSufficientFunds(totalPrice)) {
                return TransactionResult.failure("Buyer has insufficient funds");
            }
            if (!buyerAccount.transferTo(sellerAccount, totalPrice,
                    TransactionContext.transfer("Purchase of " + commodity.getDisplayName().getString(), trader))) {
                return TransactionResult.failure("Payment failed");
            }

            if (!isServerBuyer && level != null && item != null) {
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
                } else if (serverOrder) {
                    itemsToDeliver = generateItemStacks(item, tradeQty);
                }
                if (!itemsToDeliver.isEmpty()) {
                    if (!VaultManager.hasVault(trader)) {
                        sellerAccount.transferTo(buyerAccount, totalPrice,
                                TransactionContext.transfer("Refund - buyer has no vault", trader));
                        if (!reservedItems.isEmpty() && !itemsToDeliver.isEmpty()) {
                            for (ItemStack s : itemsToDeliver) reservedItems.add(s);
                        }
                        return TransactionResult.failure("Buyer has no Vault block");
                    }
                    if (!VaultManager.insertItemStacksToVaults(level, trader, itemsToDeliver)) {
                        sellerAccount.transferTo(buyerAccount, totalPrice,
                                TransactionContext.transfer("Refund - buyer vault full", trader));
                        if (!reservedItems.isEmpty()) {
                            for (ItemStack s : itemsToDeliver) reservedItems.add(s);
                        }
                        return TransactionResult.failure("Buyer's vault is full");
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
            }

            this.quantity -= tradeQty;
            TradeLedger.recordTrade(commodity.getId().toString(), pricePerUnit, tradeQty, trader, owner);
            return TransactionResult.success("Purchase successful", totalPrice, tradeQty);
        } else {
            IBankAccount buyerAccount = serverOrder ? accounts.getServerAccount() : accounts.getOrCreatePlayerAccount(owner);
            IBankAccount sellerAccount = accounts.getOrCreatePlayerAccount(trader);
            BigDecimal totalPrice = pricePerUnit.multiply(BigDecimal.valueOf(tradeQty));

            if (!serverOrder && !buyerAccount.hasSufficientFunds(totalPrice)) {
                return TransactionResult.failure("Buyer has insufficient funds");
            }

            if (level != null && item != null) {
                if (!VaultManager.hasVault(trader)) {
                    return TransactionResult.failure("Seller has no Vault block");
                }
                if (VaultManager.countItemInVaults(level, trader, item) < tradeQty) {
                    return TransactionResult.failure("Not enough items in seller vault(s)");
                }
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
            }

            if (!buyerAccount.transferTo(sellerAccount, totalPrice,
                    TransactionContext.transfer("Sale of " + commodity.getDisplayName().getString(), owner))) {
                return TransactionResult.failure("Payment failed");
            }

            this.quantity -= tradeQty;
            TradeLedger.recordTrade(commodity.getId().toString(), pricePerUnit, tradeQty, owner, trader);
            return TransactionResult.success("Sale successful", totalPrice, tradeQty);
        }
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
