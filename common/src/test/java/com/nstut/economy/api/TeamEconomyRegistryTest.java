package com.nstut.economy.api;

import com.nstut.economy.core.AccountManager;
import com.nstut.economy.core.TransactionContext;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeamEconomyRegistryTest {
    @Test
    void disabledModeDoesNotExposeTeamEconomy() {
        MutableProvider provider = new MutableProvider();
        TeamEconomyRegistry registry = new TeamEconomyRegistry();
        registry.registerProvider(provider);

        registry.setMode(TeamEconomyMode.PERSONAL_ONLY);
        assertEquals(TeamEconomyMode.PERSONAL_ONLY, registry.mode());
        assertTrue(registry.resolveTeam(provider.playerId).isEmpty());
        assertEquals(AccountRef.player(provider.playerId), registry.defaultPrincipal(provider.playerId));
    }

    @Test
    void enabledDefaultExposesWalletButKeepsPersonalAsDefault() {
        MutableProvider provider = new MutableProvider();
        TeamEconomyRegistry registry = new TeamEconomyRegistry();
        registry.registerProvider(provider);
        assertEquals(TeamEconomyMode.HYBRID, registry.mode());

        assertEquals(provider.team.id(), registry.resolveTeam(provider.playerId).orElseThrow().id());
        assertEquals(AccountRef.player(provider.playerId), registry.defaultPrincipal(provider.playerId));
        assertTrue(registry.canView(provider.playerId, provider.team.id()));
        assertTrue(registry.canDeposit(provider.playerId, provider.team.id()));
        assertFalse(registry.canSpend(provider.playerId, provider.team.id()));

        AccountManager accounts = new AccountManager();
        assertEquals(AccountKind.TEAM,
                registry.teamAccount(accounts, provider.playerId).orElseThrow().getAccountRef().kind());
    }

    @Test
    void authorizedMoneyOperationsRecheckRoleAtMutationTime() {
        MutableProvider provider = new MutableProvider();
        TeamEconomyRegistry registry = new TeamEconomyRegistry();
        registry.registerProvider(provider);
        registry.setMode(TeamEconomyMode.HYBRID);
        AccountManager accounts = new AccountManager();

        var personal = accounts.getOrCreatePlayerAccount(provider.playerId);
        assertTrue(personal.credit(new BigDecimal("100"), TransactionContext.adminGive("test")));

        assertTrue(registry.depositFromPlayer(accounts, provider.playerId, new BigDecimal("40"),
                TransactionContext.transfer("team deposit", provider.team.id())));
        assertEquals(new BigDecimal("60"), personal.getBalance());
        assertEquals(new BigDecimal("40"), accounts.getTeamAccount(provider.team.id()).orElseThrow().getBalance());

        assertFalse(registry.withdrawToPlayer(accounts, provider.playerId, BigDecimal.TEN,
                TransactionContext.transfer("member cannot spend", provider.playerId)));
        assertEquals(new BigDecimal("40"), accounts.getTeamAccount(provider.team.id()).orElseThrow().getBalance());

        provider.role = TeamRole.OFFICER;
        assertTrue(registry.canSpend(provider.playerId, provider.team.id()),
                "officer must retain market-spend permission");
        assertFalse(registry.canWithdraw(provider.playerId, provider.team.id()),
                "market spending must not imply treasury extraction");
        assertFalse(registry.withdrawToPlayer(accounts, provider.playerId, BigDecimal.TEN,
                TransactionContext.transfer("officer cannot withdraw", provider.playerId)));
        assertEquals(new BigDecimal("60"), personal.getBalance());
        assertEquals(new BigDecimal("40"), accounts.getTeamAccount(provider.team.id()).orElseThrow().getBalance());

        provider.role = TeamRole.OWNER;
        assertTrue(registry.canWithdraw(provider.playerId, provider.team.id()));
        assertTrue(registry.withdrawToPlayer(accounts, provider.playerId, BigDecimal.TEN,
                TransactionContext.transfer("owner withdraw", provider.playerId)));
        assertEquals(new BigDecimal("70"), personal.getBalance());
        assertEquals(new BigDecimal("30"), accounts.getTeamAccount(provider.team.id()).orElseThrow().getBalance());

        provider.role = TeamRole.MEMBER;
        assertFalse(registry.withdrawToPlayer(accounts, provider.playerId, BigDecimal.ONE,
                TransactionContext.transfer("demoted", provider.playerId)));
    }

    @Test
    void teamPrimaryUsesFreshRankChecksAndFailsClosedAfterDemotion() {
        MutableProvider provider = new MutableProvider();
        provider.role = TeamRole.OFFICER;
        TeamEconomyRegistry registry = new TeamEconomyRegistry();
        registry.registerProvider(provider);
        registry.setMode(TeamEconomyMode.TEAM_PRIMARY);

        assertEquals(provider.team.account(), registry.defaultPrincipal(provider.playerId));
        assertTrue(registry.canSpend(provider.playerId, provider.team.id()));

        provider.role = TeamRole.MEMBER;
        assertFalse(registry.canSpend(provider.playerId, provider.team.id()),
                "rank must be revalidated rather than cached");
        assertTrue(registry.canView(provider.playerId, provider.team.id()));
        assertEquals(AccountRef.player(provider.playerId), registry.defaultPrincipal(provider.playerId),
                "TEAM_PRIMARY must not default to a wallet the actor cannot spend");

        provider.member = false;
        assertEquals(AccountRef.player(provider.playerId), registry.defaultPrincipal(provider.playerId));
        assertFalse(registry.canView(provider.playerId, provider.team.id()));
    }

    @Test
    void walletSnapshotDistinguishesPersonalAndTeamAndClearsAfterLeave() {
        MutableProvider provider = new MutableProvider();
        provider.role = TeamRole.MEMBER;
        TeamEconomyRegistry registry = new TeamEconomyRegistry();
        registry.registerProvider(provider);
        registry.setMode(TeamEconomyMode.HYBRID);
        AccountManager accounts = new AccountManager();

        var personal = accounts.getOrCreatePlayerAccount(provider.playerId);
        personal.credit(new BigDecimal("25"), TransactionContext.adminGive("test"));
        var team = accounts.getOrCreateTeamAccount(provider.team.id());
        team.credit(new BigDecimal("80"), TransactionContext.adminGive("test"));

        TeamWalletSnapshot snapshot = registry.walletSnapshot(accounts, provider.playerId);
        assertEquals(new BigDecimal("25"), snapshot.personalBalance());
        assertTrue(snapshot.teamVisible());
        assertEquals("Test Party", snapshot.team().orElseThrow().displayName());
        assertEquals(new BigDecimal("80"), snapshot.teamBalance());
        assertEquals(TeamRole.MEMBER, snapshot.role());
        assertTrue(snapshot.canDeposit());
        assertFalse(snapshot.canSpend());
        assertFalse(snapshot.canWithdraw());
        assertEquals(TeamRole.OFFICER, snapshot.spendRole());
        assertEquals(TeamRole.OWNER, snapshot.withdrawRole());

        provider.member = false;
        TeamWalletSnapshot afterLeave = registry.walletSnapshot(accounts, provider.playerId);
        assertFalse(afterLeave.teamVisible(), "leave/kick must remove team UI state immediately");
        assertEquals(BigDecimal.ZERO, afterLeave.teamBalance());
        assertEquals(TeamRole.NONE, afterLeave.role());
    }

    @Test
    void walletSnapshotHidesTeamWhenViewPermissionIsLost() {
        MutableProvider provider = new MutableProvider();
        TeamEconomyRegistry registry = new TeamEconomyRegistry();
        registry.registerProvider(provider);
        registry.setMode(TeamEconomyMode.HYBRID);
        registry.setViewRole(TeamRole.OFFICER);
        AccountManager accounts = new AccountManager();

        assertFalse(registry.walletSnapshot(accounts, provider.playerId).teamVisible());

        provider.role = TeamRole.OFFICER;
        assertTrue(registry.walletSnapshot(accounts, provider.playerId).teamVisible());
    }

    @Test
    void storageAdministrationIsIndependentFromMarketSpend() {
        MutableProvider provider = new MutableProvider();
        provider.role = TeamRole.OFFICER;
        TeamEconomyRegistry registry = new TeamEconomyRegistry();
        registry.registerProvider(provider);
        registry.setMode(TeamEconomyMode.HYBRID);

        assertTrue(registry.canSpend(provider.playerId, provider.team.id()),
                "officer should keep team market-spend permission");
        assertFalse(registry.canAdmin(provider.playerId, provider.team.id()),
                "market-spend permission must not authorize storage administration");

        provider.role = TeamRole.OWNER;
        assertTrue(registry.canAdmin(provider.playerId, provider.team.id()));

        registry.setAdminRole(TeamRole.OFFICER);
        provider.role = TeamRole.OFFICER;
        assertTrue(registry.canAdmin(provider.playerId, provider.team.id()),
                "storage administration must honor the configurable adminRole threshold");
    }

    @Test
    void permissionThresholdCannotAuthorizeNonmembers() {
        TeamEconomyRegistry registry = new TeamEconomyRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.setSpendRole(TeamRole.NONE));
        assertThrows(IllegalArgumentException.class, () -> registry.setWithdrawRole(TeamRole.NONE));
        assertThrows(IllegalArgumentException.class, () -> registry.setAdminRole(TeamRole.NONE));
        assertFalse(registry.canWithdraw(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void legacyWalletSnapshotDoesNotGrantSeparateWithdrawalPermission() {
        TeamWalletSnapshot snapshot = new TeamWalletSnapshot(TeamEconomyMode.HYBRID, BigDecimal.TEN,
                Optional.empty(), BigDecimal.ZERO, TeamRole.OFFICER, true, true, TeamRole.OFFICER);
        assertFalse(snapshot.canWithdraw());
        assertEquals(TeamRole.OWNER, snapshot.withdrawRole());
        assertFalse(TeamWalletSnapshot.personalOnly(TeamEconomyMode.PERSONAL_ONLY, BigDecimal.TEN,
                TeamRole.OFFICER).canWithdraw());
    }

    private static final class MutableProvider implements TeamEconomyProvider {
        final UUID playerId = UUID.randomUUID();
        final TeamRef team = new TeamRef(UUID.randomUUID(), "Test Party", playerId);
        TeamRole role = TeamRole.MEMBER;
        boolean member = true;

        @Override
        public Optional<TeamRef> resolveTeam(UUID playerId) {
            return member && this.playerId.equals(playerId) ? Optional.of(team) : Optional.empty();
        }

        @Override
        public Optional<TeamRef> getTeam(UUID teamId) {
            return team.id().equals(teamId) ? Optional.of(team) : Optional.empty();
        }

        @Override
        public TeamRole getRole(UUID playerId, UUID teamId) {
            return member && this.playerId.equals(playerId) && team.id().equals(teamId) ? role : TeamRole.NONE;
        }

        @Override
        public boolean isMember(UUID playerId, UUID teamId) {
            return member && this.playerId.equals(playerId) && team.id().equals(teamId);
        }
    }
}
