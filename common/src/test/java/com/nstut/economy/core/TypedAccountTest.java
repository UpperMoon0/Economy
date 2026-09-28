package com.nstut.economy.core;

import com.nstut.economy.api.AccountKind;
import com.nstut.economy.api.AccountRef;
import com.nstut.economy.config.EconomyConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TypedAccountTest {
    private final BigDecimal previousStartingBalance = EconomyConfig.getInstance().getStartingBalance();

    @AfterEach
    void restoreConfig() {
        EconomyConfig.getInstance().setStartingBalance(previousStartingBalance);
    }

    @Test
    void sameUuidCanIdentifyIndependentPlayerAndTeamAccounts() {
        AccountManager manager = new AccountManager();
        UUID id = UUID.randomUUID();

        var player = manager.getOrCreateAccount(AccountRef.player(id));
        var team = manager.getOrCreateAccount(AccountRef.team(id));

        assertNotSame(player, team);
        assertEquals(AccountKind.PLAYER, player.getAccountRef().kind());
        assertEquals(AccountKind.TEAM, team.getAccountRef().kind());
        assertEquals(id, player.getOwner());
        assertEquals(id, team.getOwner());

        assertTrue(player.credit(new BigDecimal("12"), TransactionContext.adminGive("test")));
        assertTrue(team.credit(new BigDecimal("7"), TransactionContext.adminGive("test")));
        assertEquals(new BigDecimal("12"), player.getBalance());
        assertEquals(new BigDecimal("7"), team.getBalance());
    }

    @Test
    void startingBalanceAppliesOnlyToPlayers() {
        EconomyConfig.getInstance().setStartingBalance(new BigDecimal("250"));
        AccountManager manager = new AccountManager();

        var player = manager.getOrCreatePlayerAccount(UUID.randomUUID());
        var team = manager.getOrCreateTeamAccount(UUID.randomUUID());
        var server = manager.getServerAccount();
        var tax = manager.getTaxAccount();

        assertEquals(new BigDecimal("250"), player.getBalance());
        assertEquals(BigDecimal.ZERO, team.getBalance());
        assertNotEquals(new BigDecimal("250"), server.getBalance(),
                "SERVER must retain its intentional reserve instead of receiving the player starting balance");
        assertEquals(BigDecimal.ZERO, tax.getBalance(),
                "TAX must not receive the configured player starting balance");
    }

    @Test
    void typedPersistenceRoundTripsWithoutReinterpretingLegacyPlayers() {
        TypedStore store = new TypedStore();
        UUID playerId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        store.playerBalances.put(playerId, new BigDecimal("31"));
        store.typedBalances.put(AccountRef.team(teamId), new BigDecimal("79"));

        AccountManager first = new AccountManager();
        first.loadFrom(store);
        assertEquals(new BigDecimal("31"), first.getPlayerAccount(playerId).orElseThrow().getBalance());
        assertEquals(new BigDecimal("79"), first.getTeamAccount(teamId).orElseThrow().getBalance());

        first.getOrCreateTeamAccount(teamId).credit(BigDecimal.ONE, TransactionContext.adminGive("test"));
        first.saveAll();

        AccountManager second = new AccountManager();
        second.loadFrom(store);
        assertEquals(new BigDecimal("31"), second.getPlayerAccount(playerId).orElseThrow().getBalance());
        assertEquals(new BigDecimal("80"), second.getTeamAccount(teamId).orElseThrow().getBalance());
        assertFalse(second.getTeamAccount(playerId).isPresent(),
                "legacy UUID balances must migrate deterministically to PLAYER principals");
    }

    @Test
    void serverAndTaxBalancesUseTypedPersistenceAndNeverPlayerStartingBalance() {
        EconomyConfig.getInstance().setStartingBalance(new BigDecimal("250"));
        TypedStore store = new TypedStore();
        store.typedBalances.put(AccountRef.server(AccountManager.SERVER_ACCOUNT_ID), new BigDecimal("1234"));
        store.typedBalances.put(AccountRef.tax(AccountManager.TAX_ACCOUNT_ID), new BigDecimal("45"));

        AccountManager first = new AccountManager();
        first.loadFrom(store);

        assertEquals(new BigDecimal("1234"), first.getServerAccount().getBalance());
        assertEquals(new BigDecimal("45"), first.getTaxAccount().getBalance());
        assertNotEquals(new BigDecimal("250"), first.getServerAccount().getBalance());
        assertNotEquals(new BigDecimal("250"), first.getTaxAccount().getBalance());

        first.getServerAccount().credit(BigDecimal.ONE, TransactionContext.adminGive("test"));
        first.getTaxAccount().credit(BigDecimal.TEN, TransactionContext.adminGive("test"));
        first.saveAll();

        AccountManager second = new AccountManager();
        second.loadFrom(store);
        assertEquals(new BigDecimal("1235"), second.getServerAccount().getBalance());
        assertEquals(new BigDecimal("55"), second.getTaxAccount().getBalance());
    }

    private static final class TypedStore implements BalanceStore {
        final Map<UUID, BigDecimal> playerBalances = new HashMap<>();
        final Map<AccountRef, BigDecimal> typedBalances = new HashMap<>();

        @Override public Map<UUID, BigDecimal> getBalances() { return playerBalances; }
        @Override public void setBalance(UUID player, BigDecimal balance) { playerBalances.put(player, balance); }
        @Override public void removeBalance(UUID player) { playerBalances.remove(player); }
        @Override public boolean supportsTypedAccounts() { return true; }

        @Override
        public Map<AccountRef, BigDecimal> getAccountBalances() {
            Map<AccountRef, BigDecimal> result = BalanceStore.super.getAccountBalances();
            result.putAll(typedBalances);
            return result;
        }

        @Override
        public void setAccountBalance(AccountRef account, BigDecimal balance) {
            if (account.kind() == AccountKind.PLAYER) setBalance(account.id(), balance);
            else typedBalances.put(account, balance);
        }

        @Override
        public void removeAccountBalance(AccountRef account) {
            if (account.kind() == AccountKind.PLAYER) removeBalance(account.id());
            else typedBalances.remove(account);
        }
    }
}
