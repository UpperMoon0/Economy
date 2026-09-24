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
        TeamRole spendRole
) {
    public TeamWalletSnapshot {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(personalBalance, "personalBalance");
        team = team == null ? Optional.empty() : team;
        Objects.requireNonNull(teamBalance, "teamBalance");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(spendRole, "spendRole");
    }

    public boolean teamVisible() {
        return team.isPresent();
    }

    public static TeamWalletSnapshot personalOnly(TeamEconomyMode mode, BigDecimal personalBalance,
                                                  TeamRole spendRole) {
        return new TeamWalletSnapshot(mode, personalBalance, Optional.empty(), BigDecimal.ZERO,
                TeamRole.NONE, false, false, spendRole);
    }
}
