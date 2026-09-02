package com.nstut;

import com.nstut.economy.api.EconomyApi;
import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.internal.BuiltinContainerStorageProvider;
import com.nstut.economy.core.AccountManager;
import com.nstut.economy.trading.FluidCommodity;
import com.nstut.economy.trading.ItemCommodity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Economy {
    public static final String MOD_ID = "economy";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static AccountManager accountManager;
    private static com.nstut.economy.trading.OrderManager orderManager;
    private static final BuiltinContainerStorageProvider BUILTIN_STORAGE = new BuiltinContainerStorageProvider();

    public static void init() {
        LOGGER.info("Initializing Economy Mod");

        accountManager = new AccountManager();
        orderManager = new com.nstut.economy.trading.OrderManager();
        ensureApiRegistrations();

        LOGGER.info("Economy Mod initialized successfully");
    }


    /** Registers Economy-owned extension points before persisted orders are decoded. */
    public static synchronized void ensureApiRegistrations() {
        if (EconomyApi.commodityTypes().handler(ICommodity.ITEM_TYPE).isEmpty()) ItemCommodity.registerApiType();
        if (EconomyApi.commodityTypes().handler(ICommodity.FLUID_TYPE).isEmpty()) FluidCommodity.registerApiType();
        if (EconomyApi.storage().provider(BuiltinContainerStorageProvider.ID).isEmpty()) EconomyApi.storage().register(BUILTIN_STORAGE);
    }

    public static AccountManager getAccountManager() {
        return accountManager;
    }

    public static com.nstut.economy.trading.OrderManager getOrderManager() {
        return orderManager;
    }
}

