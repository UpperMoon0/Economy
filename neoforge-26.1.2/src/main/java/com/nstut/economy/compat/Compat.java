package com.nstut.economy.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Version-specific helpers for Minecraft 26.1.2. */
public final class Compat {

    private Compat() {
    }

    public static Identifier rl(String id) {
        return Identifier.parse(id);
    }

    public static Identifier rl(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    public static boolean stacksEqual(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameComponents(a, b);
    }

    public static int maxStackSize(Item item) {
        return item.getDefaultMaxStackSize();
    }

    public static String canonicalItemStack(HolderLookup.Provider registries, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "{}";
        if (registries == null) throw new IllegalArgumentException("Registry access is required to serialize 26.1.2 item components");
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return com.nstut.economy.util.ItemStackNbtCompat.save(registries, copy).toString();
    }

    public static ItemStack deserializeCanonicalItemStack(HolderLookup.Provider registries, String canonical) {
        if (canonical == null || canonical.isBlank()) return ItemStack.EMPTY;
        if (registries == null) throw new IllegalArgumentException("Registry access is required to deserialize 26.1.2 item components");
        try {
            CompoundTag tag = TagParser.parseCompoundFully(canonical);
            return com.nstut.economy.util.ItemStackNbtCompat.parseOptional(registries, tag);
        } catch (Exception failure) {
            throw new IllegalArgumentException("Invalid canonical 26.1.2 item variant", failure);
        }
    }

    public static CompoundTag serializeItemStackTag(ServerLevel level, ItemStack stack) {
        return stack == null || stack.isEmpty()
                ? new CompoundTag()
                : com.nstut.economy.util.ItemStackNbtCompat.save(level.registryAccess(), stack);
    }

    public static ItemStack deserializeItemStackTag(ServerLevel level, CompoundTag tag) {
        return tag == null || tag.isEmpty()
                ? ItemStack.EMPTY
                : com.nstut.economy.util.ItemStackNbtCompat.parseOptional(level.registryAccess(), tag.copy());
    }

    public static ListTag getCompoundList(CompoundTag parent, String key) {
        return parent != null && parent.contains(key) ? parent.getListOrEmpty(key) : new ListTag();
    }

    public static CompoundTag getCompoundAt(ListTag list, int index) {
        return list.getCompoundOrEmpty(index);
    }
}
