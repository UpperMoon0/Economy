package com.nstut.economy.api;

import net.minecraft.network.chat.Component;

import java.math.BigDecimal;

/** Represents a tradeable commodity in the Economy market. */
public interface ICommodity {
    EconomyId ITEM_TYPE = EconomyId.of("economy", "item");
    EconomyId FLUID_TYPE = EconomyId.of("economy", "fluid");
    EconomyId ENERGY_TYPE = EconomyId.of("economy", "energy");

    /** Stable commodity identity independent of Minecraft identifier class names. */
    EconomyId getId();

    /** Legacy broad category. Custom addons should also override {@link #getTypeId()}. */
    CommodityType getType();

    /** Namespaced handler/codec type used for extensible behavior and persistence. */
    default EconomyId getTypeId() {
        return switch (getType()) {
            case ITEM -> ITEM_TYPE;
            case FLUID -> FLUID_TYPE;
            case ENERGY -> ENERGY_TYPE;
            case CUSTOM -> EconomyId.of("economy", "custom");
        };
    }

    Component getDisplayName();
    BigDecimal getBasePrice();
    boolean hasDynamicPricing();

    /** Legacy direct storage hooks retained for source compatibility. */
    boolean canExtractFrom(IStorage storage, int amount);
    boolean canInsertInto(IStorage storage, int amount);
    boolean extractFrom(IStorage storage, int amount);
    boolean insertInto(IStorage storage, int amount);

    enum CommodityType {
        ITEM,
        FLUID,
        ENERGY,
        CUSTOM
    }

    /** @deprecated Use registered IStorageProvider integrations instead. */
    @Deprecated
    interface IStorage { }
}
