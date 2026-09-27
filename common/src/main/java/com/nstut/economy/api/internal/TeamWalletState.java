package com.nstut.economy.api.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Internal durable team-closure state saved alongside balances. Not addon API. */
public record TeamWalletState(
        UUID teamId,
        UUID ownerId,
        boolean closing,
        boolean storageSettled,
        List<UUID> settlementMembers,
        Set<UUID> settledMembers
) {
    public TeamWalletState(UUID teamId, UUID ownerId, boolean closing) {
        this(teamId, ownerId, closing, false, List.of(ownerId), Set.of());
    }

    public TeamWalletState(UUID teamId, UUID ownerId, boolean closing, boolean storageSettled) {
        this(teamId, ownerId, closing, storageSettled, List.of(ownerId), Set.of());
    }

    public TeamWalletState {
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(ownerId, "ownerId");
        settlementMembers = normalizedMembers(ownerId, settlementMembers);
        Set<UUID> normalizedSettled = new HashSet<>();
        if (settledMembers != null) {
            for (UUID member : settledMembers) {
                if (member != null && settlementMembers.contains(member)) normalizedSettled.add(member);
            }
        }
        settledMembers = Set.copyOf(normalizedSettled);
    }

    public TeamWalletState withClosing(Collection<UUID> members) {
        return new TeamWalletState(teamId, ownerId, true, false,
                members == null || members.isEmpty() ? settlementMembers : List.copyOf(members), Set.of());
    }

    public TeamWalletState withSettledMember(UUID member) {
        HashSet<UUID> paid = new HashSet<>(settledMembers);
        if (member != null) paid.add(member);
        return new TeamWalletState(teamId, ownerId, closing, storageSettled, settlementMembers, paid);
    }

    public TeamWalletState withStorageSettled() {
        return new TeamWalletState(teamId, ownerId, closing, true, settlementMembers, settledMembers);
    }

    private static List<UUID> normalizedMembers(UUID ownerId, Collection<UUID> members) {
        HashSet<UUID> unique = new HashSet<>();
        if (members != null) for (UUID member : members) if (member != null) unique.add(member);
        unique.add(ownerId);
        ArrayList<UUID> sorted = new ArrayList<>(unique);
        sorted.sort(UUID::compareTo);
        return List.copyOf(sorted);
    }
}
