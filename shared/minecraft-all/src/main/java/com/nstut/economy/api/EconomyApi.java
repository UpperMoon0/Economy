package com.nstut.economy.api;

import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Stable entry point for Economy addons. Only types in {@code com.nstut.economy.api}
 * are covered by the public compatibility policy unless documented otherwise.
 */
public final class EconomyApi {
    private static final CommodityTypeRegistry COMMODITY_TYPES = new CommodityTypeRegistry();
    private static final StorageProviderRegistry STORAGE = new StorageProviderRegistry();

    private static volatile IAccountManager accounts;
    private static volatile IOrderManager orders;
    private static volatile IMarketDataService marketData;
    private static volatile ServerLevel serverLevel;

    private EconomyApi() { }

    public static boolean isReady() {
        return accounts != null && orders != null && marketData != null && serverLevel != null;
    }

    public static IAccountManager accounts() {
        IAccountManager value = accounts;
        if (value == null) throw new IllegalStateException("Economy API is not bound to a running server");
        return value;
    }

    public static IOrderManager orders() {
        IOrderManager value = orders;
        if (value == null) throw new IllegalStateException("Economy API is not bound to a running server");
        return value;
    }

    public static IMarketDataService marketData() {
        IMarketDataService value = marketData;
        if (value == null) throw new IllegalStateException("Economy API is not bound to a running server");
        return value;
    }

    public static CommodityTypeRegistry commodityTypes() { return COMMODITY_TYPES; }
    public static StorageProviderRegistry storage() { return STORAGE; }

    /** Read-only lifecycle visibility for providers that need the active overworld. */
    public static Optional<ServerLevel> serverLevel() { return Optional.ofNullable(serverLevel); }

    /** Internal bootstrap hook; not part of the supported addon surface. */
    public static void bindRuntime(IAccountManager accountService, IOrderManager orderService,
                                   IMarketDataService marketDataService, ServerLevel level) {
        if (accountService == null || orderService == null || marketDataService == null || level == null) {
            throw new IllegalArgumentException("Economy runtime services cannot be null");
        }
        accounts = accountService;
        orders = orderService;
        marketData = marketDataService;
        serverLevel = level;
    }

    /** Internal lifecycle hook; registries intentionally survive server restarts. */
    public static void unbindRuntime() {
        serverLevel = null;
        marketData = null;
        orders = null;
        accounts = null;
    }
}
