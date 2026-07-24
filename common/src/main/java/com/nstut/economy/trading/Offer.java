package com.nstut.economy.trading;

import com.nstut.economy.api.IAccountManager;
import com.nstut.economy.api.IBankAccount;
import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.IOffer;
import com.nstut.economy.blocks.VaultBlockEntity;
import com.nstut.economy.blocks.VaultManager;
import com.nstut.economy.core.TransactionContext;
import net.minecraft.core.NonNullList;
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

        if (level != null && item != null && !reservedItems.isEmpty()) {
            VaultBlockEntity buyerVault = VaultManager.getVault(level, buyer);
            if (buyerVault == null) {
                sellerAccount.transferTo(buyerAccount, totalPrice,
                        TransactionContext.transfer("Refund - buyer has no vault", buyer));
                return TransactionResult.failure("Buyer does not have a Vault block to receive items");
            }
            if (!buyerVault.insertItemStacks(copyStacks(reservedItems))) {
                sellerAccount.transferTo(buyerAccount, totalPrice,
                        TransactionContext.transfer("Refund - buyer vault full", buyer));
                return TransactionResult.failure("Buyer's vault is full");
            }
        }

        this.quantity = 0;
        return TransactionResult.success("Purchase successful", totalPrice, quantity);
    }

    private TransactionResult executeBuy(UUID seller, IAccountManager accounts, Item item, ServerLevel level) {
        IBankAccount buyerAccount = accounts.getOrCreatePlayerAccount(owner);
        IBankAccount sellerAccount = accounts.getOrCreatePlayerAccount(seller);
        BigDecimal totalPrice = getTotalPrice();

        if (level != null && item != null) {
            VaultBlockEntity sellerVault = VaultManager.getVault(level, seller);
            if (sellerVault == null) {
                return TransactionResult.failure("You do not have a Vault block with the required items");
            }
            if (sellerVault.countItem(item) < quantity) {
                return TransactionResult.failure("Not enough items in your vault");
            }

            NonNullList<ItemStack> extracted = NonNullList.create();
            if (!sellerVault.extractItem(item, quantity, extracted)) {
                return TransactionResult.failure("Failed to extract items from vault");
            }

            VaultBlockEntity buyerVault = VaultManager.getVault(level, owner);
            if (buyerVault == null || !buyerVault.insertItemStacks(copyStacks(extracted))) {
                sellerVault.insertItemStacks(extracted);
                return TransactionResult.failure("Buyer's vault is full or missing");
            }
        }

        if (!buyerAccount.transferTo(sellerAccount, totalPrice,
                TransactionContext.transfer("Sale of " + commodity.getDisplayName().getString(), owner))) {
            return TransactionResult.failure("Payment failed");
        }

        this.quantity = 0;
        return TransactionResult.success("Sale successful", totalPrice, quantity);
    }

    private static NonNullList<ItemStack> copyStacks(NonNullList<ItemStack> original) {
        NonNullList<ItemStack> copy = NonNullList.create();
        for (ItemStack stack : original) {
            copy.add(stack.copy());
        }
        return copy;
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
