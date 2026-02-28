package com.nstut.economy.core;

import com.nstut.economy.api.ITransactionContext;
import com.nstut.economy.api.ITransactionRecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Implementation of ITransactionRecord.
 */
public class TransactionRecord implements ITransactionRecord {
    
    private final UUID transactionId;
    private final Instant timestamp;
    private final ITransactionContext.TransactionType type;
    private final BigDecimal amount;
    private final BigDecimal resultingBalance;
    private final UUID counterparty;
    private final String description;
    
    public TransactionRecord(UUID transactionId, Instant timestamp, 
                            ITransactionContext.TransactionType type,
                            BigDecimal amount, BigDecimal resultingBalance,
                            UUID counterparty, String description) {
        this.transactionId = transactionId;
        this.timestamp = timestamp;
        this.type = type;
        this.amount = amount;
        this.resultingBalance = resultingBalance;
        this.counterparty = counterparty;
        this.description = description;
    }
    
    @Override
    public UUID getTransactionId() {
        return transactionId;
    }
    
    @Override
    public Instant getTimestamp() {
        return timestamp;
    }
    
    @Override
    public ITransactionContext.TransactionType getType() {
        return type;
    }
    
    @Override
    public BigDecimal getAmount() {
        return amount;
    }
    
    @Override
    public BigDecimal getResultingBalance() {
        return resultingBalance;
    }
    
    @Override
    public UUID getCounterparty() {
        return counterparty;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
}
