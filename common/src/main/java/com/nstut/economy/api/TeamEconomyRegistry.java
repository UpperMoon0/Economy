package com.nstut.economy.api;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Process-level team-economy policy and provider registry.
 *
 * <p>Authorization is deliberately resolved on every call. Economy does not
 * cache membership or ranks, so a kick/demotion takes effect before the next
 * destructive team action.</p>
 */
public final class TeamEconomyRegistry {
    private volatile TeamEconomyProvider provider;
    private volatile java.util.function.Consumer<TeamRef> observer = team -> {};
    private volatile java.util.function.Predicate<UUID> closing = team -> false;

    /** Internal server lifecycle hooks; cleared on server shutdown. */
    public void bindLifecycle(java.util.function.Consumer<TeamRef> observer, java.util.function.Predicate<UUID> closing) {
        this.observer = Objects.requireNonNull(observer);
        this.closing = Objects.requireNonNull(closing);
    }
    public void clearLifecycle() { observer = team -> {}; closing = team -> false; }
    public boolean isClosing(UUID teamId) { return closing.test(teamId); }

    private volatile TeamEconomyMode mode = TeamEconomyMode.HYBRID;
    private volatile TeamRole viewRole = TeamRole.MEMBER;
    private volatile TeamRole depositRole = TeamRole.MEMBER;
    private volatile TeamRole spendRole = TeamRole.OFFICER;
    private volatile TeamRole payoutRole = TeamRole.OWNER;
    private volatile TeamRole adminRole = TeamRole.OWNER;

    public Optional<TeamEconomyProvider> provider() {
        return Optional.ofNullable(provider);
    }

    public synchronized void registerProvider(TeamEconomyProvider provider) {
        Objects.requireNonNull(provider, "provider");
        if (this.provider != null && this.provider != provider) {
            throw new IllegalStateException("A team economy provider is already registered");
        }
        this.provider = provider;
    }

    public synchronized boolean unregisterProvider(TeamEconomyProvider provider) {
        if (provider != null && this.provider == provider) {
            this.provider = null;
            return true;
        }
        return false;
    }

    public TeamEconomyMode mode() { return mode; }
    public void setMode(TeamEconomyMode mode) { this.mode = Objects.requireNonNull(mode, "mode"); }

    public TeamRole viewRole() { return viewRole; }
    public TeamRole depositRole() { return depositRole; }
    public TeamRole spendRole() { return spendRole; }
    public TeamRole payoutRole() { return payoutRole; }
    public TeamRole adminRole() { return adminRole; }

    public void setViewRole(TeamRole role) { viewRole = requireMemberRole(role); }
    public void setDepositRole(TeamRole role) { depositRole = requireMemberRole(role); }
    public void setSpendRole(TeamRole role) { spendRole = requireMemberRole(role); }
    public void setPayoutRole(TeamRole role) { payoutRole = requireMemberRole(role); }
    public void setAdminRole(TeamRole role) { adminRole = requireMemberRole(role); }

    private static TeamRole requireMemberRole(TeamRole role) {
        Objects.requireNonNull(role, "role");
        if (role == TeamRole.NONE) throw new IllegalArgumentException("A permission threshold must require membership");
        return role;
    }

