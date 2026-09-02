package com.nstut.economy.api;

import java.util.Objects;

/** Stable full identity for a market commodity. */
public record CommodityKey(EconomyId commodityTypeId, EconomyId commodityId) {
    public CommodityKey {
        Objects.requireNonNull(commodityTypeId, "commodityTypeId");
        Objects.requireNonNull(commodityId, "commodityId");
    }

    public static CommodityKey of(ICommodity commodity) {
        Objects.requireNonNull(commodity, "commodity");
        return new CommodityKey(commodity.getTypeId(), commodity.getId());
    }

    public boolean matches(ICommodity commodity) {
        return commodity != null
                && commodityTypeId.equals(commodity.getTypeId())
                && commodityId.equals(commodity.getId());
    }
}
