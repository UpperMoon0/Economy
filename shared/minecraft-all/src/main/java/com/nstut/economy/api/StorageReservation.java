package com.nstut.economy.api;

import java.util.Map;
import java.util.Objects;

/**
 * Opaque durable reservation returned by a storage provider. The provider owns
 * the token format and must be able to resolve it after a server save/reload.
 */
public record StorageReservation(
        EconomyId providerId,
        EconomyId commodityId,
        int amount,
        String token,
        Map<String, String> metadata
) {
    public StorageReservation {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(commodityId, "commodityId");
        Objects.requireNonNull(token, "token");
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
