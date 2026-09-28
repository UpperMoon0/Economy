package dev.ftb.mods.ftbteams.api;

import java.util.Optional;
import java.util.UUID;

public interface TeamManager {
    Optional<Team> getTeamForPlayerID(UUID uuid);
    Optional<Team> getTeamByID(UUID teamId);
}
