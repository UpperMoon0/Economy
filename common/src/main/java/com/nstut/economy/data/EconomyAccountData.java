package com.nstut.economy.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EconomyAccountData extends SavedData {

    private static final String NAME = "economy_accounts";

    public static final class VaultRecord {
        public final BlockPos pos;
        public final String dimension;

        public VaultRecord(BlockPos pos, String dimension) {
            this.pos = pos;
            this.dimension = dimension != null ? dimension : "minecraft:overworld";
        }
    }

    private final Map<UUID, BigDecimal> balances = new HashMap<>();
    private final Map<UUID, List<VaultRecord>> vaults = new HashMap<>();

    public Map<UUID, BigDecimal> getBalances() { return balances; }
    public void setBalance(UUID player, BigDecimal balance) { balances.put(player, balance); setDirty(); }
    public BigDecimal getBalance(UUID player) { return balances.getOrDefault(player, BigDecimal.ZERO); }

    public Map<UUID, List<VaultRecord>> getVaults() { return vaults; }
    public void addVault(UUID owner, BlockPos pos, String dimension) {
        List<VaultRecord> list = vaults.computeIfAbsent(owner, k -> new ArrayList<>());
        BlockPos p = pos.immutable();
        String dim = dimension != null ? dimension : "minecraft:overworld";
        for (VaultRecord r : list) {
            if (r.pos.equals(p) && r.dimension.equals(dim)) return;
        }
        list.add(new VaultRecord(p, dim));
        setDirty();
    }
    public void removeVault(UUID owner, BlockPos pos, String dimension) {
        List<VaultRecord> list = vaults.get(owner);
        if (list != null) {
            BlockPos p = pos.immutable();
            String dim = dimension != null ? dimension : "minecraft:overworld";
            if (list.removeIf(r -> r.pos.equals(p) && r.dimension.equals(dim))) {
                setDirty();
            }
        }
    }
    public boolean hasVault(UUID owner) { return vaults.containsKey(owner) && !vaults.get(owner).isEmpty(); }

    public static EconomyAccountData get(net.minecraft.server.level.ServerLevel level) {
        net.minecraft.server.level.ServerLevel target = (level != null && level.getServer() != null) ? level.getServer().overworld() : level;
        return target.getDataStorage().computeIfAbsent(EconomyAccountData::load, EconomyAccountData::new, NAME);
    }

    public static EconomyAccountData load(CompoundTag tag) {
        EconomyAccountData data = new EconomyAccountData();
        CompoundTag balancesTag = tag.getCompound("Balances");
        for (String key : balancesTag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                data.balances.put(uuid, new BigDecimal(balancesTag.getString(key)));
            } catch (IllegalArgumentException e) {}
        }
        CompoundTag vaultsTag = tag.getCompound("Vaults");
        for (String key : vaultsTag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                List<VaultRecord> list = new ArrayList<>();
                if (vaultsTag.getTagType(key) == Tag.TAG_LIST) {
                    ListTag listTag = vaultsTag.getList(key, Tag.TAG_COMPOUND);
                    for (int i = 0; i < listTag.size(); i++) {
                        CompoundTag posTag = listTag.getCompound(i);
                        BlockPos pos = new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"));
                        String dim = posTag.contains("Dimension") ? posTag.getString("Dimension") : "minecraft:overworld";
                        list.add(new VaultRecord(pos, dim));
                    }
                } else {
                    CompoundTag posTag = vaultsTag.getCompound(key);
                    BlockPos pos = new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"));
                    String dim = posTag.contains("Dimension") ? posTag.getString("Dimension") : "minecraft:overworld";
                    list.add(new VaultRecord(pos, dim));
                }
                data.vaults.put(uuid, list);
            } catch (Exception e) {}
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag balancesTag = new CompoundTag();
        for (Map.Entry<UUID, BigDecimal> e : balances.entrySet())
            balancesTag.putString(e.getKey().toString(), e.getValue().toPlainString());
        tag.put("Balances", balancesTag);

        CompoundTag vaultsTag = new CompoundTag();
        for (Map.Entry<UUID, List<VaultRecord>> e : vaults.entrySet()) {
            ListTag listTag = new ListTag();
            for (VaultRecord r : e.getValue()) {
                CompoundTag posTag = new CompoundTag();
                posTag.putInt("X", r.pos.getX());
                posTag.putInt("Y", r.pos.getY());
                posTag.putInt("Z", r.pos.getZ());
                posTag.putString("Dimension", r.dimension);
                listTag.add(posTag);
            }
            vaultsTag.put(e.getKey().toString(), listTag);
        }
        tag.put("Vaults", vaultsTag);
        return tag;
    }
}
