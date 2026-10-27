package com.nstut.economy.data;

import com.nstut.economy.api.AccountKind;
import com.nstut.economy.api.AccountRef;
import com.nstut.economy.api.internal.TeamWalletState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EconomyAccountData extends SavedData implements com.nstut.economy.core.BalanceStore {

    private static final String NAME = "economy_accounts";

    private final com.nstut.economy.core.PortfolioStoreCache portfolioStores = new com.nstut.economy.core.PortfolioStoreCache(this::setDirty);
    public com.nstut.economy.core.PortfolioStoreCache portfolioStores() { return portfolioStores; }

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
        public final String operationId, source, reason;
        public final BigDecimal cashDelta, externalFlow;
        public final boolean attributed;
        /** This mark preserves an unallocated legacy total while unloaded stores remain unknown. */
        public final boolean legacyAssetFallback;

        public PortfolioPoint(long timestamp, BigDecimal netWorth, BigDecimal balance, BigDecimal assets) {
            this(timestamp, netWorth, balance, assets, "", "legacy", "Legacy snapshot", BigDecimal.ZERO, BigDecimal.ZERO, false, true);
        }

        public PortfolioPoint(long timestamp, BigDecimal netWorth, BigDecimal balance, BigDecimal assets,
                              String operationId, String source, String reason, BigDecimal cashDelta,
                              BigDecimal externalFlow, boolean attributed) {
            this(timestamp, netWorth, balance, assets, operationId, source, reason, cashDelta,
                    externalFlow, attributed, false);
        }

        public PortfolioPoint(long timestamp, BigDecimal netWorth, BigDecimal balance, BigDecimal assets,
                              String operationId, String source, String reason, BigDecimal cashDelta,
                              BigDecimal externalFlow, boolean attributed, boolean legacyAssetFallback) {
            this.legacyAssetFallback = legacyAssetFallback;
            this.timestamp = timestamp; this.netWorth = netWorth; this.balance = balance; this.assets = assets;
            this.operationId = operationId; this.source = source; this.reason = reason;
            this.cashDelta = cashDelta; this.externalFlow = externalFlow; this.attributed = attributed;
        }
    }

    private transient net.minecraft.server.level.ServerLevel portfolioLevel;
    private static final int MAX_PORTFOLIO_POINTS = 1024;

    @Override public void recordMutation(AccountRef account, com.nstut.economy.core.BalanceMutation mutation) {
        List<PortfolioPoint> current = getPortfolioHistory(account);
        if (current.stream().anyMatch(point -> point.operationId.equals(mutation.operationId().toString()))) return;
        BigDecimal assets = current.isEmpty() ? BigDecimal.ZERO : current.get(current.size() - 1).assets;
        boolean legacyAssetFallback = !current.isEmpty() && current.get(current.size() - 1).legacyAssetFallback;
        if (portfolioLevel != null) {
            try {
                var mark = PortfolioValuation.assetMark(portfolioLevel, account);
                assets = mark.assets();
                legacyAssetFallback = mark.legacyAssetFallback();
            }
            catch (RuntimeException failure) {
                com.nstut.Economy.LOGGER.error("Portfolio valuation failed; retaining last asset mark for {}", account, failure);
            }
        }
        List<PortfolioPoint> list = account.kind() == AccountKind.PLAYER
                ? portfolioHistory.computeIfAbsent(account.id(), key -> new ArrayList<>())
                : typedPortfolioHistory.computeIfAbsent(account, key -> new ArrayList<>());
        // Deferred independent operations share a final live asset observation. Pair it with
        // the final saved cash balance; reconstructed intermediate cash would invent net worth.
        BigDecimal observedBalance = mutation.groupedFinalMark()
                ? getAccountBalances().getOrDefault(account, BigDecimal.ZERO) : mutation.after();
        list.add(new PortfolioPoint(mutation.timestamp(), observedBalance.add(assets), observedBalance, assets,
                mutation.operationId().toString(), mutation.source(), mutation.reason(), mutation.cashDelta(),
                mutation.externalFlow(), true, legacyAssetFallback));
        compactPortfolio(list);
        setDirty();
    }

    /** Keep recent observations plus an explicit older-history checkpoint with preserved cash flows. */
    private static void compactPortfolio(List<PortfolioPoint> list) {
        while (list.size() > MAX_PORTFOLIO_POINTS) {
            PortfolioPoint first = list.remove(0), second = list.get(0);
            list.set(0, new PortfolioPoint(second.timestamp, second.netWorth, second.balance, second.assets,
                    "", "economy:rollup", "Earlier history aggregated; cash flows preserved",
                    first.cashDelta.add(second.cashDelta), first.externalFlow.add(second.externalFlow),
                    first.attributed && second.attributed, second.legacyAssetFallback));
        }
    }

    private final Map<UUID, TeamWalletState> teamWallets = new HashMap<>();
    public Map<UUID, TeamWalletState> getTeamWallets() { return Map.copyOf(teamWallets); }
    public void putTeamWallet(TeamWalletState state) {
        if (!state.equals(teamWallets.put(state.teamId(), state))) setDirty();
    }

    private final Map<UUID, BigDecimal> balances = new HashMap<>();
    /** Non-player principals; legacy PLAYER balances remain in {@link #balances}. */
    private final Map<AccountRef, BigDecimal> typedBalances = new HashMap<>();
    private final Map<UUID, List<VaultRecord>> vaults = new HashMap<>();
    private final Map<UUID, List<VaultRecord>> tanks = new HashMap<>();
    private final Map<AccountRef, List<VaultRecord>> typedVaults = new HashMap<>();
    private final Map<AccountRef, List<VaultRecord>> typedTanks = new HashMap<>();
    private final Map<UUID, List<PortfolioPoint>> portfolioHistory = new HashMap<>();
    private final Map<AccountRef, List<PortfolioPoint>> typedPortfolioHistory = new HashMap<>();

    public List<PortfolioPoint> getPortfolioHistory(UUID player) {
        return getPortfolioHistory(AccountRef.player(player));
    }

    public List<PortfolioPoint> getPortfolioHistory(AccountRef account) {
        if (account == null) return java.util.Collections.emptyList();
        return account.kind() == AccountKind.PLAYER
                ? List.copyOf(portfolioHistory.getOrDefault(account.id(), java.util.Collections.emptyList()))
                : List.copyOf(typedPortfolioHistory.getOrDefault(account, java.util.Collections.emptyList()));
    }

    public void addPortfolioPoint(UUID player, BigDecimal balance, BigDecimal assets) {
        addPortfolioPoint(AccountRef.player(player), balance, assets);
    }

    public void addPortfolioPoint(AccountRef account, BigDecimal balance, BigDecimal assets) {
        addPortfolioPoint(account, balance, assets, false);
    }

    public void addPortfolioPoint(AccountRef account, BigDecimal balance, BigDecimal assets, boolean legacyAssetFallback) {
        if (account == null) return;
        List<PortfolioPoint> list = account.kind() == AccountKind.PLAYER
                ? portfolioHistory.computeIfAbsent(account.id(), k -> new ArrayList<>())
                : typedPortfolioHistory.computeIfAbsent(account, k -> new ArrayList<>());
        long now = System.currentTimeMillis();
        BigDecimal netWorth = balance.add(assets);
        if (!list.isEmpty()) {
            PortfolioPoint last = list.get(list.size() - 1);
            if (last.netWorth.compareTo(netWorth) == 0 &&
                last.balance.compareTo(balance) == 0 &&
                last.assets.compareTo(assets) == 0 && last.legacyAssetFallback == legacyAssetFallback) {
                return;
            }
        }
        list.add(new PortfolioPoint(now, netWorth, balance, assets, "", "economy:valuation", "Valuation refresh", BigDecimal.ZERO, BigDecimal.ZERO, true, legacyAssetFallback));
        compactPortfolio(list);
        setDirty();
    }


    public Map<UUID, BigDecimal> getBalances() { return balances; }
    public void setBalance(UUID player, BigDecimal balance) { balances.put(player, balance); setDirty(); }
    @Override
    public void removeBalance(UUID player) { if (balances.remove(player) != null) setDirty(); }
    public BigDecimal getBalance(UUID player) { return balances.getOrDefault(player, BigDecimal.ZERO); }

    @Override
    public boolean supportsTypedAccounts() { return true; }

    @Override
    public Map<AccountRef, BigDecimal> getAccountBalances() {
        Map<AccountRef, BigDecimal> result = new HashMap<>();
        balances.forEach((id, balance) -> result.put(AccountRef.player(id), balance));
        result.putAll(typedBalances);
        return result;
    }

    @Override
    public void setAccountBalance(AccountRef account, BigDecimal balance) {
        if (account.kind() == AccountKind.PLAYER) {
            setBalance(account.id(), balance);
        } else {
            typedBalances.put(account, balance);
            setDirty();
        }
    }

    @Override
    public void removeAccountBalance(AccountRef account) {
        if (account.kind() == AccountKind.PLAYER) {
            removeBalance(account.id());
        } else if (typedBalances.remove(account) != null) {
            setDirty();
        }
    }

    public Map<UUID, List<VaultRecord>> getVaults() { return vaults; }
    public Map<AccountRef, List<VaultRecord>> getTypedVaults() { return typedVaults; }
    public Map<AccountRef, List<VaultRecord>> getStorageVaults() {
        Map<AccountRef, List<VaultRecord>> result = new HashMap<>();
        vaults.forEach((id, records) -> result.put(AccountRef.player(id), new ArrayList<>(records)));
        typedVaults.forEach((owner, records) -> result.put(owner, new ArrayList<>(records)));
        return result;
    }
    public void addVault(AccountRef owner, BlockPos pos, String dimension) {
        if (owner.kind() == AccountKind.PLAYER) { addVault(owner.id(), pos, dimension); return; }
        addTypedStorage(typedVaults, owner, pos, dimension);
    }
    public void removeVault(AccountRef owner, BlockPos pos, String dimension) {
        if (owner.kind() == AccountKind.PLAYER) { removeVault(owner.id(), pos, dimension); return; }
        removeTypedStorage(typedVaults, owner, pos, dimension);
    }
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
    public Map<AccountRef, List<VaultRecord>> getTypedTanks() { return typedTanks; }
    public Map<AccountRef, List<VaultRecord>> getStorageTanks() {
        Map<AccountRef, List<VaultRecord>> result = new HashMap<>();
        tanks.forEach((id, records) -> result.put(AccountRef.player(id), new ArrayList<>(records)));
        typedTanks.forEach((owner, records) -> result.put(owner, new ArrayList<>(records)));
        return result;
    }
    public void addTank(AccountRef owner, BlockPos pos, String dimension) {
        if (owner.kind() == AccountKind.PLAYER) { addTank(owner.id(), pos, dimension); return; }
        addTypedStorage(typedTanks, owner, pos, dimension);
    }
    public void removeTank(AccountRef owner, BlockPos pos, String dimension) {
        if (owner.kind() == AccountKind.PLAYER) { removeTank(owner.id(), pos, dimension); return; }
        removeTypedStorage(typedTanks, owner, pos, dimension);
    }
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

    private void addTypedStorage(Map<AccountRef, List<VaultRecord>> target, AccountRef owner, BlockPos pos, String dimension) {
        List<VaultRecord> list = target.computeIfAbsent(owner, k -> new ArrayList<>());
        BlockPos p = pos.immutable();
        String dim = dimension != null ? dimension : "minecraft:overworld";
        for (VaultRecord r : list) if (r.pos.equals(p) && r.dimension.equals(dim)) return;
        list.add(new VaultRecord(p, dim));
        setDirty();
    }
    private void removeTypedStorage(Map<AccountRef, List<VaultRecord>> target, AccountRef owner, BlockPos pos, String dimension) {
        List<VaultRecord> list = target.get(owner);
        if (list == null) return;
        BlockPos p = pos.immutable();
        String dim = dimension != null ? dimension : "minecraft:overworld";
        if (list.removeIf(r -> r.pos.equals(p) && r.dimension.equals(dim))) {
            if (list.isEmpty()) target.remove(owner);
            setDirty();
        }
    }

    private void loadVaultRecords(CompoundTag vaultsTag, Map<UUID, List<VaultRecord>> target) {
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
                target.put(uuid, list);
            } catch (Exception e) {}
        }
    }

    private void loadTypedVaultRecords(CompoundTag ownersTag, Map<AccountRef, List<VaultRecord>> target) {
        for (String key : ownersTag.getAllKeys()) {
            try {
                AccountRef owner = AccountRef.parse(key);
                if (owner.kind() == AccountKind.PLAYER) continue;
                List<VaultRecord> list = new ArrayList<>();
                ListTag listTag = ownersTag.getList(key, Tag.TAG_COMPOUND);
                for (int i = 0; i < listTag.size(); i++) {
                    CompoundTag posTag = listTag.getCompound(i);
                    BlockPos pos = new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"));
                    String dim = posTag.contains("Dimension") ? posTag.getString("Dimension") : "minecraft:overworld";
                    list.add(new VaultRecord(pos, dim));
                }
                target.put(owner, list);
            } catch (RuntimeException ignored) {}
        }
    }

    public static EconomyAccountData get(net.minecraft.server.level.ServerLevel level) {
        net.minecraft.server.level.ServerLevel target = (level != null && level.getServer() != null) ? level.getServer().overworld() : level;
        EconomyAccountData data = target.getDataStorage().computeIfAbsent(new SavedData.Factory<>(EconomyAccountData::new, EconomyAccountData::load, null), NAME);
        data.portfolioLevel = target;
        return data;
    }

    private static void readTeamWallets(CompoundTag tag, EconomyAccountData data) {
        ListTag list = tag.getList("TeamWallets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                UUID team = UUID.fromString(entry.getString("Team"));
                UUID owner = UUID.fromString(entry.getString("Owner"));
                java.util.List<UUID> members = readUuidKeys(entry.getCompound("SettlementMembers"));
                java.util.Set<UUID> settled = java.util.Set.copyOf(readUuidKeys(entry.getCompound("SettledMembers")));
                data.teamWallets.put(team, new TeamWalletState(team, owner,
                        TeamWalletState.readProviderId(entry.getString("Provider")),
                        entry.getBoolean("Closing"), entry.getBoolean("StorageSettled"), members, settled));
            } catch (RuntimeException ignored) {}
        }
    }

    private static java.util.List<UUID> readUuidKeys(CompoundTag tag) {
        java.util.ArrayList<UUID> values = new java.util.ArrayList<>();
        for (String key : tag.getAllKeys()) {
            try { values.add(UUID.fromString(key)); } catch (IllegalArgumentException ignored) {}
        }
        values.sort(UUID::compareTo);
        return java.util.List.copyOf(values);
    }

    private static CompoundTag writeUuidKeys(java.util.Collection<UUID> values) {
        CompoundTag tag = new CompoundTag();
        if (values != null) for (UUID value : values) if (value != null) tag.putBoolean(value.toString(), true);
        return tag;
    }

    private void writeTeamWallets(CompoundTag tag) {
        ListTag list = new ListTag();
        for (TeamWalletState state : teamWallets.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Team", state.teamId().toString());
            entry.putString("Owner", state.ownerId().toString());
            entry.putString("Provider", state.providerId().toString());
            entry.putBoolean("Closing", state.closing());
            entry.putBoolean("StorageSettled", state.storageSettled());
            entry.put("SettlementMembers", writeUuidKeys(state.settlementMembers()));
            entry.put("SettledMembers", writeUuidKeys(state.settledMembers()));
            list.add(entry);
        }
        tag.put("TeamWallets", list);
    }

    public static EconomyAccountData load(CompoundTag tag, HolderLookup.Provider registries) {
        EconomyAccountData data = new EconomyAccountData();
        readTeamWallets(tag, data);
        data.portfolioStores.decode(tag.getByteArray("PortfolioStores"));
        CompoundTag balancesTag = tag.getCompound("Balances");
        for (String key : balancesTag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                data.balances.put(uuid, new BigDecimal(balancesTag.getString(key)));
            } catch (IllegalArgumentException e) {}
        }
        ListTag typedBalancesTag = tag.getList("TypedBalances", Tag.TAG_COMPOUND);
        for (int i = 0; i < typedBalancesTag.size(); i++) {
            CompoundTag entry = typedBalancesTag.getCompound(i);
            try {
                AccountKind kind = AccountKind.valueOf(entry.getString("Kind"));
                UUID id = UUID.fromString(entry.getString("Id"));
                BigDecimal balance = new BigDecimal(entry.getString("Balance"));
                if (kind != AccountKind.PLAYER) data.typedBalances.put(new AccountRef(kind, id), balance);
            } catch (RuntimeException ignored) {}
        }
        data.loadVaultRecords(tag.getCompound("Vaults"), data.vaults);
        data.loadVaultRecords(tag.getCompound("Tanks"), data.tanks);
        data.loadTypedVaultRecords(tag.getCompound("TypedVaults"), data.typedVaults);
        data.loadTypedVaultRecords(tag.getCompound("TypedTanks"), data.typedTanks);

        CompoundTag historyTag = tag.getCompound("PortfolioHistory");
        for (String key : historyTag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                ListTag listTag = historyTag.getList(key, Tag.TAG_COMPOUND);
                List<PortfolioPoint> list = new ArrayList<>();
                for (int i = 0; i < listTag.size(); i++) {
                    CompoundTag ptTag = listTag.getCompound(i);
                    long ts = ptTag.getLong("TS");
                    BigDecimal nw = new BigDecimal(ptTag.getString("NW"));
                    BigDecimal bal = new BigDecimal(ptTag.getString("BAL"));
                    BigDecimal ass = new BigDecimal(ptTag.getString("ASS"));
                    list.add(readPortfolioPoint(ptTag, ts, nw, bal, ass));
                }
                compactPortfolio(list);
                data.portfolioHistory.put(uuid, list);
            } catch (Exception e) {}
        }
        CompoundTag typedHistoryTag = tag.getCompound("TypedPortfolioHistory");
        for (String key : typedHistoryTag.getAllKeys()) {
            try {
                AccountRef account = AccountRef.parse(key);
                if (account.kind() == AccountKind.PLAYER) continue;
                ListTag listTag = typedHistoryTag.getList(key, Tag.TAG_COMPOUND);
                List<PortfolioPoint> list = new ArrayList<>();
                for (int i = 0; i < listTag.size(); i++) {
                    CompoundTag ptTag = listTag.getCompound(i);
                    long ts = ptTag.getLong("TS");
                    BigDecimal nw = new BigDecimal(ptTag.getString("NW"));
                    BigDecimal bal = new BigDecimal(ptTag.getString("BAL"));
                    BigDecimal ass = new BigDecimal(ptTag.getString("ASS"));
                    list.add(readPortfolioPoint(ptTag, ts, nw, bal, ass));
                }
                compactPortfolio(list);
                data.typedPortfolioHistory.put(account, list);
            } catch (RuntimeException ignored) {}
        }
        return data;
    }

    private static PortfolioPoint readPortfolioPoint(CompoundTag tag, long ts, BigDecimal nw, BigDecimal bal, BigDecimal ass) {
        if (!tag.contains("Operation")) return new PortfolioPoint(ts, nw, bal, ass, "", "legacy", "Legacy snapshot",
                BigDecimal.ZERO, BigDecimal.ZERO, false,
                !tag.contains("LegacyAssetFallback") || tag.getBoolean("LegacyAssetFallback"));
        return new PortfolioPoint(ts, nw, bal, ass, tag.getString("Operation"), tag.getString("Source"),
                tag.getString("Reason"), new BigDecimal(tag.getString("CashDelta")),
                new BigDecimal(tag.getString("ExternalFlow")), tag.getBoolean("Attributed"), tag.getBoolean("LegacyAssetFallback"));
    }

    public static void recordSnapshot(UUID player, net.minecraft.server.level.ServerLevel level) {
        if (player == null) return;
        recordSnapshot(AccountRef.player(player), level);
    }

    public static void recordSnapshot(AccountRef account, net.minecraft.server.level.ServerLevel level) {
        if (account == null || level == null) return;
        EconomyAccountData accountData = get(level);
        BigDecimal balance = accountData.getAccountBalances().getOrDefault(account, BigDecimal.ZERO);

        var mark = PortfolioValuation.assetMark(level, account);

        accountData.addPortfolioPoint(account, balance, mark.assets(), mark.legacyAssetFallback());
    }

    static String portfolioCommodityId(net.minecraft.core.HolderLookup.Provider registries,
                                       net.minecraft.world.item.ItemStack stack) {
        return com.nstut.economy.trading.ItemCommodity
                .identityFromItemStack(registries, stack, BigDecimal.ZERO)
                .getId().toString();
    }

    public static BigDecimal assetValue(AccountRef account, net.minecraft.server.level.ServerLevel level) {
        return PortfolioValuation.assets(level, account);
    }

    static BigDecimal valueHoldings(Map<String, Integer> holdings,
                                    List<com.nstut.economy.data.EconomyTradeData.TradeSnapshot> trades) {
        return PortfolioValuation.valueHoldings(holdings, trades);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        writeTeamWallets(tag);
        tag.putByteArray("PortfolioStores", portfolioStores.encode());
        CompoundTag balancesTag = new CompoundTag();
        for (Map.Entry<UUID, BigDecimal> e : balances.entrySet())
            balancesTag.putString(e.getKey().toString(), e.getValue().toPlainString());
        tag.put("Balances", balancesTag);

        ListTag typedBalancesTag = new ListTag();
        for (Map.Entry<AccountRef, BigDecimal> e : typedBalances.entrySet()) {
            if (e.getKey().kind() == AccountKind.PLAYER) continue;
            CompoundTag entry = new CompoundTag();
            entry.putString("Kind", e.getKey().kind().name());
            entry.putString("Id", e.getKey().id().toString());
            entry.putString("Balance", e.getValue().toPlainString());
            typedBalancesTag.add(entry);
        }
        tag.put("TypedBalances", typedBalancesTag);

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

        CompoundTag typedVaultsTag = new CompoundTag();
        for (Map.Entry<AccountRef, List<VaultRecord>> e : typedVaults.entrySet()) {
            ListTag listTag = new ListTag();
            for (VaultRecord r : e.getValue()) {
                CompoundTag posTag = new CompoundTag();
                posTag.putInt("X", r.pos.getX());
                posTag.putInt("Y", r.pos.getY());
                posTag.putInt("Z", r.pos.getZ());
                posTag.putString("Dimension", r.dimension);
                listTag.add(posTag);
            }
            typedVaultsTag.put(e.getKey().toString(), listTag);
        }
        tag.put("TypedVaults", typedVaultsTag);

        CompoundTag typedTanksTag = new CompoundTag();
        for (Map.Entry<AccountRef, List<VaultRecord>> e : typedTanks.entrySet()) {
            ListTag listTag = new ListTag();
            for (VaultRecord r : e.getValue()) {
                CompoundTag posTag = new CompoundTag();
                posTag.putInt("X", r.pos.getX());
                posTag.putInt("Y", r.pos.getY());
                posTag.putInt("Z", r.pos.getZ());
                posTag.putString("Dimension", r.dimension);
                listTag.add(posTag);
            }
            typedTanksTag.put(e.getKey().toString(), listTag);
        }
        tag.put("TypedTanks", typedTanksTag);

        CompoundTag historyTag = new CompoundTag();
        for (Map.Entry<UUID, List<PortfolioPoint>> e : portfolioHistory.entrySet()) {
            ListTag listTag = new ListTag();
            for (PortfolioPoint pt : e.getValue()) {
                CompoundTag ptTag = new CompoundTag();
                ptTag.putLong("TS", pt.timestamp);
                ptTag.putString("NW", pt.netWorth.toPlainString());
                ptTag.putString("BAL", pt.balance.toPlainString());
                ptTag.putString("ASS", pt.assets.toPlainString());
                ptTag.putBoolean("LegacyAssetFallback", pt.legacyAssetFallback);
                if (pt.attributed || pt.source.equals("economy:rollup")) {
                    ptTag.putString("Operation", pt.operationId);
                    ptTag.putBoolean("Attributed", pt.attributed);
                    ptTag.putString("Source", pt.source);
                    ptTag.putString("Reason", pt.reason);
                    ptTag.putString("CashDelta", pt.cashDelta.toPlainString());
                    ptTag.putString("ExternalFlow", pt.externalFlow.toPlainString());
                }
                listTag.add(ptTag);
            }
            historyTag.put(e.getKey().toString(), listTag);
        }
        tag.put("PortfolioHistory", historyTag);

        CompoundTag typedHistoryTag = new CompoundTag();
        for (Map.Entry<AccountRef, List<PortfolioPoint>> e : typedPortfolioHistory.entrySet()) {
            if (e.getKey().kind() == AccountKind.PLAYER) continue;
            ListTag listTag = new ListTag();
            for (PortfolioPoint pt : e.getValue()) {
                CompoundTag ptTag = new CompoundTag();
                ptTag.putLong("TS", pt.timestamp);
                ptTag.putString("NW", pt.netWorth.toPlainString());
                ptTag.putString("BAL", pt.balance.toPlainString());
                ptTag.putString("ASS", pt.assets.toPlainString());
                ptTag.putBoolean("LegacyAssetFallback", pt.legacyAssetFallback);
                if (pt.attributed || pt.source.equals("economy:rollup")) {
                    ptTag.putString("Operation", pt.operationId);
                    ptTag.putBoolean("Attributed", pt.attributed);
                    ptTag.putString("Source", pt.source);
                    ptTag.putString("Reason", pt.reason);
                    ptTag.putString("CashDelta", pt.cashDelta.toPlainString());
                    ptTag.putString("ExternalFlow", pt.externalFlow.toPlainString());
                }
                listTag.add(ptTag);
            }
            typedHistoryTag.put(e.getKey().toString(), listTag);
        }
        tag.put("TypedPortfolioHistory", typedHistoryTag);
        return tag;
    }
}

