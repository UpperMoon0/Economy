package com.nstut.economy.compat;

import net.minecraft.nbt.CompoundTag;
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
}
