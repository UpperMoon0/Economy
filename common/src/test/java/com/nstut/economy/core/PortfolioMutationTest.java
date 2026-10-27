package com.nstut.economy.core;

import com.nstut.economy.api.AccountKind;
import com.nstut.economy.api.AccountRef;
import com.nstut.economy.api.EconomyEvents;
import com.nstut.economy.api.IBankAccount;
import com.nstut.economy.api.TransactionCauses;
import com.nstut.economy.config.EconomyConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Contracts at the single shared money boundary used by commands, UI and integrations. */
class PortfolioMutationTest {
    private BigDecimal starting;
    @BeforeEach void isolate() {
        starting = EconomyConfig.getInstance().getStartingBalance();
        EconomyConfig.getInstance().setStartingBalance(BigDecimal.ZERO);
    }
    @AfterEach void restore() { EconomyConfig.getInstance().setStartingBalance(starting); }

    @Test void personalAndTeamCreditDebitAdminSetHaveExactCommittedDeltas() {
        for (AccountKind kind : List.of(AccountKind.PLAYER, AccountKind.TEAM)) {
            Fixture f = fixture(kind);
            assertTrue(f.account.credit(new BigDecimal("12.50"), TransactionContext.adminGive("grant")));
            assertTrue(f.account.debit(new BigDecimal("2.50"), TransactionContext.adminTake("take")));
            f.account.setBalance(new BigDecimal("25"));
            assertEquals(3, f.store.events.size());
            assertTrue(f.store.events.stream().noneMatch(event -> event.mutation.groupedFinalMark()));
            assertEvent(f.store.events.get(0), f.ref, "0", "12.50", "12.50", "economy:admin_give");
            assertEvent(f.store.events.get(1), f.ref, "12.50", "10", "-2.50", "economy:admin_take");
            assertEvent(f.store.events.get(2), f.ref, "10", "25", "15", "economy:admin_set");
            assertEquals(3, f.store.events.stream().map(e -> e.mutation.operationId()).distinct().count());
            decimal("25", f.store.balances.get(f.ref));
        }
    }

    @Test void failuresCancellationAndNoOpsDoNotCreateHistory() {
        for (AccountKind kind : List.of(AccountKind.PLAYER, AccountKind.TEAM)) {
            Fixture f = fixture(kind);
            f.account.setBalance(BigDecimal.TEN);
            f.store.events.clear();
            assertFalse(f.account.credit(BigDecimal.ZERO, null));
            assertFalse(f.account.debit(new BigDecimal("11"), null));
            assertFalse(f.account.debit(BigDecimal.ONE.negate(), null));
            assertFalse(f.account.credit(null, null));
            assertTrue(f.account.transferTo(f.account, BigDecimal.ONE, null));
            f.account.setBalance(new BigDecimal("10.00"));
            try (var ignored = EconomyEvents.listen(EconomyEvents.BalanceChangePre.class, e -> e.cancel())) {
                assertFalse(f.account.credit(BigDecimal.ONE, null));
                assertFalse(f.account.debit(BigDecimal.ONE, null));
            }
            assertTrue(f.store.events.isEmpty());
            decimal("10", f.account.getBalance());
        }
    }

    @Test void allPersonalTeamTransferCombinationsCaptureBothSidesOnce() {
        for (AccountKind from : List.of(AccountKind.PLAYER, AccountKind.TEAM)) {
            for (AccountKind to : List.of(AccountKind.PLAYER, AccountKind.TEAM)) {
                Fixture f = fixture(from);
                AccountRef targetRef = new AccountRef(to, UUID.randomUUID());
                IBankAccount target = f.manager.getOrCreateAccount(targetRef);
                f.account.setBalance(new BigDecimal("100"));
                f.store.events.clear();
                int writes = f.store.writes;
                assertTrue(f.manager.transfer(f.ref, targetRef, BigDecimal.TEN,
                        TransactionContext.transfer("payment", f.ref.id())));
                assertEquals(2, f.store.events.size());
                assertTrue(f.store.writes - writes >= 2);
                assertEvent(f.store.events.get(0), f.ref, "100", "90", "-10", "economy:transfer");
                assertEvent(f.store.events.get(1), targetRef, "0", "10", "10", "economy:transfer");
                assertEquals(f.store.events.get(0).mutation.operationId(), f.store.events.get(1).mutation.operationId());
                decimal("0", f.store.events.stream().map(e -> e.mutation.externalFlow()).reduce(BigDecimal.ZERO, BigDecimal::add));
                f.store.events.clear();
                try (var ignored = EconomyEvents.listen(EconomyEvents.TransferPre.class, e -> e.cancel())) {
                    assertFalse(f.account.transferTo(target, BigDecimal.ONE, null));
                }
                try (var ignored = EconomyEvents.listen(EconomyEvents.BalanceChangePre.class, e -> {
                    if (e.accountRef().equals(targetRef)) e.cancel();
                })) {
                    assertFalse(f.account.transferTo(target, BigDecimal.ONE, null));
                }
                assertFalse(f.account.transferTo(target, new BigDecimal("1000"), null));
                assertTrue(f.store.events.isEmpty());
                decimal("90", f.account.getBalance());
                decimal("10", target.getBalance());
            }
        }
    }

