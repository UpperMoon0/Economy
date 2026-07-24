package com.nstut.economy.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VaultBlock extends Block implements EntityBlock {

    public VaultBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(5.0F, 1200.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL));
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state,
                            @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof VaultBlockEntity vault) {
            vault.setOwner(player.getUUID());
        }
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, Level level, @NotNull BlockPos pos,
                                          @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (level.getBlockEntity(pos) instanceof VaultBlockEntity vault) {
                if (vault.getOwner() != null && !vault.getOwner().equals(player.getUUID())) {
                    sp.displayClientMessage(Component.translatable("message.economy.vault.not_owner"), true);
                    return InteractionResult.CONSUME;
                }
                sp.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> ChestMenu.threeRows(id, inv, vault),
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
                VaultManager.unregister(vault.getOwner());
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
