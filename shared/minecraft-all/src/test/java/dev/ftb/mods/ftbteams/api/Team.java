package dev.ftb.mods.ftbteams.api;

import java.util.Set;
import java.util.UUID;

public interface Team {
    UUID getId();
    UUID getOwner();
    String getShortName();
    Set<UUID> getMembers();
    TeamRank getRankForPlayer(UUID playerId);
    boolean isPartyTeam();
}
