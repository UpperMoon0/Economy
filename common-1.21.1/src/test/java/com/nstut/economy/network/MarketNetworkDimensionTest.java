package com.nstut.economy.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MarketNetworkDimensionTest {
    @Test
    void malformedClientDimensionIsRejectedWithoutThrowing() {
        assertNull(assertDoesNotThrow(() -> MarketNetwork.tryParseDimensionId("minecraft:bad path")));
        assertNull(assertDoesNotThrow(() -> MarketNetwork.tryParseDimensionId("not a valid id")));
        assertNull(assertDoesNotThrow(() -> MarketNetwork.tryParseDimensionId(null)));
        assertNotNull(MarketNetwork.tryParseDimensionId("minecraft:overworld"));
    }
}