    @Test void startingBalanceIsOneExternalFlowAndAccountLookupDoesNotRepeatIt() {
        EconomyConfig.getInstance().setStartingBalance(new BigDecimal("7.25"));
        Fixture f = fixture(AccountKind.PLAYER);
        assertEquals(1, f.store.events.size());
        assertEvent(f.store.events.get(0), f.ref, "0", "7.25", "7.25", "economy:starting_balance");
        assertSame(f.account, f.manager.getOrCreateAccount(f.ref));
        f.manager.getOrCreateAccount(AccountRef.team(UUID.randomUUID()));
        assertEquals(1, f.store.events.size());
    }

    @Test void reusedCallerContextStillRecordsSeparateLegitimateMutationsAndZeroNetSequence() {
        Fixture f = fixture(AccountKind.PLAYER);
        var context = TransactionContext.adminGive("reused context");
        assertTrue(f.account.credit(BigDecimal.TEN, context));
        assertTrue(f.account.credit(BigDecimal.TEN, context));
        assertTrue(f.account.debit(new BigDecimal("20"), null));
        assertEquals(3, f.store.events.size());
        assertEquals(3, f.store.events.stream().map(e -> e.mutation.operationId()).distinct().count());
        decimal("0", f.account.getBalance());
        decimal("0", f.store.events.stream().map(e -> e.mutation.cashDelta()).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test void tradeAggregatesChildPaymentAndPartialRefundTransfersIntoOneEventPerAccount() {
        Fixture f = fixture(AccountKind.PLAYER);
        AccountRef seller = AccountRef.team(UUID.randomUUID());
        f.manager.getOrCreateAccount(seller);
        f.account.setBalance(new BigDecimal("100"));
        f.store.events.clear();
        PortfolioOperation operation = PortfolioOperation.trade("partial trade");
        try (operation) {
            assertTrue(f.manager.transfer(f.ref, seller, new BigDecimal("40"), tradeContext()));
            assertTrue(f.manager.transfer(seller, f.ref, new BigDecimal("15"), tradeContext()));
            assertTrue(f.store.events.isEmpty());
        }
        operation.close(); // callbacks/cleanup may close twice
        assertEquals(2, f.store.events.size());
        assertEvent(f.store.events.get(0), f.ref, "100", "75", "0", "economy:trade");
        assertEvent(f.store.events.get(1), seller, "0", "25", "0", "economy:trade");
        assertEquals(f.store.events.get(0).mutation.operationId(), f.store.events.get(1).mutation.operationId());
        assertEquals("partial trade", f.store.events.get(0).mutation.reason());
    }

    @Test void fullyCompensatedTradeAndEmptyAttemptProduceNoMisleadingEvent() {
        Fixture f = fixture(AccountKind.TEAM);
        AccountRef other = AccountRef.player(UUID.randomUUID());
        f.manager.getOrCreateAccount(other);
        f.account.setBalance(new BigDecimal("100"));
        f.store.events.clear();
        try (var ignored = PortfolioOperation.trade("rejected delivery")) {
            assertTrue(f.manager.transfer(f.ref, other, BigDecimal.TEN, tradeContext()));
            assertTrue(f.manager.transfer(other, f.ref, BigDecimal.TEN, tradeContext()));
        }
        try (var ignored = PortfolioOperation.trade("invalid trade")) {
            assertFalse(f.account.debit(new BigDecimal("1000"), null));
        }
        assertTrue(f.store.events.isEmpty());
        decimal("100", f.account.getBalance());
        // Scope cleanup must restore normal attribution afterwards.
        assertTrue(f.account.credit(BigDecimal.ONE, null));
        assertEquals(1, f.store.events.size());
        decimal("1", f.store.events.get(0).mutation.externalFlow());
    }

    @Test void saveLoadAndRetiredHandlesNeverReplayOperations() {
        for (AccountKind kind : List.of(AccountKind.PLAYER, AccountKind.TEAM)) {
            Fixture f = fixture(kind);
            assertTrue(f.account.credit(BigDecimal.TEN, null));
            f.manager.saveAll();
            assertEquals(1, f.store.events.size());
            f.manager.loadFrom(f.store);
            f.manager.saveAll();
            f.manager.setAccountData(f.store);
            assertEquals(1, f.store.events.size());
            BankAccount current = (BankAccount) f.manager.getAccount(f.ref).orElseThrow();
            assertFalse(f.account.credit(BigDecimal.ONE, null));
            assertFalse(f.account.transferTo(current, BigDecimal.ONE, null));
            assertThrows(IllegalStateException.class, () -> f.account.setBalance(BigDecimal.ZERO));
            assertEquals(1, f.store.events.size());
            assertTrue(current.debit(BigDecimal.ONE, null));
            assertEquals(2, f.store.events.size());
            assertTrue(f.manager.deleteAccount(f.ref));
            assertFalse(current.credit(BigDecimal.ONE, null));
            assertEquals(3, f.store.events.size());
            decimal("-9", f.store.events.get(2).mutation.cashDelta());
            decimal("-9", f.store.events.get(2).mutation.externalFlow());
        }
    }

    @Test void journalCallbackFailureCannotTurnCommittedMoneyIntoReportedFailure() {
        List<BalanceMutation> attempted = new ArrayList<>();
        BankAccount from = new BankAccount(AccountRef.player(UUID.randomUUID()), new BigDecimal("100"));
        BankAccount to = new BankAccount(AccountRef.team(UUID.randomUUID()), BigDecimal.ZERO);
        from.setMutationSink(mutation -> { attempted.add(mutation); throw new IllegalStateException("journal unavailable"); });
        to.setMutationSink(attempted::add);
        assertTrue(from.credit(BigDecimal.ONE, null));
        assertTrue(from.debit(BigDecimal.ONE, null));
        assertTrue(from.transferTo(to, BigDecimal.TEN, null));
        assertEquals(4, attempted.size()); // failed first sink must not suppress the second transfer leg
        decimal("90", from.getBalance());
        decimal("10", to.getBalance());
        try (var ignored = PortfolioOperation.trade("deferred commit")) {
            assertTrue(from.transferTo(to, BigDecimal.TEN, tradeContext()));
        }
        assertEquals(6, attempted.size());
        decimal("80", from.getBalance());
        decimal("20", to.getBalance());
        from.setBalance(new BigDecimal("81"));
        assertEquals(7, attempted.size());
    }

    @Test void addonListenerExternalCreditsAndTransfersKeepTheirOwnAttributionInsideTrade() {
        Fixture f = fixture(AccountKind.PLAYER);
        AccountRef seller = AccountRef.team(UUID.randomUUID());
        AccountRef reward = AccountRef.player(UUID.randomUUID());
        AccountRef recipient = AccountRef.team(UUID.randomUUID());
        f.manager.getOrCreateAccount(seller);
        IBankAccount rewards = f.manager.getOrCreateAccount(reward);
        f.manager.getOrCreateAccount(recipient);
        f.account.setBalance(new BigDecimal("100"));
        f.store.events.clear();
        try (var subscription = EconomyEvents.listen(EconomyEvents.TransferPre.class, event -> {
            if (!event.sourceRef().equals(f.ref)) return;
            assertTrue(rewards.credit(BigDecimal.TEN, TransactionContext.adminGive("addon reward")));
            assertTrue(f.manager.transfer(reward, recipient, BigDecimal.ONE, null));
        }); var ignored = PortfolioOperation.trade("market purchase")) {
            assertTrue(f.manager.transfer(f.ref, seller, BigDecimal.TEN, tradeContext()));
        }
        assertEquals(5, f.store.events.size());
        assertEvent(f.store.events.get(0), f.ref, "100", "90", "0", "economy:trade");
        assertEvent(f.store.events.get(1), seller, "0", "10", "0", "economy:trade");
        assertEquals("economy:admin_give", f.store.events.get(2).mutation.source());
        decimal("10", f.store.events.get(2).mutation.externalFlow());
        assertEvent(f.store.events.get(3), reward, "10", "9", "-1", "economy:transfer");
        assertEvent(f.store.events.get(4), recipient, "0", "1", "1", "economy:transfer");
    }

    @Test void externalCreditBetweenTradePaymentAndRefundIsNotCountedTwice() {
        Fixture f = fixture(AccountKind.PLAYER);
        AccountRef seller = AccountRef.team(UUID.randomUUID());
        f.manager.getOrCreateAccount(seller);
        f.account.setBalance(new BigDecimal("100"));
        f.store.events.clear();
        try (var ignored = PortfolioOperation.trade("interleaved trade")) {
            assertTrue(f.manager.transfer(f.ref, seller, new BigDecimal("40"), tradeContext()));
            assertTrue(f.account.credit(new BigDecimal("5"), TransactionContext.adminGive("independent grant")));
            assertTrue(f.manager.transfer(seller, f.ref, new BigDecimal("15"), tradeContext()));
        }
        assertEquals(3, f.store.events.size());
        assertEvent(f.store.events.get(0), f.ref, "100", "75", "0", "economy:trade");
        decimal("-25", f.store.events.get(0).mutation.cashDelta());
        assertEvent(f.store.events.get(1), seller, "0", "25", "0", "economy:trade");
        decimal("25", f.store.events.get(1).mutation.cashDelta());
        assertEvent(f.store.events.get(2), f.ref, "75", "80", "5", "economy:admin_give");
        decimal("80", f.account.getBalance());
    }

    @Test void nestedDistinctTradesKeepOperationIdentityAndHistoricalBalances() {
        for (AccountKind kind : List.of(AccountKind.PLAYER, AccountKind.TEAM)) {
            Fixture f = fixture(kind);
            AccountRef seller = AccountRef.team(UUID.randomUUID());
            f.manager.getOrCreateAccount(seller);
            f.account.setBalance(new BigDecimal("100"));
            f.store.events.clear();
            try (var outer = PortfolioOperation.trade("Order outer")) {
                assertTrue(f.manager.transfer(f.ref, seller, BigDecimal.TEN, tradeContext()));
                try (var inner = PortfolioOperation.trade("Order inner")) {
                    assertTrue(f.manager.transfer(f.ref, seller, new BigDecimal("5"), tradeContext()));
                }
                assertTrue(f.store.events.isEmpty(), "Provisional outer payments must not leak into history");
            }
            assertEquals(4, f.store.events.size());
            assertEvent(f.store.events.get(0), f.ref, "100", "90", "0", "economy:trade");
            assertEvent(f.store.events.get(1), seller, "0", "10", "0", "economy:trade");
            assertEvent(f.store.events.get(2), f.ref, "90", "85", "0", "economy:trade");
            assertEvent(f.store.events.get(3), seller, "10", "15", "0", "economy:trade");
            assertTrue(f.store.events.stream().allMatch(event -> event.mutation.groupedFinalMark()));
            assertTrue(f.store.events.get(0).mutation.reason().startsWith("Grouped settlement final valuation;"));
            assertTrue(f.store.events.get(0).mutation.reason().endsWith("Order outer"));
            assertTrue(f.store.events.get(2).mutation.reason().endsWith("Order inner"));
            assertEquals(f.store.events.get(0).mutation.operationId(), f.store.events.get(1).mutation.operationId());
            assertEquals(f.store.events.get(2).mutation.operationId(), f.store.events.get(3).mutation.operationId());
            assertNotEquals(f.store.events.get(0).mutation.operationId(), f.store.events.get(2).mutation.operationId());
            decimal("-10", f.store.events.get(0).mutation.cashDelta());
            decimal("-5", f.store.events.get(2).mutation.cashDelta());
            decimal("85", f.store.balances.get(f.ref));
            decimal("15", f.store.balances.get(seller));
        }
    }

    @Test void refundedOuterTradeDoesNotEraseCommittedInnerOrPersistProvisionalCash() {
        Fixture f = fixture(AccountKind.PLAYER);
        AccountRef seller = AccountRef.team(UUID.randomUUID());
        f.manager.getOrCreateAccount(seller);
        f.account.setBalance(new BigDecimal("100"));
        f.store.events.clear();
        try (var outer = PortfolioOperation.trade("Order refunded")) {
            assertTrue(f.manager.transfer(f.ref, seller, new BigDecimal("40"), tradeContext()));
            try (var inner = PortfolioOperation.trade("Order committed")) {
                assertTrue(f.manager.transfer(f.ref, seller, new BigDecimal("5"), tradeContext()));
            }
            assertTrue(f.manager.transfer(seller, f.ref, new BigDecimal("40"), tradeContext()));
        }
        assertEquals(2, f.store.events.size());
        assertEvent(f.store.events.get(0), f.ref, "100", "95", "0", "economy:trade");
        assertEvent(f.store.events.get(1), seller, "0", "5", "0", "economy:trade");
        assertEquals("Order committed", f.store.events.get(0).mutation.reason());
        assertTrue(f.store.events.stream().noneMatch(event -> event.mutation.groupedFinalMark()));
        decimal("-5", f.store.events.get(0).mutation.cashDelta());
        decimal("5", f.store.events.get(1).mutation.cashDelta());
        decimal("95", f.store.balances.get(f.ref));
        decimal("5", f.store.balances.get(seller));
    }

    @Test void oppositeNestedTradesRetainBothOperationsEvenWhenCombinedCashIsZero() {
        Fixture f = fixture(AccountKind.TEAM);
        AccountRef other = AccountRef.player(UUID.randomUUID());
        f.manager.getOrCreateAccount(other);
        f.account.setBalance(new BigDecimal("100"));
        f.store.events.clear();
        try (var outer = PortfolioOperation.trade("Order outward")) {
            assertTrue(f.manager.transfer(f.ref, other, BigDecimal.TEN, tradeContext()));
            try (var inner = PortfolioOperation.trade("Order inward")) {
                assertTrue(f.manager.transfer(other, f.ref, BigDecimal.TEN, tradeContext()));
            }
        }
        assertEquals(4, f.store.events.size());
        assertEvent(f.store.events.get(0), f.ref, "100", "90", "0", "economy:trade");
        assertEvent(f.store.events.get(1), other, "0", "10", "0", "economy:trade");
        assertEvent(f.store.events.get(2), other, "10", "0", "0", "economy:trade");
        assertEvent(f.store.events.get(3), f.ref, "90", "100", "0", "economy:trade");
        decimal("100", f.store.balances.get(f.ref));
        decimal("0", f.store.balances.get(other));
    }

    @Test void externalGrantDuringRefundedAttemptIsJournaledWithoutProvisionalPayment() {
        Fixture f = fixture(AccountKind.TEAM);
        AccountRef other = AccountRef.player(UUID.randomUUID());
        f.manager.getOrCreateAccount(other);
        f.account.setBalance(new BigDecimal("100"));
        f.store.events.clear();
        try (var outer = PortfolioOperation.trade("Order refunded")) {
            assertTrue(f.manager.transfer(f.ref, other, new BigDecimal("40"), tradeContext()));
            assertTrue(f.account.credit(new BigDecimal("5"), TransactionContext.adminGive("Independent grant")));
            assertTrue(f.manager.transfer(other, f.ref, new BigDecimal("40"), tradeContext()));
        }
        assertEquals(1, f.store.events.size());
        assertEvent(f.store.events.get(0), f.ref, "100", "105", "5", "economy:admin_give");
        decimal("5", f.store.events.get(0).mutation.cashDelta());
        decimal("105", f.store.balances.get(f.ref));
        decimal("0", f.store.balances.get(other));
    }

    @Test void nestedIndependentTransfersKeepFreshIdsWhenCallerReusesContext() {
        Fixture f = fixture(AccountKind.PLAYER);
        AccountRef target = AccountRef.team(UUID.randomUUID());
        AccountRef otherSource = AccountRef.team(UUID.randomUUID());
        AccountRef otherTarget = AccountRef.player(UUID.randomUUID());
        f.manager.getOrCreateAccount(target);
        BankAccount other = (BankAccount) f.manager.getOrCreateAccount(otherSource);
        f.manager.getOrCreateAccount(otherTarget);
        f.account.setBalance(new BigDecimal("100"));
        other.setBalance(new BigDecimal("100"));
        f.store.events.clear();
        var reusedContext = TransactionContext.transfer("Reusable caller context", f.ref.id());
        try (var listener = EconomyEvents.listen(EconomyEvents.TransferPre.class, event -> {
            if (event.sourceRef().equals(f.ref)) {
                assertTrue(f.manager.transfer(otherSource, otherTarget, new BigDecimal("20"), reusedContext));
            }
        })) {
            assertTrue(f.manager.transfer(f.ref, target, BigDecimal.TEN, reusedContext));
        }
        assertEquals(4, f.store.events.size());
        assertEvent(f.store.events.get(0), f.ref, "100", "90", "-10", "economy:transfer");
        assertEvent(f.store.events.get(1), target, "0", "10", "10", "economy:transfer");
        assertEvent(f.store.events.get(2), otherSource, "100", "80", "-20", "economy:transfer");
        assertEvent(f.store.events.get(3), otherTarget, "0", "20", "20", "economy:transfer");
        assertEquals(f.store.events.get(0).mutation.operationId(), f.store.events.get(1).mutation.operationId());
        assertEquals(f.store.events.get(2).mutation.operationId(), f.store.events.get(3).mutation.operationId());
        assertNotEquals(f.store.events.get(0).mutation.operationId(), f.store.events.get(2).mutation.operationId(),
                "A reusable caller transaction ID must not merge independent committed transfers");
        decimal("0", f.store.events.stream().map(event -> event.mutation.externalFlow()).reduce(BigDecimal.ZERO, BigDecimal::add));
        decimal("90", f.store.balances.get(f.ref));
        decimal("10", f.store.balances.get(target));
        decimal("80", f.store.balances.get(otherSource));
        decimal("20", f.store.balances.get(otherTarget));
    }

    @Test void deferredTradeSurvivesAccountDeletionWithoutResurrectingItsBalance() {
        for (AccountKind kind : List.of(AccountKind.PLAYER, AccountKind.TEAM)) {
            Fixture f = fixture(kind);
            AccountRef seller = AccountRef.team(UUID.randomUUID());
            f.manager.getOrCreateAccount(seller);
            f.account.setBalance(new BigDecimal("100"));
            f.store.events.clear();
            try (var outer = PortfolioOperation.trade("Order before account deletion")) {
                assertTrue(f.manager.transfer(f.ref, seller, BigDecimal.TEN, tradeContext()));
                assertTrue(f.manager.deleteAccount(f.ref));
                assertFalse(f.account.credit(BigDecimal.ONE, null));
            }
            assertEquals(3, f.store.events.size());
            assertEvent(f.store.events.get(0), f.ref, "100", "90", "0", "economy:trade");
            assertEvent(f.store.events.get(1), seller, "0", "10", "0", "economy:trade");
            assertEvent(f.store.events.get(2), f.ref, "90", "0", "-90", "economy:account_deleted");
            decimal("-10", f.store.events.get(0).mutation.cashDelta());
            assertFalse(f.store.balances.containsKey(f.ref), "Deferred journal must not recreate a deleted balance");
            assertTrue(f.manager.getAccount(f.ref).isEmpty());
            decimal("10", f.store.balances.get(seller));
        }
    }

    @Test void deferredOldHandleCannotOverwriteRecreatedTypedAccount() {
        for (AccountKind kind : List.of(AccountKind.PLAYER, AccountKind.TEAM)) {
            Fixture f = fixture(kind);
            AccountRef seller = AccountRef.team(UUID.randomUUID());
            f.manager.getOrCreateAccount(seller);
            f.account.setBalance(new BigDecimal("100"));
            f.store.events.clear();
            IBankAccount replacement;
            try (var outer = PortfolioOperation.trade("Order before recreation")) {
                assertTrue(f.manager.transfer(f.ref, seller, BigDecimal.TEN, tradeContext()));
                assertTrue(f.manager.deleteAccount(f.ref));
                replacement = f.manager.getOrCreateAccount(f.ref);
                assertTrue(replacement.credit(new BigDecimal("5"), TransactionContext.adminGive("New account grant")));
            }
            assertEquals(4, f.store.events.size());
            assertEvent(f.store.events.get(0), f.ref, "100", "90", "0", "economy:trade");
            assertEvent(f.store.events.get(2), f.ref, "90", "0", "-90", "economy:account_deleted");
            assertEvent(f.store.events.get(3), f.ref, "0", "5", "5", "economy:admin_give");
            assertTrue(f.store.events.stream().allMatch(event -> event.mutation.groupedFinalMark()));
            assertSame(replacement, f.manager.getAccount(f.ref).orElseThrow());
            assertFalse(f.account.credit(BigDecimal.ONE, null));
            decimal("5", replacement.getBalance());
            decimal("5", f.store.balances.get(f.ref));
            decimal("10", f.store.balances.get(seller));
        }
    }

    private static Fixture fixture(AccountKind kind) {
        Store store = new Store();
        AccountManager manager = new AccountManager();
        manager.loadFrom(store);
        AccountRef ref = new AccountRef(kind, UUID.randomUUID());
        return new Fixture(store, manager, ref, (BankAccount) manager.getOrCreateAccount(ref));
    }
    private static TransactionContext tradeContext() {
        return new TransactionContext(TransactionCauses.TRADE, "market payment", "test");
    }

    @Test void addonAttributionIsBoundedBeforePersistenceAndNullsAreSafe() {
        var mutation = new BalanceMutation(UUID.randomUUID(), 1L, "s".repeat(400), "r".repeat(2000),
                BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE);
        assertEquals(256, mutation.source().length());
        assertEquals(1024, mutation.reason().length());
        assertTrue(mutation.source().endsWith("..."));
        assertTrue(mutation.reason().endsWith("..."));
        var nulls = new BalanceMutation(UUID.randomUUID(), 1L, null, null,
                BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE);
        assertEquals("", nulls.source());
        assertEquals("", nulls.reason());
    }
    private record Fixture(Store store, AccountManager manager, AccountRef ref, BankAccount account) { }
    private record Recorded(AccountRef account, BalanceMutation mutation) { }
    private static void assertEvent(Recorded event, AccountRef ref, String before, String after, String flow, String source) {
        assertEquals(ref, event.account);
        decimal(before, event.mutation.before());
        decimal(after, event.mutation.after());
        decimal(flow, event.mutation.externalFlow());
        assertEquals(source, event.mutation.source());
        assertNotNull(event.mutation.operationId());
        assertTrue(event.mutation.timestamp() > 0);
    }
    private static void decimal(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> expected + " != " + actual);
    }
    private static final class Store implements BalanceStore {
        final Map<AccountRef, BigDecimal> balances = new HashMap<>();
        final List<Recorded> events = new ArrayList<>();
        int writes;
        @Override public Map<UUID, BigDecimal> getBalances() {
            Map<UUID, BigDecimal> result = new HashMap<>();
            balances.forEach((ref, amount) -> { if (ref.kind() == AccountKind.PLAYER) result.put(ref.id(), amount); });
            return result;
        }
        @Override public void setBalance(UUID id, BigDecimal amount) { setAccountBalance(AccountRef.player(id), amount); }
        @Override public boolean supportsTypedAccounts() { return true; }
        @Override public Map<AccountRef, BigDecimal> getAccountBalances() { return Map.copyOf(balances); }
        @Override public void setAccountBalance(AccountRef account, BigDecimal amount) { balances.put(account, amount); writes++; }
        @Override public void removeBalance(UUID id) { balances.remove(AccountRef.player(id)); }
        @Override public void removeAccountBalance(AccountRef account) { balances.remove(account); }
        @Override public void recordMutation(AccountRef account, BalanceMutation mutation) {
            // Historical intermediate balances can differ from the actual persisted final balance.
            events.add(new Recorded(account, mutation));
        }
    }
}
