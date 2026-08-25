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
import net.minecraft.world.entity.LivingEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.player.Player;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.inventory.ChestMenu;
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

public class VaultBlock extends DirectionalBlock implements EntityBlock  {
    public static final MapCodec<VaultBlock> CODEC = simpleCodec(p -> new VaultBlock());

    @Override
    protected MapCodec<? extends VaultBlock> codec() {
        return CODEC;
    }


    public VaultBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(5.0F, 1200.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL));
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
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof VaultBlockEntity vault) {
            vault.setOwner(player.getUUID());
        }
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos,
            @NotNull Player player, @NotNull BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (level.getBlockEntity(pos) instanceof VaultBlockEntity vault) {
                if (vault.getOwner() != null && !vault.getOwner().equals(player.getUUID())) {
                    sp.displayClientMessage(Component.translatable("message.economy.vault.not_owner"), true);
                    return InteractionResult.CONSUME;
                }
                sp.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new VaultMenu(id, inv, vault, new net.minecraft.world.inventory.ContainerData() {
                            @Override public int get(int idx) { return vault.getMode().id; }
                            @Override public void set(int idx, int val) { vault.setMode(VaultBlockEntity.VaultMode.byId(val)); }
                            @Override public int getCount() { return 1; }
                        }, vault),
                        Component.translatable("block.economy.vault")
                ));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                         @NotNull BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof VaultBlockEntity vault) {
                net.minecraft.world.Containers.dropContents(level, pos, vault);
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new VaultBlockEntity(pos, state);
    }
}

