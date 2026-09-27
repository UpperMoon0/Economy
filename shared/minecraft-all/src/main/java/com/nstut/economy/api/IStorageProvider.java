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

    /**
     * Typed owner path. Existing providers remain source/binary compatible and
     * automatically support PLAYER storage; providers must opt in to TEAM storage.
     */
    default int available(ServerLevel level, AccountRef owner, ICommodity commodity) {
        return owner != null && owner.kind() == AccountKind.PLAYER ? available(level, owner.id(), commodity) : 0;
    }
    default int receivable(ServerLevel level, AccountRef owner, ICommodity commodity, int requestedAmount) {
        return owner != null && owner.kind() == AccountKind.PLAYER ? receivable(level, owner.id(), commodity, requestedAmount) : 0;
    }

    /**
     * Atomically extracts/escrows exactly the requested amount or returns empty without mutation.
     * A non-empty result must keep this provider ID and commodity ID, report the exact requested
     * amount, and provide a non-blank durable token. The registry validates these invariants.
     */
    Optional<StorageReservation> reserve(ServerLevel level, UUID owner, ICommodity commodity, int amount);
    default Optional<StorageReservation> reserve(ServerLevel level, AccountRef owner, ICommodity commodity, int amount) {
        return owner != null && owner.kind() == AccountKind.PLAYER
                ? reserve(level, owner.id(), commodity, amount) : Optional.empty();
    }

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
    default StorageDeliveryResult deliverReserved(ServerLevel level, StorageReservation reservation,
                                                  AccountRef receiver, int amount) {
        return receiver != null && receiver.kind() == AccountKind.PLAYER
                ? deliverReserved(level, reservation, receiver.id(), amount)
                : StorageDeliveryResult.unchanged(reservation);
    }

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
    default String describe(ServerLevel level, AccountRef owner) {
        return owner != null && owner.kind() == AccountKind.PLAYER ? describe(level, owner.id()) : id().toString();
    }
}
