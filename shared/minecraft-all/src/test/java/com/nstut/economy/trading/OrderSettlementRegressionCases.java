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
}
