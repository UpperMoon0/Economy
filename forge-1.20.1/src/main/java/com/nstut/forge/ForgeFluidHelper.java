package com.nstut.forge;

import com.nstut.economy.platform.services.IFluidHelper;
import com.nstut.economy.trading.EconomyFluidStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import java.util.Optional;

/**
 * Forge implementation of loader-specific fluid operations.
 */
public class ForgeFluidHelper implements IFluidHelper {

    @Override
    public Component displayName(Fluid fluid) {
        return Component.translatable(fluid.getFluidType().getDescriptionId());
    }

    @Override
    public boolean isAir(Fluid fluid) {
        return fluid.getFluidType().isAir();
    }

    @Override
    public ResourceLocation stillTexture(Fluid fluid) {
        return IClientFluidTypeExtensions.of(fluid).getStillTexture();
    }

    @Override
    public int tint(Fluid fluid) {
        return IClientFluidTypeExtensions.of(fluid).getTintColor();
    }

    @Override
    public boolean isFluidContainer(ItemStack stack) {
        return FluidUtil.getFluidHandler(stack).isPresent();
    }

    @Override
    public Optional<BucketTransfer> tryEmptyContainerIntoTank(ItemStack container, int capacity, EconomyFluidStack current) {
        IFluidHandlerItem itemHandler = FluidUtil.getFluidHandler(container).orElse(null);
        if (itemHandler == null) return Optional.empty();

        FluidTank tankHandler = new FluidTank(capacity);
        if (!current.isEmpty()) {
            tankHandler.setFluid(new FluidStack(current.getFluid(), current.getAmount()));
        } else {
            tankHandler.setFluid(FluidStack.EMPTY);
        }

        BucketResult result = tryEmpty(container, tankHandler);
        if (result == null) return Optional.empty();
        EconomyFluidStack newCurrent = fromForge(tankHandler.getFluid());
        return Optional.of(new BucketTransfer(result.stack(), newCurrent));
    }

    @Override
    public Optional<BucketTransfer> tryFillContainerFromTank(ItemStack container, int capacity, EconomyFluidStack current) {
        if (current.isEmpty()) return Optional.empty();

        IFluidHandlerItem itemHandler = FluidUtil.getFluidHandler(container).orElse(null);
        if (itemHandler == null) return Optional.empty();

        FluidTank tankHandler = new FluidTank(capacity);
        tankHandler.setFluid(new FluidStack(current.getFluid(), current.getAmount()));

        var result = FluidUtil.tryFillContainer(container.copy(), tankHandler, Integer.MAX_VALUE, null, true);
        if (!result.isSuccess()) return Optional.empty();
        EconomyFluidStack newCurrent = fromForge(tankHandler.getFluid());
        return Optional.of(new BucketTransfer(result.getResult(), newCurrent));
    }

    @Override
    public boolean interactWithFluidHandler(Player player, InteractionHand hand, Level level, BlockPos pos, Direction side) {
        return FluidUtil.interactWithFluidHandler(player, hand, level, pos, side);
    }

    // Mirrors FluidActionResult without leaking the forge type into the interface.
    private record BucketResult(ItemStack stack) {
    }

    private BucketResult tryEmpty(ItemStack container, FluidTank tankHandler) {
        var result = FluidUtil.tryEmptyContainer(container.copy(), tankHandler, Integer.MAX_VALUE, null, true);
        if (!result.isSuccess()) return null;
        return new BucketResult(result.getResult());
    }

    private static EconomyFluidStack fromForge(FluidStack stack) {
        if (stack == null || stack.isEmpty()) return EconomyFluidStack.EMPTY;
        return new EconomyFluidStack(stack.getFluid(), stack.getAmount());
    }
}
