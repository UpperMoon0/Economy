package com.nstut.economy.api;

import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

/**
 * Pluggable owner-scoped market storage backend. Providers must make simulation
 * side-effect free and reservations durable/lossless across save/reload.
 */
public interface IStorageProvider {
    EconomyId id();

    default int priority() { return 0; }
    boolean supports(ICommodity commodity);

    int available(ServerLevel level, UUID owner, ICommodity commodity);
    int receivable(ServerLevel level, UUID owner, ICommodity commodity, int requestedAmount);

    /** Atomically extracts/escrows up to the requested amount or returns empty without mutation. */
    Optional<StorageReservation> reserve(ServerLevel level, UUID owner, ICommodity commodity, int amount);

    /**
     * Commits up to {@code amount} reserved units to receiver. Returning N means exactly N units
     * left escrow and reached the receiver/consumer. Must never exceed amount.
     */
    int deliverReserved(ServerLevel level, StorageReservation reservation, UUID receiver, int amount);

    /**
     * Returns the provider-owned reservation representing what remains after a successful delivery.
     * Economy deliberately does not fabricate a smaller reservation/token because provider tokens may
     * encode amount- or state-specific ownership. Providers that can partially deliver MUST override
     * this method. The default is only valid for zero- or full-delivery results.
     */
    default Optional<StorageReservation> remainingAfterDelivery(ServerLevel level, StorageReservation reservation,
                                                                int deliveredAmount) {
        if (deliveredAmount < 0 || deliveredAmount > reservation.amount()) {
            throw new IllegalArgumentException("deliveredAmount outside reservation bounds");
        }
        if (deliveredAmount == 0) return Optional.of(reservation);
        if (deliveredAmount == reservation.amount()) return Optional.empty();
        throw new IllegalStateException("Storage provider " + id()
                + " returned a partial delivery without supplying provider-owned remaining reservation state");
    }

    /** Returns all remaining reserved goods to their original owner. */
    boolean release(ServerLevel level, StorageReservation reservation);

    /** Immutable provider-facing summary text suitable for diagnostics/UI aggregation. */
    default String describe(ServerLevel level, UUID owner) { return id().toString(); }
}
