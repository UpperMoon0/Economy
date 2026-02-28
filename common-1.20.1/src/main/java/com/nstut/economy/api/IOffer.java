package com.nstut.economy.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a buy or sell offer in the market.
 */
public interface IOffer {
    
    /**
     * Gets the unique offer ID.
     * @return The offer UUID
     */
    UUID getOfferId();
    
    /**
     * Gets the UUID of the player who created this offer.
     * @return The owner's UUID
     */
    UUID getOwner();
    
    /**
     * Gets the commodity being traded.
     * @return The commodity
     */
    ICommodity getCommodity();
    
    /**
     * Gets the quantity being offered.
     * @return The quantity
     */
    int getQuantity();
    
    /**
     * Gets the price per unit.
     * @return The unit price
     */
    BigDecimal getPricePerUnit();
    
    /**
     * Gets the total price for the entire offer.
     * @return The total price
     */
    default BigDecimal getTotalPrice() {
        return getPricePerUnit().multiply(BigDecimal.valueOf(getQuantity()));
    }
    
    /**
     * Gets the type of offer.
     * @return BUY or SELL
     */
    OfferType getType();
    
    /**
     * Gets when this offer was created.
     * @return The creation timestamp
     */
    Instant getCreatedAt();
    
    /**
     * Gets when this offer expires (null for no expiration).
     * @return The expiration timestamp, or null
     */
    Instant getExpiresAt();
    
    /**
     * Checks if this offer is still valid.
     * @return true if valid and not expired
     */
    boolean isValid();
    
    /**
     * Checks if this offer can be executed by the given buyer/seller.
     * @param trader The UUID of the trader
     * @return true if execution is possible
     */
    boolean canExecute(UUID trader);
    
    /**
     * Executes this offer with the given trader.
     * @param trader The UUID of the trader executing the offer
     * @return The result of the transaction
     */
    TransactionResult execute(UUID trader);
    
    /**
     * Cancels this offer.
     * @return true if successfully cancelled
     */
    boolean cancel();
    
    /**
     * Enum representing offer types.
     */
    enum OfferType {
        BUY,
        SELL
    }
    
    /**
     * Result of a transaction execution.
     */
    class TransactionResult {
        public final boolean success;
        public final String message;
        public final BigDecimal amountTransferred;
        public final int quantityTransferred;
        
        public TransactionResult(boolean success, String message, 
                                BigDecimal amountTransferred, int quantityTransferred) {
            this.success = success;
            this.message = message;
            this.amountTransferred = amountTransferred;
            this.quantityTransferred = quantityTransferred;
        }
        
        public static TransactionResult success(String message, BigDecimal amount, int quantity) {
            return new TransactionResult(true, message, amount, quantity);
        }
        
        public static TransactionResult failure(String message) {
            return new TransactionResult(false, message, BigDecimal.ZERO, 0);
        }
    }
}
