package com.nstut.economy.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BankAccountTest {
    private BankAccount alice;
    private BankAccount bob;

    @BeforeEach
    void setUp() {
        alice = new BankAccount(UUID.randomUUID(), new BigDecimal("500.00"));
        bob = new BankAccount(UUID.randomUUID(), new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Credits, debits, and insufficient-fund checks preserve balances")
    void creditsAndDebits() {
        assertTrue(alice.credit(new BigDecimal("250.50"), TransactionContext.adminGive("test")));
        assertEquals(new BigDecimal("750.50"), alice.getBalance());
        assertTrue(alice.debit(new BigDecimal("200.00"), TransactionContext.adminTake("test")));
        assertEquals(new BigDecimal("550.50"), alice.getBalance());
        assertFalse(bob.debit(new BigDecimal("150.00"), TransactionContext.adminTake("test")));
        assertEquals(new BigDecimal("100.00"), bob.getBalance());
        assertFalse(alice.credit(BigDecimal.ZERO, TransactionContext.adminGive("invalid")));
        assertFalse(alice.debit(new BigDecimal("-1"), TransactionContext.adminTake("invalid")));
    }

    @Test
    @DisplayName("Transfers debit the sender, credit the receiver, and record both sides")
    void transfersAndRecordsHistory() {
        assertTrue(alice.transferTo(bob, new BigDecimal("150.00"),
                TransactionContext.transfer("trade", alice.getOwner())));
        assertEquals(new BigDecimal("350.00"), alice.getBalance());
        assertEquals(new BigDecimal("250.00"), bob.getBalance());
        assertEquals(1, alice.getRecentTransactions(10).size());
        assertEquals(1, bob.getRecentTransactions(10).size());
        assertFalse(alice.transferTo(null, BigDecimal.ONE,
                TransactionContext.transfer("invalid", alice.getOwner())));
    }

    @Test
    @DisplayName("AccountManager writes through to BalanceStore on mutations and account creation")
    void accountManagerWritesThroughToBalanceStore() {
        java.util.Map<UUID, BigDecimal> storeBalances = new java.util.HashMap<>();
        BalanceStore fakeStore = new BalanceStore() {
            @Override
            public java.util.Map<UUID, BigDecimal> getBalances() {
                return storeBalances;
            }

            @Override
            public void setBalance(UUID player, BigDecimal balance) {
                storeBalances.put(player, balance);
            }

            @Override
            public void removeBalance(UUID player) {
                storeBalances.remove(player);
            }
        };

        AccountManager manager = new AccountManager();
        manager.setAccountData(fakeStore);

        UUID user1 = UUID.randomUUID();
        var acct1 = manager.getOrCreatePlayerAccount(user1);
        // BalanceStore must contain the new account immediately
        assertTrue(storeBalances.containsKey(user1));
        assertEquals(BigDecimal.ZERO, storeBalances.get(user1));

        // Credit writes through immediately
        acct1.credit(new BigDecimal("100.00"), TransactionContext.adminGive("test"));
        assertEquals(new BigDecimal("100.00"), storeBalances.get(user1));

        // Debit writes through immediately
        acct1.debit(new BigDecimal("30.00"), TransactionContext.adminTake("test"));
        assertEquals(new BigDecimal("70.00"), storeBalances.get(user1));

        // Transfer writes through for both accounts immediately
        UUID user2 = UUID.randomUUID();
        var acct2 = manager.getOrCreatePlayerAccount(user2);
        acct1.transferTo(acct2, new BigDecimal("20.00"), TransactionContext.transfer("test", user1));
        assertEquals(new BigDecimal("50.00"), storeBalances.get(user1));
        assertEquals(new BigDecimal("20.00"), storeBalances.get(user2));

        // Delete account removes from BalanceStore
        manager.deleteAccount(user1);
        assertFalse(storeBalances.containsKey(user1));
    }
}
