package com.nstut.economy.trading;

import com.nstut.economy.api.ICommodity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.math.BigDecimal;

public class FluidCommodity implements ICommodity {
    private final ResourceLocation id;
    private final Fluid fluid;
    private final BigDecimal basePrice;
    private final boolean dynamicPricing;

    public FluidCommodity(ResourceLocation id, Fluid fluid, BigDecimal basePrice, boolean dynamicPricing) {
        this.id = id;
        this.fluid = fluid;
        this.basePrice = basePrice;
        this.dynamicPricing = dynamicPricing;
    }

    public FluidCommodity(ResourceLocation id, Fluid fluid, BigDecimal basePrice) {
        this(id, fluid, basePrice, true);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public CommodityType getType() {
        return CommodityType.FLUID;
    }

    @Override
    public Component getDisplayName() {
        return new FluidStack(fluid, 1000).getDisplayName();
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

    public FluidStack createFluidStack(int amount) {
        return new FluidStack(fluid, amount);
    }

    public static FluidCommodity fromFluid(Fluid fluid, BigDecimal basePrice) {
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        if (id == null) {
            id = new ResourceLocation("minecraft", "water");
        }
        return new FluidCommodity(id, fluid, basePrice);
    }

    public static FluidCommodity fromFluidId(String fluidId, BigDecimal basePrice) {
        ResourceLocation rl = new ResourceLocation(fluidId);
        Fluid fluid = BuiltInRegistries.FLUID.get(rl);
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
