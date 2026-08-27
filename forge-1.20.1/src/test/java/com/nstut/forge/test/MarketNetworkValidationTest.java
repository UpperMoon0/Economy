package com.nstut.economy.test;

import com.nstut.economy.util.OrderInputValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MarketNetworkValidationTest extends MinecraftTestBase {

    @Test
    @DisplayName("Commodity ids must parse as resource locations")
    void rejectsMalformedCommodityIds() {
        assertNotNull(OrderInputValidator.parseCommodityId("minecraft:diamond"));
        assertNull(OrderInputValidator.parseCommodityId(null));
        assertNull(OrderInputValidator.parseCommodityId(""));
        assertNull(OrderInputValidator.parseCommodityId("!!not-a-location"));
        assertNull(OrderInputValidator.parseCommodityId("minecraft::diamond"));
        assertNull(OrderInputValidator.parseCommodityId("a:".repeat(200)));
    }

    @Test
    @DisplayName("Prices must be positive, bounded, and reasonably scaled")
    void rejectsHostilePrices() {
        assertEquals(new BigDecimal("2.5"), OrderInputValidator.parsePrice("2.5"));
        assertEquals(new BigDecimal("10"), OrderInputValidator.parsePrice(" 10 "));

        assertNull(OrderInputValidator.parsePrice(null));
        assertNull(OrderInputValidator.parsePrice(""));
        assertNull(OrderInputValidator.parsePrice("abc"));
        assertNull(OrderInputValidator.parsePrice("0"));
        assertNull(OrderInputValidator.parsePrice("-1"));
        assertNull(OrderInputValidator.parsePrice("0.001"), "below configured min price");
        assertNull(OrderInputValidator.parsePrice("99999999"), "above configured max price");
        assertNull(OrderInputValidator.parsePrice("1.000001"), "excessive scale");
        assertNull(OrderInputValidator.parsePrice("1e999999999"), "absurd precision");
    }

    @Test
    @DisplayName("Quantities must be positive and within the configured cap")
    void boundsQuantities() {
        assertTrue(OrderInputValidator.isValidQuantity(1));
        assertTrue(OrderInputValidator.isValidQuantity(64));
        assertFalse(OrderInputValidator.isValidQuantity(0));
        assertFalse(OrderInputValidator.isValidQuantity(-5));
        assertFalse(OrderInputValidator.isValidQuantity(Integer.MAX_VALUE));
    }
}
