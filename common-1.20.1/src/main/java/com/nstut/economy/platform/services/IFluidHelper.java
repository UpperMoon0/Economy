package com.nstut.economy.platform.services;

import com.nstut.economy.trading.EconomyFluidStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.Fluid;

import java.util.Optional;

/**
 * Fluid operations that differ per loader (render info, display names and
 * bucket interactions). Implementations must be safe to call on both sides.
 */
public interface IFluidHelper {

    Component displayName(Fluid fluid);

    boolean isAir(Fluid fluid);

    ResourceLocation stillTexture(Fluid fluid);

    int tint(Fluid fluid);

    boolean isFluidContainer(ItemStack stack);

    /**
     * Empties a fluid container into an in-memory tank of the given capacity.
     * Returns empty when the container cannot be drained into the tank.
     */
    Optional<BucketTransfer> tryEmptyContainerIntoTank(ItemStack container, int capacity, EconomyFluidStack current);

    /**
     * Fills a fluid container from an in-memory tank. Returns empty when the
     * container cannot accept the tank's fluid.
     */
    Optional<BucketTransfer> tryFillContainerFromTank(ItemStack container, int capacity, EconomyFluidStack current);

    /**
     * Handles a direct right-click interaction between the held item and a
     * tank block position (bucket fill/drain). Returns true when the
     * interaction consumed the click.
     */
    boolean interactWithFluidHandler(Player player, InteractionHand hand, Level level, BlockPos pos, Direction side);

    record BucketTransfer(ItemStack resultContainer, EconomyFluidStack resultTankFluid) {
    }
}

