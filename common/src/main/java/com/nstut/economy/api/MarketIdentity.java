package com.nstut.economy.api;

import java.util.Objects;
import java.util.UUID;

/** Economic ownership, human attribution, and physical storage are independent identities. */
public record MarketIdentity(AccountRef principal, UUID actor, UUID storageOwner) {
    public MarketIdentity {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(storageOwner, "storageOwner");
    }
    public static MarketIdentity personal(UUID player) {
        return new MarketIdentity(AccountRef.player(player), player, player);
    }
    public boolean authorized(TeamEconomyRegistry teams) {
        return switch (principal.kind()) {
            case PLAYER -> principal.id().equals(actor) && actor.equals(storageOwner);
            case TEAM -> teams.canSpend(actor, principal.id()) && teams.canSpend(storageOwner, principal.id());
            case SERVER -> true; // Only trusted server-order APIs may create these.
            case TAX -> false;
        };
    }
    public boolean canManage(UUID requester, TeamEconomyRegistry teams) {
        return principal.kind() == AccountKind.TEAM
                ? teams.canSpend(requester, principal.id())
                : actor.equals(requester);
    }
}
