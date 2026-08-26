package com.nstut.economy.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Record of a completed transaction.
 */
public interface ITransactionRecord {
    
    /**
     * Gets the unique transaction ID.
     * @return The transaction UUID
     */
    UUID getTransactionId();
    
    /**
     * Gets when the transaction occurred.
     * @return The timestamp
     */
    Instant getTimestamp();
    
    /**
     * Gets the type of transaction.
     * @return The transaction type
     */
    ITransactionContext.TransactionType getType();
    
    /**
     * Gets the amount involved in the transaction.
     * Positive for credits, negative for debits.
     * @return The transaction amount
     */
    BigDecimal getAmount();
    
    /**
     * Gets the balance after the transaction.
     * @return The resulting balance
     */
    BigDecimal getResultingBalance();
    
    /**
     * Gets the counterparty in this transaction (if applicable).
     * @return The counterparty UUID, or null if not applicable
     */
    UUID getCounterparty();
    
    /**
     * Gets a description of the transaction.
     * @return The description
     */
    String getDescription();
}
