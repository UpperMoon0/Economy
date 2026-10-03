package com.nstut.economy.api;

import com.nstut.economy.core.AccountManager;
import com.nstut.economy.core.AccountManagerHolder;
import com.nstut.economy.data.EconomyOrderData;
import com.nstut.economy.data.EconomyTradeData;
import com.nstut.economy.data.TradeLedger;
import com.nstut.economy.test.MinecraftTestBase;
import com.nstut.economy.trading.ItemCommodity;
import com.nstut.economy.trading.Order;
import com.nstut.economy.trading.OrderManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class TradeCancellationPersistenceRegressionTest extends MinecraftTestBase {
    private AccountManager accounts;

    @BeforeEach
    void setUp() {
        com.nstut.economy.api.internal.EconomyEventBridge.clearListeners();
        TradeLedger.setTradeData(new EconomyTradeData());
        accounts = new AccountManager();
    }

    @AfterEach
    void tearDown() {
        com.nstut.economy.api.internal.EconomyEventBridge.clearListeners();
        TradeLedger.clearTradeData();
        AccountManagerHolder.setInstance(null);
    }

    @Test
    @DisplayName("Infinite BUY cancellation is guarded during TradeCompleted and durable after settlement")
    void cancellationAfterTradeCompletedDoesNotResurrectInfiniteBuy() {
        ItemCommodity iron = new ItemCommodity(
                new ResourceLocation("minecraft", "iron_ingot"), Items.IRON_INGOT, BigDecimal.ZERO);
        UUID buyer = UUID.randomUUID();

        assertTrue(accounts.getOrCreatePlayerAccount(buyer)
                .credit(new BigDecimal("100"), null));

        EconomyOrderData data = new EconomyOrderData();
        OrderManager manager = new OrderManager();
        manager.setOrderData(data);

        manager.createBuyOrder(buyer, iron, 1, BigDecimal.ONE, true, null);
        Order infiniteBuy = manager.getPlayerOrders(buyer).stream()
                .filter(order -> order.getType() == IOrder.OrderType.BUY)
                .findFirst()
                .orElseThrow();
        UUID buyOrderId = infiniteBuy.getOrderId();
        assertTrue(infiniteBuy.isInfinite());
        assertTrue(data.getOrders().containsKey(buyOrderId));

        Order serverSell = manager.createServerSellOrder(iron, 1, BigDecimal.ONE);
        assertNotNull(serverSell);

        AtomicBoolean cancelledInCallback = new AtomicBoolean();
        AtomicBoolean callbackObserved = new AtomicBoolean();
        try (EconomyEvents.Subscription ignored = EconomyEvents.listen(MarketEvents.TradeCompleted.class,
                event -> {
                    callbackObserved.set(true);
                    cancelledInCallback.set(manager.cancelOrder(buyOrderId, buyer));
                })) {
            manager.matchAllPendingOrders(null);
        }

        assertTrue(callbackObserved.get(), "TradeCompleted callback must run");
        assertFalse(cancelledInCallback.get(), "TradeCompleted runs within the settlement guard");
        assertTrue(manager.getOrder(buyOrderId).isPresent());
        assertTrue(data.getOrders().containsKey(buyOrderId), "infinite BUY remains durably registered after fill");
        assertEquals(1, infiniteBuy.getQuantity(), "infinite BUY quantity is not reduced");
        assertTrue(manager.cancelOrder(buyOrderId, buyer), "cancellation succeeds once settlement returns");
        assertTrue(manager.getOrder(buyOrderId).isEmpty(), "cancelled order must leave the live book");
        assertFalse(data.getOrders().containsKey(buyOrderId),
                "outer matching code must not write a cancelled infinite order back to SavedData");

        OrderManager reloaded = new OrderManager();
        reloaded.loadFrom(data);
        assertTrue(reloaded.getOrder(buyOrderId).isEmpty(),
                "cancelled infinite BUY must stay gone after a save/reload boundary");
    }
}
