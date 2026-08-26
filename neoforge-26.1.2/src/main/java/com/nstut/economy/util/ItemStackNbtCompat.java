package com.nstut.economy.util;

import com.nstut.Economy;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;

/** ItemStack <-> NBT helpers replacing the removed {@code ItemStack.parse}/save overloads. */
public final class ItemStackNbtCompat {
    private ItemStackNbtCompat() {}

    public static ItemStack parseOptional(HolderLookup.Provider registries, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return ItemStack.EMPTY;
        return ItemStack.OPTIONAL_CODEC.parse(
                        registries.createSerializationContext(NbtOps.INSTANCE), tag)
                .resultOrPartial(error -> Economy.LOGGER.warn("Failed to parse item stack: {}", error))
                .orElse(ItemStack.EMPTY);
    }

    public static CompoundTag save(HolderLookup.Provider registries, ItemStack stack) {
        CompoundTag out = new CompoundTag();
        if (stack == null || stack.isEmpty()) return out;
        ItemStack.OPTIONAL_CODEC.encodeStart(
                        registries.createSerializationContext(NbtOps.INSTANCE), stack)
                .resultOrPartial(error -> Economy.LOGGER.warn("Failed to save item stack: {}", error))
                .ifPresent(tag -> {
                    if (tag instanceof CompoundTag compoundTag) {
                        out.merge(compoundTag);
                    }
                });
        return out;
    }
}
