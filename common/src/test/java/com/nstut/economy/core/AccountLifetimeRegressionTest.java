package com.nstut.economy.core;

import com.nstut.economy.api.AccountRef;
import com.nstut.economy.api.EconomyEvents;
import com.nstut.economy.api.IBankAccount;
import com.nstut.economy.config.EconomyConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AccountLifetimeRegressionTest {
    private BigDecimal startingBalance;

    @BeforeEach void isolateStartingBalance() {
        startingBalance = EconomyConfig.getInstance().getStartingBalance();
        EconomyConfig.getInstance().setStartingBalance(BigDecimal.ZERO);
    }

    @AfterEach void restoreStartingBalance() {
        EconomyConfig.getInstance().setStartingBalance(startingBalance);
    }

    @Test void retiredPlayerAndTeamHandlesCannotMutateReplacementAccounts() {
        for (AccountRef ref : new AccountRef[]{AccountRef.player(UUID.randomUUID()), AccountRef.team(UUID.randomUUID())}) {
            Store store = new Store();
            AccountManager manager = new AccountManager();
            manager.loadFrom(store);
            IBankAccount old = manager.getOrCreateAccount(ref);
            assertTrue(old.credit(BigDecimal.TEN, null));
            assertTrue(manager.deleteAccount(ref));
            IBankAccount replacement = manager.getOrCreateAccount(ref);
            assertTrue(replacement.credit(new BigDecimal("100"), null));
            assertFalse(old.credit(BigDecimal.ONE, null));
            assertFalse(old.debit(BigDecimal.ONE, null));
            assertFalse(old.transferTo(replacement, BigDecimal.ONE, null));
            assertFalse(replacement.transferTo(old, BigDecimal.ONE, null));
            assertFalse(old.transferTo(old, BigDecimal.ONE, null));
            assertThrows(IllegalStateException.class, () -> ((BankAccount) old).setBalance(BigDecimal.ZERO));
            AccountManager reloaded = new AccountManager();
            reloaded.loadFrom(store);
            assertEquals(new BigDecimal("100"), reloaded.getAccount(ref).orElseThrow().getBalance());
            assertEquals(BigDecimal.TEN, old.getBalance());
        }
    }

    @Test void loadFromRetiresOldHandlesAndBindsPersistenceToNewStore() {
        UUID id = UUID.randomUUID();
        Store first = new Store();
        AccountManager manager = new AccountManager();
        manager.loadFrom(first);
        IBankAccount old = manager.getOrCreatePlayerAccount(id);
        old.credit(BigDecimal.TEN, null);
        Store second = new Store();
        second.typed.put(AccountRef.player(id), new BigDecimal("100"));
        manager.loadFrom(second);
        assertFalse(old.credit(BigDecimal.ONE, null));
        assertTrue(manager.getOrCreatePlayerAccount(id).credit(BigDecimal.ONE, null));
        assertEquals(new BigDecimal("101"), second.typed.get(AccountRef.player(id)));
        assertEquals(BigDecimal.TEN, first.typed.get(AccountRef.player(id)));
    }

    @Test void legacyReloadAlsoRetiresHandles() {
        AccountManager manager = new AccountManager();
        UUID id = UUID.randomUUID();
        IBankAccount old = manager.getOrCreatePlayerAccount(id);
        manager.loadAccounts(Map.of(id, BigDecimal.TEN));
        assertFalse(old.credit(BigDecimal.ONE, null));
        assertEquals(BigDecimal.TEN, manager.getOrCreatePlayerAccount(id).getBalance());
    }

    @Test void startingBalanceListenerCanInitializeAnotherAccountAndReadTheSameHandle() {
        EconomyConfig.getInstance().setStartingBalance(BigDecimal.TEN);
        AccountManager manager = new AccountManager();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        AtomicReference<IBankAccount> seen = new AtomicReference<>();
        AtomicBoolean entered = new AtomicBoolean();
        try (var subscription = EconomyEvents.listen(EconomyEvents.BalanceChanged.class, event -> {
            if (event.owner().equals(first) && entered.compareAndSet(false, true)) {
                seen.set(manager.getOrCreatePlayerAccount(first));
                manager.getOrCreatePlayerAccount(second);
            }
        })) {
            IBankAccount account = assertDoesNotThrow(() -> manager.getOrCreatePlayerAccount(first));
            assertSame(account, seen.get());
            assertSame(account, manager.getOrCreatePlayerAccount(first));
            assertEquals(BigDecimal.TEN, account.getBalance());
            assertEquals(BigDecimal.TEN, manager.getOrCreatePlayerAccount(second).getBalance());
        }
    }

    @Test void deletionAndReloadDuringPreTransferAreRejectedWithoutChangingTheView() {
        Store store = new Store();
        AccountManager manager = new AccountManager();
        manager.loadFrom(store);
        UUID id = UUID.randomUUID();
        IBankAccount source = manager.getOrCreatePlayerAccount(id);
        source.credit(BigDecimal.TEN, null);
        IBankAccount target = manager.getOrCreatePlayerAccount(UUID.randomUUID());
        AtomicBoolean deleted = new AtomicBoolean(true), reloadRejected = new AtomicBoolean();
        try (var subscription = EconomyEvents.listen(EconomyEvents.TransferPre.class, event -> {
            deleted.set(manager.deleteAccount(id));
            try { manager.loadFrom(new Store()); }
            catch (IllegalStateException expected) { reloadRejected.set(true); }
        })) {
            assertTrue(source.transferTo(target, BigDecimal.ONE, null));
        }
        assertFalse(deleted.get());
        assertTrue(reloadRejected.get());
        assertSame(source, manager.getOrCreatePlayerAccount(id));
        assertEquals(new BigDecimal("9"), store.typed.get(AccountRef.player(id)));
        assertTrue(manager.deleteAccount(id));
    }

    @Test void startingCreditVetoLeavesOneInstalledZeroBalanceAccount() {
        EconomyConfig.getInstance().setStartingBalance(BigDecimal.TEN);
        AccountManager manager = new AccountManager();
        UUID id = UUID.randomUUID();
        Store store = new Store();
        manager.loadFrom(store);
        AtomicReference<IBankAccount> seen = new AtomicReference<>();
        try (var subscription = EconomyEvents.listen(EconomyEvents.BalanceChangePre.class, event -> {
            seen.set(manager.getOrCreatePlayerAccount(id));
            event.cancel();
        })) {
            IBankAccount created = manager.getOrCreatePlayerAccount(id);
            assertSame(created, seen.get());
        }
        assertEquals(BigDecimal.ZERO, manager.getOrCreatePlayerAccount(id).getBalance());
        assertEquals(BigDecimal.ZERO, store.typed.get(AccountRef.player(id)));
    }

    private static final class Store implements BalanceStore {
        private final Map<AccountRef, BigDecimal> typed = new HashMap<>();
        @Override public Map<UUID, BigDecimal> getBalances() { return Map.of(); }
        @Override public Map<AccountRef, BigDecimal> getAccountBalances() { return typed; }
        @Override public boolean supportsTypedAccounts() { return true; }
        @Override public void setBalance(UUID id, BigDecimal value) { typed.put(AccountRef.player(id), value); }
        @Override public void removeBalance(UUID id) { typed.remove(AccountRef.player(id)); }
        @Override public void setAccountBalance(AccountRef ref, BigDecimal value) { typed.put(ref, value); }
        @Override public void removeAccountBalance(AccountRef ref) { typed.remove(ref); }
    }
}
