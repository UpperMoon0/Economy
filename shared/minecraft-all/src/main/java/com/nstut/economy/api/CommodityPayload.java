package com.nstut.economy.api;

import java.util.Map;

/** Versioned, namespaced persistence payload owned by a commodity type handler. */
public record CommodityPayload(int version, Map<String, String> values) {
    public CommodityPayload {
        if (version < 1) throw new IllegalArgumentException("version must be >= 1");
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public static CommodityPayload empty(int version) {
        return new CommodityPayload(version, Map.of());
    }
}
