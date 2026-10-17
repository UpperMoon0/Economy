package com.nstut.forge;

import com.nstut.economy.blocks.VaultBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Standard item capability for pipes and machine auto I/O on every face. */
public class ForgeVaultBlockEntity extends VaultBlockEntity {
    // All faces expose the same slots and permissions, including unsided lookups.
    private LazyOptional<IItemHandler> items = createItemCapability();

    public ForgeVaultBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    private LazyOptional<IItemHandler> createItemCapability() {
        return LazyOptional.of(() -> new SidedInvWrapper(this, Direction.UP));
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return !isRemoved()
                    ? items.cast() : LazyOptional.empty();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        items.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        items = createItemCapability();
    }
}
