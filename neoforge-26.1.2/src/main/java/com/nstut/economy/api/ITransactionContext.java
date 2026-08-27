package com.nstut.economy.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Context information for a transaction.
 */
public interface ITransactionContext {
    
    /**
     * Gets the unique transaction ID.
     * @return The transaction UUID
     */
    UUID getTransactionId();
    
    /**
     * Gets the timestamp when the transaction was created.
     * @return The timestamp as Instant
     */
    Instant getTimestamp();
    
    /**
     * Gets the type of transaction.
     * @return The transaction type
     */
    TransactionType getType();
    
    /**
     * Gets a description of the transaction purpose.
     * @return The description string
     */
    String getDescription();
    
    /**
     * Gets the source of the transaction (e.g., player UUID, "SERVER", "MARKET").
     * @return The source identifier
     */
    String getSource();
    
    /**
     * Enum representing different types of transactions.
     */
    enum TransactionType {
        CREDIT,
        DEBIT,
        TRANSFER,
        TRADE,
        TAX,
        ADMIN_GIVE,
        ADMIN_TAKE,
        STARTING_BALANCE
    }
}
