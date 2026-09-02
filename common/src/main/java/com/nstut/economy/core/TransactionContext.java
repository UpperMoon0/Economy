package com.nstut.economy.core;

import com.nstut.economy.api.EconomyId;
import com.nstut.economy.api.ITransactionContext;
import com.nstut.economy.api.TransactionCauses;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Default immutable implementation of {@link ITransactionContext}. */
public class TransactionContext implements ITransactionContext {
    private final UUID transactionId;
    private final Instant timestamp;
    private final EconomyId causeId;
    private final String description;
    private final String source;
    private final Map<String, String> metadata;

    public TransactionContext(TransactionType type, String description, String source) {
        this(UUID.randomUUID(), Instant.now(), TransactionCauses.fromLegacy(type), description, source, Map.of());
    }

    public TransactionContext(UUID transactionId, Instant timestamp, TransactionType type,
                              String description, String source) {
        this(transactionId, timestamp, TransactionCauses.fromLegacy(type), description, source, Map.of());
    }

    public TransactionContext(EconomyId causeId, String description, String source) {
        this(UUID.randomUUID(), Instant.now(), causeId, description, source, Map.of());
    }

    public TransactionContext(EconomyId causeId, String description, String source,
                              Map<String, String> metadata) {
        this(UUID.randomUUID(), Instant.now(), causeId, description, source, metadata);
    }

    public TransactionContext(UUID transactionId, Instant timestamp, EconomyId causeId,
                              String description, String source, Map<String, String> metadata) {
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.causeId = Objects.requireNonNull(causeId, "causeId");
        this.description = Objects.requireNonNullElse(description, "");
        this.source = Objects.requireNonNullElse(source, "");
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override public UUID getTransactionId() { return transactionId; }
    @Override public Instant getTimestamp() { return timestamp; }
    @Override public EconomyId getCauseId() { return causeId; }
    @Override public TransactionType getType() { return TransactionCauses.toLegacy(causeId); }
    @Override public String getDescription() { return description; }
    @Override public String getSource() { return source; }
    @Override public Map<String, String> getMetadata() { return metadata; }

    public static TransactionContext transfer(String description, UUID fromPlayer) {
        return new TransactionContext(TransactionCauses.TRANSFER, description, fromPlayer.toString());
    }

    public static TransactionContext adminGive(String description) {
        return new TransactionContext(TransactionCauses.ADMIN_GIVE, description, "ADMIN");
    }

    public static TransactionContext adminTake(String description) {
        return new TransactionContext(TransactionCauses.ADMIN_TAKE, description, "ADMIN");
    }

    public static TransactionContext startingBalance() {
        return new TransactionContext(TransactionCauses.STARTING_BALANCE, "Starting balance", "SERVER");
    }
}
