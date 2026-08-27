package com.nstut.economy.util;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** Small bridge for UUID helpers removed from CompoundTag in Minecraft 26.1. */
public final class NbtCompat {
    private NbtCompat() {}

    public static void putUuid(CompoundTag tag, String key, UUID value) {
        tag.putIntArray(key, UUIDUtil.uuidToIntArray(value));
    }

    public static boolean hasUuid(CompoundTag tag, String key) {
        return tag.getIntArray(key).filter(value -> value.length == 4).isPresent();
    }

    public static UUID getUuid(CompoundTag tag, String key) {
        return tag.getIntArray(key)
                .filter(value -> value.length == 4)
                .map(UUIDUtil::uuidFromIntArray)
                .orElseThrow(() -> new IllegalArgumentException("Missing UUID tag: " + key));
    }
}