    /** Returns only party wallets that are currently usable under the configured mode. */
    public Optional<TeamRef> resolveTeam(UUID playerId) {
        if (playerId == null || mode == TeamEconomyMode.PERSONAL_ONLY) return Optional.empty();
        TeamEconomyProvider current = provider;
        if (current == null || !safeAvailable(current)) return Optional.empty();
        try {
            return current.resolveTeam(playerId)
                    .filter(team -> current.isMember(playerId, team.id()))
                    .filter(team -> !closing.test(team.id()))
                    .map(team -> { observer.accept(team); return team; });
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public Optional<AccountRef> teamPrincipal(UUID playerId) {
        return resolveTeam(playerId).map(TeamRef::account);
    }

    /**
     * Principal used when a spend-capable UI/action asks for the configured default.
     * HYBRID intentionally remains personal-first; TEAM_PRIMARY selects a team only when the actor can spend it.
     */
    public AccountRef defaultPrincipal(UUID playerId) {
        if (playerId == null) throw new IllegalArgumentException("playerId cannot be null");
        if (mode == TeamEconomyMode.TEAM_PRIMARY) {
            Optional<TeamRef> team = resolveTeam(playerId);
            if (team.isPresent() && roleFor(playerId, team.get().id()).atLeast(spendRole)) {
                return team.get().account();
            }
        }
        return AccountRef.player(playerId);
    }

    public Optional<IBankAccount> teamAccount(IAccountManager accounts, UUID playerId) {
        Objects.requireNonNull(accounts, "accounts");
        Optional<TeamRef> team = resolveTeam(playerId);
        if (team.isEmpty() || !canView(playerId, team.get().id())) return Optional.empty();
        return Optional.of(accounts.getOrCreateTeamAccount(team.get().id()));
    }

    /**
     * Builds a fresh, server-authoritative wallet view for UI/network sync.
     * If membership/rank changes between resolution and authorization, the team
     * portion is hidden rather than exposing stale state.
     */
    public TeamWalletSnapshot walletSnapshot(IAccountManager accounts, UUID playerId) {
        Objects.requireNonNull(accounts, "accounts");
        if (playerId == null) throw new IllegalArgumentException("playerId cannot be null");

        BigDecimal personalBalance = accounts.getOrCreatePlayerAccount(playerId).getBalance();
        Optional<TeamRef> team = resolveTeam(playerId);
        if (team.isEmpty()) {
            return TeamWalletSnapshot.personalOnly(mode, personalBalance, spendRole, payoutRole);
        }

        TeamRef current = team.get();
        TeamRole role = roleFor(playerId, current.id());
        if (!role.atLeast(viewRole)) {
            return TeamWalletSnapshot.personalOnly(mode, personalBalance, spendRole, payoutRole);
        }

        BigDecimal teamBalance = accounts.getOrCreateTeamAccount(current.id()).getBalance();
        return new TeamWalletSnapshot(
                mode,
                personalBalance,
                Optional.of(current),
                teamBalance,
                role,
                role.atLeast(depositRole),
                role.atLeast(spendRole),
                role.atLeast(payoutRole),
                spendRole,
                payoutRole);
    }

    public boolean canView(UUID playerId, UUID teamId) {
        return roleFor(playerId, teamId).atLeast(viewRole);
    }

    public boolean canDeposit(UUID playerId, UUID teamId) {
        return roleFor(playerId, teamId).atLeast(depositRole);
    }

    public boolean canSpend(UUID playerId, UUID teamId) {
        return roleFor(playerId, teamId).atLeast(spendRole);
    }

    /** Direct Team payouts to other players are deliberately stricter than market spending. */
    public boolean canPayout(UUID playerId, UUID teamId) {
        return roleFor(playerId, teamId).atLeast(payoutRole);
    }

    public boolean canAdmin(UUID playerId, UUID teamId) {
        return roleFor(playerId, teamId).atLeast(adminRole);
    }

    /**
     * Atomically chooses the actor's current party, revalidates deposit permission,
     * then transfers from the actor's personal wallet into that TEAM principal.
     */
    public boolean depositFromPlayer(IAccountManager accounts, UUID actor, BigDecimal amount,
                                     ITransactionContext context) {
        Objects.requireNonNull(accounts, "accounts");
        if (actor == null || amount == null || amount.signum() <= 0) return false;
        Optional<TeamRef> team = resolveTeam(actor);
        if (team.isEmpty() || !canDeposit(actor, team.get().id())) return false;
        return accounts.transfer(AccountRef.player(actor), team.get().account(), amount, context);
    }

    /**
     * Revalidates the actor immediately before a direct payout from the shared wallet.
     * Self-payout is forbidden: Team funds only become personal through lifecycle settlement.
     */
    public boolean spendFromTeam(IAccountManager accounts, UUID actor, AccountRef target,
                                 BigDecimal amount, ITransactionContext context) {
        Objects.requireNonNull(accounts, "accounts");
        if (actor == null || target == null || amount == null || amount.signum() <= 0) return false;
        if (target.equals(AccountRef.player(actor))) return false;
        Optional<TeamRef> team = resolveTeam(actor);
        if (team.isEmpty() || !canPayout(actor, team.get().id())) return false;
        if (target.kind() == AccountKind.TEAM && closing.test(target.id())) return false;
        return accounts.transfer(team.get().account(), target, amount, context);
    }

    /** Fresh server-side membership/rank lookup; never trusts client state. */
    public TeamRole roleFor(UUID playerId, UUID teamId) {
        if (playerId == null || teamId == null || closing.test(teamId) || mode == TeamEconomyMode.PERSONAL_ONLY) return TeamRole.NONE;
        TeamEconomyProvider current = provider;
        if (current == null || !safeAvailable(current)) return TeamRole.NONE;
        try {
            Optional<TeamRef> currentTeam = current.resolveTeam(playerId);
            if (currentTeam.isEmpty() || !currentTeam.get().id().equals(teamId)) return TeamRole.NONE;
            if (!current.isMember(playerId, teamId)) return TeamRole.NONE;
            TeamRole role = current.getRole(playerId, teamId);
            return role == null ? TeamRole.NONE : role;
        } catch (RuntimeException ignored) {
            return TeamRole.NONE;
        }
    }

    public boolean isProviderAvailable() { return provider != null && safeAvailable(provider); }

    private static boolean safeAvailable(TeamEconomyProvider provider) {
        try {
            return provider.isAvailable();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
