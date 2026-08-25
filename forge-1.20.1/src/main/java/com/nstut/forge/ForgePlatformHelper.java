package com.nstut.forge;

import com.nstut.economy.blocks.TankBlockEntity;
import com.nstut.economy.config.EconomyConfig;
import com.nstut.economy.platform.services.IPlatformHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Forge implementation of the economy platform services.
 */
public class ForgePlatformHelper implements IPlatformHelper {

    @Override
    public String platformName() {
        return "Forge";
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
        return new ForgeTankBlockEntity(pos, state);
    }
}
