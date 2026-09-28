package com.nstut.economy.api;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Public factories for immutable transaction contexts used by addon code. */
public final class TransactionContexts {
    private TransactionContexts() {}

    public static ITransactionContext of(EconomyId causeId, String description, String source) {
        return of(causeId, description, source, Map.of());
    }

    public static ITransactionContext of(EconomyId causeId, String description, String source,
                                         Map<String, String> metadata) {
        return new ImmutableContext(UUID.randomUUID(), Instant.now(),
                Objects.requireNonNull(causeId, "causeId"),
                Objects.requireNonNullElse(description, ""),
                Objects.requireNonNullElse(source, ""),
                metadata == null ? Map.of() : Map.copyOf(metadata));
    }

    public static ITransactionContext transfer(String description, UUID actor) {
        Objects.requireNonNull(actor, "actor");
        return of(TransactionCauses.TRANSFER, description, actor.toString());
    }

    private record ImmutableContext(UUID transactionId, Instant timestamp, EconomyId causeId,
                                    String description, String source, Map<String, String> metadata)
            implements ITransactionContext {
        @Override public UUID getTransactionId() { return transactionId; }
        @Override public Instant getTimestamp() { return timestamp; }
        @Override public TransactionType getType() { return TransactionCauses.toLegacy(causeId); }
        @Override public String getDescription() { return description; }
        @Override public String getSource() { return source; }
        @Override public EconomyId getCauseId() { return causeId; }
        @Override public Map<String, String> getMetadata() { return metadata; }
    }
}
