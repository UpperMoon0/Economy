package com.nstut.economy.data;

import com.nstut.economy.api.AccountRef;
import com.nstut.economy.core.BalanceMutation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Same persistence contract exercised by every supported saved-data adapter. */
abstract class PortfolioHistoryContract {
    protected abstract EconomyAccountData roundTrip(EconomyAccountData data);
    protected abstract EconomyAccountData load(CompoundTag encoded);

    @Test void incompleteKnownOnlyMarksNeverBecomeLegacyFallbackAfterRefreshOrRestart() {
        UUID id = UUID.randomUUID();
        for (AccountRef account : java.util.List.of(AccountRef.player(id), AccountRef.team(id))) {
            EconomyAccountData data = new EconomyAccountData();
            var history = data.getPortfolioHistory(account);
            BigDecimal first = PortfolioValuation.markedAssets(new BigDecimal("20"), 1, history);
            data.addPortfolioPoint(account, BigDecimal.ZERO, first, PortfolioValuation.legacyAssetFallback(1, history));
            data = roundTrip(data);
            history = data.getPortfolioHistory(account);
            assertFalse(history.get(0).legacyAssetFallback);
            BigDecimal changed = PortfolioValuation.markedAssets(new BigDecimal("30"), 1, history);
            assertEquals(new BigDecimal("30"), changed, "unknown store must not freeze fresh known assets");
            data.addPortfolioPoint(account, BigDecimal.ZERO, changed, PortfolioValuation.legacyAssetFallback(1, history));
            data = roundTrip(data);
            history = data.getPortfolioHistory(account);
            assertEquals(new BigDecimal("30"), history.get(history.size() - 1).assets);
            assertFalse(history.get(history.size() - 1).legacyAssetFallback);
            assertEquals(new BigDecimal("35"), PortfolioValuation.markedAssets(new BigDecimal("35"), 1, history),
                    "new claims/known changes must still count after restart");
        }
    }

    @Test void legacyFallbackProvenanceSurvivesMutationAndRestartButClearsOnceObserved() {
        UUID id = UUID.randomUUID();
        for (AccountRef account : java.util.List.of(AccountRef.player(id), AccountRef.team(id))) {
            EconomyAccountData data = legacyAssetHistory(account, new BigDecimal("100"));
            assertTrue(data.getPortfolioHistory(account).get(0).legacyAssetFallback);
            // A cash event must preserve the mark's provenance when no newer world observation is available.
            data.recordMutation(account, mutation(UUID.randomUUID(), "0", "1", "1"));
            data = roundTrip(data);
            var history = data.getPortfolioHistory(account);
            assertTrue(history.get(history.size() - 1).legacyAssetFallback);
            assertEquals(new BigDecimal("100"), PortfolioValuation.markedAssets(new BigDecimal("20"), 1, history));
            // Even an equal-valued complete observation must clear provenance rather than being deduplicated.
            data.addPortfolioPoint(account, BigDecimal.ONE, new BigDecimal("100"),
                    PortfolioValuation.legacyAssetFallback(0, history));
            data = roundTrip(data);
            history = data.getPortfolioHistory(account);
            assertEquals(3, history.size());
            assertFalse(history.get(history.size() - 1).legacyAssetFallback);
            assertEquals(new BigDecimal("30"), PortfolioValuation.markedAssets(new BigDecimal("30"), 1, history),
                    "an ancient legacy point must not resurrect after complete observation");
            data.recordMutation(account, mutation(UUID.randomUUID(), "1", "2", "1"));
            history = roundTrip(data).getPortfolioHistory(account);
            assertFalse(history.get(history.size() - 1).legacyAssetFallback);
        }
    }

    private EconomyAccountData legacyAssetHistory(AccountRef account, BigDecimal assets) {
        CompoundTag root = new CompoundTag(), histories = new CompoundTag(), point = new CompoundTag();
        ListTag points = new ListTag();
        point.putLong("TS", 1L); point.putString("NW", assets.toPlainString());
        point.putString("BAL", "0"); point.putString("ASS", assets.toPlainString());
        points.add(point);
        histories.put(account.kind() == com.nstut.economy.api.AccountKind.PLAYER
                ? account.id().toString() : account.toString(), points);
        root.put(account.kind() == com.nstut.economy.api.AccountKind.PLAYER
                ? "PortfolioHistory" : "TypedPortfolioHistory", histories);
        return load(root);
    }

