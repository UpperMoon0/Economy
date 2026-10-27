package com.nstut.economy.data;

import com.nstut.economy.api.AccountRef;
import com.nstut.economy.api.EconomyApi;
import com.nstut.economy.api.IStorageProvider;
import com.nstut.economy.api.internal.BuiltinContainerStorageProvider;
import com.nstut.economy.trading.ItemCommodity;
import com.nstut.economy.trading.OrderManager;
import net.minecraft.server.level.ServerLevel;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared valuation for all supported Minecraft versions; never mutates storage or orders. */
public final class PortfolioValuation {
    private PortfolioValuation() {}
    private record Cached(long observed, Map<String, Long> stored, Map<String, Long> owned,
                          BigDecimal assets, String status, boolean legacyAssetFallback) { }
    private static final Map<EconomyAccountData, Map<AccountRef, Cached>> CACHE =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static Cached snapshot(ServerLevel level, AccountRef account) {
        EconomyAccountData data = EconomyAccountData.get(level);
        Map<AccountRef, Cached> accounts = CACHE.computeIfAbsent(data, ignored -> new HashMap<>());
        long now = System.currentTimeMillis();
        Cached existing = accounts.get(account);
        if (existing != null && now >= existing.observed && now - existing.observed < 1000) return existing;
        List<EconomyOrderData.OrderSnapshot> orders = orders(level, account);
        Map<String, Long> stored = storedHoldings(level, account, EconomyApi.storage().providers());
        Map<String, Long> owned = new HashMap<>(stored);
        addEscrow(level, account, owned, orders);
        BigDecimal value = valueHoldings(owned, EconomyTradeData.get(level).getTrades()).add(compensation(account, orders));
        var stores = BuiltinContainerStorageProvider.portfolioSnapshot(level, account);
        String status = stores.status();
        List<EconomyAccountData.PortfolioPoint> history = data.getPortfolioHistory(account);
        boolean legacyAssetFallback = legacyAssetFallback(stores.unknown(), history);
        value = markedAssets(value, stores.unknown(), history);
        if (stores.unknown() > 0) status += legacyAssetFallback
                ? "; stale previous total asset mark (unknown stores)"
                : "; incomplete known-only total";
        Cached next = new Cached(now, stored, Map.copyOf(owned), value, status, legacyAssetFallback);
        accounts.put(account, next);
        return next;
    }

    static BigDecimal markedAssets(BigDecimal known, int unknown, List<EconomyAccountData.PortfolioPoint> history) {
        return legacyAssetFallback(unknown, history) ? history.get(history.size() - 1).assets : known;
    }

    static boolean legacyAssetFallback(int unknown, List<EconomyAccountData.PortfolioPoint> history) {
        return unknown > 0 && !history.isEmpty() && history.get(history.size() - 1).legacyAssetFallback;
    }

    /** Value and provenance always describe the same cached observation. */
    public record AssetMark(BigDecimal assets, boolean legacyAssetFallback) { }

    public static AssetMark assetMark(ServerLevel level, AccountRef account) {
        Cached current = snapshot(level, account);
        return new AssetMark(current.assets, current.legacyAssetFallback);
    }

    public static Map<String, Long> holdings(ServerLevel level, AccountRef account) {
        return snapshot(level, account).owned;
    }

    /** Goods in provider stores, excluding owned reservation/recovery escrow. */
    public static Map<String, Long> storedHoldings(ServerLevel level, AccountRef account) {
        return snapshot(level, account).stored;
    }

    public static String status(ServerLevel level, AccountRef account) { return snapshot(level, account).status; }

    /** Invalidate only the cheap owner result; loaded-store marks remain reusable. */
    public static void invalidate(ServerLevel level, AccountRef account) {
        safe(() -> {
            var accounts = CACHE.get(EconomyAccountData.get(level));
            if (accounts != null) accounts.remove(account);
        });
    }

    /** Topology changed: re-enumerate records at next observation without discarding known marks. */
    public static void invalidateStores(ServerLevel level, AccountRef account) {
        safe(() -> {
            EconomyAccountData.get(level).portfolioStores().invalidate(account);
            invalidate(level, account);
        });
    }

    public static void storageChanged(ServerLevel level, AccountRef owner, String kind, String dimension, net.minecraft.core.BlockPos pos) {
        if (!EconomyApi.isReady() || level == null || owner == null) return;
        safe(() -> {
            BuiltinContainerStorageProvider.storageChanged(level, owner, kind, dimension, pos);
            invalidate(level, owner);
        });
    }

    public static void storeOwnerChanged(ServerLevel level, AccountRef owner, String kind, String dimension, net.minecraft.core.BlockPos pos) {
        if (!EconomyApi.isReady() || level == null || owner == null) return;
        safe(() -> {
            var data = EconomyAccountData.get(level);
            data.portfolioStores().reassign(BuiltinContainerStorageProvider.storeKey(kind, dimension, pos), owner);
            // A physical key can move between typed owners while the chunk stays unloaded.
            CACHE.remove(data);
        });
    }

    private static void safe(Runnable action) {
        try { action.run(); }
        catch (RuntimeException failure) {
            com.nstut.Economy.LOGGER.warn("Portfolio cache update failed; economic operation remains committed", failure);
        }
    }

    static Map<String, Long> holdings(ServerLevel level, AccountRef account,
                                      List<IStorageProvider> providers,
                                      Collection<EconomyOrderData.OrderSnapshot> orders) {
        Map<String, Long> holdings = new HashMap<>(storedHoldings(level, account, providers));
        addEscrow(level, account, holdings, orders);
        return Map.copyOf(holdings);
    }

