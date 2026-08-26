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
import net.minecraft.world.InteractionHand;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.InteractionResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.SimpleMenuProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.player.Player;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.context.BlockPlaceContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.Level;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.DirectionalBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.SoundType;
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

public class MarketBlock extends DirectionalBlock  {
    public static final MapCodec<MarketBlock> CODEC = simpleCodec(MarketBlock::new);

    @Override
    protected MapCodec<? extends MarketBlock> codec() {
        return CODEC;
    }


    public MarketBlock(BlockBehaviour.Properties properties) {
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
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos,
            @NotNull Player player, @NotNull BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            sp.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new MarketMenu(id, inv),
                    Component.translatable("block.economy.market")
            ));
        }
        return InteractionResult.SUCCESS;
    }
}
