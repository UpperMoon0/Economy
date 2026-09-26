package com.nstut.economy.trading;

import com.nstut.Economy;
import com.nstut.economy.api.AccountRef;
import com.nstut.economy.api.IOrder;
import com.nstut.economy.data.EconomyOrderData;
import com.nstut.economy.test.MinecraftTestBase;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CompensationRecoveryTest extends MinecraftTestBase {

    @BeforeEach
    void setUp() {
        Economy.ensureApiRegistrations();
    }

    @Test
    @DisplayName("Debt-only compensation quarantine survives save and reload")
    void debtOnlyCompensationSurvivesReload() {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        ItemCommodity commodity = new ItemCommodity(new ResourceLocation("minecraft", "iron_ingot"),
                Items.IRON_INGOT, BigDecimal.ZERO);
        Order order = new Order(buyer, commodity, 10, new BigDecimal("2"), IOrder.OrderType.BUY, null);
        BigDecimal debt = new BigDecimal("8");

        order.quarantineCompensation(seller, buyer, debt, "partial BUY refund failed");
        assertFalse(order.isValid());
        assertTrue(order.hasCompensationDue());

        EconomyOrderData data = new EconomyOrderData();
        data.putOrder(order.toSnapshot());

        OrderManager first = new OrderManager();
        first.loadFrom(data);
        assertTrue(first.getOrder(order.getOrderId()).isEmpty(), "recovery orders must never reactivate");
        first.saveAll();

        EconomyOrderData.OrderSnapshot preserved = data.getOrders().get(order.getOrderId());
        assertNotNull(preserved, "debt-only recovery state must remain durable without item/provider escrow");
        assertTrue(preserved.reservedItems.isEmpty());
        assertTrue(preserved.reservedFluids.isEmpty());
        assertNull(preserved.externalReservation);
        assertEquals(com.nstut.economy.api.AccountRef.player(seller).toString(), preserved.addonMetadata.get("economy:compensation_debtor"));
        assertEquals(com.nstut.economy.api.AccountRef.player(buyer).toString(), preserved.addonMetadata.get("economy:compensation_creditor"));
        assertEquals("8", preserved.addonMetadata.get("economy:compensation_amount"));
        assertEquals("partial BUY refund failed", preserved.addonMetadata.get("economy:quarantine_reason"));

        OrderManager second = new OrderManager();
        second.loadFrom(data);
        assertTrue(second.getOrder(order.getOrderId()).isEmpty());
        second.saveAll();
        assertEquals("8", data.getOrders().get(order.getOrderId())
                .addonMetadata.get("economy:compensation_amount"));
    }
    @Test
    @DisplayName("Team-principal compensation debt preserves typed debtor and creditor across reload")
    void teamPrincipalCompensationSurvivesReload() {
        UUID actor = UUID.randomUUID();
        UUID team = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        ItemCommodity commodity = new ItemCommodity(new ResourceLocation("minecraft", "iron_ingot"),
                Items.IRON_INGOT, BigDecimal.ZERO);
        Order order = new Order(actor, commodity, 3, new BigDecimal("4"), IOrder.OrderType.SELL, null);
        order.quarantineCompensation(AccountRef.team(team), AccountRef.player(buyer), new BigDecimal("12"),
                "team SELL refund failed");

        EconomyOrderData data = new EconomyOrderData();
        data.putOrder(order.toSnapshot());
        OrderManager manager = new OrderManager();
        manager.loadFrom(data);
        manager.saveAll();

        EconomyOrderData.OrderSnapshot preserved = data.getOrders().get(order.getOrderId());
        assertNotNull(preserved);
        assertEquals(AccountRef.team(team).toString(), preserved.addonMetadata.get("economy:compensation_debtor"));
        assertEquals(AccountRef.player(buyer).toString(), preserved.addonMetadata.get("economy:compensation_creditor"));
        assertEquals("12", preserved.addonMetadata.get("economy:compensation_amount"));
        assertEquals("team SELL refund failed", preserved.addonMetadata.get("economy:quarantine_reason"));
    }

}
