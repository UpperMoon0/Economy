package com.nstut.economy.api;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Server-authoritative wallet state suitable for user-facing balance/status sync.
 * Team identity and authorization are resolved fresh when the snapshot is built.
 */
public record TeamWalletSnapshot(
        TeamEconomyMode mode,
        BigDecimal personalBalance,
        Optional<TeamRef> team,
        BigDecimal teamBalance,
        TeamRole role,
        boolean canDeposit,
        boolean canSpend,
        boolean canPayout,
        TeamRole spendRole,
        TeamRole payoutRole
) {
    public TeamWalletSnapshot {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(personalBalance, "personalBalance");
        team = team == null ? Optional.empty() : team;
        Objects.requireNonNull(teamBalance, "teamBalance");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(spendRole, "spendRole");
        Objects.requireNonNull(payoutRole, "payoutRole");
    }

    /** Legacy snapshots do not grant the separately privileged direct payout permission. */
    public TeamWalletSnapshot(TeamEconomyMode mode, BigDecimal personalBalance, Optional<TeamRef> team,
                              BigDecimal teamBalance, TeamRole role, boolean canDeposit, boolean canSpend,
                              TeamRole spendRole) {
        this(mode, personalBalance, team, teamBalance, role, canDeposit, canSpend, false, spendRole, TeamRole.OWNER);
    }

    public static TeamWalletSnapshot personalOnly(TeamEconomyMode mode, BigDecimal balance, TeamRole spendRole) {
        return personalOnly(mode, balance, spendRole, TeamRole.OWNER);
    }

    public boolean teamVisible() {
        return team.isPresent();
    }

    public static TeamWalletSnapshot personalOnly(TeamEconomyMode mode, BigDecimal personalBalance,
                                                  TeamRole spendRole, TeamRole payoutRole) {
        return new TeamWalletSnapshot(mode, personalBalance, Optional.empty(), BigDecimal.ZERO,
                TeamRole.NONE, false, false, false, spendRole, payoutRole);
    }
}
