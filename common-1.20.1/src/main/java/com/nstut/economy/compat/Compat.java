package com.nstut.economy.compat;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
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

    public static String serializeItemStack(ServerLevel level, ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        stack.save(tag);
        return tag.toString();
    }

    public static ItemStack deserializeItemStack(ServerLevel level, String serialized) {
        try {
            return ItemStack.of(TagParser.parseTag(serialized));
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException("Invalid persisted ItemStack SNBT", e);
        }
    }
}
