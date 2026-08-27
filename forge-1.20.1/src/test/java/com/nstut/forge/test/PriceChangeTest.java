package com.nstut.economy.test;

import com.nstut.economy.util.EconomyFormatUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PriceChangeTest {

    @Test
    @DisplayName("Price increases format with + sign and green color code")
    public void testPriceIncrease() {
        double change = 400.0;
        assertEquals("+400.00%", EconomyFormatUtil.formatPriceChange(change));
        assertEquals(0xFF66FF66, EconomyFormatUtil.getPriceChangeColor(change));
    }

    @Test
    @DisplayName("Price drops format with - sign and red color code")
    public void testPriceDrop() {
        double change = -15.5;
        assertEquals("-15.50%", EconomyFormatUtil.formatPriceChange(change));
        assertEquals(0xFFFF6666, EconomyFormatUtil.getPriceChangeColor(change));
    }

    @Test
    @DisplayName("Double.NaN returns 'No Change' and gray color code when no prior distinct price exists")
    public void testNoChange() {
        double change = Double.NaN;
        assertEquals("No Change", EconomyFormatUtil.formatPriceChange(change));
        assertEquals(0xFF9E9E9E, EconomyFormatUtil.getPriceChangeColor(change));
    }
}
