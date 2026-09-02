package com.nstut.economy.api;

import com.nstut.economy.data.EconomyOrderData;
import com.nstut.economy.test.MinecraftTestBase;
import com.nstut.economy.trading.OrderManager;
import net.minecraft.core.NonNullList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UnavailableCommodityRecoveryRegressionTest extends MinecraftTestBase {
    private static final EconomyId MISSING_TYPE = EconomyId.of("missingaddon", "commodity");
    private static final EconomyId COMMODITY = EconomyId.of("missingaddon", "token");

    @AfterEach
    void tearDown() {
        EconomyApi.commodityTypes().unregister(MISSING_TYPE);
    }

    @Test
    @DisplayName("A BUY order survives save/reload while its addon commodity codec is unavailable")
    void unreadableBuyOrderIsPreservedWithoutEscrow() {
        EconomyApi.commodityTypes().unregister(MISSING_TYPE);
        UUID orderId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        EconomyOrderData.OrderSnapshot snapshot = new EconomyOrderData.OrderSnapshot(
                orderId,
                owner,
                COMMODITY.toString(),
                7,
                7,
                "3.25",
                "BUY",
                Instant.now().toEpochMilli(),
                0L,
                false,
                NonNullList.create(),
                new ArrayList<>(),
                false,
                false,
                "CUSTOM",
                MISSING_TYPE.toString(),
                2,
                Map.of("variant", "blue"),
                null,
                Map.of());

        EconomyOrderData data = new EconomyOrderData();
        data.putOrder(snapshot);

        OrderManager first = new OrderManager();
        first.loadFrom(data);
        assertTrue(first.getAllOrders().isEmpty(), "unreadable addon orders must never become active");
        first.saveAll();

        EconomyOrderData.OrderSnapshot preserved = data.getOrders().get(orderId);
        assertNotNull(preserved, "saveAll must not delete an order solely because its codec is unavailable");
        assertEquals(MISSING_TYPE.toString(), preserved.commodityTypeId);
        assertEquals(COMMODITY.toString(), preserved.itemId);
        assertEquals(Map.of("variant", "blue"), preserved.commodityPayload);
        assertFalse(preserved.hasEscrow(), "the regression specifically covers non-escrow BUY orders");

        OrderManager second = new OrderManager();
        second.loadFrom(data);
        second.saveAll();
        assertNotNull(data.getOrders().get(orderId), "repeated saves while the addon is absent must remain lossless");
    }
}
