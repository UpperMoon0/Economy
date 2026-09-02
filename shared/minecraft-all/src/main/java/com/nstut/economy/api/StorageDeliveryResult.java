package com.nstut.economy.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Atomic result of moving goods out of a provider-owned reservation.
 *
 * <p>The provider returns both the amount that actually reached the receiver and
 * the exact provider-owned reservation that still remains. Economy never
 * reconstructs a remainder from a numeric count, so opaque tokens and exact
 * item/component state remain under provider control.</p>
 */
public record StorageDeliveryResult(
        int deliveredAmount,
        Optional<StorageReservation> remainingReservation
) {
    public StorageDeliveryResult {
        if (deliveredAmount < 0) throw new IllegalArgumentException("deliveredAmount must be non-negative");
        remainingReservation = Objects.requireNonNull(remainingReservation, "remainingReservation");
    }

    public static StorageDeliveryResult unchanged(StorageReservation reservation) {
        return new StorageDeliveryResult(0, Optional.of(Objects.requireNonNull(reservation, "reservation")));
    }

    public static StorageDeliveryResult complete(int deliveredAmount) {
        return new StorageDeliveryResult(deliveredAmount, Optional.empty());
    }

    public static StorageDeliveryResult partial(int deliveredAmount, StorageReservation remainingReservation) {
        return new StorageDeliveryResult(deliveredAmount, Optional.of(Objects.requireNonNull(remainingReservation, "remainingReservation")));
    }

    /** Validates the accounting invariant against the reservation before delivery. */
    public StorageDeliveryResult validateAgainst(StorageReservation before, int requestedAmount) {
        Objects.requireNonNull(before, "before");
        if (requestedAmount < 0 || requestedAmount > before.amount()) {
            throw new IllegalArgumentException("requestedAmount outside reservation bounds");
        }
        if (deliveredAmount > requestedAmount) {
            throw new IllegalStateException("Storage provider delivered more than requested: " + deliveredAmount + " > " + requestedAmount);
        }
        int remaining = remainingReservation.map(StorageReservation::amount).orElse(0);
        if (deliveredAmount + remaining != before.amount()) {
            throw new IllegalStateException("Storage provider reservation accounting mismatch: delivered="
                    + deliveredAmount + ", remaining=" + remaining + ", before=" + before.amount());
        }
        remainingReservation.ifPresent(value -> {
            if (!before.providerId().equals(value.providerId())) {
                throw new IllegalStateException("Storage provider changed reservation provider identity");
            }
            if (!before.commodityId().equals(value.commodityId())) {
                throw new IllegalStateException("Storage provider changed reservation commodity identity");
            }
        });
        return this;
    }
}
