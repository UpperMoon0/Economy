package com.nstut.economy.trading;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Loader-neutral fluid stack value type. Replaces the Forge-only FluidStack so
 * tank/market logic compiles and behaves identically on Fabric, Forge and
 * NeoForge across every supported Minecraft version.
 */
public class EconomyFluidStack {

    public static final EconomyFluidStack EMPTY = new EconomyFluidStack(Fluids.EMPTY, 0);

    private Fluid fluid;
    private int amount;

    public EconomyFluidStack(Fluid fluid, int amount) {
        this.fluid = fluid == null ? Fluids.EMPTY : fluid;
        this.amount = Math.max(0, amount);
    }

    public Fluid getFluid() {
        return fluid == null ? Fluids.EMPTY : fluid;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(0, amount);
    }

    public void grow(int amount) {
        this.amount += amount;
    }

    public void shrink(int amount) {
        this.amount = Math.max(0, this.amount - amount);
    }

    public boolean isEmpty() {
        return amount <= 0 || getFluid() == Fluids.EMPTY;
    }

    public boolean isFluidEqual(EconomyFluidStack other) {
        if (other == null) return false;
        if (isEmpty() || other.isEmpty()) return isEmpty() && other.isEmpty();
        return getFluid() == other.getFluid();
    }

    public EconomyFluidStack copy() {
        return new EconomyFluidStack(getFluid(), amount);
    }

    public void writeTo(CompoundTag tag) {
        Identifier id = BuiltInRegistries.FLUID.getKey(getFluid());
        tag.putString("FluidId", id.toString());
        tag.putInt("Amount", amount);
    }

    public CompoundTag writeToNewNBT() {
        CompoundTag tag = new CompoundTag();
        writeTo(tag);
        return tag;
    }

    public static EconomyFluidStack readFrom(CompoundTag tag) {
        if (tag == null) {
            return EMPTY;
        }
        // New format written by this class.
        if (tag.contains("FluidId")) {
            Identifier id = Identifier.tryParse(tag.getStringOr("FluidId", ""));
            if (id == null) {
                return EMPTY;
            }
            return new EconomyFluidStack(BuiltInRegistries.FLUID.getValue(id), tag.getIntOr("Amount", 0));
        }
        // Back-compat with worlds saved by the Forge-only FluidStack format
        // ({FluidName: "minecraft:water", Amount: 1000}).
        if (tag.contains("FluidName")) {
            Identifier id = Identifier.tryParse(tag.getStringOr("FluidName", ""));
            if (id == null) {
                return EMPTY;
            }
            return new EconomyFluidStack(BuiltInRegistries.FLUID.getValue(id), tag.getIntOr("Amount", 0));
        }
        return EMPTY;
    }

    public static EconomyFluidStack loadFluidStackFromNBT(CompoundTag tag) {
        return readFrom(tag);
    }
}