    @Test void groupedCashEventsUseFinalCashAndAssetsWithoutPhantomIntermediateNetWorth() {
        UUID id = UUID.randomUUID();
        String reason = "Grouped settlement final valuation; per-operation cash delta. Order purchase";
        for (AccountRef account : java.util.List.of(AccountRef.player(id), AccountRef.team(id))) {
            EconomyAccountData data = new EconomyAccountData();
            // The deferred callbacks see the final holdings after two at-mark purchases (10 and 5).
            data.addPortfolioPoint(account, new BigDecimal("85"), new BigDecimal("15"));
            data.setAccountBalance(account, new BigDecimal("85"));
            data.recordMutation(account, new BalanceMutation(UUID.randomUUID(), 1L, "economy:trade", reason,
                    new BigDecimal("100"), new BigDecimal("90"), BigDecimal.ZERO, new BigDecimal("-10"), true));
            data.recordMutation(account, new BalanceMutation(UUID.randomUUID(), 2L, "economy:trade", reason,
                    new BigDecimal("90"), new BigDecimal("85"), BigDecimal.ZERO, new BigDecimal("-5"), true));
            var history = roundTrip(data).getPortfolioHistory(account);
            assertEquals(3, history.size());
            for (var point : history.subList(1, 3)) {
                assertEquals(new BigDecimal("85"), point.balance);
                assertEquals(new BigDecimal("15"), point.assets);
                assertEquals(new BigDecimal("100"), point.netWorth,
                        "final assets must never pair with reconstructed cash90 and fabricate NW105");
                assertEquals(BigDecimal.ZERO, point.externalFlow);
                assertTrue(point.reason.startsWith("Grouped settlement final valuation; per-operation cash delta."));
            }
            assertEquals(new BigDecimal("-10"), history.get(1).cashDelta);
            assertEquals(new BigDecimal("-5"), history.get(2).cashDelta);
            assertNotEquals(history.get(1).operationId, history.get(2).operationId);
        }
    }

    @Test void groupedDeletedAccountMarksMissingFinalCashAsZero() {
        UUID id = UUID.randomUUID();
        String reason = "Grouped settlement final valuation; per-operation cash delta. Deleted account";
        for (AccountRef account : java.util.List.of(AccountRef.player(id), AccountRef.team(id))) {
            EconomyAccountData data = new EconomyAccountData();
            data.addPortfolioPoint(account, BigDecimal.ZERO, new BigDecimal("15"));
            // Deletion before the deferred flush intentionally leaves no saved cash entry.
            assertFalse(data.getAccountBalances().containsKey(account));
            data.recordMutation(account, new BalanceMutation(UUID.randomUUID(), 1L, "economy:trade", reason,
                    new BigDecimal("100"), new BigDecimal("90"), BigDecimal.ZERO, new BigDecimal("-10"), true));
            data.recordMutation(account, new BalanceMutation(UUID.randomUUID(), 2L, "economy:account_deleted", reason,
                    new BigDecimal("90"), BigDecimal.ZERO, new BigDecimal("-90"), new BigDecimal("-90"), true));
            var restored = roundTrip(data);
            assertFalse(restored.getAccountBalances().containsKey(account));
            var history = restored.getPortfolioHistory(account);
            assertEquals(3, history.size());
            for (var point : history.subList(1, 3)) {
                assertEquals(BigDecimal.ZERO, point.balance);
                assertEquals(new BigDecimal("15"), point.netWorth,
                        "missing deleted balance must not use reconstructed positive cash");
            }
            assertEquals(new BigDecimal("-10"), history.get(1).cashDelta);
            assertEquals(new BigDecimal("-90"), history.get(2).cashDelta);
        }
    }

