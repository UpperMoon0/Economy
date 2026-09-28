package com.nstut.economy.api;

import java.util.Objects;
import java.util.UUID;

/** Stable Economy view of an external party/team identity. */
public record TeamRef(UUID id, String displayName, UUID ownerId) {
    public TeamRef {
        Objects.requireNonNull(id, "id");
        displayName = displayName == null || displayName.isBlank() ? id.toString() : displayName;
    }

    public AccountRef account() {
        return AccountRef.team(id);
    }
}
