package com.nstut.economy.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EconomyAccountData extends SavedData implements com.nstut.economy.core.BalanceStore {

    private static final String NAME = "economy_accounts";

    private static final SavedDataType<EconomyAccountData> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(NAME),
            level -> new EconomyAccountData(),
            level -> CompoundTag.CODEC.xmap(
                    EconomyAccountData::load,
                    data -> data.save(new CompoundTag())));

    public static final class VaultRecord {
        public final BlockPos pos;
        public final String dimension;

        public VaultRecord(BlockPos pos, String dimension) {
            this.pos = pos;
            this.dimension = dimension != null ? dimension : "minecraft:overworld";
        }
    }

    public static final class PortfolioPoint {
        public final long timestamp;
        public final BigDecimal netWorth;
        public final BigDecimal balance;
        public final BigDecimal assets;

        public PortfolioPoint(long timestamp, BigDecimal netWorth, BigDecimal balance, BigDecimal assets) {
            this.timestamp = timestamp;
            this.netWorth = netWorth;
            this.balance = balance;
            this.assets = assets;
        }
    }

    private final Map<UUID, BigDecimal> balances = new HashMap<>();
    private final Map<UUID, List<VaultRecord>> vaults = new HashMap<>();
    private final Map<UUID, List<VaultRecord>> tanks = new HashMap<>();
    private final Map<UUID, List<PortfolioPoint>> portfolioHistory = new HashMap<>();

    public List<PortfolioPoint> getPortfolioHistory(UUID player) {
        return portfolioHistory.getOrDefault(player, java.util.Collections.emptyList());
    }

    public void addPortfolioPoint(UUID player, BigDecimal balance, BigDecimal assets) {
        List<PortfolioPoint> list = portfolioHistory.computeIfAbsent(player, k -> new ArrayList<>());
        long now = System.currentTimeMillis();
        BigDecimal netWorth = balance.add(assets);
        if (!list.isEmpty()) {
            PortfolioPoint last = list.get(list.size() - 1);
            if (last.netWorth.compareTo(netWorth) == 0 &&
                last.balance.compareTo(balance) == 0 &&
                last.assets.compareTo(assets) == 0) {
                return; // Values haven't changed; ignore duplicate snapshot
            }
        }
        list.add(new PortfolioPoint(now, netWorth, balance, assets));
        while (list.size() > 40) {
            list.remove(0);
        }
        setDirty();
    }

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

    public Map<UUID, List<VaultRecord>> getTanks() { return tanks; }
    public void addTank(UUID owner, BlockPos pos, String dimension) {
        List<VaultRecord> list = tanks.computeIfAbsent(owner, k -> new ArrayList<>());
        BlockPos p = pos.immutable();
        String dim = dimension != null ? dimension : "minecraft:overworld";
        for (VaultRecord r : list) {
            if (r.pos.equals(p) && r.dimension.equals(dim)) return;
        }
        list.add(new VaultRecord(p, dim));
        setDirty();
    }
    public void removeTank(UUID owner, BlockPos pos, String dimension) {
        List<VaultRecord> list = tanks.get(owner);
        if (list != null) {
            BlockPos p = pos.immutable();
            String dim = dimension != null ? dimension : "minecraft:overworld";
            if (list.removeIf(r -> r.pos.equals(p) && r.dimension.equals(dim))) {
                setDirty();
            }
        }
    }
    public boolean hasTank(UUID owner) { return tanks.containsKey(owner) && !tanks.get(owner).isEmpty(); }

    private void loadVaultRecords(CompoundTag vaultsTag, Map<UUID, List<VaultRecord>> target) {
        for (String key : vaultsTag.keySet()) {
            try {
                UUID uuid = UUID.fromString(key);
                List<VaultRecord> list = new ArrayList<>();
                if (vaultsTag.get(key) instanceof ListTag) {
                    ListTag listTag = vaultsTag.getListOrEmpty(key);
                    for (int i = 0; i < listTag.size(); i++) {
                        CompoundTag posTag = listTag.getCompoundOrEmpty(i);
                        BlockPos pos = new BlockPos(posTag.getIntOr("X", 0), posTag.getIntOr("Y", 0), posTag.getIntOr("Z", 0));
                        String dim = posTag.contains("Dimension") ? posTag.getStringOr("Dimension", "") : "minecraft:overworld";
                        list.add(new VaultRecord(pos, dim));
                    }
                } else {
                    CompoundTag posTag = vaultsTag.getCompoundOrEmpty(key);
                    BlockPos pos = new BlockPos(posTag.getIntOr("X", 0), posTag.getIntOr("Y", 0), posTag.getIntOr("Z", 0));
                    String dim = posTag.contains("Dimension") ? posTag.getStringOr("Dimension", "") : "minecraft:overworld";
                    list.add(new VaultRecord(pos, dim));
                }
                target.put(uuid, list);
            } catch (Exception e) {}
        }
    }

    public static EconomyAccountData get(net.minecraft.server.level.ServerLevel level) {
        net.minecraft.server.level.ServerLevel target = (level != null && level.getServer() != null) ? level.getServer().overworld() : level;
        return target.getDataStorage().computeIfAbsent(TYPE);
    }

    public static EconomyAccountData load(CompoundTag tag) {
        EconomyAccountData data = new EconomyAccountData();
        CompoundTag balancesTag = tag.getCompoundOrEmpty("Balances");
        for (String key : balancesTag.keySet()) {
            try {
                UUID uuid = UUID.fromString(key);
                data.balances.put(uuid, new BigDecimal(balancesTag.getStringOr(key, "")));
            } catch (IllegalArgumentException e) {}
        }
        data.loadVaultRecords(tag.getCompoundOrEmpty("Vaults"), data.vaults);
        data.loadVaultRecords(tag.getCompoundOrEmpty("Tanks"), data.tanks);

        CompoundTag historyTag = tag.getCompoundOrEmpty("PortfolioHistory");
        for (String key : historyTag.keySet()) {
            try {
                UUID uuid = UUID.fromString(key);
                ListTag listTag = historyTag.getListOrEmpty(key);
                List<PortfolioPoint> list = new ArrayList<>();
                for (int i = 0; i < listTag.size(); i++) {
                    CompoundTag ptTag = listTag.getCompoundOrEmpty(i);
                    long ts = ptTag.getLongOr("TS", 0L);
                    BigDecimal nw = new BigDecimal(ptTag.getStringOr("NW", ""));
                    BigDecimal bal = new BigDecimal(ptTag.getStringOr("BAL", ""));
                    BigDecimal ass = new BigDecimal(ptTag.getStringOr("ASS", ""));
                    list.add(new PortfolioPoint(ts, nw, bal, ass));
                }
                data.portfolioHistory.put(uuid, list);
            } catch (Exception e) {}
        }
        return data;
    }

    public static void recordSnapshot(UUID player, net.minecraft.server.level.ServerLevel level) {
        if (player == null || level == null) return;
        EconomyAccountData accountData = get(level);
        BigDecimal balance = accountData.getBalance(player);

        BigDecimal assetValue = BigDecimal.ZERO;
        List<com.nstut.economy.blocks.VaultBlockEntity> vaults = com.nstut.economy.blocks.VaultManager.getVaults(level, player);
        Map<String, Integer> itemCounts = new HashMap<>();
        for (com.nstut.economy.blocks.VaultBlockEntity vault : vaults) {
            for (int slot = 0; slot < vault.getContainerSize(); slot++) {
                net.minecraft.world.item.ItemStack stack = vault.getItem(slot);
                if (!stack.isEmpty()) {
                    String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    itemCounts.put(itemId, itemCounts.getOrDefault(itemId, 0) + stack.getCount());
                }
            }
        }
        for (com.nstut.economy.blocks.TankBlockEntity tank
                : com.nstut.economy.blocks.TankManager.getTanks(level, player)) {
            com.nstut.economy.trading.EconomyFluidStack fluid = tank.getFluid();
            if (!fluid.isEmpty()) {
                String fluidId = net.minecraft.core.registries.BuiltInRegistries.FLUID
                        .getKey(fluid.getFluid()).toString();
                itemCounts.put(fluidId, itemCounts.getOrDefault(fluidId, 0) + fluid.getAmount());
            }
        }

        com.nstut.economy.data.EconomyTradeData historyData = com.nstut.economy.data.EconomyTradeData.get(level);
        for (Map.Entry<String, Integer> entry : itemCounts.entrySet()) {
            String id = entry.getKey();
            int qty = entry.getValue();
            BigDecimal unitPrice = BigDecimal.ZERO;
            List<com.nstut.economy.data.EconomyTradeData.TradeSnapshot> trades = historyData.getTrades();
            for (int i = trades.size() - 1; i >= 0; i--) {
                if (trades.get(i).itemId.equalsIgnoreCase(id)) {
                    unitPrice = new BigDecimal(trades.get(i).price);
                    break;
                }
            }
            assetValue = assetValue.add(unitPrice.multiply(BigDecimal.valueOf(qty)));
        }

        accountData.addPortfolioPoint(player, balance, assetValue);
    }

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

        CompoundTag tanksTag = new CompoundTag();
        for (Map.Entry<UUID, List<VaultRecord>> e : tanks.entrySet()) {
            ListTag listTag = new ListTag();
            for (VaultRecord r : e.getValue()) {
                CompoundTag posTag = new CompoundTag();
                posTag.putInt("X", r.pos.getX());
                posTag.putInt("Y", r.pos.getY());
                posTag.putInt("Z", r.pos.getZ());
                posTag.putString("Dimension", r.dimension);
                listTag.add(posTag);
            }
            tanksTag.put(e.getKey().toString(), listTag);
        }
        tag.put("Tanks", tanksTag);

        CompoundTag historyTag = new CompoundTag();
        for (Map.Entry<UUID, List<PortfolioPoint>> e : portfolioHistory.entrySet()) {
            ListTag listTag = new ListTag();
            for (PortfolioPoint pt : e.getValue()) {
                CompoundTag ptTag = new CompoundTag();
                ptTag.putLong("TS", pt.timestamp);
                ptTag.putString("NW", pt.netWorth.toPlainString());
                ptTag.putString("BAL", pt.balance.toPlainString());
                ptTag.putString("ASS", pt.assets.toPlainString());
                listTag.add(ptTag);
            }
            historyTag.put(e.getKey().toString(), listTag);
        }
        tag.put("PortfolioHistory", historyTag);
        return tag;
    }
}

