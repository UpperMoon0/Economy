package com.nstut.fabric;

import com.nstut.economy.blocks.TankBlockEntity;
import com.nstut.economy.platform.services.IPlatformHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Path;

/**
 * Fabric implementation of the economy platform services.
 */
public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public String platformName() {
        return "Fabric";
    }

    @Override
    public boolean isClientEnvironment() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public TankBlockEntity newTankBlockEntity(BlockPos pos, BlockState state) {
        return new TankBlockEntity(pos, state);
    }
}
