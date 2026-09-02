package com.nstut.economy.core;

import com.nstut.economy.api.EconomyId;
import com.nstut.economy.api.ITransactionContext;
import com.nstut.economy.api.ITransactionRecord;
import com.nstut.economy.api.TransactionCauses;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Default immutable implementation of {@link ITransactionRecord}. */
public class TransactionRecord implements ITransactionRecord {
    private final UUID transactionId;
    private final Instant timestamp;
    private final EconomyId causeId;
    private final BigDecimal amount;
    private final BigDecimal resultingBalance;
    private final UUID counterparty;
    private final String description;
    private final Map<String, String> metadata;

    public TransactionRecord(UUID transactionId, Instant timestamp,
                             ITransactionContext.TransactionType type,
                             BigDecimal amount, BigDecimal resultingBalance,
                             UUID counterparty, String description) {
        this(transactionId, timestamp, TransactionCauses.fromLegacy(type), amount, resultingBalance,
                counterparty, description, Map.of());
    }

    public TransactionRecord(UUID transactionId, Instant timestamp, EconomyId causeId,
                             BigDecimal amount, BigDecimal resultingBalance,
                             UUID counterparty, String description, Map<String, String> metadata) {
        this.transactionId = transactionId;
        this.timestamp = timestamp;
        this.causeId = causeId;
        this.amount = amount;
        this.resultingBalance = resultingBalance;
        this.counterparty = counterparty;
        this.description = description;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override public UUID getTransactionId() { return transactionId; }
    @Override public Instant getTimestamp() { return timestamp; }
    @Override public EconomyId getCauseId() { return causeId; }
    @Override public ITransactionContext.TransactionType getType() { return TransactionCauses.toLegacy(causeId); }
    @Override public Map<String, String> getMetadata() { return metadata; }
    @Override public BigDecimal getAmount() { return amount; }
    @Override public BigDecimal getResultingBalance() { return resultingBalance; }
    @Override public UUID getCounterparty() { return counterparty; }
    @Override public String getDescription() { return description; }
}
