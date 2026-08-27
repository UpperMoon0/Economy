package com.nstut.economy.trading;

import com.nstut.economy.test.MinecraftTestBase;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderManagerTest extends MinecraftTestBase {
    @Test
    @DisplayName("Server orders support item and fluid commodities through the same manager")
    void createsServerOrdersForBothCommodityTypes() {
        OrderManager manager = new OrderManager();
        ItemCommodity iron = new ItemCommodity(new ResourceLocation("minecraft", "iron_ingot"),
                Items.IRON_INGOT, BigDecimal.ZERO);
        FluidCommodity water = new FluidCommodity(new ResourceLocation("minecraft", "water"),
                Fluids.WATER, BigDecimal.ZERO);

        Order itemOrder = manager.createServerSellOrder(iron, 64, new BigDecimal("12"));
        Order fluidOrder = manager.createServerBuyOrder(water, 16_000, new BigDecimal("2"));

        assertTrue(itemOrder.isServerOrder());
        assertTrue(fluidOrder.isServerOrder());
        assertEquals(64, itemOrder.getQuantity());
        assertEquals(16_000, fluidOrder.getQuantity());
        assertEquals(2, manager.getAllOrders().size());
    }

    @Test
    @DisplayName("Sell books sort low-to-high and buy books high-to-low")
    void sortsOrderBooksByBestPrice() {
        OrderManager manager = new OrderManager();
        ItemCommodity diamond = new ItemCommodity(new ResourceLocation("minecraft", "diamond"),
                Items.DIAMOND, BigDecimal.ZERO);

        manager.createServerSellOrder(diamond, 1, new BigDecimal("30"));
        manager.createServerSellOrder(diamond, 1, new BigDecimal("10"));
        manager.createServerBuyOrder(diamond, 1, new BigDecimal("5"));
        manager.createServerBuyOrder(diamond, 1, new BigDecimal("20"));

        assertEquals(new BigDecimal("10"), manager.getSellOrders(diamond).get(0).getPricePerUnit());
        assertEquals(new BigDecimal("20"), manager.getBuyOrders(diamond).get(0).getPricePerUnit());
    }

    @Test
    @DisplayName("Only an order owner can cancel an order")
    void enforcesCancellationOwnership() {
        OrderManager manager = new OrderManager();
        ItemCommodity iron = new ItemCommodity(new ResourceLocation("minecraft", "iron_ingot"),
                Items.IRON_INGOT, BigDecimal.ZERO);
        UUID owner = UUID.randomUUID();
        Order order = manager.createSellOrder(owner, iron, 4, BigDecimal.ONE);

        assertFalse(manager.cancelOrder(order.getOrderId(), UUID.randomUUID()));
        assertTrue(manager.cancelOrder(order.getOrderId(), owner));
        assertTrue(manager.getOrder(order.getOrderId()).isEmpty());
    }
}
