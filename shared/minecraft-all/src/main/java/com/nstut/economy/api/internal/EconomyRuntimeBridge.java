package com.nstut.economy.api.internal;

import com.nstut.economy.api.IAccountManager;
import com.nstut.economy.api.IMarketDataService;
import com.nstut.economy.api.IOrderManager;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/** Internal server-runtime binding. Not part of the supported addon API. */
public final class EconomyRuntimeBridge {
    private static volatile IAccountManager accounts;
    private static volatile IOrderManager orders;
    private static volatile IMarketDataService marketData;
    private static volatile ServerLevel serverLevel;

    private EconomyRuntimeBridge() {}

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

    public static Optional<ServerLevel> serverLevel() {
        return Optional.ofNullable(serverLevel);
    }

    public static void bind(IAccountManager accountService, IOrderManager orderService,
                            IMarketDataService marketDataService, ServerLevel level) {
        if (accountService == null || orderService == null || marketDataService == null || level == null) {
            throw new IllegalArgumentException("Economy runtime services cannot be null");
        }
        accounts = accountService;
        orders = orderService;
        marketData = marketDataService;
        serverLevel = level;
    }

    public static void unbind() {
        serverLevel = null;
        marketData = null;
        orders = null;
        accounts = null;
    }
}
