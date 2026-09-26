package com.nstut.economy.server;

import com.nstut.Economy;
import com.nstut.economy.api.*;
import com.nstut.economy.core.TransactionContext;
import com.nstut.economy.data.EconomyAccountData;
import net.minecraft.server.level.ServerLevel;

import java.math.BigDecimal;
import java.util.UUID;

/** Server-thread lifecycle; tombstones and owner attribution persist in the same saved data as cash. */
public final class TeamWalletLifecycle {
    private static EconomyAccountData data;
    private TeamWalletLifecycle() {}

    public static void bind(EconomyAccountData accountData) {
        data = accountData;
        EconomyApi.teamEconomy().bindLifecycle(TeamWalletLifecycle::observe, TeamWalletLifecycle::isClosing);
    }
    public static void clear() { data = null; EconomyApi.teamEconomy().clearLifecycle(); }
    public static boolean isClosing(UUID teamId) {
        var state = data == null ? null : data.getTeamWallets().get(teamId);
        return state != null && state.closing();
    }
    public static void observe(TeamRef team) {
        if (data != null && !isClosing(team.id())) data.putTeamWallet(new TeamWalletState(team.id(), team.ownerId(), false));
    }
    public static void deleted(TeamRef team) {
        if (data == null) return;
        // Preserve the first deletion owner; duplicate events cannot redirect an outstanding payout.
        if (!isClosing(team.id())) data.putTeamWallet(new TeamWalletState(team.id(), team.ownerId(), true));
    }
    public static void tick(ServerLevel level) {
        if (data == null || !EconomyApi.isReady()) return;
        reconcile(EconomyApi.accounts(), Economy.getOrderManager(), level);
    }
    public static void reconcile(IAccountManager accounts, com.nstut.economy.trading.OrderManager orders, ServerLevel level) {
        if (data == null) return;
        var registry = EconomyApi.teamEconomy();
        var provider = registry.provider().orElse(null);
        if (provider == null || !registry.isProviderAvailable()) return;
        // Include wallets created through the account API, even if no player has opened the UI.
        for (AccountRef ref : data.getAccountBalances().keySet()) {
            if (ref.kind() != AccountKind.TEAM || isClosing(ref.id())) continue;
            try { provider.getTeam(ref.id()).ifPresent(TeamWalletLifecycle::observe); }
            catch (RuntimeException failure) { Economy.LOGGER.warn("Could not observe team {}", ref.id(), failure); }
        }
        for (TeamWalletState state : data.getTeamWallets().values()) {
            if (state.closing()) continue;
            try {
                var current = provider.getTeam(state.teamId());
                if (current.isPresent()) observe(current.get());
                else if (provider.isTeamDeleted(state.teamId()))
                    deleted(new TeamRef(state.teamId(), "Deleted team", state.ownerId()));
            } catch (RuntimeException failure) {
                Economy.LOGGER.warn("Team lookup failed; preserving wallet {}", state.teamId(), failure);
            }
        }
        orders.revalidateTeamOrders(level);
        for (TeamWalletState state : data.getTeamWallets().values()) {
            if (!state.closing() || orders.hasTeamRecoveryReferences(state.teamId())) continue;
            var account = accounts.getOrCreateTeamAccount(state.teamId());
            BigDecimal remaining = account.getBalance();
            if (remaining.signum() > 0) {
                accounts.transfer(AccountRef.team(state.teamId()), AccountRef.player(state.ownerId()), remaining,
                        TransactionContext.transfer("Disbanded team wallet settlement", state.ownerId()));
            }
            // Retain the tombstone. A repeated tick/replayed event drains zero, never a remembered original amount.
        }
    }
}
