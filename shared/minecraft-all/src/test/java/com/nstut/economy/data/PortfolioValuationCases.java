package com.nstut.economy.data;

import com.nstut.economy.api.*;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class PortfolioValuationCases {
    private static final EconomyId GOOD = EconomyId.parse("test:commodity");
    private final UUID id = UUID.randomUUID();
    private final AccountRef player = AccountRef.player(id);
    private final AccountRef team = AccountRef.team(id);
    private final UUID orderId = UUID.randomUUID();

    @Test
    void reservePartialFillCancelAndProviderRemovalConserveOwnedUnits() {
        assertUnits(10, 10, List.of());
        var reserve = snapshot(player, reservation(4), Map.of());
        assertUnits(10, 6, List.of(reserve));
        var partial = snapshot(player, reservation(2), Map.of());
        assertUnits(8, 6, List.of(partial));
        assertUnits(8, 8, List.of());
        // Recovery escrow does not depend on a provider still being installed.
        assertEquals(2L, PortfolioValuation.holdings(null, player, List.of(), List.of(partial)).get(GOOD.toString()));
    }

    @Test
    void typedOwnersAndLegacyTeamPersonalStorageAreIsolated() {
        var personal = snapshot(player, reservation(4), Map.of());
        assertTrue(PortfolioValuation.holdings(null, team, List.of(), List.of(personal)).isEmpty());
        var teamEscrow = snapshot(team, reservation(7), Map.of());
        assertTrue(PortfolioValuation.holdings(null, player, List.of(), List.of(teamEscrow)).isEmpty());
        assertEquals(7L, PortfolioValuation.holdings(null, team, List.of(), List.of(teamEscrow)).get(GOOD.toString()));
        var legacyTeam = snapshot(new MarketIdentity(team, id, player), reservation(4), Map.of());
        assertEquals(4L, PortfolioValuation.holdings(null, player, List.of(), List.of(legacyTeam)).get(GOOD.toString()));
        assertTrue(PortfolioValuation.holdings(null, team, List.of(), List.of(legacyTeam)).isEmpty());
    }

    @Test
    void duplicateSnapshotsAndReservationTokensAreCountedOnce() {
        var original = snapshot(player, reservation(4), Map.of());
        var duplicateToken = new EconomyOrderData.OrderSnapshot(UUID.randomUUID(), id, GOOD.toString(), 4, 4,
                "900", "SELL", 1L, 0L, false, NonNullList.create(), List.of(), false, false,
                "ITEM", "economy:item", 1, Map.of(), original.externalReservation, Map.of(), MarketIdentity.personal(id));
        assertEquals(4L, PortfolioValuation.holdings(null, player, List.of(),
                List.of(original, original, duplicateToken)).get(GOOD.toString()));
    }

    @Test
    void compensationIsAConservedTypedReceivableAndLiability() {
        var recovery = snapshot(player, null, Map.of("economy:compensation_amount", "12.5",
                "economy:compensation_debtor", team.toString(), "economy:compensation_creditor", player.toString()));
        assertEquals(0, PortfolioValuation.compensation(player, List.of(recovery, recovery)).compareTo(new BigDecimal("12.5")));
        assertEquals(0, PortfolioValuation.compensation(team, List.of(recovery)).compareTo(new BigDecimal("-12.5")));
        assertEquals(0, PortfolioValuation.compensation(player, List.of(recovery))
                .add(PortfolioValuation.compensation(team, List.of(recovery))).signum());
    }

    @Test
    void latestTradeMarkChangesValueWithoutUsingAskPriceAndSupportsLargeHoldings() {
        var prices = List.of(new EconomyTradeData.TradeSnapshot(GOOD.toString(), "2", 1, id, id, 1L),
                new EconomyTradeData.TradeSnapshot(GOOD.toString(), "3", 1, id, id, 2L));
        assertEquals(0, PortfolioValuation.valueHoldings(Map.of(GOOD.toString(), 3_000_000_000L), prices)
                .compareTo(new BigDecimal("9000000000")));
        assertEquals(BigDecimal.ZERO, PortfolioValuation.valueHoldings(Map.of("test:untraded", 10L), prices));
    }

    @Test
    void legacyProviderDefaultDoesNotInventHoldings() {
        assertTrue(new FakeProvider(0).portfolioHoldings(null, team).isEmpty());
    }

    @Test
    void builtInEscrowCountsRetainedUnitsRatherThanOrderQuantity() {
        var items = NonNullList.<net.minecraft.world.item.ItemStack>create();
        items.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 2));
        var retained = new EconomyOrderData.OrderSnapshot(orderId, id, "minecraft:iron_ingot", 99, 99,
                "900", "SELL", 1L, 0L, false, items, List.of(), false, false,
                "ITEM", "economy:item", 1, Map.of(), null, Map.of(), MarketIdentity.personal(id));
        assertEquals(2L, PortfolioValuation.holdings(null, player, List.of(), List.of(retained)).get("minecraft:iron_ingot"));
    }

    @Test
    void fallbackRecoveryUsesOriginalStorageOwnerInsteadOfBuyOrderOwner() {
        var fallback = snapshot(team, reservation(4), Map.of("recovery_storage_account", player.toString()));
        assertEquals(4L, PortfolioValuation.holdings(null, player, List.of(), List.of(fallback)).get(GOOD.toString()));
        assertTrue(PortfolioValuation.holdings(null, team, List.of(), List.of(fallback)).isEmpty());
    }

    @Test
    void missingAddonEscrowRemainsValuedAfterOrderManagerReload() {
        var saved = new EconomyOrderData();
        saved.putOrder(snapshot(player, reservation(4), Map.of()));
        var manager = new com.nstut.economy.trading.OrderManager();
        manager.loadFrom(saved);
        assertEquals(4L, PortfolioValuation.holdings(null, player, List.of(), manager.getPortfolioOrders()).get(GOOD.toString()));
        manager.saveAll();
        manager.loadFrom(saved);
        assertEquals(4L, PortfolioValuation.holdings(null, player, List.of(), manager.getPortfolioOrders()).get(GOOD.toString()));
        assertEquals(1, manager.getPortfolioOrders().size());
    }

    @Test
    void legacyUnknownUnloadedStoreRetainsPreviousAssetMarkRatherThanFalseLoss() {
        var legacy = new EconomyAccountData.PortfolioPoint(1L, new BigDecimal("110"), BigDecimal.TEN, new BigDecimal("100"));
        assertEquals(new BigDecimal("100"), PortfolioValuation.markedAssets(BigDecimal.ZERO, 1, List.of(legacy)));
        assertEquals(new BigDecimal("20"), PortfolioValuation.markedAssets(new BigDecimal("20"), 0, List.of(legacy)));
        assertEquals(BigDecimal.ZERO, PortfolioValuation.markedAssets(BigDecimal.ZERO, 1, List.of()));
    }

    private void assertUnits(long expected, long available, List<EconomyOrderData.OrderSnapshot> orders) {
        IStorageProvider provider = new FakeProvider(available) {
            @Override public Map<EconomyId, Long> portfolioHoldings(ServerLevel level, AccountRef owner) {
                return owner.equals(player) ? Map.of(GOOD, amount) : Map.of();
            }
        };
        assertEquals(expected, PortfolioValuation.holdings(null, player, List.of(provider), orders).get(GOOD.toString()));
    }

    private StorageReservation reservation(int amount) {
        return new StorageReservation(EconomyId.parse("test:provider"), GOOD, amount, "durable-token", Map.of());
    }

    private EconomyOrderData.OrderSnapshot snapshot(AccountRef owner, StorageReservation reservation, Map<String, String> metadata) {
        return snapshot(new MarketIdentity(owner, id, owner), reservation, metadata);
    }

    private EconomyOrderData.OrderSnapshot snapshot(MarketIdentity identity, StorageReservation reservation, Map<String, String> metadata) {
        return new EconomyOrderData.OrderSnapshot(orderId, id, GOOD.toString(), 4, 4,
                "900", "SELL", 1L, 0L, false, NonNullList.create(), List.of(), false, false,
                "ITEM", "economy:item", 1, Map.of(), reservation, metadata, identity);
    }

    private static class FakeProvider implements IStorageProvider {
        final long amount;
        FakeProvider(long amount) { this.amount = amount; }
        public EconomyId id() { return EconomyId.parse("test:provider"); }
        public boolean supports(ICommodity commodity) { return true; }
        public int available(ServerLevel level, UUID owner, ICommodity commodity) { return (int) amount; }
        public int receivable(ServerLevel level, UUID owner, ICommodity commodity, int requested) { return 0; }
        public Optional<StorageReservation> reserve(ServerLevel level, UUID owner, ICommodity commodity, int requested) { return Optional.empty(); }
        public StorageDeliveryResult deliverReserved(ServerLevel level, StorageReservation reservation, UUID receiver, int requested) { return StorageDeliveryResult.unchanged(reservation); }
        public boolean release(ServerLevel level, StorageReservation reservation) { return false; }
    }
}