    @Test void perStoreMarksSurviveAdapterSaveAboveNbtStringLimit() {
        AccountRef account = AccountRef.player(UUID.randomUUID());
        EconomyAccountData data = new EconomyAccountData();
        for (int i = 0; i < 2500; i++) data.portfolioStores().update(account,
                "vault|minecraft:overworld|" + i, java.util.Map.of("test:good", 1L), 10);
        assertTrue(data.portfolioStores().encode().length > 65535);
        EconomyAccountData restored = roundTrip(data);
        assertEquals(2500, restored.portfolioStores().keys(account).size());
        var stale = restored.portfolioStores().snapshot(account, 20, java.util.Map::of);
        assertEquals(2500L, stale.holdings().get("test:good"));
        assertEquals(2500, stale.stale());
    }

    private static BalanceMutation mutation(UUID id, String before, String after, String flow) {
        return new BalanceMutation(id, 123456789L, "test:salary", "Precise attributed credit",
                new BigDecimal(before), new BigDecimal(after), new BigDecimal(flow));
    }

    @Test
    void attributedOperationsSurviveRestartWithoutReplayingCallbacks() {
        AccountRef account = AccountRef.player(UUID.randomUUID());
        UUID operationId = UUID.randomUUID();
        EconomyAccountData data = new EconomyAccountData();
        data.addPortfolioPoint(account, BigDecimal.ZERO, new BigDecimal("4.25"));
        BalanceMutation mutation = mutation(operationId, "0", "12.000000000000000001", "12.000000000000000001");
        data.setAccountBalance(account, mutation.after());
        data.recordMutation(account, mutation);
        data.recordMutation(account, mutation);
        assertEquals(2, data.getPortfolioHistory(account).size());

        EconomyAccountData restarted = roundTrip(data);
        restarted.recordMutation(account, mutation);
        var points = restarted.getPortfolioHistory(account);
        assertEquals(2, points.size(), "repeated committed callback remains idempotent after restart");
        var event = points.get(1);
        assertEquals(operationId.toString(), event.operationId);
        assertEquals(123456789L, event.timestamp);
        assertEquals("test:salary", event.source);
        assertEquals("Precise attributed credit", event.reason);
        assertEquals(new BigDecimal("12.000000000000000001"), event.balance);
        assertEquals(new BigDecimal("16.250000000000000001"), event.netWorth);
        assertEquals(new BigDecimal("4.25"), event.assets);
        assertEquals(mutation.cashDelta(), event.cashDelta);
        assertEquals(mutation.externalFlow(), event.externalFlow);
        assertTrue(event.attributed);
        assertEquals(mutation.after(), restarted.getAccountBalances().get(account));
        assertThrows(UnsupportedOperationException.class, () -> points.clear());
        assertEquals(2, roundTrip(restarted).getPortfolioHistory(account).size(), "saving does not invent events");
    }

    @Test
    void sameUuidAndOperationIdRemainIsolatedBetweenPlayerAndTeam() {
        UUID id = UUID.randomUUID();
        AccountRef player = AccountRef.player(id);
        AccountRef team = AccountRef.team(id);
        UUID operation = UUID.randomUUID();
        EconomyAccountData data = new EconomyAccountData();
        data.recordMutation(player, mutation(operation, "0", "12", "12"));
        data.recordMutation(team, mutation(operation, "0", "70", "70"));
        data.recordMutation(player, mutation(operation, "0", "12", "12"));
        EconomyAccountData restarted = roundTrip(data);
        assertEquals(1, restarted.getPortfolioHistory(player).size());
        assertEquals(1, restarted.getPortfolioHistory(team).size());
        assertEquals(new BigDecimal("12"), restarted.getPortfolioHistory(player).get(0).balance);
        assertEquals(new BigDecimal("70"), restarted.getPortfolioHistory(team).get(0).balance);
    }

