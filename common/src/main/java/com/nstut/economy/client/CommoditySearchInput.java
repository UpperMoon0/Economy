package com.nstut.economy.client;

import com.nstut.economy.api.EconomyId;

/** Separates optional exact-ID lookups from unrestricted item-name search text. */
public final class CommoditySearchInput {
    private CommoditySearchInput() {}

    /**
     * Returns a canonical ID only when the query satisfies Economy's ID rules.
     * Validate before Minecraft parsing: mod packs can relax the platform's ID
     * validation, but those IDs still cannot be used by Economy's API.
     */
    public static EconomyId tryParseExactId(String query) {
        if (query == null || query.isBlank()) return null;
        try {
            return EconomyId.parse(query.trim());
        } catch (IllegalArgumentException ignored) {
            // Free text and partially typed IDs remain ordinary search queries.
            return null;
        }
    }
}
