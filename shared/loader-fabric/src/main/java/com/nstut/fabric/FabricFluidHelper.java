package com.nstut.fabric;

import com.nstut.economy.blocks.TankBlockEntity;
import com.nstut.economy.platform.services.IFluidHelper;
import com.nstut.economy.trading.EconomyFluidStack;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.Optional;

/**
 * Fabric implementation of loader-specific fluid operations. Bucket
 * interactions cover vanilla buckets; modded bucket fill/drain goes through
 * the tank inventory slot instead.
 */
public class FabricFluidHelper implements IFluidHelper {

    private static final int BUCKET_VOLUME = 1000;

    @Override
    public Component displayName(Fluid fluid) {
        return Component.translatable(fluid.defaultFluidState().createLegacyBlock().getBlock().getDescriptionId());
    }

    @Override
    public boolean isAir(Fluid fluid) {
        return fluid == Fluids.EMPTY || fluid.defaultFluidState().isEmpty();
    }

    @Override
    public ResourceLocation stillTexture(Fluid fluid) {
        FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(fluid);
        if (handler == null) {
            return com.nstut.economy.compat.Compat.rl("minecraft", "block/water_still");
        }
        return handler.getFluidSprites(null, null, fluid.defaultFluidState())[0].contents().name();
    }

    @Override
    public int tint(Fluid fluid) {
        FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(fluid);
        if (handler == null) return 0xFFFFFFFF;
        return handler.getFluidColor(null, null, fluid.defaultFluidState());
    }

    @Override
    public boolean isFluidContainer(ItemStack stack) {
        return stack.getItem() instanceof BucketItem || stack.is(Items.BUCKET);
    }

    @Override
    public Optional<BucketTransfer> tryEmptyContainerIntoTank(ItemStack container, int capacity, EconomyFluidStack current) {
        if (!(container.getItem() instanceof BucketItem bucket)) return Optional.empty();
        Fluid content = contentOf(container);
        if (content == Fluids.EMPTY || content.defaultFluidState().isEmpty()) return Optional.empty();
        if (!current.isEmpty() && current.getFluid() != content) return Optional.empty();

        int room = capacity - current.getAmount();
        if (room <= 0) return Optional.empty();
        int take = Math.min(BUCKET_VOLUME, room);

        EconomyFluidStack newCurrent = current.isEmpty()
                ? new EconomyFluidStack(content, take)
                : new EconomyFluidStack(current.getFluid(), current.getAmount() + take);
        return Optional.of(new BucketTransfer(new ItemStack(Items.BUCKET), newCurrent));
    }

    @Override
    public Optional<BucketTransfer> tryFillContainerFromTank(ItemStack container, int capacity, EconomyFluidStack current) {
        if (current.isEmpty() || current.getAmount() < BUCKET_VOLUME) return Optional.empty();
        if (!container.is(Items.BUCKET)) return Optional.empty();

        ItemStack filled;
        Fluid fluid = current.getFluid();
        if (fluid == Fluids.WATER) filled = new ItemStack(Items.WATER_BUCKET);
        else if (fluid == Fluids.LAVA) filled = new ItemStack(Items.LAVA_BUCKET);
        else return Optional.empty();

        int remaining = current.getAmount() - BUCKET_VOLUME;
        EconomyFluidStack newCurrent = remaining > 0
                ? new EconomyFluidStack(fluid, remaining)
                : EconomyFluidStack.EMPTY;
        return Optional.of(new BucketTransfer(filled, newCurrent));
    }

    private static Fluid contentOf(ItemStack stack) {
        if (stack.is(Items.WATER_BUCKET)) return Fluids.WATER;
        if (stack.is(Items.LAVA_BUCKET)) return Fluids.LAVA;
        return Fluids.EMPTY;
    }

    @Override
    public boolean interactWithFluidHandler(Player player, InteractionHand hand, Level level, BlockPos pos, Direction side) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) {
            return held.getItem() instanceof BucketItem || held.is(Items.BUCKET);
        }
        if (!(level.getBlockEntity(pos) instanceof TankBlockEntity tank)) return false;

        var emptied = tryEmptyContainerIntoTank(held.copy(), tank.getCapacity(), tank.getFluid());
        if (emptied.isPresent()) {
            applyTransfer(player, hand, held, emptied.get(), tank);
            return true;
        }

        var filled = tryFillContainerFromTank(held.copy(), tank.getCapacity(), tank.getFluid());
        if (filled.isPresent()) {
            applyTransfer(player, hand, held, filled.get(), tank);
            return true;
        }
        return false;
    }

    private void applyTransfer(Player player, InteractionHand hand, ItemStack held,
                               BucketTransfer transfer, TankBlockEntity tank) {
        tank.setFluid(transfer.resultTankFluid());
        if (!player.getAbilities().instabuild) {
            player.setItemInHand(hand, transfer.resultContainer());
        }
    }
}
