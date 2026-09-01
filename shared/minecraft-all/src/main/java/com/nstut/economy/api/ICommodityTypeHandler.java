package com.nstut.economy.api;

/**
 * Registered behavior/codec for one namespaced commodity type. Built-in item
 * and fluid commodities use the same registration path as addon types.
 */
public interface ICommodityTypeHandler {
    EconomyId id();
    int currentSchemaVersion();
    boolean supports(ICommodity commodity);
    CommodityPayload encode(ICommodity commodity);
    ICommodity decode(EconomyId commodityId, CommodityPayload payload);

    /** Whether quantity is conventionally displayed as a fluid-like amount (mB). */
    default boolean fluidLike() { return false; }
}
