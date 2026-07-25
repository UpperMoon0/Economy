package com.nstut.economy.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a buy or sell order in the market.
 */
public interface IOrder {
    
    /**
     * Gets the unique order ID.
     * @return The order UUID
     */
    UUID getOrderId();
    
    /**
     * Gets the UUID of the player who created this order.
     * @return The owner's UUID
     */
    UUID getOwner();
    
    /**
     * Gets the commodity being traded.
     * @return The commodity
     */
    ICommodity getCommodity();
    
    /**
     * Gets the quantity being ordered.
     * @return The quantity
     */
    int getQuantity();
    
    /**
     * Gets the price per unit.
     * @return The unit price
     */
    BigDecimal getPricePerUnit();
    
    /**
     * Gets the total price for the entire order.
     * @return The total price
     */
    default BigDecimal getTotalPrice() {
        return getPricePerUnit().multiply(BigDecimal.valueOf(getQuantity()));
    }
    
    /**
     * Gets the type of order.
     * @return BUY or SELL
     */
    OrderType getType();
    
    /**
     * Gets when this order was created.
     * @return The creation timestamp
     */
    Instant getCreatedAt();
    
    /**
     * Gets when this order expires (null for no expiration).
     * @return The expiration timestamp, or null
     */
    Instant getExpiresAt();
    
    /**
     * Checks if this order is still valid.
     * @return true if valid and not expired
     */
    boolean isValid();
    
    /**
     * Checks if this order can be executed by the given buyer/seller.
     * @param trader The UUID of the trader
     * @return true if execution is possible
     */
    boolean canExecute(UUID trader);
    
    /**
     * Executes this order with the given trader, including item transfer via vaults.
     * @param trader The UUID of the trader executing the order
     * @param level The server level for vault lookups
     * @return The result of the transaction
     */
    TransactionResult execute(UUID trader, net.minecraft.server.level.ServerLevel level);

    /**
     * Executes this order with the given trader (no item transfer).
     * @param trader The UUID of the trader executing the order
     * @return The result of the transaction
     */
    TransactionResult execute(UUID trader);
    
    /**
     * Cancels this order.
     * @return true if successfully cancelled
     */
    boolean cancel();
    
    /**
     * Enum representing order types.
     */
    enum OrderType {
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
