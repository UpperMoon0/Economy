package com.nstut.economy.client;

import com.nstut.economy.api.EconomyId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CommoditySearchInputTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "raw ru", "Raw Ruthenium", "minecraft:", ":stone",
            "minecraft:stone:extra", "mod:raw ore", "mod:raw\tore", "mod:raw\nore",
            "mod:ore#tag", "mod:ore[variant]", "mod:ore?", "mod:ore\\path", "mod:é"})
    void freeTextAndIncompleteIdsAreNotExactLookups(String query) {
        assertNull(CommoditySearchInput.tryParseExactId(query));
    }

    @ParameterizedTest
    @ValueSource(strings = {"minecraft:stone", "minecraft:water", "tfc:ore/raw_iron",
            "mod-name:item.path-1", "mod:item/variant/" +
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"})
    void preservesValidItemFluidAndExactVariantIds(String query) {
        assertEquals(EconomyId.parse(query), CommoditySearchInput.tryParseExactId(query));
    }

    @Test
    void preservesDefaultNamespaceAndSurroundingWhitespaceHandling() {
        assertEquals(EconomyId.parse("minecraft:stone"), CommoditySearchInput.tryParseExactId("stone"));
        assertEquals(EconomyId.parse("tfc:ore/raw_iron"),
                CommoditySearchInput.tryParseExactId("  tfc:ore/raw_iron\t"));
    }

    @Test
    void typingAndDeletingFreeTextNeverThrows() {
        String query = "raw ruthenium";
        for (int length = 0; length <= query.length(); length++) {
            String prefix = query.substring(0, length);
            assertDoesNotThrow(() -> CommoditySearchInput.tryParseExactId(prefix), prefix);
        }
        for (int length = query.length(); length >= 0; length--) {
            String prefix = query.substring(0, length);
            assertDoesNotThrow(() -> CommoditySearchInput.tryParseExactId(prefix), prefix);
        }
    }

    @Test
    void invalidSearchDoesNotAffectTheNextExactLookup() {
        assertNull(CommoditySearchInput.tryParseExactId("raw ru"));
        assertNull(CommoditySearchInput.tryParseExactId("minecraft:"));
        assertEquals(EconomyId.parse("minecraft:stone"),
                CommoditySearchInput.tryParseExactId("minecraft:stone"));
    }
}
