package com.nstut.economy.api;

import com.nstut.Economy;
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
    @BeforeEach
    void setUp() {
        EconomyEvents.clearListeners();
        TradeLedger.setTradeData(new EconomyTradeData());
    }

    @AfterEach
    void tearDown() {
        EconomyEvents.clearListeners();
        TradeLedger.clearTradeData();
    }

    @Test
    @DisplayName("Cancelling an infinite BUY from TradeCompleted cannot resurrect it from SavedData")
    void tradeCompletedCancellationDoesNotResurrectInfiniteBuy() {
        ItemCommodity iron = new ItemCommodity(
                new ResourceLocation("minecraft", "iron_ingot"), Items.IRON_INGOT, BigDecimal.ZERO);
        UUID buyer = UUID.randomUUID();

        assertTrue(Economy.getAccountManager().getOrCreatePlayerAccount(buyer)
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
        try (EconomyEvents.Subscription ignored = EconomyEvents.listen(MarketEvents.TradeCompleted.class,
                event -> cancelledInCallback.set(manager.cancelOrder(buyOrderId, buyer)))) {
            manager.matchAllPendingOrders(null);
        }

        assertTrue(cancelledInCallback.get(), "TradeCompleted listener must cancel the infinite BUY");
        assertTrue(manager.getOrder(buyOrderId).isEmpty(), "cancelled order must leave the live book");
        assertFalse(data.getOrders().containsKey(buyOrderId),
                "outer matching code must not write a cancelled infinite order back to SavedData");

        OrderManager reloaded = new OrderManager();
        reloaded.loadFrom(data);
        assertTrue(reloaded.getOrder(buyOrderId).isEmpty(),
                "cancelled infinite BUY must stay gone after a save/reload boundary");
    }
}
