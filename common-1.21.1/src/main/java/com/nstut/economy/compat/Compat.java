package com.nstut.economy.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Version-specific helpers for code compiled into multiple Minecraft
 * versions. This is the 1.21.1 implementation.
 */
public final class Compat {

    private Compat() {
    }

    public static ResourceLocation rl(String id) {
        return ResourceLocation.parse(id);
    }

    public static ResourceLocation rl(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    public static boolean stacksEqual(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameComponents(a, b);
    }

    public static int maxStackSize(Item item) {
        return item.getDefaultMaxStackSize();
    }

    public static String canonicalItemStack(HolderLookup.Provider registries, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "{}";
        if (registries == null) throw new IllegalArgumentException("Registry access is required to serialize 1.21.1 item components");
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy.save(registries).toString();
    }

    public static ItemStack deserializeCanonicalItemStack(HolderLookup.Provider registries, String canonical) {
        if (canonical == null || canonical.isBlank()) return ItemStack.EMPTY;
        if (registries == null) throw new IllegalArgumentException("Registry access is required to deserialize 1.21.1 item components");
        try {
            return ItemStack.parseOptional(registries, TagParser.parseTag(canonical));
        } catch (Exception failure) {
            throw new IllegalArgumentException("Invalid canonical 1.21.1 item variant", failure);
        }
    }

    public static CompoundTag serializeItemStackTag(ServerLevel level, ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        if (stack != null && !stack.isEmpty()) stack.save(level.registryAccess(), tag);
        return tag;
    }

    public static ItemStack deserializeItemStackTag(ServerLevel level, CompoundTag tag) {
        return tag == null || tag.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(level.registryAccess(), tag.copy());
    }

    public static ListTag getCompoundList(CompoundTag parent, String key) {
        return parent != null && parent.contains(key, Tag.TAG_LIST)
                ? parent.getList(key, Tag.TAG_COMPOUND)
                : new ListTag();
    }

    public static CompoundTag getCompoundAt(ListTag list, int index) {
        return list.getCompound(index);
    }
}
