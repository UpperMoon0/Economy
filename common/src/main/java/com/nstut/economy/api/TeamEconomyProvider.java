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
    Optional<TeamRef> resolveTeam(UUID playerId);
    Optional<TeamRef> getTeam(UUID teamId);
    TeamRole getRole(UUID playerId, UUID teamId);

    default boolean isMember(UUID playerId, UUID teamId) {
        return getRole(playerId, teamId).atLeast(TeamRole.MEMBER);
    }

    /** Current team members used to snapshot deterministic disband settlement recipients. */
    default Collection<UUID> getMembers(UUID teamId) {
        return getTeam(teamId).map(team -> List.of(team.ownerId())).orElseGet(List::of);
    }

    /** True only after an authoritative successful lookup confirms deletion, never on lookup failure. */
    default boolean isTeamDeleted(UUID teamId) { return false; }

    default boolean isAvailable() {
        return true;
    }
}
