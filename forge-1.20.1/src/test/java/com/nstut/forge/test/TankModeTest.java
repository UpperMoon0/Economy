package com.nstut.forge.test;

import com.nstut.economy.blocks.TankBlockEntity.TankMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TankModeTest {
    @Test
    @DisplayName("Tank modes cycle through BOTH, INPUT, OUTPUT, and back")
    void cyclesAndFallsBackSafely() {
        assertEquals(TankMode.INPUT, TankMode.byId(TankMode.BOTH.id + 1));
        assertEquals(TankMode.OUTPUT, TankMode.byId(TankMode.INPUT.id + 1));
        assertEquals(TankMode.BOTH, TankMode.byId(TankMode.OUTPUT.id + 1));
        assertEquals(TankMode.BOTH, TankMode.byId(-1));
        assertEquals(TankMode.BOTH, TankMode.byId(999));
    }

    @Test
    @DisplayName("Tank modes route sell availability and buy capacity correctly")
    void routesMarketFluidCorrectly() {
        assertTrue(TankMode.BOTH.canSupplyMarket());
        assertTrue(TankMode.BOTH.canReceiveMarket());
        assertTrue(TankMode.INPUT.canSupplyMarket());
        assertFalse(TankMode.INPUT.canReceiveMarket());
        assertFalse(TankMode.OUTPUT.canSupplyMarket());
        assertTrue(TankMode.OUTPUT.canReceiveMarket());
    }
}
