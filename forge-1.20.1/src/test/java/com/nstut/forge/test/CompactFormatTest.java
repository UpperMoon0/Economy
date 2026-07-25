package com.nstut.forge.test;

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
        assertEquals("1 k", EconomyFormatUtil.formatCompact(1000.0));
        assertEquals("2.22 k", EconomyFormatUtil.formatCompact(2220.0));
        assertEquals("14.76 k", EconomyFormatUtil.formatCompact(14760.0));
    }

    @Test
    @DisplayName("Numbers >= 1,000,000 format with 'm' suffix")
    public void testMillionsFormat() {
        assertEquals("1 m", EconomyFormatUtil.formatCompact(1000000.0));
        assertEquals("1.5 m", EconomyFormatUtil.formatCompact(1500000.0));
        assertEquals("14.76 m", EconomyFormatUtil.formatCompact(14760000.0));
    }

    @Test
    @DisplayName("Numbers >= 1,000,000,000 format with 'b' suffix")
    public void testBillionsFormat() {
        assertEquals("1 b", EconomyFormatUtil.formatCompact(1000000000.0));
        assertEquals("2.5 b", EconomyFormatUtil.formatCompact(2500000000.0));
    }

    @Test
    @DisplayName("Numbers >= 1,000,000,000,000 format with 't' suffix")
    public void testTrillionsFormat() {
        assertEquals("1 t", EconomyFormatUtil.formatCompact(1000000000000.0));
        assertEquals("14.76 t", EconomyFormatUtil.formatCompact(14760000000000.0));
    }

    @Test
    @DisplayName("Overloads for BigDecimal, long, and String parse correctly")
    public void testOverloads() {
        assertEquals("2.22 k", EconomyFormatUtil.formatCompact(new BigDecimal("2220.00")));
        assertEquals("14.76 k", EconomyFormatUtil.formatCompact(14760L));
        assertEquals("14.76 k", EconomyFormatUtil.formatCompact("14760"));
        assertEquals("0", EconomyFormatUtil.formatCompact("--"));
    }
}
