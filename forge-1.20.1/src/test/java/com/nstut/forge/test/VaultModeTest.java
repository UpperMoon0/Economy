package com.nstut.economy.test;

import com.nstut.economy.blocks.VaultBlockEntity.VaultMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VaultModeTest {

    @Test
    @DisplayName("VaultMode cycling transitions correctly through BOTH -> INPUT -> OUTPUT -> BOTH")
    public void testVaultModeCycling() {
        VaultMode mode = VaultMode.BOTH;
        assertEquals(0, mode.id);

        mode = VaultMode.byId(mode.id + 1);
        assertEquals(VaultMode.INPUT, mode);
        assertEquals(1, mode.id);

        mode = VaultMode.byId(mode.id + 1);
        assertEquals(VaultMode.OUTPUT, mode);
        assertEquals(2, mode.id);

        mode = VaultMode.byId(mode.id + 1);
        assertEquals(VaultMode.BOTH, mode);
        assertEquals(0, mode.id);
    }

    @Test
    @DisplayName("VaultMode.byId returns BOTH as default fallback for invalid IDs")
    public void testVaultModeFallback() {
        assertEquals(VaultMode.BOTH, VaultMode.byId(-1));
        assertEquals(VaultMode.BOTH, VaultMode.byId(999));
    }

    @Test
    @DisplayName("Vault modes route market supply and purchased-item delivery correctly")
    public void testVaultModeRouting() {
        assertTrue(VaultMode.BOTH.canSupplyMarket());
        assertTrue(VaultMode.BOTH.canReceiveMarket());
        assertTrue(VaultMode.INPUT.canSupplyMarket());
        assertFalse(VaultMode.INPUT.canReceiveMarket());
        assertFalse(VaultMode.OUTPUT.canSupplyMarket());
        assertTrue(VaultMode.OUTPUT.canReceiveMarket());
    }
}
