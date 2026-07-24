package com.nstut.economy.core;

import com.nstut.economy.api.ITransactionContext;

import java.time.Instant;
import java.util.UUID;

/**
 * Implementation of ITransactionContext.
 */
public class TransactionContext implements ITransactionContext {
    
    private final UUID transactionId;
    private final Instant timestamp;
    private final TransactionType type;
    private final String description;
    private final String source;
    
    public TransactionContext(TransactionType type, String description, String source) {
        this(UUID.randomUUID(), Instant.now(), type, description, source);
    }
    
    public TransactionContext(UUID transactionId, Instant timestamp, TransactionType type, 
                              String description, String source) {
        this.transactionId = transactionId;
        this.timestamp = timestamp;
        this.type = type;
        this.description = description;
        this.source = source;
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
    public TransactionType getType() {
        return type;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    @Override
    public String getSource() {
        return source;
    }
    
    /**
     * Creates a context for a transfer between players.
     */
    public static TransactionContext transfer(String description, UUID fromPlayer) {
        return new TransactionContext(TransactionType.TRANSFER, description, fromPlayer.toString());
    }
    
    /**
     * Creates a context for admin operations.
     */
    public static TransactionContext adminGive(String description) {
        return new TransactionContext(TransactionType.ADMIN_GIVE, description, "ADMIN");
    }
    
    /**
     * Creates a context for admin operations.
     */
    public static TransactionContext adminTake(String description) {
        return new TransactionContext(TransactionType.ADMIN_TAKE, description, "ADMIN");
    }
    
    /**
     * Creates a context for starting balance.
     */
    public static TransactionContext startingBalance() {
        return new TransactionContext(TransactionType.STARTING_BALANCE, "Starting balance", "SERVER");
    }
}
