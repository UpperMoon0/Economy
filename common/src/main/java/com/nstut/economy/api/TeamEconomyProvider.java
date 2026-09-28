package com.nstut.economy.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Neutral bridge between Economy and an external team system. Implementations
 * own identity/membership/rank lookup; Economy remains the source of truth for money.
 */
public interface TeamEconomyProvider {
    /**
     * Stable, namespaced identity for this provider's Team domain. This value is persisted with Team
     * wallet lifecycle state and must never change for the same external Team system across restarts
     * or addon versions. Different providers must use different ids.
     */
    EconomyId providerId();

    Optional<TeamRef> resolveTeam(UUID playerId);
    Optional<TeamRef> getTeam(UUID teamId);
    TeamRole getRole(UUID playerId, UUID teamId);

    default boolean isMember(UUID playerId, UUID teamId) {
        return getRole(playerId, teamId).atLeast(TeamRole.MEMBER);
    }

    /**
     * Current complete team membership used to snapshot deterministic disband settlement recipients.
     * Returning an empty collection means membership enumeration is unsupported or unavailable; Economy
     * will preserve the team wallet instead of guessing recipients. Providers that support Team wallets
     * should override this and return every current member, including the owner.
     */
    default Collection<UUID> getMembers(UUID teamId) {
        return List.of();
    }

    /** True only after an authoritative successful lookup confirms deletion, never on lookup failure. */
    default boolean isTeamDeleted(UUID teamId) { return false; }

    default boolean isAvailable() {
        return true;
    }
}
