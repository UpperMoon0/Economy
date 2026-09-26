package com.nstut.economy.core;

import com.nstut.economy.api.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TypedTransactionTest {
    private final UUID id = UUID.randomUUID();
    private final AccountRef playerRef = AccountRef.player(id);
    private final AccountRef teamRef = AccountRef.team(id);
    private final BankAccount player = new BankAccount(playerRef, BigDecimal.TEN);
    private final BankAccount team = new BankAccount(teamRef, BigDecimal.TEN);
    private final ITransactionContext context = TransactionContext.adminGive("test");

    @AfterEach void cleanup() { EconomyEvents.clearListeners(); }

    @Test void standaloneMutationsExposeTypedPreAndPostEvents() {
        List<AccountRef> before = new ArrayList<>();
        List<AccountRef> after = new ArrayList<>();
        EconomyEvents.listen(EconomyEvents.BalanceChangePre.class, e -> before.add(e.accountRef()));
        EconomyEvents.listen(EconomyEvents.BalanceChanged.class, e -> after.add(e.accountRef()));
        for (BankAccount account : List.of(player, team)) {
            assertTrue(account.credit(BigDecimal.ONE, context));
            assertTrue(account.debit(BigDecimal.ONE, context));
            assertNull(account.getRecentTransactions(1).get(0).getCounterpartyRef());
        }
        assertEquals(List.of(playerRef, playerRef, teamRef, teamRef), before);
        assertEquals(before, after);
    }

    @Test void sameUuidTransfersKeepBothIdentitiesInEventsAndHistory() {
        List<EconomyEvents.TransferPre> pre = new ArrayList<>();
        List<EconomyEvents.TransferCompleted> completed = new ArrayList<>();
        List<AccountRef> balancePre = new ArrayList<>();
        List<AccountRef> balancePost = new ArrayList<>();
        EconomyEvents.listen(EconomyEvents.TransferPre.class, pre::add);
        EconomyEvents.listen(EconomyEvents.TransferCompleted.class, completed::add);
        EconomyEvents.listen(EconomyEvents.BalanceChangePre.class, e -> balancePre.add(e.accountRef()));
        EconomyEvents.listen(EconomyEvents.BalanceChanged.class, e -> balancePost.add(e.accountRef()));
        assertTrue(player.transferTo(team, BigDecimal.ONE, context));
        assertEquals(playerRef, pre.get(0).sourceRef());
        assertEquals(teamRef, pre.get(0).targetRef());
        assertEquals(playerRef, completed.get(0).sourceRef());
        assertEquals(teamRef, completed.get(0).targetRef());
        assertEquals(id, completed.get(0).source());
        assertEquals(id, completed.get(0).target());
        assertEquals(List.of(playerRef, teamRef), balancePre);
        assertEquals(balancePre, balancePost);
        assertEquals(teamRef, player.getRecentTransactions(1).get(0).getCounterpartyRef());
        assertEquals(playerRef, team.getRecentTransactions(1).get(0).getCounterpartyRef());
        assertEquals(id, player.getRecentTransactions(1).get(0).getCounterparty());
        assertTrue(team.transferTo(player, BigDecimal.ONE, context));
        assertEquals(teamRef, completed.get(1).sourceRef());
        assertEquals(playerRef, completed.get(1).targetRef());
        assertEquals(BigDecimal.TEN, player.getBalance());
        assertEquals(BigDecimal.TEN, team.getBalance());
    }

    @Test void typedVetoCanRejectTeamMutationWithoutRejectingSameUuidPlayer() {
        EconomyEvents.listen(EconomyEvents.BalanceChangePre.class, event -> {
            if (event.accountRef().equals(teamRef)) event.cancel();
        });
        assertTrue(player.credit(BigDecimal.ONE, context));
        assertFalse(team.credit(BigDecimal.ONE, context));
        assertFalse(player.transferTo(team, BigDecimal.ONE, context));
        assertEquals(new BigDecimal("11"), player.getBalance());
        assertEquals(BigDecimal.TEN, team.getBalance());
        assertTrue(team.getRecentTransactions(10).isEmpty());
    }

    @Test void typedTransferVetoDoesNotCommitOrRecordEitherLeg() {
        List<EconomyEvents.TransferCompleted> completed = new ArrayList<>();
        EconomyEvents.listen(EconomyEvents.TransferCompleted.class, completed::add);
        EconomyEvents.listen(EconomyEvents.TransferPre.class, event -> {
            if (event.sourceRef().equals(teamRef)) event.cancel();
        });
        assertFalse(team.transferTo(player, BigDecimal.ONE, context));
        assertEquals(BigDecimal.TEN, player.getBalance());
        assertEquals(BigDecimal.TEN, team.getBalance());
        assertTrue(player.getRecentTransactions(10).isEmpty());
        assertTrue(team.getRecentTransactions(10).isEmpty());
        assertTrue(completed.isEmpty());
        assertTrue(player.transferTo(team, BigDecimal.ONE, context));
    }

    @Test void externalAccountTransfersUseItsTypedIdentity() {
        IBankAccount external = new IBankAccount() {
            @Override public UUID getOwner() { return id; }
            @Override public AccountRef getAccountRef() { return teamRef; }
            @Override public BigDecimal getBalance() { return team.getBalance(); }
            @Override public boolean credit(BigDecimal amount, ITransactionContext ctx) { return team.credit(amount, ctx); }
            @Override public boolean debit(BigDecimal amount, ITransactionContext ctx) { return team.debit(amount, ctx); }
            @Override public boolean transferTo(IBankAccount target, BigDecimal amount, ITransactionContext ctx) { return false; }
            @Override public List<ITransactionRecord> getRecentTransactions(int count) { return team.getRecentTransactions(count); }
        };
        List<EconomyEvents.TransferPre> pre = new ArrayList<>();
        List<EconomyEvents.TransferCompleted> post = new ArrayList<>();
        EconomyEvents.listen(EconomyEvents.TransferPre.class, pre::add);
        EconomyEvents.listen(EconomyEvents.TransferCompleted.class, post::add);
        assertTrue(player.transferTo(external, BigDecimal.ONE, context));
        assertEquals(teamRef, pre.get(0).targetRef());
        assertEquals(teamRef, post.get(0).targetRef());
        assertEquals(teamRef, player.getRecentTransactions(1).get(0).getCounterpartyRef());
    }

    @Test void legacyConstructorsAndRecordImplementationsRemainPlayerIdentities() {
        assertEquals(playerRef, new EconomyEvents.BalanceChangePre(id, BigDecimal.ZERO, BigDecimal.ONE, context).accountRef());
        assertEquals(playerRef, new EconomyEvents.BalanceChanged(id, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, context).accountRef());
        assertEquals(playerRef, new EconomyEvents.TransferPre(id, id, BigDecimal.ONE, context).sourceRef());
        assertEquals(playerRef, new EconomyEvents.TransferCompleted(id, id, BigDecimal.ONE, context).targetRef());
        var record = new TransactionRecord(id, Instant.now(), TransactionCauses.TRANSFER,
                BigDecimal.ONE, BigDecimal.TEN, id, "legacy", Map.of());
        assertEquals(playerRef, record.getCounterpartyRef());
        var standalone = new TransactionRecord(id, Instant.now(), TransactionCauses.CREDIT,
                BigDecimal.ONE, BigDecimal.TEN, null, "legacy", Map.of());
        assertNull(standalone.getCounterpartyRef());
        ITransactionRecord legacy = new ITransactionRecord() {
            public UUID getTransactionId() { return id; }
            public Instant getTimestamp() { return Instant.EPOCH; }
            public ITransactionContext.TransactionType getType() { return ITransactionContext.TransactionType.TRANSFER; }
            public BigDecimal getAmount() { return BigDecimal.ONE; }
            public BigDecimal getResultingBalance() { return BigDecimal.TEN; }
            public UUID getCounterparty() { return id; }
            public String getDescription() { return "legacy addon"; }
        };
        assertEquals(playerRef, legacy.getCounterpartyRef());
    }
}
