package com.nstut.economy.trading;

import com.nstut.economy.api.IAccountManager;
import com.nstut.economy.api.IBankAccount;
import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.IOffer;
import com.nstut.economy.api.ITransactionContext;
import com.nstut.economy.core.TransactionContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Implementation of IOffer.
 */
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
    
    public Offer(UUID owner, ICommodity commodity, int quantity, 
                 BigDecimal pricePerUnit, OfferType type, Instant expiresAt) {
        this.offerId = UUID.randomUUID();
        this.owner = owner;
        this.commodity = commodity;
        this.quantity = quantity;
        this.pricePerUnit = pricePerUnit;
        this.type = type;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
        this.cancelled = false;
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
        
        // Can't trade with yourself
        if (owner.equals(trader)) {
            return false;
        }
        
        IAccountManager accounts = IAccountManager.getInstance();
        IBankAccount traderAccount = accounts.getOrCreatePlayerAccount(trader);
        
        if (type == OfferType.SELL) {
            // Buying: check if buyer has enough money
            return traderAccount.hasSufficientFunds(getTotalPrice());
        } else {
            // Selling to a buy order: check if buyer (offer owner) has enough money
            IBankAccount ownerAccount = accounts.getOrCreatePlayerAccount(owner);
            return ownerAccount.hasSufficientFunds(getTotalPrice());
        }
    }
    
    @Override
    public TransactionResult execute(UUID trader) {
        if (!canExecute(trader)) {
            return TransactionResult.failure("Cannot execute this offer");
        }
        
        IAccountManager accounts = IAccountManager.getInstance();
        IBankAccount ownerAccount = accounts.getOrCreatePlayerAccount(owner);
        IBankAccount traderAccount = accounts.getOrCreatePlayerAccount(trader);
        
        BigDecimal totalPrice = getTotalPrice();
        
        if (type == OfferType.SELL) {
            // Buyer pays seller
            ITransactionContext ctx = TransactionContext.transfer(
                "Purchase of " + commodity.getDisplayName().getString(),
                trader
            );
            
            if (!traderAccount.transferTo(ownerAccount, totalPrice, ctx)) {
                return TransactionResult.failure("Payment failed");
            }
            
            // TODO: Transfer items (platform-specific)
            
            this.quantity = 0; // Offer consumed
            return TransactionResult.success("Purchase successful", totalPrice, quantity);
            
        } else {
            // Selling to buy order: seller gets money from buyer
            ITransactionContext ctx = TransactionContext.transfer(
                "Sale of " + commodity.getDisplayName().getString(),
                owner
            );
            
            if (!ownerAccount.transferTo(traderAccount, totalPrice, ctx)) {
                return TransactionResult.failure("Payment failed");
            }
            
            // TODO: Transfer items (platform-specific)
            
            this.quantity = 0; // Offer consumed
            return TransactionResult.success("Sale successful", totalPrice, quantity);
        }
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
    
    /**
     * Reduces the quantity of this offer (for partial fills).
     * @param amount The amount to reduce
     */
    public void reduceQuantity(int amount) {
        this.quantity = Math.max(0, this.quantity - amount);
    }
}
