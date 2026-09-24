package com.nstut.economy.compat;

import com.nstut.economy.api.TeamEconomyProvider;
import com.nstut.economy.api.TeamRef;
import com.nstut.economy.api.TeamRole;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Optional FTB Teams bridge implemented through its stable public API by
 * reflection. Economy therefore has no hard runtime or compile dependency on
 * FTB Teams.
 */
public final class FtbTeamsTeamEconomyProvider implements TeamEconomyProvider {
    private static final String API_CLASS = "dev.ftb.mods.ftbteams.api.FTBTeamsAPI";
    private final Method apiMethod;

    private FtbTeamsTeamEconomyProvider(Method apiMethod) {
        this.apiMethod = apiMethod;
    }

    public static Optional<FtbTeamsTeamEconomyProvider> createIfPresent() {
        try {
            Class<?> api = Class.forName(API_CLASS, false, FtbTeamsTeamEconomyProvider.class.getClassLoader());
            return Optional.of(new FtbTeamsTeamEconomyProvider(api.getMethod("api")));
        } catch (ClassNotFoundException absent) {
            return Optional.empty();
        } catch (ReflectiveOperationException incompatible) {
            return Optional.empty();
        }
    }

    @Override
    public boolean isAvailable() {
        return manager().isPresent();
    }

    @Override
    public Optional<TeamRef> resolveTeam(UUID playerId) {
        if (playerId == null) return Optional.empty();
        return manager()
                .flatMap(manager -> invokeOptional(manager, "getTeamForPlayerID", playerId))
                .filter(this::isPartyTeam)
                .flatMap(this::toRef);
    }

    @Override
    public Optional<TeamRef> getTeam(UUID teamId) {
        if (teamId == null) return Optional.empty();
        return manager()
                .flatMap(manager -> invokeOptional(manager, "getTeamByID", teamId))
                .filter(this::isPartyTeam)
                .flatMap(this::toRef);
    }

    @Override
    public TeamRole getRole(UUID playerId, UUID teamId) {
        if (playerId == null || teamId == null) return TeamRole.NONE;
        Optional<Object> team = manager()
                .flatMap(manager -> invokeOptional(manager, "getTeamByID", teamId))
                .filter(this::isPartyTeam);
        if (team.isEmpty() || !containsMember(team.get(), playerId)) return TeamRole.NONE;
        try {
            Object rank = team.get().getClass().getMethod("getRankForPlayer", UUID.class).invoke(team.get(), playerId);
            if (rank instanceof Enum<?> value) {
                return switch (value.name()) {
                    case "OWNER" -> TeamRole.OWNER;
                    case "OFFICER" -> TeamRole.OFFICER;
                    case "MEMBER" -> TeamRole.MEMBER;
                    default -> TeamRole.NONE;
                };
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fail closed when FTB Teams changes unexpectedly.
        }
        return TeamRole.NONE;
    }

    private Optional<Object> manager() {
        try {
            Object api = apiMethod.invoke(null);
            if (api == null) return Optional.empty();
            Object loaded = api.getClass().getMethod("isManagerLoaded").invoke(api);
            if (!(loaded instanceof Boolean value) || !value) return Optional.empty();
            return Optional.ofNullable(api.getClass().getMethod("getManager").invoke(api));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<Object> invokeOptional(Object target, String method, UUID argument) {
        try {
            Object result = target.getClass().getMethod(method, UUID.class).invoke(target, argument);
            if (result instanceof Optional<?> optional) return (Optional<Object>) optional;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Optional integration fails closed.
        }
        return Optional.empty();
    }

    private boolean isPartyTeam(Object team) {
        try {
            Object result = team.getClass().getMethod("isPartyTeam").invoke(team);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private boolean containsMember(Object team, UUID playerId) {
        try {
            Object result = team.getClass().getMethod("getMembers").invoke(team);
            return result instanceof Collection<?> members && members.contains(playerId);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private Optional<TeamRef> toRef(Object team) {
        try {
            UUID id = (UUID) team.getClass().getMethod("getId").invoke(team);
            UUID owner = (UUID) team.getClass().getMethod("getOwner").invoke(team);
            String name = String.valueOf(team.getClass().getMethod("getShortName").invoke(team));
            return Optional.of(new TeamRef(id, name, owner));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
