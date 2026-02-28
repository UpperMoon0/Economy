package com.nstut;

import com.nstut.economy.blocks.BlockRegistries;
import com.nstut.economy.core.AccountManager;
import com.nstut.economy.items.ItemRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Economy {
    public static final String MOD_ID = "economy";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    
    private static AccountManager accountManager;
    private static com.nstut.economy.trading.OfferManager offerManager;

    public static void init() {
        LOGGER.info("Initializing Economy Mod");
        
        // Register blocks and items
        BlockRegistries.BLOCKS.register();
        ItemRegistries.ITEMS.register();
        
        // Initialize the account manager
        accountManager = new AccountManager();
        
        // Initialize the offer manager
        offerManager = new com.nstut.economy.trading.OfferManager();
        
        LOGGER.info("Economy Mod initialized successfully");
    }
    
    public static AccountManager getAccountManager() {
        return accountManager;
    }
    
    public static com.nstut.economy.trading.OfferManager getOfferManager() {
        return offerManager;
    }
}
