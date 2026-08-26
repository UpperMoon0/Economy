package com.nstut.economy.core;

import com.nstut.economy.api.IBankAccount;
import com.nstut.economy.api.ITransactionContext;
import com.nstut.economy.api.ITransactionRecord;
import com.nstut.economy.config.EconomyConfig;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of IBankAccount.
 */
public class BankAccount implements IBankAccount {
    
    private final UUID owner;
    private BigDecimal balance;
    private final List<ITransactionRecord> transactionHistory;
    private final int maxHistory;
    
    public BankAccount(UUID owner, BigDecimal initialBalance) {
        this.owner = owner;
        this.balance = initialBalance;
        this.maxHistory = EconomyConfig.getInstance().getMaxTransactionHistory();
        this.transactionHistory = new LinkedList<>();
    }
    
    @Override
    public UUID getOwner() {
        return owner;
    }
    
    @Override
    public synchronized BigDecimal getBalance() {
        return balance;
    }
    
    @Override
    public synchronized boolean credit(BigDecimal amount, ITransactionContext ctx) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        balance = balance.add(amount);
        recordTransaction(ctx, amount, null);
        return true;
    }
    
    @Override
    public synchronized boolean debit(BigDecimal amount, ITransactionContext ctx) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        if (!hasSufficientFunds(amount)) {
            return false;
        }
        
        balance = balance.subtract(amount);
        recordTransaction(ctx, amount.negate(), null);
        return true;
    }
    
    @Override
    public synchronized boolean transferTo(IBankAccount target, BigDecimal amount, ITransactionContext ctx) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || target == null) {
            return false;
        }
        
        if (!hasSufficientFunds(amount)) {
            return false;
        }
        
        // Debit from this account
        balance = balance.subtract(amount);
        recordTransaction(ctx, amount.negate(), target.getOwner());
        
        // Credit to target account
        ITransactionContext targetCtx = new TransactionContext(
            ITransactionContext.TransactionType.TRANSFER,
            "Transfer from " + owner,
            owner.toString()
        );
        target.credit(amount, targetCtx);
        
        return true;
    }
    
    @Override
    public List<ITransactionRecord> getRecentTransactions(int count) {
        synchronized (transactionHistory) {
            int size = transactionHistory.size();
            int limit = Math.min(count, size);
            return new ArrayList<>(transactionHistory.subList(0, limit));
        }
    }
    
    private void recordTransaction(ITransactionContext ctx, BigDecimal amount, UUID counterparty) {
        TransactionRecord record = new TransactionRecord(
            ctx.getTransactionId(),
            ctx.getTimestamp(),
            ctx.getType(),
            amount,
            balance,
            counterparty,
            ctx.getDescription()
        );
        
        synchronized (transactionHistory) {
            transactionHistory.add(0, record); // Add to beginning (newest first)
            
            // Trim history if exceeds max
            while (transactionHistory.size() > maxHistory) {
                transactionHistory.remove(transactionHistory.size() - 1);
            }
        }
    }
    
    /**
     * Sets the balance directly (for server operations only).
     */
    public synchronized void setBalance(BigDecimal newBalance) {
        this.balance = newBalance;
    }
}
