package com.nstut.economy.compat;

import com.nstut.economy.api.TeamEconomyProvider;
import com.nstut.economy.api.TeamRef;
import com.nstut.economy.api.TeamRole;

import java.lang.reflect.InvocationTargetException;
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
    private static final String MANAGER_CLASS = "dev.ftb.mods.ftbteams.api.TeamManager";
    private static final String TEAM_CLASS = "dev.ftb.mods.ftbteams.api.Team";

    private final Method apiMethod;
    private final Method isManagerLoadedMethod;
    private final Method getManagerMethod;
    private final Method getTeamForPlayerMethod;
    private final Method getTeamByIdMethod;
    private final Method isPartyTeamMethod;
    private final Method getTeamIdMethod;
    private final Method getOwnerMethod;
    private final Method getShortNameMethod;
    private final Method getMembersMethod;
    private final Method getRankForPlayerMethod;

    private FtbTeamsTeamEconomyProvider(Method apiMethod,
                                        Method isManagerLoadedMethod,
                                        Method getManagerMethod,
                                        Method getTeamForPlayerMethod,
                                        Method getTeamByIdMethod,
                                        Method isPartyTeamMethod,
                                        Method getTeamIdMethod,
                                        Method getOwnerMethod,
                                        Method getShortNameMethod,
                                        Method getMembersMethod,
                                        Method getRankForPlayerMethod) {
        this.apiMethod = apiMethod;
        this.isManagerLoadedMethod = isManagerLoadedMethod;
        this.getManagerMethod = getManagerMethod;
        this.getTeamForPlayerMethod = getTeamForPlayerMethod;
        this.getTeamByIdMethod = getTeamByIdMethod;
        this.isPartyTeamMethod = isPartyTeamMethod;
        this.getTeamIdMethod = getTeamIdMethod;
        this.getOwnerMethod = getOwnerMethod;
        this.getShortNameMethod = getShortNameMethod;
        this.getMembersMethod = getMembersMethod;
        this.getRankForPlayerMethod = getRankForPlayerMethod;
    }

    public static Optional<FtbTeamsTeamEconomyProvider> createIfPresent() {
        try {
            ClassLoader loader = FtbTeamsTeamEconomyProvider.class.getClassLoader();
            Class<?> apiClass = Class.forName(API_CLASS, false, loader);
            Class<?> managerClass = Class.forName(MANAGER_CLASS, false, loader);
            Class<?> teamClass = Class.forName(TEAM_CLASS, false, loader);
            Method api = apiClass.getMethod("api");
            Class<?> apiInterface = api.getReturnType();
            return Optional.of(new FtbTeamsTeamEconomyProvider(
                    api,
                    apiInterface.getMethod("isManagerLoaded"),
                    apiInterface.getMethod("getManager"),
                    managerClass.getMethod("getTeamForPlayerID", UUID.class),
                    managerClass.getMethod("getTeamByID", UUID.class),
                    teamClass.getMethod("isPartyTeam"),
                    teamClass.getMethod("getId"),
                    teamClass.getMethod("getOwner"),
                    teamClass.getMethod("getShortName"),
                    teamClass.getMethod("getMembers"),
                    teamClass.getMethod("getRankForPlayer", UUID.class)
            ));
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
                .flatMap(manager -> invokeOptional(getTeamForPlayerMethod, manager, playerId))
                .filter(this::isPartyTeam)
                .flatMap(this::toRef);
    }

    @Override
    public Optional<TeamRef> getTeam(UUID teamId) {
        if (teamId == null) return Optional.empty();
        return manager()
                .flatMap(manager -> invokeOptional(getTeamByIdMethod, manager, teamId))
                .filter(this::isPartyTeam)
                .flatMap(this::toRef);
    }

    @Override
    public TeamRole getRole(UUID playerId, UUID teamId) {
        if (playerId == null || teamId == null) return TeamRole.NONE;
        Optional<Object> team = manager()
                .flatMap(manager -> invokeOptional(getTeamByIdMethod, manager, teamId))
                .filter(this::isPartyTeam);
        if (team.isEmpty() || !containsMember(team.get(), playerId)) return TeamRole.NONE;
        try {
            Object rank = getRankForPlayerMethod.invoke(team.get(), playerId);
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

    @Override
    public boolean isMember(UUID playerId, UUID teamId) {
        if (playerId == null || teamId == null) return false;
        return manager()
                .flatMap(manager -> invokeOptional(getTeamByIdMethod, manager, teamId))
                .filter(this::isPartyTeam)
                .map(team -> containsMember(team, playerId))
                .orElse(false);
    }

    private Optional<Object> manager() {
        try {
            Object api = apiMethod.invoke(null);
            if (api == null) return Optional.empty();
            Object loaded = isManagerLoadedMethod.invoke(api);
            if (!(loaded instanceof Boolean value) || !value) return Optional.empty();
            return Optional.ofNullable(getManagerMethod.invoke(api));
        } catch (InvocationTargetException notReady) {
            return Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<Object> invokeOptional(Method method, Object target, UUID argument) {
        try {
            Object result = method.invoke(target, argument);
            if (result instanceof Optional<?> optional) return (Optional<Object>) optional;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Optional integration fails closed.
        }
        return Optional.empty();
    }

    private boolean isPartyTeam(Object team) {
        try {
            Object result = isPartyTeamMethod.invoke(team);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private boolean containsMember(Object team, UUID playerId) {
        try {
            Object result = getMembersMethod.invoke(team);
            return result instanceof Collection<?> members && members.contains(playerId);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private Optional<TeamRef> toRef(Object team) {
        try {
            UUID id = (UUID) getTeamIdMethod.invoke(team);
            UUID owner = (UUID) getOwnerMethod.invoke(team);
            String name = String.valueOf(getShortNameMethod.invoke(team));
            return Optional.of(new TeamRef(id, name, owner));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
