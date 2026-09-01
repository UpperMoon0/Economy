package com.nstut.economy.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Immutable public view of a completed balance transaction. */
public interface ITransactionRecord {
    UUID getTransactionId();
    Instant getTimestamp();

    /** Legacy built-in classification; use {@link #getCauseId()} for addons. */
    @Deprecated
    ITransactionContext.TransactionType getType();

    default EconomyId getCauseId() {
        return TransactionCauses.fromLegacy(getType());
    }

    default Map<String, String> getMetadata() {
        return Map.of();
    }

    BigDecimal getAmount();
    BigDecimal getResultingBalance();
    UUID getCounterparty();
    String getDescription();
}
