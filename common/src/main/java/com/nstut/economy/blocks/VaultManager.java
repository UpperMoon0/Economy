package com.nstut.economy.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VaultManager {
    private static final Map<UUID, BlockPos> vaults = new ConcurrentHashMap<>();

    public static void register(UUID owner, BlockPos pos) {
        vaults.put(owner, pos);
    }

    public static void unregister(UUID owner) {
        vaults.remove(owner);
    }

    @Nullable
    public static BlockPos getVaultPos(UUID owner) {
        return vaults.get(owner);
    }

    @Nullable
    public static VaultBlockEntity getVault(Level level, UUID owner) {
        BlockPos pos = vaults.get(owner);
        if (pos == null) return null;
        if (level.getBlockEntity(pos) instanceof VaultBlockEntity vault) {
            return vault;
        }
        return null;
    }

    public static boolean hasVault(UUID owner) {
        return vaults.containsKey(owner);
    }

    public static void clear() {
        vaults.clear();
    }
}
