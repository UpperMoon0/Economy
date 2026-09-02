package com.nstut.economy.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Version-specific helpers for code compiled into multiple Minecraft
 * versions. This is the 1.20.1 implementation; ResourceLocation constructors
 * are public here and item NBT comparison uses the legacy helper.
 */
public final class Compat {

    private Compat() {
    }

    public static ResourceLocation rl(String id) {
        return new ResourceLocation(id);
    }

    public static ResourceLocation rl(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    public static boolean stacksEqual(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameTags(a, b);
    }

    public static int maxStackSize(Item item) {
        return item.getMaxStackSize();
    }

    public static CompoundTag serializeItemStackTag(ServerLevel level, ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        if (stack != null && !stack.isEmpty()) stack.save(tag);
        return tag;
    }

    public static ItemStack deserializeItemStackTag(ServerLevel level, CompoundTag tag) {
        return tag == null || tag.isEmpty() ? ItemStack.EMPTY : ItemStack.of(tag.copy());
    }
}
