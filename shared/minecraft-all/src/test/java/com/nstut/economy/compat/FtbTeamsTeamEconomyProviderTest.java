package com.nstut.economy.compat;

import com.nstut.economy.api.TeamRole;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.ftb.mods.ftbteams.api.TeamRank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FtbTeamsTeamEconomyProviderTest {
    @AfterEach
    void resetApi() {
        FTBTeamsAPI.configure(false, null);
    }

    @Test
    void reflectsSupportedPublicApiAndIgnoresPersonalTeams() {
        UUID player = UUID.randomUUID();
        FakeTeam personal = new FakeTeam(player, player, "Personal", false);
        personal.members.add(player);
        personal.ranks.put(player, TeamRank.OWNER);
        FakeManager manager = new FakeManager();
        manager.assign(player, personal);
        FTBTeamsAPI.configure(true, manager);

        var provider = FtbTeamsTeamEconomyProvider.createIfPresent().orElseThrow();
        assertTrue(provider.isAvailable());
        assertTrue(provider.resolveTeam(player).isEmpty(),
                "FTB personal teams must not become duplicate Economy team wallets");
    }

    @Test
    void resolvesPartyNameMembershipAndRanksWithoutCachingLifecycleState() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        FakeTeam party = new FakeTeam(teamId, owner, "Upper Moon", true);
        party.members.add(owner);
        party.members.add(member);
        party.ranks.put(owner, TeamRank.OWNER);
        party.ranks.put(member, TeamRank.OFFICER);

        FakeManager manager = new FakeManager();
        manager.assign(owner, party);
        manager.assign(member, party);
        FTBTeamsAPI.configure(true, manager);

        var provider = FtbTeamsTeamEconomyProvider.createIfPresent().orElseThrow();
        var ref = provider.resolveTeam(member).orElseThrow();
        assertEquals(teamId, ref.id());
        assertEquals(owner, ref.ownerId());
        assertEquals("Upper Moon", ref.displayName());
        assertTrue(provider.isMember(member, teamId));
        assertEquals(TeamRole.OFFICER, provider.getRole(member, teamId));

        // Simulate kick/leave: FTB changes both effective-team resolution and party membership.
        party.members.remove(member);
        party.ranks.remove(member);
        manager.unassign(member);

        assertTrue(provider.resolveTeam(member).isEmpty());
        assertFalse(provider.isMember(member, teamId));
        assertEquals(TeamRole.NONE, provider.getRole(member, teamId));
    }

    @Test
    void unavailableOrUnexpectedManagerStateFailsClosed() {
        FTBTeamsAPI.configure(false, null);
        var provider = FtbTeamsTeamEconomyProvider.createIfPresent().orElseThrow();
        assertFalse(provider.isAvailable());
        assertTrue(provider.resolveTeam(UUID.randomUUID()).isEmpty());

        FTBTeamsAPI.configure(true, new FakeManager());
        FTBTeamsAPI.failManagerLookup(true);
        assertFalse(provider.isAvailable());
        assertTrue(provider.resolveTeam(UUID.randomUUID()).isEmpty());
    }

    private static final class FakeManager implements TeamManager {
        private final Map<UUID, Team> byPlayer = new HashMap<>();
        private final Map<UUID, Team> byId = new HashMap<>();

        void assign(UUID player, Team team) {
            byPlayer.put(player, team);
            byId.put(team.getId(), team);
        }

        void unassign(UUID player) {
            byPlayer.remove(player);
        }

        @Override
        public Optional<Team> getTeamForPlayerID(UUID uuid) {
            return Optional.ofNullable(byPlayer.get(uuid));
        }

        @Override
        public Optional<Team> getTeamByID(UUID teamId) {
            return Optional.ofNullable(byId.get(teamId));
        }
    }

    private static final class FakeTeam implements Team {
        private final UUID id;
        private final UUID owner;
        private final String name;
        private final boolean party;
        private final Set<UUID> members = new HashSet<>();
        private final Map<UUID, TeamRank> ranks = new HashMap<>();

        FakeTeam(UUID id, UUID owner, String name, boolean party) {
            this.id = id;
            this.owner = owner;
            this.name = name;
            this.party = party;
        }

        @Override public UUID getId() { return id; }
        @Override public UUID getOwner() { return owner; }
        @Override public String getShortName() { return name; }
        @Override public Set<UUID> getMembers() { return Set.copyOf(members); }
        @Override public TeamRank getRankForPlayer(UUID playerId) { return ranks.getOrDefault(playerId, TeamRank.NONE); }
        @Override public boolean isPartyTeam() { return party; }
    }
}
