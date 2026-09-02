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
     * Atomically commits up to {@code amount} reserved units to the receiver and
     * returns both what actually moved and the exact provider-owned remainder.
     *
     * <p>The returned remainder must account for every unit that did not move,
     * including exact item/component state. Economy will never infer a remainder
     * from the delivered count.</p>
     */
    StorageDeliveryResult deliverReserved(ServerLevel level, StorageReservation reservation,
                                          UUID receiver, int amount);

    /**
     * Returns every remaining reserved unit to the original owner.
     *
     * <p>This operation is strictly all-or-nothing: returning {@code false}
     * means storage and reservation ownership were not mutated. Providers must
     * simulate/validate the complete restoration before committing it.</p>
     */
    boolean release(ServerLevel level, StorageReservation reservation);

    /** Immutable provider-facing summary text suitable for diagnostics/UI aggregation. */
    default String describe(ServerLevel level, UUID owner) { return id().toString(); }
}
