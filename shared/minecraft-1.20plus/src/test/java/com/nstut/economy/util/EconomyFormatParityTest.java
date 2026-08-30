package com.nstut.economy.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals("0.01", EconomyFormatUtil.formatMoneyCompact(new BigDecimal("0.01")));
        assertEquals("0.25", EconomyFormatUtil.formatMoneyCompact(new BigDecimal("0.25")));
        assertEquals("1.25k", EconomyFormatUtil.formatMoneyCompact(new BigDecimal("1250")));
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

    @Test
    void moneyFormattingKeepsMicroValuesWithoutNoisyTrailingZeros() {
        assertEquals("0.00001", CoinText.formatMoney(new BigDecimal("0.00001")));
        assertEquals("0.005", CoinText.formatMoney(new BigDecimal("0.005000")));
        assertEquals("42", CoinText.formatMoney(new BigDecimal("42.000000")));
    }

    @Test
    void chartScalingPreservesSmallFractionalPriceMovement() {
        double graphRange = EconomyFormatUtil.chartGraphRange(0.01, 0.02);

        assertTrue(graphRange > 0.01, "chart padding should preserve the data range");
        assertTrue(graphRange < 0.1, "fractional prices must not be forced into a one-coin range");
    }
}
