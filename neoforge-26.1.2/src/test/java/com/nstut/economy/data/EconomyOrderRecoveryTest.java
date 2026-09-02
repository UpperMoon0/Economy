package com.nstut.economy.data;

import com.nstut.economy.api.EconomyApi;
import com.nstut.economy.api.EconomyId;
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

class EconomyOrderRecoveryTest {
    private static final EconomyId MISSING_TYPE = EconomyId.of("missingaddon", "commodity");
    private static final EconomyId COMMODITY = EconomyId.of("missingaddon", "token");

    @AfterEach
    void tearDown() {
        EconomyApi.commodityTypes().unregister(MISSING_TYPE);
    }

    @Test
    @DisplayName("26.1.2 preserves a BUY order across repeated saves while its addon codec is unavailable")
    void missingCodecBuyOrderSurvivesLoadSaveCycles() {
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
        assertNotNull(preserved, "26.1.2 must not erase a BUY order solely because its codec is unavailable");
        assertEquals(MISSING_TYPE.toString(), preserved.commodityTypeId);
        assertEquals(COMMODITY.toString(), preserved.itemId);
        assertEquals(Map.of("variant", "blue"), preserved.commodityPayload);
        assertTrue(preserved.reservedItems.isEmpty());
        assertTrue(preserved.reservedFluids.isEmpty());
        assertNull(preserved.externalReservation);

        OrderManager second = new OrderManager();
        second.loadFrom(data);
        assertTrue(second.getAllOrders().isEmpty());
        second.saveAll();
        assertNotNull(data.getOrders().get(orderId), "repeated 26.1.2 saves while the addon is absent must remain lossless");
    }

    @Test
    @DisplayName("26.1.2 does not retain ordinary empty built-in snapshots as recovery state")
    void builtInSnapshotsDoNotGainSyntheticRecoveryState() {
        assertFalse(snapshot("economy:item", "ITEM").hasEscrow());
        assertFalse(snapshot("economy:fluid", "FLUID").hasEscrow());
    }

    private static EconomyOrderData.OrderSnapshot snapshot(String commodityTypeId, String legacyType) {
        return new EconomyOrderData.OrderSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "minecraft:stone",
                1,
                1,
                "1",
                "BUY",
                Instant.now().toEpochMilli(),
                0L,
                false,
                NonNullList.create(),
                new ArrayList<>(),
                false,
                false,
                legacyType,
                commodityTypeId,
                1,
                Map.of(),
                null,
                Map.of());
    }
}
