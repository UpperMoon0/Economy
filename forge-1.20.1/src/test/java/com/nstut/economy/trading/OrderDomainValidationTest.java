package com.nstut.economy.trading;

import com.nstut.economy.api.IOrder;
import com.nstut.forge.test.MinecraftTestBase;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderDomainValidationTest extends MinecraftTestBase {

    private final OrderManager manager = new OrderManager();
    private final ItemCommodity iron = new ItemCommodity(
            new ResourceLocation("minecraft", "iron_ingot"), Items.IRON_INGOT, BigDecimal.ZERO);
    private final UUID owner = UUID.randomUUID();

    @Test
    @DisplayName("Zero and negative prices are rejected at creation, not just in packets")
    void rejectsNonPositivePrices() {
        assertNull(manager.createSellOrder(owner, iron, 4, BigDecimal.ZERO));
        assertNull(manager.createSellOrder(owner, iron, 4, new BigDecimal("-5")));
        assertNull(manager.createBuyOrder(owner, iron, 4, BigDecimal.ZERO));
        assertNull(manager.createBuyOrder(owner, iron, 4, new BigDecimal("-1")));
        assertTrue(manager.getAllOrders().isEmpty());
    }

    @Test
    @DisplayName("Out-of-range quantities are rejected at creation")
    void rejectsInvalidQuantities() {
        assertNull(manager.createSellOrder(owner, iron, 0, BigDecimal.ONE));
        assertNull(manager.createSellOrder(owner, iron, -3, BigDecimal.ONE));
        assertNull(manager.createBuyOrder(owner, iron, Integer.MAX_VALUE, BigDecimal.ONE));
        assertTrue(manager.getAllOrders().isEmpty());
    }

    @Test
    @DisplayName("Editing an order to a zero price is refused")
    void editRejectsInvalidPrice() {
        Order order = manager.createSellOrder(owner, iron, 4, BigDecimal.ONE);
        assertNotNull(order);

        assertFalse(manager.editOrder(order.getOrderId(), owner, 4, BigDecimal.ZERO, false, null));
        assertFalse(manager.editOrder(order.getOrderId(), owner, 4, new BigDecimal("-2"), false, null));

        assertTrue(manager.getOrder(order.getOrderId()).isPresent());
        assertEquals(0, BigDecimal.ONE.compareTo(manager.getOrder(order.getOrderId()).get().getPricePerUnit()));
    }

    @Test
    @DisplayName("Orders with invalid prices never become matchable")
    void invalidPriceOrdersAreNotValid() {
        Order zeroPrice = new Order(owner, iron, 4, 4, BigDecimal.ZERO,
                IOrder.OrderType.BUY, null, net.minecraft.core.NonNullList.create(), false);
        assertFalse(zeroPrice.isValid());
    }
}
