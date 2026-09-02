package com.nstut.economy.api;

import net.minecraft.nbt.CompoundTag;

import java.util.Map;
import java.util.Objects;

/**
 * Opaque durable reservation returned by a storage provider. The provider owns
 * the token format and must be able to resolve it after a server save/reload.
 *
 * <p>{@code metadata} is intentionally small diagnostic/indexing text. Arbitrary
 * provider state belongs in {@code providerState}, which is persisted as
 * structured NBT so exact escrow is never forced through StringTag/writeUTF
 * limits.</p>
 */
public record StorageReservation(
        EconomyId providerId,
        EconomyId commodityId,
        int amount,
        String token,
        Map<String, String> metadata,
        CompoundTag providerState
) {
    public StorageReservation {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(commodityId, "commodityId");
        Objects.requireNonNull(token, "token");
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        providerState = providerState == null ? new CompoundTag() : providerState.copy();
    }

    /** Backward/source-compatible constructor for providers that need no structured state. */
    public StorageReservation(EconomyId providerId, EconomyId commodityId, int amount,
                              String token, Map<String, String> metadata) {
        this(providerId, commodityId, amount, token, metadata, new CompoundTag());
    }

    /** Returns a defensive copy because NBT tags are mutable. */
    @Override
    public CompoundTag providerState() {
        return providerState.copy();
    }
}
