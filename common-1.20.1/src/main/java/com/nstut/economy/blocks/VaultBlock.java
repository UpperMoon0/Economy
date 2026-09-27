package com.nstut.economy.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VaultBlock extends DirectionalBlock implements EntityBlock {

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
    public @NotNull InteractionResult use(@NotNull BlockState state, Level level, @NotNull BlockPos pos,
                                          @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide) {
            com.nstut.economy.client.ClientMenuContext.setVaultPos(pos);
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp) {
            if (level.getBlockEntity(pos) instanceof VaultBlockEntity vault) {
                if (!com.nstut.economy.server.TeamStorageAccess.canUse(player.getUUID(), vault.getOwnerRef())) {
                    sp.displayClientMessage(Component.translatable("message.economy.vault.not_owner"), true);
                    return InteractionResult.CONSUME;
                }
                sp.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new VaultMenu(id, inv, vault, new net.minecraft.world.inventory.ContainerData() {
                            @Override public int get(int idx) {
                                return switch (idx) {
                                    case VaultMenu.DATA_MODE -> vault.getMode().id;
                                    case VaultMenu.DATA_TEAM_OWNED -> vault.getOwnerRef() != null
                                            && vault.getOwnerRef().kind() == com.nstut.economy.api.AccountKind.TEAM ? 1 : 0;
                                    case VaultMenu.DATA_TEAM_AVAILABLE -> com.nstut.economy.api.EconomyApi.teamEconomy()
                                            .resolveTeam(p.getUUID()).isPresent() ? 1 : 0;
                                    default -> 0;
                                };
                            }
                            @Override public void set(int idx, int val) {
                                if (idx == VaultMenu.DATA_MODE) vault.setMode(VaultBlockEntity.VaultMode.byId(val));
                            }
                            @Override public int getCount() { return VaultMenu.DATA_COUNT; }
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
