package com.nstut.economy.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable completed-trade view exposed to addons. */
public record TradeView(
        EconomyId commodityId,
        EconomyId commodityTypeId,
        BigDecimal pricePerUnit,
        int quantity,
        UUID buyer,
        UUID seller,
        Instant timestamp,
        MarketIdentity buyerIdentity,
        MarketIdentity sellerIdentity
) {
    public TradeView(EconomyId commodityId, EconomyId commodityTypeId, BigDecimal pricePerUnit,
                     int quantity, UUID buyer, UUID seller, Instant timestamp) {
        this(commodityId, commodityTypeId, pricePerUnit, quantity, buyer, seller, timestamp,
                MarketIdentity.personal(buyer), MarketIdentity.personal(seller));
    }
    public TradeView {
        Objects.requireNonNull(commodityId, "commodityId");
        Objects.requireNonNull(commodityTypeId, "commodityTypeId");
        Objects.requireNonNull(pricePerUnit, "pricePerUnit");
        Objects.requireNonNull(buyer, "buyer");
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
