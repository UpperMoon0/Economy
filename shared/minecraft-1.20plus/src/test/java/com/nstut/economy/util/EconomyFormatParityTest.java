package com.nstut.economy.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Shared format contract compiled against every 1.20.1 and 1.21.1 common module. */
class EconomyFormatParityTest {
    @Test
    void compactNumbersRemainStableAcrossVersionFamilies() {
        assertEquals("0", EconomyFormatUtil.formatCompact(Double.NaN));
        assertEquals("0", EconomyFormatUtil.formatCompact(Double.POSITIVE_INFINITY));
        assertEquals("999", EconomyFormatUtil.formatCompact(999));
        assertEquals("1k", EconomyFormatUtil.formatCompact(1_000));
        assertEquals("1.5m", EconomyFormatUtil.formatCompact(new BigDecimal("1500000")));
        assertEquals("2.5b", EconomyFormatUtil.formatCompact("2500000000"));
        assertEquals("3t", EconomyFormatUtil.formatCompact(3_000_000_000_000L));
    }

    @Test
    void commodityUnitsAndPriceChangesRemainUnambiguous() {
        assertEquals("1 item", EconomyFormatUtil.formatCommodityQuantity(1, false));
        assertEquals("2 items", EconomyFormatUtil.formatCommodityQuantity(2, false));
        assertEquals("16k mB", EconomyFormatUtil.formatCommodityQuantity(16_000, true));
        assertEquals("No Change", EconomyFormatUtil.formatPriceChange(Double.NaN));
        assertEquals("+12.50%", EconomyFormatUtil.formatPriceChange(12.5));
        assertEquals("-12.50%", EconomyFormatUtil.formatPriceChange(-12.5));
    }
}
