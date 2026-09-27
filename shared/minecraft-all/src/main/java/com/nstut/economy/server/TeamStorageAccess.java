package com.nstut.economy.server;

import com.nstut.economy.api.*;

import java.util.Optional;
import java.util.UUID;

/** Central authorization for PLAYER/TEAM-owned Vaults and Tanks. */
public final class TeamStorageAccess {
    private TeamStorageAccess() {}

    public static boolean canUse(UUID actor, AccountRef owner) {
        if (actor == null || owner == null) return false;
        return switch (owner.kind()) {
            case PLAYER -> owner.id().equals(actor);
            case TEAM -> EconomyApi.teamEconomy().canSpend(actor, owner.id());
            default -> false;
        };
    }

    public static boolean canAdmin(UUID actor, AccountRef owner) {
        if (actor == null || owner == null) return false;
        return switch (owner.kind()) {
            case PLAYER -> owner.id().equals(actor);
            case TEAM -> EconomyApi.teamEconomy().canAdmin(actor, owner.id());
            default -> false;
        };
    }

    public static Optional<AccountRef> currentTeamAdminTarget(UUID actor) {
        return EconomyApi.teamEconomy().resolveTeam(actor)
                .filter(team -> EconomyApi.teamEconomy().canAdmin(actor, team.id()))
                .map(TeamRef::account);
    }

    /** Reassignment is permitted only from storage the actor controls into self/current administered team. */
    public static boolean canReassign(UUID actor, AccountRef from, AccountRef to) {
        if (!canAdmin(actor, from) || to == null) return false;
        if (to.kind() == AccountKind.PLAYER) return to.id().equals(actor);
        if (to.kind() == AccountKind.TEAM) {
            return currentTeamAdminTarget(actor).filter(to::equals).isPresent();
        }
        return false;
    }
}
