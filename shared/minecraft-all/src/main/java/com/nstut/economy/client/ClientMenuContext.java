package com.nstut.economy.client;

import net.minecraft.core.BlockPos;

/**
 * Carries the tank position from the client-side block click to the menu
 * factory. Vanilla menu types receive no extra buffer data, so the position
 * observed during {@code use()} is stashed here and consumed when the client
 * reconstructs the TankMenu.
 */
public final class ClientMenuContext {

    private static BlockPos lastTankPos;

    public static void setTankPos(BlockPos pos) {
        lastTankPos = pos;
    }

    public static BlockPos consumeTankPos() {
        BlockPos pos = lastTankPos;
        lastTankPos = null;
        return pos != null ? pos.immutable() : null;
    }
}

