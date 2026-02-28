package com.nstut.economy.core;

import com.nstut.economy.api.IAccountManager;
import com.nstut.economy.api.IBankAccount;
import com.nstut.economy.config.EconomyConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of IAccountManager.
 * Manages all bank accounts in the economy system.
 */
public class AccountManager implements IAccountManager {
    
    private final Map<UUID, BankAccount> accounts;
    private final BankAccount serverAccount;
    private final BankAccount taxAccount;
    
    public AccountManager() {
        this.accounts = new HashMap<>();
        
        // Create system accounts with infinite balance
        this.serverAccount = new BankAccount(UUID.fromString("00000000-0000-0000-0000-000000000001"), 
                                             new java.math.BigDecimal("999999999999"));
        this.taxAccount = new BankAccount(UUID.fromString("00000000-0000-0000-0000-000000000002"), 
                                          java.math.BigDecimal.ZERO);
        
        // Set this as the singleton instance
        AccountManagerHolder.setInstance(this);
    }
    
    @Override
    public Optional<IBankAccount> getPlayerAccount(UUID player) {
        return Optional.ofNullable(accounts.get(player));
    }
    
    @Override
    public IBankAccount getOrCreatePlayerAccount(UUID player) {
        return accounts.computeIfAbsent(player, uuid -> {
            EconomyConfig config = EconomyConfig.getInstance();
            BankAccount account = new BankAccount(uuid, config.getStartingBalance());
            
            // Record starting balance transaction
            account.credit(config.getStartingBalance(), TransactionContext.startingBalance());
            
            return account;
        });
    }
    
    @Override
    public boolean hasAccount(UUID player) {
        return accounts.containsKey(player);
    }
    
    @Override
    public IBankAccount getServerAccount() {
        return serverAccount;
    }
    
    @Override
    public IBankAccount getTaxAccount() {
        return taxAccount;
    }
    
    @Override
    public boolean deleteAccount(UUID player) {
        return accounts.remove(player) != null;
    }
    
    /**
     * Gets all accounts (for save operations).
     */
    public Map<UUID, BankAccount> getAllAccounts() {
        return new HashMap<>(accounts);
    }
    
    /**
     * Loads accounts from saved data.
     */
    public void loadAccounts(Map<UUID, java.math.BigDecimal> savedBalances) {
        accounts.clear();
        for (Map.Entry<UUID, java.math.BigDecimal> entry : savedBalances.entrySet()) {
            BankAccount account = new BankAccount(entry.getKey(), entry.getValue());
            accounts.put(entry.getKey(), account);
        }
    }
}
