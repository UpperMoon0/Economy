package com.nstut.economy.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class VaultBlockEntity extends BlockEntity implements Container {

    public enum VaultMode {
        BOTH(0, "Both (Input & Output)"),
        INPUT(1, "Input Only (Sell Orders)"),
        OUTPUT(2, "Output Only (Bought Items)");

        public final int id;
        public final String displayName;

        VaultMode(int id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public static VaultMode byId(int id) {
            for (VaultMode m : values()) if (m.id == id) return m;
            return BOTH;
        }

        public boolean canSupplyMarket() {
            return this == BOTH || this == INPUT;
        }

        public boolean canReceiveMarket() {
            return this == BOTH || this == OUTPUT;
        }
    }

    private static final int SIZE = 54;
    private NonNullList<ItemStack> items;
    private UUID owner;
    private VaultMode mode = VaultMode.BOTH;

    public VaultBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistries.VAULT_BE.get(), pos, state);
        this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    }

    public VaultMode getMode() { return mode != null ? mode : VaultMode.BOTH; }

    public void setMode(VaultMode mode) {
        this.mode = mode != null ? mode : VaultMode.BOTH;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void cycleMode() {
        setMode(VaultMode.byId((getMode().id + 1) % VaultMode.values().length));
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
        if (level != null && !level.isClientSide) {
            VaultManager.register(owner, worldPosition, level.dimension().location().toString());
        }
    }

    public UUID getOwner() {
        return owner;
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public @NotNull ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public @NotNull ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(items, slot);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) return false;
        return player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    public NonNullList<ItemStack> getItems() {
        return items;
    }

    public int countItem(net.minecraft.world.item.Item item) {
        int count = 0;
        for (ItemStack stack : items) {
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    public int countAvailableSpace(ItemStack stack) {
        int space = 0;
        int maxStack = stack.getMaxStackSize();
        for (ItemStack slot : items) {
            if (slot.isEmpty()) {
                space += maxStack;
            } else if (ItemStack.isSameItemSameTags(slot, stack)) {
                space += maxStack - slot.getCount();
            }
        }
        return space;
    }

    public boolean extractItem(net.minecraft.world.item.Item item, int amount, NonNullList<ItemStack> destination) {
        if (countItem(item) < amount) return false;
        int remaining = amount;
        for (int i = 0; i < items.size() && remaining > 0; i++) {
            ItemStack stack = items.get(i);
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                ItemStack taken = removeItem(i, take);
                destination.add(taken);
                remaining -= take;
            }
        }
        return true;
    }

    public boolean insertItemStacks(NonNullList<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            ItemStack remainder = stack.copy();
            for (int i = 0; i < items.size() && !remainder.isEmpty(); i++) {
                ItemStack slot = items.get(i);
                if (slot.isEmpty()) {
                    items.set(i, remainder.copy());
                    remainder.setCount(0);
                } else if (ItemStack.isSameItemSameTags(slot, remainder)) {
                    int space = slot.getMaxStackSize() - slot.getCount();
                    int add = Math.min(space, remainder.getCount());
                    slot.grow(add);
                    remainder.shrink(add);
                }
            }
            if (!remainder.isEmpty()) return false;
        }
        setChanged();
        return true;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && owner != null) {
            VaultManager.register(owner, worldPosition, level.dimension().location().toString());
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide && owner != null) {
            VaultManager.unregister(owner, worldPosition, level.dimension().location().toString());
        }
        super.setRemoved();
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        if (tag.hasUUID("Owner")) {
            owner = tag.getUUID("Owner");
        }
        if (tag.contains("Mode")) {
            mode = VaultMode.byId(tag.getInt("Mode"));
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putInt("Mode", getMode().id);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        ContainerHelper.saveAllItems(tag, items);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putInt("Mode", getMode().id);
        return tag;
    }
}
