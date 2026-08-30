package com.nstut.economy.trading;

import com.nstut.economy.api.ICommodity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import com.nstut.economy.trading.EconomyFluidStack;

import java.math.BigDecimal;

public class FluidCommodity implements ICommodity {
    public static final int QUOTE_AMOUNT_MB = 1_000;
    private static final BigDecimal QUOTE_AMOUNT = BigDecimal.valueOf(QUOTE_AMOUNT_MB);
    private final Identifier id;
    private final Fluid fluid;
    private final BigDecimal basePrice;
    private final boolean dynamicPricing;

    public FluidCommodity(Identifier id, Fluid fluid, BigDecimal basePrice, boolean dynamicPricing) {
        this.id = id;
        this.fluid = fluid;
        this.basePrice = basePrice;
        this.dynamicPricing = dynamicPricing;
    }

    public FluidCommodity(Identifier id, Fluid fluid, BigDecimal basePrice) {
        this(id, fluid, basePrice, true);
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public CommodityType getType() {
        return CommodityType.FLUID;
    }

    @Override
    public Component getDisplayName() {
        return com.nstut.economy.platform.Services.FLUID.displayName(fluid);
    }

    @Override
    public BigDecimal getBasePrice() {
        return basePrice;
    }

    @Override
    public boolean hasDynamicPricing() {
        return dynamicPricing;
    }

    @Override
    public boolean canExtractFrom(IStorage storage, int amount) {
        return false;
    }

    @Override
    public boolean canInsertInto(IStorage storage, int amount) {
        return false;
    }

    @Override
    public boolean extractFrom(IStorage storage, int amount) {
        return false;
    }

    @Override
    public boolean insertInto(IStorage storage, int amount) {
        return false;
    }

    public Fluid getFluid() {
        return fluid;
    }

    public EconomyFluidStack createFluidStack(int amount) {
        return new EconomyFluidStack(fluid, amount);
    }

    /** Converts a player-facing bucket quote to the internal price for one mB. */
    public static BigDecimal pricePerMb(BigDecimal pricePerBucket) {
        return pricePerBucket.divide(QUOTE_AMOUNT);
    }

    /** Converts an internal one-mB price to the player-facing bucket quote. */
    public static BigDecimal pricePerBucket(BigDecimal pricePerMb) {
        return pricePerMb.multiply(QUOTE_AMOUNT).stripTrailingZeros();
    }

    /** Calculates the charge for an mB quantity from a player-facing bucket quote. */
    public static BigDecimal totalFromBucketQuote(BigDecimal pricePerBucket, int amountMb) {
        return pricePerMb(pricePerBucket).multiply(BigDecimal.valueOf(amountMb));
    }

    public static FluidCommodity fromFluid(Fluid fluid, BigDecimal basePrice) {
        Identifier id = BuiltInRegistries.FLUID.getKey(fluid);
        if (id == null) {
            id = com.nstut.economy.compat.Compat.rl("minecraft", "water");
        }
        return new FluidCommodity(id, fluid, basePrice);
    }

    public static FluidCommodity fromFluidId(String fluidId, BigDecimal basePrice) {
        Identifier rl = com.nstut.economy.compat.Compat.rl(fluidId);
        Fluid fluid = BuiltInRegistries.FLUID.getValue(rl);
        if (fluid == net.minecraft.world.level.material.Fluids.EMPTY) {
            return null;
        }
        return new FluidCommodity(rl, fluid, basePrice);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FluidCommodity that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return 31 * FluidCommodity.class.hashCode() + (id != null ? id.hashCode() : 0);
    }
}


