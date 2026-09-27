package com.nstut.economy.blocks;

import com.nstut.economy.api.AccountRef;
import com.nstut.economy.data.EconomyAccountData;
import com.nstut.economy.data.EconomyAccountData.VaultRecord;
import com.nstut.economy.trading.ItemCommodity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class VaultManager {
    private static final Map<AccountRef, List<VaultRecord>> vaults = new ConcurrentHashMap<>();
    private static EconomyAccountData savedData;

    public static void setAccountData(EconomyAccountData data) {
        savedData = data;
        vaults.clear();
        for (Map.Entry<AccountRef, List<VaultRecord>> e : data.getStorageVaults().entrySet())
            vaults.put(e.getKey(), new CopyOnWriteArrayList<>(e.getValue()));
    }

    public static void register(UUID owner, BlockPos pos, String dimension) {
        register(AccountRef.player(owner), pos, dimension);
    }
    public static void register(AccountRef owner, BlockPos pos, String dimension) {
        if (owner == null) return;
        List<VaultRecord> list = vaults.computeIfAbsent(owner, k -> new CopyOnWriteArrayList<>());
        BlockPos p = pos.immutable();
        String dim = dimension != null ? dimension : "minecraft:overworld";
        for (VaultRecord r : list) if (r.pos.equals(p) && r.dimension.equals(dim)) return;
        list.add(new VaultRecord(p, dim));
        if (savedData != null) savedData.addVault(owner, pos, dimension);
    }
    public static void register(UUID owner, BlockPos pos) { register(AccountRef.player(owner), pos, "minecraft:overworld"); }
    public static void register(AccountRef owner, BlockPos pos) { register(owner, pos, "minecraft:overworld"); }

    public static void unregister(UUID owner, BlockPos pos, String dimension) {
        unregister(AccountRef.player(owner), pos, dimension);
    }
    public static void unregister(AccountRef owner, BlockPos pos, String dimension) {
        if (owner == null) return;
        List<VaultRecord> list = vaults.get(owner);
        if (list == null) return;
        BlockPos p = pos.immutable();
        String dim = dimension != null ? dimension : "minecraft:overworld";
        list.removeIf(r -> r.pos.equals(p) && r.dimension.equals(dim));
        if (list.isEmpty()) vaults.remove(owner);
        if (savedData != null) savedData.removeVault(owner, pos, dimension);
    }
    public static void unregister(UUID owner) { unregister(AccountRef.player(owner)); }
    public static void unregister(AccountRef owner) {
        List<VaultRecord> records = new ArrayList<>(vaults.getOrDefault(owner, List.of()));
        for (VaultRecord r : records) unregister(owner, r.pos, r.dimension);
    }

    public static boolean hasVault(UUID owner) { return hasVault(AccountRef.player(owner)); }
    public static boolean hasVault(AccountRef owner) {
        List<VaultRecord> list = vaults.get(owner);
        return list != null && !list.isEmpty();
    }

    public static List<VaultRecord> getVaultRecords(UUID owner) { return getVaultRecords(AccountRef.player(owner)); }
    public static List<VaultRecord> getVaultRecords(AccountRef owner) {
        return List.copyOf(vaults.getOrDefault(owner, Collections.emptyList()));
    }

    @Nullable public static VaultBlockEntity getVault(Level level, UUID owner) { return getVault(level, AccountRef.player(owner)); }
    @Nullable public static VaultBlockEntity getVault(Level level, AccountRef owner) {
        List<VaultBlockEntity> list = getVaults(level, owner);
        return list.isEmpty() ? null : list.get(0);
    }

    public static List<VaultBlockEntity> getVaults(Level level, UUID owner) { return getVaults(level, AccountRef.player(owner)); }
    public static List<VaultBlockEntity> getVaults(Level level, AccountRef owner) {
        List<VaultRecord> records = vaults.get(owner);
        if (records == null || records.isEmpty()) return Collections.emptyList();
        List<VaultBlockEntity> result = new ArrayList<>();
        for (VaultRecord record : records) {
            Level targetLevel = resolveRecordLevel(level, record.dimension);
            if (targetLevel == null || !targetLevel.dimension().location().toString().equals(record.dimension)) continue;
            if (targetLevel.getBlockEntity(record.pos) instanceof VaultBlockEntity vault
                    && owner.equals(vault.getOwnerRef())) result.add(vault);
        }
        return result;
    }

    /** Reassigns both loaded blocks and the durable index. Unloaded blocks self-heal from team tombstones on load. */
    public static void reassignOwner(Level level, AccountRef from, AccountRef to) {
        if (from == null || to == null || from.equals(to)) return;
        for (VaultRecord record : new ArrayList<>(getVaultRecords(from))) {
            Level target = resolveRecordLevel(level, record.dimension);
            if (target != null && target.getBlockEntity(record.pos) instanceof VaultBlockEntity vault
                    && from.equals(vault.getOwnerRef())) {
                vault.setOwner(to);
            } else {
                unregister(from, record.pos, record.dimension);
                register(to, record.pos, record.dimension);
            }
        }
    }

    @Nullable private static Level resolveRecordLevel(Level fallback, String dimension) {
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

    public static int countItemInVaults(Level level, UUID owner, Item item) { return countItemInVaults(level, AccountRef.player(owner), item); }
    public static int countItemInVaults(Level level, AccountRef owner, Item item) {
        int count=0;
        for (VaultBlockEntity v:getVaults(level,owner)) if(v.getMode().canSupplyMarket()) count+=v.countItem(item);
        return count;
    }

    public static int countItemInVaults(Level level, UUID owner, ItemCommodity commodity) { return countItemInVaults(level, AccountRef.player(owner), commodity); }
    public static int countItemInVaults(Level level, AccountRef owner, ItemCommodity commodity) {
        if(level==null||commodity==null)return 0;
        int count=0;
        for(VaultBlockEntity vault:getVaults(level,owner)){
            if(!vault.getMode().canSupplyMarket())continue;
            for(int slot=0;slot<vault.getContainerSize();slot++){
                ItemStack stack=vault.getItem(slot);
                if(commodity.matches(level,stack))count+=stack.getCount();
            }
        }
        return count;
    }

    public static boolean extractItemFromVaults(Level level, UUID owner, Item item, int amount, NonNullList<ItemStack> destination) {
        return extractItemFromVaults(level, AccountRef.player(owner), item, amount, destination);
    }
    public static boolean extractItemFromVaults(Level level, AccountRef owner, Item item, int amount, NonNullList<ItemStack> destination) {
        if(countItemInVaults(level,owner,item)<amount)return false;
        int remaining=amount;
        for(VaultBlockEntity v:getVaults(level,owner)){
            if(remaining<=0)break;
            if(!v.getMode().canSupplyMarket())continue;
            int count=v.countItem(item);
            if(count>0){
                int take=Math.min(remaining,count);
                NonNullList<ItemStack> temp=NonNullList.create();
                if(v.extractItem(item,take,temp)){destination.addAll(temp);remaining-=take;}
            }
        }
        return remaining==0;
    }

    public static boolean extractItemFromVaults(Level level, UUID owner, ItemCommodity commodity, int amount, NonNullList<ItemStack> destination) {
        return extractItemFromVaults(level, AccountRef.player(owner), commodity, amount, destination);
    }
    public static boolean extractItemFromVaults(Level level, AccountRef owner, ItemCommodity commodity, int amount, NonNullList<ItemStack> destination) {
        if(level==null||commodity==null||amount<0)return false;
        if(countItemInVaults(level,owner,commodity)<amount)return false;
        int remaining=amount;
        for(VaultBlockEntity vault:getVaults(level,owner)){
            if(remaining<=0)break;
            if(!vault.getMode().canSupplyMarket())continue;
            for(int slot=0;slot<vault.getContainerSize()&&remaining>0;slot++){
                ItemStack stack=vault.getItem(slot);
                if(!commodity.matches(level,stack))continue;
                int take=Math.min(remaining,stack.getCount());
                ItemStack extracted=vault.removeItem(slot,take);
                if(!extracted.isEmpty()){destination.add(extracted);remaining-=extracted.getCount();}
            }
        }
        return remaining==0;
    }

    public static NonNullList<ItemStack> simulateInsertItemStacksToVaults(Level level, UUID owner, List<ItemStack> stacks) {
        return simulateInsertItemStacksToVaults(level, AccountRef.player(owner), stacks);
    }
    public static NonNullList<ItemStack> simulateInsertItemStacksToVaults(Level level, AccountRef owner, List<ItemStack> stacks) {
        List<List<ItemStack>> snapshots=new ArrayList<>();
        for(VaultBlockEntity v:getVaults(level,owner)){
            if(!v.getMode().canReceiveMarket())continue;
            List<ItemStack> snapshot=new ArrayList<>(v.getContainerSize());
            for(int i=0;i<v.getContainerSize();i++)snapshot.add(v.getItem(i));
            snapshots.add(snapshot);
        }
        return VaultInventoryOps.simulateDistribute(snapshots,stacks);
    }

    public static int countMaxAcceptableItems(Level level, UUID owner, List<ItemStack> payload) {
        return countMaxAcceptableItems(level, AccountRef.player(owner), payload);
    }
    public static int countMaxAcceptableItems(Level level, AccountRef owner, List<ItemStack> payload) {
        int total=VaultInventoryOps.total(payload);
        return total-VaultInventoryOps.total(simulateInsertItemStacksToVaults(level,owner,payload));
    }

    public static NonNullList<ItemStack> insertItemStacksToVaults(Level level, UUID owner, List<ItemStack> stacks) {
        return insertItemStacksToVaults(level, AccountRef.player(owner), stacks);
    }
    public static NonNullList<ItemStack> insertItemStacksToVaults(Level level, AccountRef owner, List<ItemStack> stacks) {
        List<VaultBlockEntity> receivers=new ArrayList<>();
        List<List<ItemStack>> inventories=new ArrayList<>();
        for(VaultBlockEntity v:getVaults(level,owner)){
            if(!v.getMode().canReceiveMarket())continue;
            receivers.add(v);inventories.add(v.getItems());
        }
        int before=VaultInventoryOps.total(stacks);
        NonNullList<ItemStack> remaining=VaultInventoryOps.distribute(inventories,stacks);
        if(VaultInventoryOps.total(remaining)!=before)for(VaultBlockEntity receiver:receivers)receiver.setChanged();
        return remaining;
    }
}
