package com.nstut.economy.compat;

import net.minecraft.resources.Identifier;
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
}
