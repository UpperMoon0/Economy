package com.nstut.economy.api;

import java.util.Objects;
import java.util.UUID;

/** Durable team owner and closure tombstone, saved alongside balances. */
public record TeamWalletState(UUID teamId, UUID ownerId, boolean closing, boolean storageSettled) {
    public TeamWalletState(UUID teamId, UUID ownerId, boolean closing) {
        this(teamId, ownerId, closing, false);
    }
    public TeamWalletState { Objects.requireNonNull(teamId); Objects.requireNonNull(ownerId); }
}
