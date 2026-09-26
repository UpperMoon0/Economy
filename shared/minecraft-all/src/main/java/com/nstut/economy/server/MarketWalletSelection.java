package com.nstut.economy.server;

import com.nstut.economy.api.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Explicit server-owned selection. A stale team selection is rejected, never silently charged personally. */
public final class MarketWalletSelection {
    private static final Map<UUID, AccountRef> selections = new HashMap<>();
    private MarketWalletSelection() {}
    public static AccountRef selected(UUID actor) {
        return selections.computeIfAbsent(actor, id -> EconomyApi.teamEconomy().defaultPrincipal(id));
    }
    public static void selectPersonal(UUID actor) { selections.put(actor, AccountRef.player(actor)); }
    public static boolean selectTeam(UUID actor) {
        var team = EconomyApi.teamEconomy().resolveTeam(actor);
        if (team.isEmpty() || !EconomyApi.teamEconomy().canSpend(actor, team.get().id())) return false;
        selections.put(actor, team.get().account());
        return true;
    }
    public static void reset(UUID actor) { selections.remove(actor); }
    public static void clear() { selections.clear(); }
    public static MarketIdentity identity(UUID actor) {
        var identity = new MarketIdentity(selected(actor), actor, actor);
        if (!identity.authorized(EconomyApi.teamEconomy())) throw new IllegalStateException("Selected team wallet unavailable or requires " + EconomyApi.teamEconomy().spendRole());
        return identity;
    }
    public static String label(UUID actor) {
        var selected = selected(actor);
        if (selected.kind() == AccountKind.PLAYER) return "Personal";
        var team = EconomyApi.teamEconomy().resolveTeam(actor).filter(t -> t.id().equals(selected.id()));
        return team.map(t -> "Team: " + t.displayName()).orElse("Team unavailable - /eco team use personal");
    }
}
