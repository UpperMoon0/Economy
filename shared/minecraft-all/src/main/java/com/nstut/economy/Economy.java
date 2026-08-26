package com.nstut;

import com.nstut.economy.core.AccountManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Economy {
    public static final String MOD_ID = "economy";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static AccountManager accountManager;
    private static com.nstut.economy.trading.OrderManager orderManager;

    public static void init() {
        LOGGER.info("Initializing Economy Mod");

        accountManager = new AccountManager();
        orderManager = new com.nstut.economy.trading.OrderManager();

        LOGGER.info("Economy Mod initialized successfully");
    }

    public static AccountManager getAccountManager() {
        return accountManager;
    }

    public static com.nstut.economy.trading.OrderManager getOrderManager() {
        return orderManager;
    }
}

