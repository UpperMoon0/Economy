package com.nstut.economy.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Central manager for all bank accounts in the economy system.
 */
public interface IAccountManager {
    
    /**
     * Gets an existing player account.
     * @param player The player's UUID
     * @return Optional containing the account if it exists
     */
    Optional<IBankAccount> getPlayerAccount(UUID player);
    
    /**
     * Gets an existing account or creates a new one with starting balance.
     * @param player The player's UUID
     * @return The player's bank account
     */
    IBankAccount getOrCreatePlayerAccount(UUID player);
    
    /**
     * Checks if a player has an account.
     * @param player The player's UUID
     * @return true if account exists
     */
    boolean hasAccount(UUID player);
    
    /**
     * Gets the server system account for admin operations.
     * @return The server account
     */
    IBankAccount getServerAccount();
    
    /**
     * Gets the tax collection account.
     * @return The tax account
     */
    IBankAccount getTaxAccount();
    
    /**
     * Deletes a player account (use with caution).
     * @param player The player's UUID
     * @return true if deleted, false if not found
     */
    boolean deleteAccount(UUID player);
    
    /**
     * Gets the singleton instance of the account manager.
     * @return The account manager instance
     */
    static IAccountManager getInstance() {
        return AccountManagerHolder.INSTANCE;
    }
}

/**
 * Holder class for the singleton instance.
 */
class AccountManagerHolder {
    static IAccountManager INSTANCE;
    
    static void setInstance(IAccountManager instance) {
        INSTANCE = instance;
    }
}
