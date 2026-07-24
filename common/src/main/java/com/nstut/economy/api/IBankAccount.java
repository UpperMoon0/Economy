package com.nstut.economy.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Represents a virtual bank account for storing currency.
 * All operations are server-side only - no physical items involved.
 */
public interface IBankAccount {
    
    /**
     * Gets the unique identifier of the account owner.
     * @return The owner's UUID
     */
    UUID getOwner();
    
    /**
     * Gets the current balance of the account.
     * @return The balance as BigDecimal
     */
    BigDecimal getBalance();
    
    /**
     * Credits (adds) the specified amount to the account.
     * @param amount The amount to credit
     * @param ctx The transaction context for logging
     * @return true if successful, false otherwise
     */
    boolean credit(BigDecimal amount, ITransactionContext ctx);
    
    /**
     * Debits (removes) the specified amount from the account.
     * @param amount The amount to debit
     * @param ctx The transaction context for logging
     * @return true if successful (sufficient funds), false otherwise
     */
    boolean debit(BigDecimal amount, ITransactionContext ctx);
    
    /**
     * Transfers funds from this account to another account.
     * @param target The target account
     * @param amount The amount to transfer
     * @param ctx The transaction context for logging
     * @return true if successful, false otherwise
     */
    boolean transferTo(IBankAccount target, BigDecimal amount, ITransactionContext ctx);
    
    /**
     * Gets recent transaction records for this account.
     * @param count The maximum number of records to return
     * @return A list of transaction records, newest first
     */
    List<ITransactionRecord> getRecentTransactions(int count);
    
    /**
     * Checks if this account has sufficient funds.
     * @param amount The amount to check
     * @return true if balance >= amount
     */
    default boolean hasSufficientFunds(BigDecimal amount) {
        return getBalance().compareTo(amount) >= 0;
    }
}
