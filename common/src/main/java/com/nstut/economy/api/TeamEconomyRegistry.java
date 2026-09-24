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
    private volatile TeamEconomyMode mode = TeamEconomyMode.PERSONAL_ONLY;
    private volatile TeamRole viewRole = TeamRole.MEMBER;
    private volatile TeamRole depositRole = TeamRole.MEMBER;
    private volatile TeamRole spendRole = TeamRole.OFFICER;
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
    public TeamRole adminRole() { return adminRole; }

    public void setViewRole(TeamRole role) { viewRole = Objects.requireNonNull(role, "role"); }
    public void setDepositRole(TeamRole role) { depositRole = Objects.requireNonNull(role, "role"); }
    public void setSpendRole(TeamRole role) { spendRole = Objects.requireNonNull(role, "role"); }
    public void setAdminRole(TeamRole role) { adminRole = Objects.requireNonNull(role, "role"); }

    /** Returns only party wallets that are currently usable under the configured mode. */
    public Optional<TeamRef> resolveTeam(UUID playerId) {
        if (playerId == null || mode == TeamEconomyMode.PERSONAL_ONLY) return Optional.empty();
        TeamEconomyProvider current = provider;
        if (current == null || !safeAvailable(current)) return Optional.empty();
        try {
            return current.resolveTeam(playerId)
                    .filter(team -> current.isMember(playerId, team.id()));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public Optional<AccountRef> teamPrincipal(UUID playerId) {
        return resolveTeam(playerId).map(TeamRef::account);
    }

    /**
     * Principal used when a UI/action asks for the configured default.
     * HYBRID intentionally remains personal-first.
     */
    public AccountRef defaultPrincipal(UUID playerId) {
        if (playerId == null) throw new IllegalArgumentException("playerId cannot be null");
        if (mode == TeamEconomyMode.TEAM_PRIMARY) {
            Optional<TeamRef> team = resolveTeam(playerId);
            if (team.isPresent() && roleFor(playerId, team.get().id()).atLeast(viewRole)) {
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
            return TeamWalletSnapshot.personalOnly(mode, personalBalance, spendRole);
        }

        TeamRef current = team.get();
        TeamRole role = roleFor(playerId, current.id());
        if (!role.atLeast(viewRole)) {
            return TeamWalletSnapshot.personalOnly(mode, personalBalance, spendRole);
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
                spendRole);
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
     * Revalidates the actor immediately before debiting the shared wallet.
     * This is the safe primitive for team payments/withdrawals outside the market.
     */
    public boolean spendFromTeam(IAccountManager accounts, UUID actor, AccountRef target,
                                 BigDecimal amount, ITransactionContext context) {
        Objects.requireNonNull(accounts, "accounts");
        if (actor == null || target == null || amount == null || amount.signum() <= 0) return false;
        Optional<TeamRef> team = resolveTeam(actor);
        if (team.isEmpty() || !canSpend(actor, team.get().id())) return false;
        return accounts.transfer(team.get().account(), target, amount, context);
    }

    /** Convenience withdrawal to the acting player's personal wallet. */
    public boolean withdrawToPlayer(IAccountManager accounts, UUID actor, BigDecimal amount,
                                    ITransactionContext context) {
        return actor != null && spendFromTeam(accounts, actor, AccountRef.player(actor), amount, context);
    }

    /** Fresh server-side membership/rank lookup; never trusts client state. */
    public TeamRole roleFor(UUID playerId, UUID teamId) {
        if (playerId == null || teamId == null || mode == TeamEconomyMode.PERSONAL_ONLY) return TeamRole.NONE;
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

    private static boolean safeAvailable(TeamEconomyProvider provider) {
        try {
            return provider.isAvailable();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
