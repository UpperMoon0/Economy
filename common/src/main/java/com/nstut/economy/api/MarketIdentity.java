package com.nstut.economy.api;

import java.util.Objects;
import java.util.UUID;

/** Economic ownership, human attribution, and physical storage ownership are independent identities. */
public record MarketIdentity(AccountRef principal, UUID actor, UUID storageOwner, AccountRef storageAccount) {
    public MarketIdentity {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(storageOwner, "storageOwner");
        Objects.requireNonNull(storageAccount, "storageAccount");
        if (!storageOwner.equals(storageAccount.id())) throw new IllegalArgumentException("Storage UUID must match typed owner");
    }

    public static MarketIdentity personal(UUID player) {
        return new MarketIdentity(AccountRef.player(player), player, player);
    }

    /** Legacy UUID storage is always personal, even when a team has the same UUID. */
    public MarketIdentity(AccountRef principal, UUID actor, UUID storageOwner) {
        this(principal, actor, storageOwner, AccountRef.player(storageOwner));
    }

    public MarketIdentity(AccountRef principal, UUID actor, AccountRef storageAccount) {
        this(principal, actor, storageAccount.id(), storageAccount);
    }

    public boolean authorized(TeamEconomyRegistry teams) {
        return switch (principal.kind()) {
            case PLAYER -> principal.id().equals(actor)
                    && storageOwner.equals(actor)
                    && storageAccount().equals(principal);
            case TEAM -> teams.canSpend(actor, principal.id())
                    && (storageAccount().equals(principal) || storageAccount.equals(AccountRef.player(actor)));
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
