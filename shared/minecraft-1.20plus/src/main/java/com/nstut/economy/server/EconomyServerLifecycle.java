package com.nstut.economy.server;

import com.nstut.Economy;
import com.nstut.economy.api.EconomyApi;
import com.nstut.economy.api.internal.DefaultMarketDataService;
import com.nstut.economy.blocks.TankManager;
import com.nstut.economy.blocks.VaultManager;
import com.nstut.economy.data.EconomyAccountData;
import com.nstut.economy.data.EconomyOrderData;
import com.nstut.economy.data.EconomyTradeData;
import com.nstut.economy.data.TradeLedger;
import com.nstut.economy.trading.OrderManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Shared server lifecycle behavior used by every loader and Minecraft version family. */
public final class EconomyServerLifecycle {
    private EconomyServerLifecycle() { }

    /** Loads persistent state only after the overworld and its data storage exist. */
    public static void load(ServerLevel overworld) {
        Economy.ensureApiRegistrations();
        EconomyAccountData accountData = EconomyAccountData.get(overworld);
        EconomyOrderData orderData = EconomyOrderData.get(overworld);
        EconomyTradeData tradeData = EconomyTradeData.get(overworld);

        Economy.getAccountManager().loadFrom(accountData);

        OrderManager orderManager = Economy.getOrderManager();
        orderManager.setOrderData(orderData);
        orderManager.loadFrom(orderData);

        TradeLedger.setTradeData(tradeData);
        VaultManager.setAccountData(accountData);
        TankManager.setAccountData(accountData);
        EconomyApi.bindRuntime(Economy.getAccountManager(), orderManager,
                new DefaultMarketDataService(orderManager), overworld);
        Economy.LOGGER.info("Economy data loaded for dimension {}", overworld.dimension().location());
    }

    public static void save() {
        Economy.getAccountManager().saveAll();
        Economy.getOrderManager().saveAll();
    }

    public static void stop() {
        try {
            save();
        } finally {
            EconomyApi.unbindRuntime();
        }
    }

    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld != null && server.getTickCount() % 20 == 0) {
            Economy.getOrderManager().matchAllPendingOrders(overworld);
        }
    }
}
