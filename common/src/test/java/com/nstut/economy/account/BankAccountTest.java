package com.nstut.economy.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class BankAccountTest {

    private BankAccount aliceAccount;
    private BankAccount bobAccount;

    @BeforeEach
    public void setUp() {
        aliceAccount = new BankAccount(UUID.randomUUID(), new BigDecimal("500.00"));
        bobAccount = new BankAccount(UUID.randomUUID(), new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Initial balances are stored correctly")
    public void testInitialBalance() {
        assertEquals(new BigDecimal("500.00"), aliceAccount.getBalance());
        assertEquals(new BigDecimal("100.00"), bobAccount.getBalance());
    }

    @Test
    @DisplayName("Deposit increases account balance")
    public void testDeposit() {
        aliceAccount.deposit(new BigDecimal("250.50"), "Reward payout");
        assertEquals(new BigDecimal("750.50"), aliceAccount.getBalance());
    }

    @Test
    @DisplayName("Withdrawal decreases balance when funds are sufficient")
    public void testWithdrawSuccess() {
        boolean success = aliceAccount.withdraw(new BigDecimal("200.00"), "Item purchase");
        assertTrue(success);
        assertEquals(new BigDecimal("300.00"), aliceAccount.getBalance());
    }

    @Test
    @DisplayName("Withdrawal fails when funds are insufficient")
    public void testWithdrawInsufficientFunds() {
        boolean success = bobAccount.withdraw(new BigDecimal("150.00"), "Overdraft attempt");
        assertFalse(success);
        assertEquals(new BigDecimal("100.00"), bobAccount.getBalance());
    }

    @Test
    @DisplayName("Transfer between accounts succeeds")
    public void testTransferSuccess() {
        boolean success = aliceAccount.transferTo(bobAccount, new BigDecimal("150.00"), TransactionContext.transfer("Player trade", aliceAccount.getOwner()));
        assertTrue(success);
        assertEquals(new BigDecimal("350.00"), aliceAccount.getBalance());
        assertEquals(new BigDecimal("250.00"), bobAccount.getBalance());
    }
}
