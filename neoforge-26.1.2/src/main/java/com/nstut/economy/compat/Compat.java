package com.nstut.economy.compat;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
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

    public static String serializeItemStack(ServerLevel level, ItemStack stack) {
        return com.nstut.economy.util.ItemStackNbtCompat.save(level.registryAccess(), stack).toString();
    }
    public static ItemStack deserializeItemStack(ServerLevel level, String serialized) {
        try { return com.nstut.economy.util.ItemStackNbtCompat.parseOptional(level.registryAccess(), TagParser.parseCompoundFully(serialized)); }
        catch (CommandSyntaxException e) { throw new IllegalArgumentException("Invalid persisted ItemStack SNBT", e); }
    }
}
