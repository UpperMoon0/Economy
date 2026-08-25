package com.nstut.economy.platform.services;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import com.nstut.economy.blocks.TankBlockEntity;

import java.nio.file.Path;

/**
 * Platform-specific environment helpers.
 */
public interface IPlatformHelper {

    String platformName();

    boolean isClientEnvironment();

    Path configDir();

    /**
     * Supplies the tank block entity implementation for this platform. Loader
     * modules may subclass TankBlockEntity to expose extra integration (for
     * example Forge fluid capabilities).
     */
    TankBlockEntity newTankBlockEntity(BlockPos pos, BlockState state);
}

