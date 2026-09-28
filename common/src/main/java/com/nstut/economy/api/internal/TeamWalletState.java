package com.nstut.economy.api.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.nstut.economy.api.EconomyId;

/** Internal durable team-closure state saved alongside balances. Not addon API. */
public record TeamWalletState(
        UUID teamId,
        UUID ownerId,
        EconomyId providerId,
        boolean closing,
        boolean storageSettled,
        List<UUID> settlementMembers,
        Set<UUID> settledMembers
) {
    /** Marker used only when loading pre-provenance development data. No provider may register this id. */
    public static final EconomyId UNKNOWN_PROVIDER_ID = EconomyId.of("economy", "unknown_team_provider");

    public TeamWalletState {
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(providerId, "providerId");
        settlementMembers = normalizedMembers(ownerId, settlementMembers);
        Set<UUID> normalizedSettled = new HashSet<>();
        if (settledMembers != null) {
            for (UUID member : settledMembers) {
                if (member != null && settlementMembers.contains(member)) normalizedSettled.add(member);
            }
        }
        settledMembers = Set.copyOf(normalizedSettled);
    }

    public static EconomyId readProviderId(String value) {
        if (value == null || value.isBlank()) return UNKNOWN_PROVIDER_ID;
        try {
            return EconomyId.parse(value);
        } catch (RuntimeException ignored) {
            return UNKNOWN_PROVIDER_ID;
        }
    }

    public boolean hasKnownProvider() {
        return !UNKNOWN_PROVIDER_ID.equals(providerId);
    }

    public TeamWalletState withProvider(EconomyId value) {
        return new TeamWalletState(teamId, ownerId, Objects.requireNonNull(value, "providerId"),
                closing, storageSettled, settlementMembers, settledMembers);
    }

    public TeamWalletState withClosing(Collection<UUID> members) {
        return new TeamWalletState(teamId, ownerId, providerId, true, false,
                members == null || members.isEmpty() ? settlementMembers : List.copyOf(members), Set.of());
    }

    public TeamWalletState withSettledMember(UUID member) {
        HashSet<UUID> paid = new HashSet<>(settledMembers);
        if (member != null) paid.add(member);
        return new TeamWalletState(teamId, ownerId, providerId, closing, storageSettled, settlementMembers, paid);
    }

    public TeamWalletState withStorageSettled() {
        return new TeamWalletState(teamId, ownerId, providerId, closing, true, settlementMembers, settledMembers);
    }

    private static List<UUID> normalizedMembers(UUID ownerId, Collection<UUID> members) {
        if (members == null || members.isEmpty()) return List.of();
        HashSet<UUID> unique = new HashSet<>();
        for (UUID member : members) if (member != null) unique.add(member);
        if (unique.isEmpty()) return List.of();
        unique.add(ownerId);
        ArrayList<UUID> sorted = new ArrayList<>(unique);
        sorted.sort(UUID::compareTo);
        return List.copyOf(sorted);
    }
}