    private static Map<String, Long> storedHoldings(ServerLevel level, AccountRef account,
                                                   List<IStorageProvider> providers) {
        Map<String, Long> holdings = new HashMap<>();
        boolean builtin = false;
        for (IStorageProvider provider : providers) {
            builtin |= provider.id().equals(BuiltinContainerStorageProvider.ID);
            provider.portfolioHoldings(level, account).forEach((id, count) -> add(holdings, id.toString(), count));
        }
        // During early startup the adapter may not yet be registered; value the same owned stores.
        if (!builtin && level != null) new BuiltinContainerStorageProvider().portfolioHoldings(level, account)
                .forEach((id, count) -> add(holdings, id.toString(), count));
        return Map.copyOf(holdings);
    }

    static void addEscrow(ServerLevel level, AccountRef account, Map<String, Long> holdings,
                          Collection<EconomyOrderData.OrderSnapshot> orders) {
        Set<UUID> seenOrders = new HashSet<>();
        Set<String> seenReservations = new HashSet<>();
        for (var order : orders) {
            if (!seenOrders.add(order.orderId)) continue;
            AccountRef storageOwner = order.identity.storageAccount();
            String recoveryOwner = order.addonMetadata.get("recovery_storage_account");
            if (recoveryOwner != null) {
                try { storageOwner = AccountRef.parse(recoveryOwner); }
                catch (IllegalArgumentException invalid) { continue; }
            }
            if ((order.isServerOrder && recoveryOwner == null) || !storageOwner.equals(account)) continue;
            // Reserve ownership follows the store it left, including legacy personal stores
            // used by a team cash principal. Returning it must conserve that owner's wealth.
            if (order.externalReservation != null) {
                var reservation = order.externalReservation;
                String key = reservation.providerId() + ":" + reservation.token();
                if (seenReservations.add(key)) add(holdings, reservation.commodityId().toString(), (long) reservation.amount());
            } else {
                for (var stack : order.reservedItems) {
                    if (stack == null || stack.isEmpty()) continue;
                    // Exact item identities must match the trade-price key, never the base item.
                    String id = level == null ? order.itemId : ItemCommodity.identityFromItemStack(
                            level.registryAccess(), stack, BigDecimal.ZERO).getId().toString();
                    add(holdings, id, (long) stack.getCount());
                }
                for (var fluid : order.reservedFluids) {
                    if (fluid != null && !fluid.isEmpty()) add(holdings,
                            net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString(),
                            (long) fluid.getAmount());
                }
            }
        }
    }

    private static void add(Map<String, Long> holdings, String id, Long amount) {
        if (id == null || amount == null || amount <= 0) return;
        holdings.merge(id, amount, Math::addExact);
    }

    public static BigDecimal assets(ServerLevel level, AccountRef account) {
        return snapshot(level, account).assets;
    }

    private static List<EconomyOrderData.OrderSnapshot> orders(ServerLevel level, AccountRef account) {
        if (EconomyApi.isReady() && EconomyApi.orders() instanceof OrderManager manager)
            return manager.getPortfolioOrders(account);
        return EconomyOrderData.get(level).getOrders().values().stream().filter(order ->
                order.identity.storageAccount().equals(account)
                        || matchesAccount(account, order.addonMetadata.get("recovery_storage_account"))
                        || matchesAccount(account, order.addonMetadata.get("economy:compensation_creditor"))
                        || matchesAccount(account, order.addonMetadata.get("economy:compensation_debtor"))).toList();
    }

    public static BigDecimal compensation(AccountRef account, Collection<EconomyOrderData.OrderSnapshot> orders) {
        BigDecimal total = BigDecimal.ZERO;
        Set<UUID> seen = new HashSet<>();
        for (var order : orders) {
            if (!seen.add(order.orderId)) continue;
            Map<String, String> metadata = order.addonMetadata;
            String raw = metadata.get("economy:compensation_amount");
            if (raw == null) continue;
            BigDecimal amount;
            try { amount = new BigDecimal(raw); } catch (NumberFormatException invalid) { continue; }
            if (amount.signum() <= 0) continue;
            if (matchesAccount(account, metadata.get("economy:compensation_creditor"))) total = total.add(amount);
            if (matchesAccount(account, metadata.get("economy:compensation_debtor"))) total = total.subtract(amount);
        }
        return total;
    }

    private static boolean matchesAccount(AccountRef account, String encoded) {
        if (encoded == null) return false;
        try {
            AccountRef ref = encoded.indexOf(':') < 0 ? AccountRef.player(UUID.fromString(encoded)) : AccountRef.parse(encoded);
            return account.equals(ref);
        } catch (IllegalArgumentException invalid) { return false; }
    }

    public static BigDecimal valueHoldings(Map<String, ? extends Number> holdings,
                                           List<EconomyTradeData.TradeSnapshot> trades) {
        Map<String, BigDecimal> latest = new HashMap<>();
        for (var trade : trades) {
            try { latest.put(trade.itemId.toLowerCase(java.util.Locale.ROOT), new BigDecimal(trade.price)); }
            catch (NumberFormatException invalid) { /* Preserve the last valid known mark. */ }
        }
        BigDecimal total = BigDecimal.ZERO;
        for (var holding : holdings.entrySet()) {
            total = total.add(latest.getOrDefault(holding.getKey().toLowerCase(java.util.Locale.ROOT), BigDecimal.ZERO)
                    .multiply(BigDecimal.valueOf(holding.getValue().longValue())));
        }
        return total;
    }
}
