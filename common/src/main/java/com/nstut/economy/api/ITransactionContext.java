package com.nstut.economy.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Context information for a transaction. */
public interface ITransactionContext {
    UUID getTransactionId();
    Instant getTimestamp();

    /**
     * Legacy built-in classification. New integrations should use
     * {@link #getCauseId()} so addon-defined causes do not require an enum change.
     */
    @Deprecated
    TransactionType getType();

    String getDescription();
    String getSource();

    /** Stable, namespaced transaction cause, e.g. economy:trade or myaddon:salary. */
    default EconomyId getCauseId() {
        return TransactionCauses.fromLegacy(getType());
    }

    /** Immutable structured metadata supplied by the transaction initiator. */
    default Map<String, String> getMetadata() {
        return Map.of();
    }

    enum TransactionType {
        CREDIT,
        DEBIT,
        TRANSFER,
        TRADE,
        TAX,
        ADMIN_GIVE,
        ADMIN_TAKE,
        STARTING_BALANCE,
        /** Legacy projection for a namespaced cause not owned by Economy. */
        CUSTOM
    }
}
