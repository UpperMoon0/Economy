package com.nstut.fabric;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;

/** Economy stores base fluids only; reject variants rather than silently erase metadata. */
final class FabricFluidVariantSupport {
    private FabricFluidVariantSupport() {}
    static boolean isPlain(FluidVariant resource) { return !resource.hasNbt(); }
}
