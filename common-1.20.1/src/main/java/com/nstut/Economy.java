package com.nstut;

import com.nstut.economy.core.AccountManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Economy {
    public static final String MOD_ID = "economy";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    
    private static AccountManager accountManager;

    public static void init() {
        LOGGER.info("Initializing Economy Mod");
        
        // Initialize the account manager
        accountManager = new AccountManager();
        
        LOGGER.info("Economy Mod initialized successfully");
    }
    
    public static AccountManager getAccountManager() {
        return accountManager;
    }
}
