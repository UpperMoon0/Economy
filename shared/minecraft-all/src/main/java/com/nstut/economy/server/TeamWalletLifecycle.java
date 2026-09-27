package com.nstut.economy.server;

import com.nstut.Economy;
import com.nstut.economy.api.*;
import com.nstut.economy.api.internal.TeamWalletState;
import com.nstut.economy.core.TransactionContext;
import com.nstut.economy.data.EconomyAccountData;
import net.minecraft.server.level.ServerLevel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Server-thread lifecycle; closure snapshots and payout progress persist beside team balances. */
public final class TeamWalletLifecycle {
    private static EconomyAccountData data;
    private TeamWalletLifecycle() {}

    public static void bind(EconomyAccountData accountData) {
        data = accountData;
        com.nstut.economy.api.internal.TeamEconomyLifecycleBridge.bind(TeamWalletLifecycle::observe, TeamWalletLifecycle::isClosing);
    }

    public static void clear() {
        data = null;
        com.nstut.economy.api.internal.TeamEconomyLifecycleBridge.clear();
    }

    public static boolean isClosing(UUID teamId) {
        var state = data == null ? null : data.getTeamWallets().get(teamId);
        return state != null && state.closing();
    }

    /** Deleted team-owned physical storage is entrusted to the last owner after cash settlement finishes. */
    public static java.util.Optional<AccountRef> replacementOwner(AccountRef owner) {
        if (owner == null || owner.kind() != AccountKind.TEAM || data == null) return java.util.Optional.empty();
        TeamWalletState state = data.getTeamWallets().get(owner.id());
        return state != null && state.closing() && state.storageSettled()
                ? java.util.Optional.of(AccountRef.player(state.ownerId()))
                : java.util.Optional.empty();
    }

    public static void observe(TeamRef team) {
        observe(team, liveMembers(team));
    }

    public static void observe(TeamRef team, Collection<UUID> members) {
        if (data == null || team == null || isClosing(team.id())) return;
        TeamWalletState previous = data.getTeamWallets().get(team.id());
        Collection<UUID> snapshot = members;
        if (snapshot == null || snapshot.isEmpty()) {
            if (previous == null || previous.settlementMembers().isEmpty()) return;
            snapshot = previous.settlementMembers();
        }
        data.putTeamWallet(new TeamWalletState(team.id(), team.ownerId(), false, false,
                java.util.List.copyOf(snapshot), java.util.Set.of()));
    }

    public static void deleted(TeamRef team) {
        if (data == null || team == null) return;
        TeamWalletState previous = data.getTeamWallets().get(team.id());
        Collection<UUID> members = previous != null ? previous.settlementMembers() : liveMembers(team);
        if (members == null || members.isEmpty()) {
            Economy.LOGGER.error("Refusing to settle deleted team {} without an authoritative final member snapshot", team.id());
            return;
        }
        deleted(team, members);
    }

    public static void deleted(TeamRef team, Collection<UUID> members) {
        if (data == null || team == null) return;
        // The first deletion snapshot is authoritative. Replayed events cannot change recipients or custody.
        TeamWalletState previous = data.getTeamWallets().get(team.id());
        if (previous != null && previous.closing()) return;
        Collection<UUID> recipients = members;
        if (recipients == null || recipients.isEmpty()) {
            recipients = previous != null ? previous.settlementMembers() : List.of();
        }
        if (recipients.isEmpty()) {
            Economy.LOGGER.error("Refusing to settle deleted team {} without an authoritative final member snapshot", team.id());
            return;
        }
        data.putTeamWallet(new TeamWalletState(team.id(), team.ownerId(), true, false, java.util.List.copyOf(recipients), java.util.Set.of()));
    }

    private static Collection<UUID> liveMembers(TeamRef team) {
        if (team == null) return List.of();
        var registry = EconomyApi.teamEconomy();
        var provider = registry.provider().orElse(null);
        if (provider != null && registry.isProviderAvailable()) {
            try {
                Collection<UUID> members = provider.getMembers(team.id());
                if (members != null && !members.isEmpty()) return members;
            } catch (RuntimeException failure) {
                Economy.LOGGER.warn("Could not snapshot members for team {}", team.id(), failure);
            }
        }
        return List.of();
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

        // Refresh owner/member snapshots while teams are live; polling is also the fallback if lifecycle events are unavailable.
        for (TeamWalletState state : data.getTeamWallets().values()) {
            if (state.closing()) continue;
            try {
                var current = provider.getTeam(state.teamId());
                if (current.isPresent()) observe(current.get());
                else if (provider.isTeamDeleted(state.teamId()))
                    deleted(new TeamRef(state.teamId(), "Deleted team", state.ownerId()), state.settlementMembers());
            } catch (RuntimeException failure) {
                Economy.LOGGER.warn("Team lookup failed; preserving wallet {}", state.teamId(), failure);
            }
        }

        // Closing principals are already rejected by the registry. First unwind every outstanding order/recovery reference.
        orders.revalidateTeamOrders(level);
        for (TeamWalletState snapshot : data.getTeamWallets().values()) {
            if (!snapshot.closing() || snapshot.storageSettled() || orders.hasTeamRecoveryReferences(snapshot.teamId())) continue;
            settle(snapshot, accounts, level);
        }
    }

    private static void settle(TeamWalletState snapshot, IAccountManager accounts, ServerLevel level) {
        TeamWalletState state = data.getTeamWallets().getOrDefault(snapshot.teamId(), snapshot);
        AccountRef teamAccount = AccountRef.team(state.teamId());
        var account = accounts.getOrCreateTeamAccount(state.teamId());

        ArrayList<UUID> unpaid = new ArrayList<>();
        for (UUID member : state.settlementMembers()) if (!state.settledMembers().contains(member)) unpaid.add(member);
        unpaid.sort(UUID::compareTo);

        for (int index = 0; index < unpaid.size(); index++) {
            BigDecimal remaining = account.getBalance();
            if (remaining.signum() <= 0) break;
            int recipientsLeft = unpaid.size() - index;
            UUID recipient = unpaid.get(index);
            BigDecimal amount;
            if (recipientsLeft == 1) {
                amount = remaining; // final recipient receives deterministic rounding dust
            } else {
                int scale = Math.max(8, Math.min(32, remaining.scale()));
                amount = remaining.divide(BigDecimal.valueOf(recipientsLeft), scale, RoundingMode.DOWN);
            }

            // Extremely small balances can round to zero for early recipients; persist that zero-share decision.
            if (amount.signum() == 0) {
                state = state.withSettledMember(recipient);
                data.putTeamWallet(state);
                continue;
            }

            if (!accounts.transfer(teamAccount, AccountRef.player(recipient), amount,
                    TransactionContext.transfer("Disbanded team equal-share settlement", state.ownerId()))) {
                return; // preserve the exact remaining recipients and retry on a later tick
            }
            state = state.withSettledMember(recipient);
            data.putTeamWallet(state);
        }

        if (account.getBalance().signum() > 0) return;

        // Cash is fully distributed. Physical blocks cannot be meaningfully split, so last-owner custody prevents orphaning.
        AccountRef storageSuccessor = AccountRef.player(state.ownerId());
        com.nstut.economy.blocks.VaultManager.reassignOwner(level, teamAccount, storageSuccessor);
        com.nstut.economy.blocks.TankManager.reassignOwner(level, teamAccount, storageSuccessor);
        data.putTeamWallet(state.withStorageSettled());
    }
}
