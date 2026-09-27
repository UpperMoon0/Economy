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
        com.nstut.economy.api.internal.TeamEconomyLifecycleBridge.bind(
                TeamWalletLifecycle::observeFromProvider, TeamWalletLifecycle::isClosing,
                TeamWalletLifecycle::allowsProvider, TeamWalletLifecycle::ownsProvider);
    }

    public static void clear() {
        data = null;
        com.nstut.economy.api.internal.TeamEconomyLifecycleBridge.clear();
    }

    public static boolean isClosing(UUID teamId) {
        var state = data == null ? null : data.getTeamWallets().get(teamId);
        return state != null && state.closing();
    }

    private static boolean allowsProvider(EconomyId providerId, UUID teamId) {
        if (data == null || providerId == null || teamId == null) return true;
        TeamWalletState state = data.getTeamWallets().get(teamId);
        return state == null || !state.hasKnownProvider() || state.providerId().equals(providerId);
    }

    private static boolean ownsProvider(EconomyId providerId, UUID teamId) {
        if (data == null || providerId == null || teamId == null) return false;
        TeamWalletState state = data.getTeamWallets().get(teamId);
        return state != null && state.hasKnownProvider() && state.providerId().equals(providerId);
    }

    /** Deleted team-owned physical storage is entrusted to the last owner after cash settlement finishes. */
    public static java.util.Optional<AccountRef> replacementOwner(AccountRef owner) {
        if (owner == null || owner.kind() != AccountKind.TEAM || data == null) return java.util.Optional.empty();
        TeamWalletState state = data.getTeamWallets().get(owner.id());
        return state != null && state.closing() && state.storageSettled()
                ? java.util.Optional.of(AccountRef.player(state.ownerId()))
                : java.util.Optional.empty();
    }

    private static void observeFromProvider(EconomyId providerId, TeamRef team) {
        observe(providerId, team, liveMembers(providerId, team));
    }

    public static void observe(TeamRef team) {
        var provider = activeProvider();
        if (provider == null) return;
        EconomyId providerId = provider.providerId();
        observe(providerId, team, liveMembers(providerId, team));
    }

    public static void observe(TeamRef team, Collection<UUID> members) {
        var provider = activeProvider();
        if (provider == null) return;
        observe(provider.providerId(), team, members);
    }

    private static void observe(EconomyId providerId, TeamRef team, Collection<UUID> members) {
        if (data == null || providerId == null || team == null || isClosing(team.id())) return;
        TeamWalletState previous = data.getTeamWallets().get(team.id());
        if (previous != null && previous.hasKnownProvider() && !previous.providerId().equals(providerId)) {
            Economy.LOGGER.warn("Refusing to let Team provider {} claim wallet {} owned by {}",
                    providerId, team.id(), previous.providerId());
            return;
        }

        Collection<UUID> snapshot = members;
        if ((snapshot == null || snapshot.isEmpty()) && previous != null && !previous.settlementMembers().isEmpty()) {
            snapshot = previous.settlementMembers();
        }
        if (snapshot == null) snapshot = List.of();
        // Provider provenance is durable even when membership enumeration is temporarily unavailable.
        // An empty settlementMembers list means deletion settlement is blocked until a complete snapshot exists.
        data.putTeamWallet(new TeamWalletState(team.id(), team.ownerId(), providerId, false, false,
                java.util.List.copyOf(snapshot), java.util.Set.of()));
    }

    public static void deleted(TeamRef team) {
        var provider = activeProvider();
        if (provider == null || data == null || team == null) return;
        EconomyId providerId = provider.providerId();
        TeamWalletState previous = data.getTeamWallets().get(team.id());
        if (!canMutateLifecycle(previous, providerId, team.id())) return;
        Collection<UUID> members = previous.settlementMembers();
        if (members.isEmpty()) {
            Economy.LOGGER.error("Refusing to settle deleted team {} without an authoritative final member snapshot", team.id());
            return;
        }
        deleted(providerId, team, members);
    }

    public static void deleted(TeamRef team, Collection<UUID> members) {
        var provider = activeProvider();
        if (provider == null || data == null || team == null) return;
        deleted(provider.providerId(), team, members);
    }

    private static void deleted(EconomyId providerId, TeamRef team, Collection<UUID> members) {
        if (data == null || providerId == null || team == null) return;
        TeamWalletState previous = data.getTeamWallets().get(team.id());
        if (!canMutateLifecycle(previous, providerId, team.id())) return;
        if (previous.closing()) return;

        Collection<UUID> recipients = members;
        if (recipients == null || recipients.isEmpty()) recipients = previous.settlementMembers();
        if (recipients.isEmpty()) {
            Economy.LOGGER.error("Refusing to settle deleted team {} without an authoritative final member snapshot", team.id());
            return;
        }
        data.putTeamWallet(new TeamWalletState(team.id(), team.ownerId(), providerId, true, false,
                java.util.List.copyOf(recipients), java.util.Set.of()));
    }

    private static boolean canMutateLifecycle(TeamWalletState state, EconomyId providerId, UUID teamId) {
        if (state == null) {
            Economy.LOGGER.warn("Refusing Team lifecycle mutation for {} without persisted provider provenance", teamId);
            return false;
        }
        if (!state.hasKnownProvider()) {
            Economy.LOGGER.warn("Refusing Team lifecycle mutation for {} until a provider positively reclaims legacy state", teamId);
            return false;
        }
        if (!state.providerId().equals(providerId)) {
            Economy.LOGGER.warn("Refusing Team lifecycle mutation for {} from provider {}; wallet belongs to {}",
                    teamId, providerId, state.providerId());
            return false;
        }
        return true;
    }

    private static TeamEconomyProvider activeProvider() {
        var registry = EconomyApi.teamEconomy();
        var provider = registry.provider().orElse(null);
        return provider != null && registry.isProviderAvailable() ? provider : null;
    }

    private static Collection<UUID> liveMembers(EconomyId providerId, TeamRef team) {
        if (team == null || providerId == null) return List.of();
        var provider = activeProvider();
        if (provider == null || !providerId.equals(provider.providerId())) return List.of();
        try {
            Collection<UUID> members = provider.getMembers(team.id());
            if (members != null && !members.isEmpty()) return members;
        } catch (RuntimeException failure) {
            Economy.LOGGER.warn("Could not snapshot members for team {} from provider {}", team.id(), providerId, failure);
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
        EconomyId providerId = provider.providerId();

        // Include wallets created through the account API, but never let a different provider claim a persisted UUID.
        for (AccountRef ref : data.getAccountBalances().keySet()) {
            if (ref.kind() != AccountKind.TEAM || isClosing(ref.id())) continue;
            TeamWalletState existing = data.getTeamWallets().get(ref.id());
            if (existing != null && existing.hasKnownProvider() && !existing.providerId().equals(providerId)) continue;
            try {
                provider.getTeam(ref.id()).ifPresent(team -> observe(providerId, team, liveMembers(providerId, team)));
            } catch (RuntimeException failure) {
                Economy.LOGGER.warn("Could not observe team {} from provider {}", ref.id(), providerId, failure);
            }
        }

        // Only the provider that owns a persisted wallet may refresh or delete it. Legacy unowned state may be
        // claimed only by a positive getTeam() result; a negative lookup can never turn it into a deletion.
        for (TeamWalletState persisted : List.copyOf(data.getTeamWallets().values())) {
            TeamWalletState state = persisted;
            if (state.hasKnownProvider() && !state.providerId().equals(providerId)) continue;
            try {
                var current = provider.getTeam(state.teamId());
                if (!state.hasKnownProvider()) {
                    if (current.isEmpty()) continue; // Unknown legacy provenance is never deleted from a negative lookup.
                    state = state.withProvider(providerId);
                    data.putTeamWallet(state);
                }
                if (state.closing()) continue;
                if (current.isPresent()) {
                    TeamRef team = current.get();
                    observe(providerId, team, liveMembers(providerId, team));
                } else if (provider.isTeamDeleted(state.teamId())) {
                    deleted(providerId, new TeamRef(state.teamId(), "Deleted team", state.ownerId()), state.settlementMembers());
                }
            } catch (RuntimeException failure) {
                Economy.LOGGER.warn("Team lookup failed for provider {}; preserving wallet {}",
                        providerId, state.teamId(), failure);
            }
        }

        // Closing settlement is also provider-owned. If that provider is absent, preserve cash/storage fail-closed.
        orders.revalidateTeamOrders(level);
        for (TeamWalletState snapshot : data.getTeamWallets().values()) {
            if (!snapshot.closing() || snapshot.storageSettled() || orders.hasTeamRecoveryReferences(snapshot.teamId())) continue;
            if (!snapshot.hasKnownProvider() || !snapshot.providerId().equals(providerId)) continue;
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
