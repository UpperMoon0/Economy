package com.nstut.neoforge;

import com.nstut.economy.blocks.TankBlockEntity;
import com.nstut.economy.platform.services.IPlatformHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * NeoForge implementation of the economy platform services, shared by the
 * 1.21.1 and 26.1.2 loader modules.
 */
public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public String platformName() {
        return "NeoForge";
    }

    @Override
    public boolean isClientEnvironment() {
        return FMLLoader.getDist().isClient();
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public TankBlockEntity newTankBlockEntity(BlockPos pos, BlockState state) {
        return new TankBlockEntity(pos, state);
    }
}
