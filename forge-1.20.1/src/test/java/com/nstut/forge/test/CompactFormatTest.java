package com.nstut.economy.test;

import com.nstut.economy.util.EconomyFormatUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class CompactFormatTest {

    @Test
    @DisplayName("Numbers less than 1000 format as exact whole integers")
    public void testSmallNumbers() {
        assertEquals("0", EconomyFormatUtil.formatCompact(0.0));
        assertEquals("123", EconomyFormatUtil.formatCompact(123.0));
        assertEquals("999", EconomyFormatUtil.formatCompact(999.0));
    }

    @Test
    @DisplayName("Numbers >= 1,000 format with 'k' suffix and up to 2 decimal places")
    public void testThousandsFormat() {
        assertEquals("1k", EconomyFormatUtil.formatCompact(1000.0));
        assertEquals("2.22k", EconomyFormatUtil.formatCompact(2220.0));
        assertEquals("14.76k", EconomyFormatUtil.formatCompact(14760.0));
    }

    @Test
    @DisplayName("Numbers >= 1,000,000 format with 'm' suffix")
    public void testMillionsFormat() {
        assertEquals("1m", EconomyFormatUtil.formatCompact(1000000.0));
        assertEquals("1.5m", EconomyFormatUtil.formatCompact(1500000.0));
        assertEquals("14.76m", EconomyFormatUtil.formatCompact(14760000.0));
    }

    @Test
    @DisplayName("Numbers >= 1,000,000,000 format with 'b' suffix")
    public void testBillionsFormat() {
        assertEquals("1b", EconomyFormatUtil.formatCompact(1000000000.0));
        assertEquals("2.5b", EconomyFormatUtil.formatCompact(2500000000.0));
    }

    @Test
    @DisplayName("Numbers >= 1,000,000,000,000 format with 't' suffix")
    public void testTrillionsFormat() {
        assertEquals("1t", EconomyFormatUtil.formatCompact(1000000000000.0));
        assertEquals("14.76t", EconomyFormatUtil.formatCompact(14760000000000.0));
    }

    @Test
    @DisplayName("Overloads for BigDecimal, long, and String parse correctly")
    public void testOverloads() {
        assertEquals("2.22k", EconomyFormatUtil.formatCompact(new BigDecimal("2220.00")));
        assertEquals("14.76k", EconomyFormatUtil.formatCompact(14760L));
        assertEquals("14.76k", EconomyFormatUtil.formatCompact("14760"));
        assertEquals("0", EconomyFormatUtil.formatCompact("--"));
    }

    @Test
    @DisplayName("Compact formatting handles negatives and invalid floating-point values")
    public void testEdgeCases() {
        assertEquals("-1.5k", EconomyFormatUtil.formatCompact(-1500));
        assertEquals("0", EconomyFormatUtil.formatCompact(Double.NaN));
        assertEquals("0", EconomyFormatUtil.formatCompact(Double.POSITIVE_INFINITY));
        assertEquals("not-a-number", EconomyFormatUtil.formatCompact("not-a-number"));
    }

    @Test
    @DisplayName("Fluid and item quantities share compact formatting and keep unit spacing")
    public void testCommodityQuantities() {
        assertEquals("1k mB", EconomyFormatUtil.formatFluidAmount(1000));
        assertEquals("1.5m mB", EconomyFormatUtil.formatCommodityQuantity(1_500_000, true));
        assertEquals("1 item", EconomyFormatUtil.formatItemAmount(1));
        assertEquals("2k items", EconomyFormatUtil.formatCommodityQuantity(2000, false));
        assertEquals("2.15b orders", EconomyFormatUtil.formatCount(Integer.MAX_VALUE, "order", "orders"));
    }
}
