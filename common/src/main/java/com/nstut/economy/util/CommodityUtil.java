package com.nstut.economy.util;

import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public final class CommodityUtil {
    private CommodityUtil() {}

    /**
     * Returns whether this registry entry is the canonical, storable form of a fluid.
     * FlowingFluid registers both source and flowing entries; only the source entry
     * represents the fluid carried by tanks, buckets, cells, and other containers.
     */
    public static boolean isCanonicalFluid(Fluid fluid) {
        if (fluid == null || fluid == Fluids.EMPTY) {
            return false;
        }
        if (fluid instanceof FlowingFluid flowingFluid && flowingFluid.getSource() != fluid) {
            return false;
        }
        return fluid.defaultFluidState().isSource();
    }

    public static Fluid getCanonicalFluid(Fluid fluid) {
        if (fluid instanceof FlowingFluid flowingFluid) {
            return flowingFluid.getSource();
        }
        return fluid;
    }

    public static boolean matchesTypeFilter(boolean fluid, int mode) {
        return switch (mode) {
            case 1 -> !fluid;
            case 2 -> fluid;
            default -> true;
        };
    }
}
