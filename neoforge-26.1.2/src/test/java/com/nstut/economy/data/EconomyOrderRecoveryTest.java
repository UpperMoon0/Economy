package com.nstut.economy.data;

import net.minecraft.core.NonNullList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyOrderRecoveryTest {

    @Test
    @DisplayName("26.1.2 preserves custom orders while their addon codec is unavailable")
    void customCommoditySnapshotsAreRecoveryStateWithoutEscrow() {
        EconomyOrderData.OrderSnapshot snapshot = snapshot("missingaddon:commodity", "CUSTOM");

        assertTrue(snapshot.hasEscrow(),
                "custom codec state must keep the snapshot in the recovery set even when BUY escrow is empty");
    }

    @Test
    @DisplayName("26.1.2 does not retain ordinary empty built-in snapshots as escrow")
    void builtInSnapshotsDoNotGainSyntheticRecoveryState() {
        assertFalse(snapshot("economy:item", "ITEM").hasEscrow());
        assertFalse(snapshot("economy:fluid", "FLUID").hasEscrow());
    }

    private static EconomyOrderData.OrderSnapshot snapshot(String commodityTypeId, String legacyType) {
        return new EconomyOrderData.OrderSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "missingaddon:token",
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
                legacyType,
                commodityTypeId,
                2,
                Map.of("variant", "blue"),
                null,
                Map.of());
    }
}
