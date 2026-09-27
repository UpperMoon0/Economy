package com.nstut.economy.blocks;

import com.nstut.economy.api.AccountRef;
import com.nstut.economy.api.AccountKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.jetbrains.annotations.NotNull;
import java.util.UUID;


public class VaultBlockEntity extends BlockEntity implements WorldlyContainer {

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
    private AccountKind ownerKind = AccountKind.PLAYER;
    private VaultMode mode = VaultMode.BOTH;

    public VaultBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistries.VAULT_BE.get(), pos, state);
        this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    }

    public VaultMode getMode() { return mode != null ? mode : VaultMode.BOTH; }

    public void setMode(VaultMode mode) {
        this.mode = mode != null ? mode : VaultMode.BOTH;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void cycleMode() {
        setMode(VaultMode.byId((getMode().id + 1) % VaultMode.values().length));
    }

    public UUID getOwner() { return owner; }
    public AccountRef getOwnerRef() { return owner == null ? null : new AccountRef(ownerKind, owner); }

    public void setOwner(UUID owner) { setOwner(owner == null ? null : AccountRef.player(owner)); }
    public void setOwner(AccountRef newOwner) {
        AccountRef oldOwner = getOwnerRef();
        if (java.util.Objects.equals(oldOwner, newOwner)) return;
        if (level != null && oldOwner != null) {
            VaultManager.unregister(oldOwner, worldPosition, level.dimension().identifier().toString());
        }
        owner = newOwner == null ? null : newOwner.id();
        ownerKind = newOwner == null ? AccountKind.PLAYER : newOwner.kind();
        setChanged();
        if (level != null && newOwner != null) {
            VaultManager.register(newOwner, worldPosition, level.dimension().identifier().toString());
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
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
        if (!level.isClientSide() && !com.nstut.economy.server.TeamStorageAccess.canUse(player.getUUID(), getOwnerRef())) return false;
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
        return VaultInventoryOps.countAvailableSpace(items, stack);
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

    /**
     * Inserts as much of the payload as fits and returns exactly what did not
     * fit. An empty result means everything was stored. Callers that need
     * all-or-nothing behavior must simulate first via
     * {@link VaultManager#simulateInsertItemStacksToVaults}.
     */
    public NonNullList<ItemStack> insertItemStacks(NonNullList<ItemStack> stacks) {
        int before = VaultInventoryOps.total(stacks);
        NonNullList<ItemStack> leftovers = VaultInventoryOps.insert(items, stacks);
        if (VaultInventoryOps.total(leftovers) != before) {
            setChanged();
        }
        return leftovers;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[0];
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
        return false;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (!level.isClientSide() && owner != null) {
            AccountRef current = getOwnerRef();
            AccountRef normalized = com.nstut.economy.server.TeamWalletLifecycle.replacementOwner(current).orElse(current);
            if (!normalized.equals(current)) {
                owner = normalized.id();
                ownerKind = normalized.kind();
                setChanged();
            }
            VaultManager.register(normalized, worldPosition, level.dimension().identifier().toString());
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide() && owner != null) {
            VaultManager.unregister(getOwnerRef(), worldPosition, level.dimension().identifier().toString());
        }
        super.setRemoved();
    }

    @Override
    protected void loadAdditional(@NotNull ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        ownerKind = input.read("OwnerKind", com.mojang.serialization.Codec.STRING)
                .map(v -> { try { return AccountKind.valueOf(v); } catch (RuntimeException ignored) { return AccountKind.PLAYER; } })
                .orElse(AccountKind.PLAYER);
        input.getInt("Mode").ifPresent(modeId -> mode = VaultMode.byId(modeId));
    }

    @Override
    protected void saveAdditional(@NotNull ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        if (owner != null) {
            output.store("Owner", UUIDUtil.CODEC, owner);
            output.store("OwnerKind", com.mojang.serialization.Codec.STRING, ownerKind.name());
        }
        output.putInt("Mode", getMode().id);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(@NotNull HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
