package com.nstut.economy.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.Containers;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.InteractionHand;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.InteractionResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.SimpleMenuProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.LivingEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.player.Player;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.inventory.ContainerData;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.ItemStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.context.BlockPlaceContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.Level;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.DirectionalBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.EntityBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.SoundType;

import com.mojang.serialization.MapCodec;
import com.nstut.economy.platform.Services;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.BlockBehaviour;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.StateDefinition;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.material.MapColor;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.phys.BlockHitResult;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Nullable;

public class TankBlock extends DirectionalBlock implements EntityBlock  {
    public static final MapCodec<TankBlock> CODEC = simpleCodec(TankBlock::new);

    @Override
    protected MapCodec<? extends TankBlock> codec() {
        return CODEC;
    }


    public TankBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state,
                            @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof TankBlockEntity tank) {
            tank.setOwner(player.getUUID());
        }
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos,
            @NotNull Player player, @NotNull BlockHitResult hit) {
        if (level.isClientSide()) {
            com.nstut.economy.client.ClientMenuContext.setTankPos(pos);
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp) {
            if (level.getBlockEntity(pos) instanceof TankBlockEntity tank) {
                if (tank.getOwner() != null && !tank.getOwner().equals(player.getUUID())) {
                    sp.sendOverlayMessage(Component.translatable("message.economy.tank.not_owner"));
                    return InteractionResult.CONSUME;
                }
                if (Services.FLUID.interactWithFluidHandler(player, InteractionHand.MAIN_HAND, level, pos, hit.getDirection())) {
                    return InteractionResult.CONSUME;
                }
                sp.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new TankMenu(id, inv, tank, new ContainerData() {
                            @Override public int get(int idx) { return tank.getMode().id; }
                            @Override public void set(int idx, int val) { tank.setMode(TankBlockEntity.TankMode.byId(val)); }
                            @Override public int getCount() { return 1; }
                        }, tank),
                        Component.translatable("block.economy.tank")
                ));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return com.nstut.economy.platform.Services.PLATFORM.newTankBlockEntity(pos, state);
    }
}