    @Test
    void zeroNetSequencePreservesEachCommittedOperationAndItsClassification() {
        AccountRef account = AccountRef.team(UUID.randomUUID());
        EconomyAccountData data = new EconomyAccountData();
        data.recordMutation(account, mutation(UUID.randomUUID(), "0", "9", "9"));
        data.recordMutation(account, mutation(UUID.randomUUID(), "9", "0", "-9"));
        var points = roundTrip(data).getPortfolioHistory(account);
        assertEquals(2, points.size());
        assertEquals(new BigDecimal("9"), points.get(0).cashDelta);
        assertEquals(new BigDecimal("-9"), points.get(1).cashDelta);
        assertEquals(BigDecimal.ZERO, points.stream().map(point -> point.externalFlow).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertNotEquals(points.get(0).operationId, points.get(1).operationId);
    }

    @Test
    void legacySnapshotsRemainExplicitlyUnattributedAlongsideNewEvents() {
        UUID id = UUID.randomUUID();
        CompoundTag root = new CompoundTag();
        CompoundTag histories = new CompoundTag();
        ListTag points = new ListTag();
        CompoundTag old = new CompoundTag();
        old.putLong("TS", 42L);
        old.putString("NW", "18.75");
        old.putString("BAL", "12.5");
        old.putString("ASS", "6.25");
        points.add(old);
        histories.put(id.toString(), points);
        root.put("PortfolioHistory", histories);
        CompoundTag typedHistories = new CompoundTag();
        typedHistories.put(AccountRef.team(id).toString(), points.copy());
        root.put("TypedPortfolioHistory", typedHistories);
        EconomyAccountData data = load(root);
        AccountRef account = AccountRef.player(id);
        assertFalse(data.getPortfolioHistory(account).get(0).attributed);
        data.recordMutation(account, mutation(UUID.randomUUID(), "12.5", "15.5", "3"));
        var persisted = roundTrip(data).getPortfolioHistory(account);
        assertEquals(2, persisted.size());
        assertEquals(42L, persisted.get(0).timestamp);
        assertEquals(new BigDecimal("18.75"), persisted.get(0).netWorth);
        assertFalse(persisted.get(0).attributed, "legacy points must never acquire fabricated flow attribution");
        assertEquals("legacy", persisted.get(0).source);
        assertTrue(persisted.get(1).attributed);
        assertEquals(new BigDecimal("3"), persisted.get(1).externalFlow);
        assertEquals(new BigDecimal("21.75"), persisted.get(1).netWorth);
        var legacyTeam = roundTrip(data).getPortfolioHistory(AccountRef.team(id));
        assertEquals(1, legacyTeam.size());
        assertFalse(legacyTeam.get(0).attributed);
        assertEquals(new BigDecimal("18.75"), legacyTeam.get(0).netWorth);
    }

    @Test
    void priceOnlyObservationsAreNotCashFlowsAndUnchangedRefreshIsDeduplicated() {
        AccountRef account = AccountRef.player(UUID.randomUUID());
        EconomyAccountData data = new EconomyAccountData();
        data.addPortfolioPoint(account, BigDecimal.TEN, new BigDecimal("2"));
        data.addPortfolioPoint(account, BigDecimal.TEN, new BigDecimal("2.0"));
        data.addPortfolioPoint(account, BigDecimal.TEN, new BigDecimal("5"));
        var history = roundTrip(data).getPortfolioHistory(account);
        assertEquals(2, history.size());
        assertEquals(new BigDecimal("15"), history.get(1).netWorth);
        assertEquals(BigDecimal.ZERO, history.get(1).cashDelta);
        assertEquals(BigDecimal.ZERO, history.get(1).externalFlow);
        assertEquals("economy:valuation", history.get(1).source);
    }

    @Test
    void boundedRetentionKeepsAggregateCashFlowsAndRecentOperationDetail() {
        AccountRef account = AccountRef.player(UUID.randomUUID());
        EconomyAccountData data = new EconomyAccountData();
        for (int i = 1; i <= 1050; i++) {
            data.recordMutation(account, mutation(UUID.randomUUID(), Integer.toString(i - 1), Integer.toString(i), "1"));
        }
        var points = roundTrip(data).getPortfolioHistory(account);
        assertEquals(1024, points.size());
        assertEquals("economy:rollup", points.get(0).source);
        assertEquals(new BigDecimal("1050"), points.get(points.size() - 1).balance);
        assertEquals(new BigDecimal("1050"), points.stream().map(point -> point.cashDelta).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(new BigDecimal("1050"), points.stream().map(point -> point.externalFlow).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(BigDecimal.ONE, points.get(1).cashDelta, "retained recent operations keep their individual deltas");
    }
}
