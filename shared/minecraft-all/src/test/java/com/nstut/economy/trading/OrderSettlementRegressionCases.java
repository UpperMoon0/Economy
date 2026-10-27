package com.nstut.economy.trading;

import com.nstut.economy.api.EconomyEvents;
import com.nstut.economy.config.EconomyConfig;
import com.nstut.economy.core.AccountManager;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Shared behavioral cases; loader/version wrappers provide registry bootstrap. */
public abstract class OrderSettlementRegressionCases {
    private BigDecimal startingBalance;
    private AccountManager accounts;
    private OrderManager manager;
    private UUID buyer;

    @BeforeEach void setupAccounts() {
        startingBalance = EconomyConfig.getInstance().getStartingBalance();
        EconomyConfig.getInstance().setStartingBalance(BigDecimal.ZERO);
        accounts = new AccountManager();
        manager = new OrderManager();
        buyer = UUID.randomUUID();
    }

    @AfterEach void restoreConfig() { EconomyConfig.getInstance().setStartingBalance(startingBalance); }

    private ItemCommodity iron() {
        return new ItemCommodity(BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), Items.IRON_INGOT, BigDecimal.ZERO);
    }

    @Test void cancelEditAndRecursiveExecutionAreRejectedDuringPayment() {
        accounts.getOrCreatePlayerAccount(buyer).credit(BigDecimal.TEN, null);
        Order sell = manager.createServerSellOrder(iron(), 2, BigDecimal.ONE);
        AtomicBoolean cancel = new AtomicBoolean(true), edit = new AtomicBoolean(true), nested = new AtomicBoolean(true);
        AtomicInteger transfers = new AtomicInteger();
        try (var subscription = EconomyEvents.listen(EconomyEvents.TransferPre.class, event -> {
            transfers.incrementAndGet();
            cancel.set(manager.cancelOrder(sell.getOrderId(), OrderManager.SERVER_ID));
            edit.set(manager.editOrder(sell.getOrderId(), OrderManager.SERVER_ID, 9, BigDecimal.TEN, false));
            nested.set(sell.executePartial(buyer, 1, null).success);
        })) {
            assertTrue(sell.executePartial(buyer, 1, null).success);
        }
        assertFalse(cancel.get());
        assertFalse(edit.get());
        assertFalse(nested.get());
        assertEquals(1, transfers.get());
        assertEquals(1, sell.getQuantity());
        assertEquals(new BigDecimal("9"), accounts.getOrCreatePlayerAccount(buyer).getBalance());
        assertTrue(manager.cancelOrder(sell.getOrderId(), OrderManager.SERVER_ID));
    }

    @Test void paymentVetoLeavesBalancesAndOrderIntactAndReleasesGuard() {
        accounts.getOrCreatePlayerAccount(buyer).credit(BigDecimal.TEN, null);
        Order sell = manager.createServerSellOrder(iron(), 1, BigDecimal.ONE);
        try (var subscription = EconomyEvents.listen(EconomyEvents.TransferPre.class, event -> event.cancel())) {
            assertFalse(sell.executePartial(buyer, 1, null).success);
        }
        assertEquals(BigDecimal.TEN, accounts.getOrCreatePlayerAccount(buyer).getBalance());
        assertEquals(1, sell.getQuantity());
        assertTrue(sell.executePartial(buyer, 1, null).success);
    }

    @Test void postPaymentCallbacksCannotCancelAnExecutingOrder() {
        accounts.getOrCreatePlayerAccount(buyer).credit(BigDecimal.TEN, null);
        Order sell = manager.createServerSellOrder(iron(), 2, BigDecimal.ONE);
        AtomicBoolean cancelled = new AtomicBoolean();
        try (var subscription = EconomyEvents.listen(EconomyEvents.BalanceChanged.class, event -> {
            if (manager.cancelOrder(sell.getOrderId(), OrderManager.SERVER_ID)) cancelled.set(true);
        })) {
            assertTrue(sell.executePartial(buyer, 1, null).success);
        }
        assertFalse(cancelled.get());
        assertEquals(1, sell.getQuantity());
    }

    @Test void matchingAlsoGuardsTheCounterpartBuyOrder() {
        Order buy = manager.createServerBuyOrder(iron(), 2, BigDecimal.ONE);
        UUID seller = UUID.randomUUID();
        AtomicBoolean cancelled = new AtomicBoolean(true), edited = new AtomicBoolean(true);
        NonNullList<ItemStack> escrow = NonNullList.create();
        escrow.add(new ItemStack(Items.IRON_INGOT));
        try (var subscription = EconomyEvents.listen(EconomyEvents.TransferPre.class, event -> {
            cancelled.set(manager.cancelOrder(buy.getOrderId(), OrderManager.SERVER_ID));
            edited.set(manager.editOrder(buy.getOrderId(), OrderManager.SERVER_ID, 9, BigDecimal.TEN, false));
        })) {
            assertEquals(1, manager.createSellOrder(seller, iron(), 1, BigDecimal.ONE, escrow, null).filledQuantity());
        }
        assertFalse(cancelled.get());
        assertFalse(edited.get());
        assertEquals(1, buy.getQuantity());
        assertEquals(BigDecimal.ONE, accounts.getOrCreatePlayerAccount(seller).getBalance());
    }

    @Test void incomingSellEscrowIsVisibleToValuationBeforeOrderBookRegistration() {
        manager.createServerBuyOrder(iron(), 1, BigDecimal.ONE);
        UUID seller = UUID.randomUUID();
        NonNullList<ItemStack> escrow = NonNullList.create();
        escrow.add(new ItemStack(Items.IRON_INGOT, 2));
        AtomicInteger observedEscrow = new AtomicInteger();
        try (var subscription = EconomyEvents.listen(EconomyEvents.TransferPre.class, event -> {
            var snapshots = manager.getPortfolioOrders(com.nstut.economy.api.AccountRef.player(seller));
            assertEquals(1, snapshots.size());
            observedEscrow.set(snapshots.get(0).reservedItems.stream().mapToInt(ItemStack::getCount).sum());
            assertTrue(manager.getPlayerOrders(seller).isEmpty(), "in-flight escrow must not enter the public order book early");
        })) {
            var result = manager.createSellOrder(seller, iron(), 2, BigDecimal.ONE, escrow, null);
            assertEquals(1, result.filledQuantity());
        }
        assertEquals(2, observedEscrow.get());
        var snapshots = manager.getPortfolioOrders(com.nstut.economy.api.AccountRef.player(seller));
        assertEquals(1, snapshots.size(), "registered and incoming valuation views must not double count");
        assertEquals(1, snapshots.get(0).reservedItems.stream().mapToInt(ItemStack::getCount).sum());
        assertEquals(1, manager.getPlayerOrders(seller).size());
    }

    private Order pendingPeriodicBuy() {
        NonNullList<ItemStack> escrow = NonNullList.create();
        escrow.add(new ItemStack(Items.IRON_INGOT));
        assertEquals(0, manager.createSellOrder(buyer, iron(), 1, BigDecimal.ONE, escrow, null).filledQuantity());
        // Server order creation registers the book entry without immediately matching it.
        return manager.createServerBuyOrder(iron(), 2, BigDecimal.ONE);
    }

    @Test void periodicMatchingRejectsBuyCancellationDuringPayment() {
        Order buy = pendingPeriodicBuy();
        AtomicBoolean cancelled = new AtomicBoolean(true);
        AtomicInteger transfers = new AtomicInteger();
        try (var subscription = EconomyEvents.listen(EconomyEvents.TransferPre.class, event -> {
            transfers.incrementAndGet();
            cancelled.set(manager.cancelOrder(buy.getOrderId(), OrderManager.SERVER_ID));
        })) {
            manager.matchAllPendingOrders(null);
        }
        assertEquals(1, transfers.get());
        assertFalse(cancelled.get());
        assertEquals(1, buy.getQuantity());
        assertEquals(BigDecimal.ONE, accounts.getOrCreatePlayerAccount(buyer).getBalance());
        assertTrue(manager.cancelOrder(buy.getOrderId(), OrderManager.SERVER_ID));
    }

    @Test void periodicMatchingRejectsBuyEditingDuringPayment() {
        Order buy = pendingPeriodicBuy();
        AtomicBoolean edited = new AtomicBoolean(true);
        AtomicInteger transfers = new AtomicInteger();
        try (var subscription = EconomyEvents.listen(EconomyEvents.TransferPre.class, event -> {
            transfers.incrementAndGet();
            edited.set(manager.editOrder(buy.getOrderId(), OrderManager.SERVER_ID, 9, BigDecimal.TEN, false));
        })) {
            manager.matchAllPendingOrders(null);
        }
        assertEquals(1, transfers.get());
        assertFalse(edited.get());
        assertEquals(1, buy.getQuantity());
        assertEquals(BigDecimal.ONE, buy.getPricePerUnit());
        assertEquals(BigDecimal.ONE, accounts.getOrCreatePlayerAccount(buyer).getBalance());
        assertTrue(manager.editOrder(buy.getOrderId(), OrderManager.SERVER_ID, 2, BigDecimal.ONE, false));
    }

    @Test void periodicMatchingKeepsInfiniteBuyQuantityAndReleasesGuard() {
        Order buy = pendingPeriodicBuy();
        buy.setInfinite(true);
        manager.matchAllPendingOrders(null);
        assertEquals(2, buy.getQuantity());
        assertEquals(BigDecimal.ONE, accounts.getOrCreatePlayerAccount(buyer).getBalance());
        assertFalse(buy.isExecutionInProgress());
        assertTrue(manager.cancelOrder(buy.getOrderId(), OrderManager.SERVER_ID));
    }

    @Test void wealthyBuyerCanPurchaseOneCheapItem() {
        accounts.getOrCreatePlayerAccount(buyer).credit(new BigDecimal("21474836.48"), null);
        Order sell = manager.createServerSellOrder(iron(), 1, new BigDecimal("0.01"));
        assertTrue(sell.executePartial(buyer, 1, null).success);
        assertEquals(new BigDecimal("21474836.47"), accounts.getOrCreatePlayerAccount(buyer).getBalance());
    }

    @Test void fluidBuyerWithNormalBalanceDoesNotOverflow() {
        accounts.getOrCreatePlayerAccount(buyer).credit(new BigDecimal("21475"), null);
        FluidCommodity water = new FluidCommodity(BuiltInRegistries.FLUID.getKey(Fluids.WATER), Fluids.WATER, BigDecimal.ZERO) {
            @Override public net.minecraft.network.chat.Component getDisplayName() {
                // This arithmetic regression does not require a loader-specific fluid-name service.
                return net.minecraft.network.chat.Component.literal("Water");
            }
        };
        Order sell = manager.createServerSellOrder(water, 1, new BigDecimal("0.00001"));
        assertNotNull(sell);
        assertTrue(sell.executePartial(buyer, 1, null).success);
        assertEquals(new BigDecimal("21474.99999"), accounts.getOrCreatePlayerAccount(buyer).getBalance());
    }

    @Test void affordabilityStillCapsToAvailableFunds() {
        accounts.getOrCreatePlayerAccount(buyer).credit(new BigDecimal("0.035"), null);
        Order sell = manager.createServerSellOrder(iron(), 5, new BigDecimal("0.01"));
        assertEquals(3, sell.executePartial(buyer, 5, null).quantityTransferred);
        assertEquals(2, sell.getQuantity());
        assertEquals(new BigDecimal("0.005"), accounts.getOrCreatePlayerAccount(buyer).getBalance());
    }

    @Test void exceptionBeforePaymentAlsoReleasesTheSettlementGuard() {
        accounts.getOrCreatePlayerAccount(buyer).credit(BigDecimal.TEN, null);
        ItemCommodity throwing = new ItemCommodity(BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), Items.IRON_INGOT, BigDecimal.ZERO) {
            @Override public net.minecraft.network.chat.Component getDisplayName() {
                throw new IllegalStateException("addon display-name failure");
            }
        };
        Order sell = manager.createServerSellOrder(throwing, 1, BigDecimal.ONE);
        assertThrows(IllegalStateException.class, () -> sell.executePartial(buyer, 1, null));
        assertEquals(BigDecimal.TEN, accounts.getOrCreatePlayerAccount(buyer).getBalance());
        assertTrue(manager.cancelOrder(sell.getOrderId(), OrderManager.SERVER_ID));
    }

    @Test void committedOrderRecordsBothCashLegsOnceAsTradeRatherThanExternalFlow() {
        RecordingBalances store = new RecordingBalances();
        accounts.setAccountData(store);
        accounts.getOrCreatePlayerAccount(buyer).credit(BigDecimal.TEN, null);
        store.events.clear();
        Order sell = manager.createServerSellOrder(iron(), 2, BigDecimal.ONE);
        assertTrue(sell.executePartial(buyer, 1, null).success);
        assertEquals(2, store.events.size());
        var purchase = store.events.stream().filter(event -> event.account().equals(
                com.nstut.economy.api.AccountRef.player(buyer))).findFirst().orElseThrow().mutation();
        assertEquals(BigDecimal.ONE.negate(), purchase.cashDelta());
        assertEquals(BigDecimal.ZERO, purchase.externalFlow());
        assertEquals("economy:trade", purchase.source());
        assertEquals(1, store.events.stream().map(event -> event.mutation().operationId()).distinct().count());
        assertTrue(sell.executePartial(buyer, 1, null).success);
        assertEquals(4, store.events.size());
        assertEquals(2, store.events.stream().map(event -> event.mutation().operationId()).distinct().count());
        accounts.saveAll();
        assertEquals(4, store.events.size(), "save must not repeat operation history");
    }

    @Test void rejectedOrderPaymentCreatesNoPortfolioEvents() {
        RecordingBalances store = new RecordingBalances();
        accounts.setAccountData(store);
        accounts.getOrCreatePlayerAccount(buyer).credit(BigDecimal.TEN, null);
        store.events.clear();
        Order sell = manager.createServerSellOrder(iron(), 1, BigDecimal.ONE);
        try (var subscription = EconomyEvents.listen(EconomyEvents.TransferPre.class, event -> event.cancel())) {
            assertFalse(sell.executePartial(buyer, 1, null).success);
        }
        assertTrue(store.events.isEmpty());
        assertEquals(BigDecimal.TEN, accounts.getOrCreatePlayerAccount(buyer).getBalance());
        assertEquals(1, sell.getQuantity());
    }

    private static final class RecordingBalances implements com.nstut.economy.core.BalanceStore {
        private record Event(com.nstut.economy.api.AccountRef account, com.nstut.economy.core.BalanceMutation mutation) { }
        final java.util.List<Event> events = new java.util.ArrayList<>();
        final java.util.Map<UUID, BigDecimal> balances = new java.util.HashMap<>();
        final java.util.Map<com.nstut.economy.api.AccountRef, BigDecimal> typed = new java.util.HashMap<>();
        public java.util.Map<UUID, BigDecimal> getBalances() { return balances; }
        public void setBalance(UUID id, BigDecimal balance) { balances.put(id, balance); }
        public boolean supportsTypedAccounts() { return true; }
        public void setAccountBalance(com.nstut.economy.api.AccountRef account, BigDecimal balance) { typed.put(account, balance); }
        public void recordMutation(com.nstut.economy.api.AccountRef account, com.nstut.economy.core.BalanceMutation mutation) {
            events.add(new Event(account, mutation));
        }
    }

    @Test void ownerPortfolioLookupNeverSerializesUnrelatedLiveOrders() throws Exception {
        var register = OrderManager.class.getDeclaredMethod("registerOrder", Order.class);
        register.setAccessible(true);
        java.util.List<CountedOrder> unrelated = new java.util.ArrayList<>();
        for (int i = 0; i < 200; i++) {
            CountedOrder order = new CountedOrder(UUID.randomUUID(), iron());
            register.invoke(manager, order);
            unrelated.add(order);
        }
        CountedOrder selected = new CountedOrder(buyer, iron());
        register.invoke(manager, selected);
        unrelated.forEach(order -> order.serializations = 0);
        selected.serializations = 0;
        var result = manager.getPortfolioOrders(com.nstut.economy.api.AccountRef.player(buyer));
        assertEquals(1, result.size());
        assertEquals(selected.getOrderId(), result.get(0).orderId);
        assertEquals(1, selected.serializations);
        assertEquals(0, unrelated.stream().mapToInt(order -> order.serializations).sum());
        assertTrue(manager.cancelOrder(selected.getOrderId(), buyer));
        assertTrue(manager.getPortfolioOrders(com.nstut.economy.api.AccountRef.player(buyer)).isEmpty());
        assertEquals(0, unrelated.stream().mapToInt(order -> order.serializations).sum());
    }

    @Test void portfolioIndexTracksIncomingReservationRecoveryAndTypedCompensationReferences() {
        var owner = com.nstut.economy.api.AccountRef.player(buyer);
        var creditor = com.nstut.economy.api.AccountRef.team(UUID.randomUUID());
        var debtor = com.nstut.economy.api.AccountRef.player(UUID.randomUUID());
        var recoveryOwner = com.nstut.economy.api.AccountRef.team(UUID.randomUUID());
        NonNullList<ItemStack> escrow = NonNullList.create();
        escrow.add(new ItemStack(Items.IRON_INGOT, 2));
        manager.createSellOrder(buyer, iron(), 2, BigDecimal.ONE, escrow, null);
        var selected = manager.getPlayerOrders(buyer).get(0);
        assertEquals(1, manager.getPortfolioOrders(owner).size());
        selected.setAddonMetadata(java.util.Map.of(
                "economy:compensation_creditor", creditor.toString(),
                "economy:compensation_debtor", debtor.toString(),
                "economy:compensation_amount", "3",
                "recovery_storage_account", recoveryOwner.toString()));
        selected.markQuarantined("manual recovery");
        manager.preserveRecoveryOrder(selected, "test recovery");
        manager.cleanupOrders();
        assertTrue(manager.getPlayerOrders(buyer).isEmpty(), "recovery stays outside the public book");
        for (var account : java.util.List.of(owner, creditor, debtor, recoveryOwner)) {
            var snapshots = manager.getPortfolioOrders(account);
            assertEquals(1, snapshots.size());
            assertEquals(selected.getOrderId(), snapshots.get(0).orderId);
        }
        assertTrue(manager.getPortfolioOrders(com.nstut.economy.api.AccountRef.player(creditor.id())).isEmpty());
        var data = new com.nstut.economy.data.EconomyOrderData();
        data.putOrder(selected.toSnapshot());
        OrderManager restarted = new OrderManager();
        restarted.loadFrom(data);
        assertEquals(1, restarted.getPortfolioOrders(creditor).size());
        assertEquals(1, restarted.getPortfolioOrders(recoveryOwner).size());
    }

    private static final class CountedOrder extends Order {
        int serializations;
        CountedOrder(UUID owner, ItemCommodity commodity) {
            super(owner, commodity, 1, BigDecimal.ONE, com.nstut.economy.api.IOrder.OrderType.BUY, null);
        }
        @Override public com.nstut.economy.data.EconomyOrderData.OrderSnapshot toSnapshot() {
            serializations++;
            return super.toSnapshot();
        }
    }
}
