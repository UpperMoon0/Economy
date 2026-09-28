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
    private static final TeamEconomyRegistry TEAM_ECONOMY = new TeamEconomyRegistry();

    private EconomyApi() { }

    public static boolean isReady() {
        return com.nstut.economy.api.internal.EconomyRuntimeBridge.isReady();
    }

    public static IAccountManager accounts() {
        return com.nstut.economy.api.internal.EconomyRuntimeBridge.accounts();
    }

    public static IOrderManager orders() {
        return com.nstut.economy.api.internal.EconomyRuntimeBridge.orders();
    }

    public static IMarketDataService marketData() {
        return com.nstut.economy.api.internal.EconomyRuntimeBridge.marketData();
    }

    public static CommodityTypeRegistry commodityTypes() { return COMMODITY_TYPES; }
    public static StorageProviderRegistry storage() { return STORAGE; }
    public static TeamEconomyRegistry teamEconomy() { return TEAM_ECONOMY; }

    /** Read-only lifecycle visibility for providers that need the active overworld. */
    public static Optional<ServerLevel> serverLevel() {
        return com.nstut.economy.api.internal.EconomyRuntimeBridge.serverLevel();
    }
}
