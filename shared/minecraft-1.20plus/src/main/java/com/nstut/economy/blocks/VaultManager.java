package com.nstut.economy.blocks;

import com.nstut.economy.data.EconomyAccountData;
import com.nstut.economy.data.EconomyAccountData.VaultRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class VaultManager {
    private static final Map<UUID, List<VaultRecord>> vaults = new ConcurrentHashMap<>();
    private static EconomyAccountData savedData;

    public static void setAccountData(EconomyAccountData data) {
        savedData = data;
        vaults.clear();
        for (Map.Entry<UUID, List<VaultRecord>> e : data.getVaults().entrySet())
            vaults.put(e.getKey(), new CopyOnWriteArrayList<>(e.getValue()));
    }

    public static void register(UUID owner, BlockPos pos, String dimension) {
        List<VaultRecord> list = vaults.computeIfAbsent(owner, k -> new CopyOnWriteArrayList<>());
        BlockPos p = pos.immutable();
        String dim = dimension != null ? dimension : "minecraft:overworld";
        for (VaultRecord r : list) {
            if (r.pos.equals(p) && r.dimension.equals(dim)) return;
        }
        list.add(new VaultRecord(p, dim));
        if (savedData != null) savedData.addVault(owner, pos, dimension);
    }

    public static void register(UUID owner, BlockPos pos) {
        register(owner, pos, "minecraft:overworld");
    }

    public static void unregister(UUID owner, BlockPos pos, String dimension) {
        List<VaultRecord> list = vaults.get(owner);
        if (list != null) {
            BlockPos p = pos.immutable();
            String dim = dimension != null ? dimension : "minecraft:overworld";
            list.removeIf(r -> r.pos.equals(p) && r.dimension.equals(dim));
            if (savedData != null) savedData.removeVault(owner, pos, dimension);
        }
    }

    public static void unregister(UUID owner) {
        vaults.remove(owner);
        if (savedData != null) savedData.getVaults().remove(owner);
    }

    public static boolean hasVault(UUID owner) {
        List<VaultRecord> list = vaults.get(owner);
        return list != null && !list.isEmpty();
    }

    public static List<VaultRecord> getVaultRecords(UUID owner) {
        return vaults.getOrDefault(owner, Collections.emptyList());
    }

    @Nullable
    public static VaultBlockEntity getVault(Level level, UUID owner) {
        List<VaultBlockEntity> list = getVaults(level, owner);
        return list.isEmpty() ? null : list.get(0);
    }

    public static List<VaultBlockEntity> getVaults(Level level, UUID owner) {
        List<VaultRecord> records = vaults.get(owner);
        if (records == null || records.isEmpty()) return Collections.emptyList();
        List<VaultBlockEntity> result = new ArrayList<>();
        for (VaultRecord record : records) {
            Level targetLevel = resolveRecordLevel(level, record.dimension);
            if (targetLevel == null) continue;
            if (!targetLevel.dimension().location().toString().equals(record.dimension)) continue;
            if (targetLevel.getBlockEntity(record.pos) instanceof VaultBlockEntity vault
                    && vault.getOwner() != null && vault.getOwner().equals(owner)) {
                result.add(vault);
            }
        }
        return result;
    }

    @Nullable
    private static Level resolveRecordLevel(Level fallback, String dimension) {
        if (fallback == null) return null;
        if (fallback.getServer() != null) {
            try {
                ResourceLocation dimRl = com.nstut.economy.compat.Compat.rl(dimension);
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimRl);
                return fallback.getServer().getLevel(key);
            } catch (Exception e) {
                com.nstut.Economy.LOGGER.warn("Ignoring storage record with unresolvable dimension {}", dimension);
                return null;
            }
        }
        return fallback;
    }

    public static int countItemInVaults(Level level, UUID owner, Item item) {
        int count = 0;
        for (VaultBlockEntity v : getVaults(level, owner)) {
            if (v.getMode().canSupplyMarket()) {
                count += v.countItem(item);
            }
        }
        return count;
    }

    public static boolean extractItemFromVaults(Level level, UUID owner, Item item, int amount, NonNullList<ItemStack> destination) {
        if (countItemInVaults(level, owner, item) < amount) return false;
        int remaining = amount;
        for (VaultBlockEntity v : getVaults(level, owner)) {
            if (remaining <= 0) break;
            if (!v.getMode().canSupplyMarket()) continue;
            int countInVault = v.countItem(item);
            if (countInVault > 0) {
                int take = Math.min(remaining, countInVault);
                NonNullList<ItemStack> temp = NonNullList.create();
                if (v.extractItem(item, take, temp)) {
                    destination.addAll(temp);
                    remaining -= take;
                }
            }
        }
        return remaining == 0;
    }

    /**
     * Simulates distributing the payload across all receiving vaults without
     * mutating any vault. Returns exactly what would not fit; an empty result
     * means a subsequent {@link #insertItemStacksToVaults} of the same payload
     * is expected to fully succeed.
     */
    public static NonNullList<ItemStack> simulateInsertItemStacksToVaults(Level level, UUID owner, List<ItemStack> stacks) {
        List<List<ItemStack>> snapshots = new ArrayList<>();
        for (VaultBlockEntity v : getVaults(level, owner)) {
            if (!v.getMode().canReceiveMarket()) continue;
            List<ItemStack> snapshot = new ArrayList<>(v.getContainerSize());
            for (int i = 0; i < v.getContainerSize(); i++) {
                snapshot.add(v.getItem(i));
            }
            snapshots.add(snapshot);
        }
        return VaultInventoryOps.simulateDistribute(snapshots, stacks);
    }

    /**
     * Returns how many items of the given payload the receiving vaults can
     * currently accept, honoring per-stack NBT matching.
     */
    public static int countMaxAcceptableItems(Level level, UUID owner, List<ItemStack> payload) {
        int payloadTotal = VaultInventoryOps.total(payload);
        NonNullList<ItemStack> leftover = simulateInsertItemStacksToVaults(level, owner, payload);
        return payloadTotal - VaultInventoryOps.total(leftover);
    }

    /**
     * Inserts the payload vault-by-vault, carrying remainders forward. Returns
     * exactly what did not fit anywhere; an empty result means full success.
     */
    public static NonNullList<ItemStack> insertItemStacksToVaults(Level level, UUID owner, List<ItemStack> stacks) {
        NonNullList<ItemStack> remaining = NonNullList.create();
        for (ItemStack s : stacks) {
            if (s != null && !s.isEmpty()) remaining.add(s.copy());
        }
        for (VaultBlockEntity v : getVaults(level, owner)) {
            if (remaining.isEmpty()) break;
            if (!v.getMode().canReceiveMarket()) continue;
            remaining = v.insertItemStacks(remaining);
        }
        return remaining;
    }
}
