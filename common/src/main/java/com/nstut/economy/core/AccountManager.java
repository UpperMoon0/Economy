package com.nstut.economy.core;

import com.nstut.economy.api.IAccountManager;
import com.nstut.economy.api.IBankAccount;
import com.nstut.economy.config.EconomyConfig;
import com.nstut.economy.core.BalanceStore;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AccountManager implements IAccountManager {

    private final Map<UUID, BankAccount> accounts;
    private final BankAccount serverAccount;
    private final BankAccount taxAccount;
    private BalanceStore backingData;

    public AccountManager() {
        this.accounts = new HashMap<>();

        this.serverAccount = new BankAccount(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                                             new BigDecimal("999999999999"));
        this.taxAccount = new BankAccount(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                                          BigDecimal.ZERO);

        AccountManagerHolder.setInstance(this);
    }

    private BankAccount createAccount(UUID owner, BigDecimal initialBalance) {
        return new BankAccount(owner, initialBalance, bal -> {
            if (backingData != null) {
                backingData.setBalance(owner, bal);
            }
        });
    }

    public void setAccountData(BalanceStore data) {
        this.backingData = data;
        if (data != null) {
            for (Map.Entry<UUID, BankAccount> entry : accounts.entrySet()) {
                data.setBalance(entry.getKey(), entry.getValue().getBalance());
            }
        }
    }

    public void loadFrom(BalanceStore data) {
        this.backingData = data;
        accounts.clear();
        for (Map.Entry<UUID, BigDecimal> entry : data.getBalances().entrySet()) {
            BankAccount account = createAccount(entry.getKey(), entry.getValue());
            accounts.put(entry.getKey(), account);
        }
    }

    public void saveAll() {
        if (backingData == null) return;
        for (Map.Entry<UUID, BankAccount> entry : accounts.entrySet()) {
            backingData.setBalance(entry.getKey(), entry.getValue().getBalance());
        }
    }

    @Override
    public Optional<IBankAccount> getPlayerAccount(UUID player) {
        return Optional.ofNullable(accounts.get(player));
    }

    @Override
    public IBankAccount getOrCreatePlayerAccount(UUID player) {
        return accounts.computeIfAbsent(player, uuid -> {
            EconomyConfig config = EconomyConfig.getInstance();
            BigDecimal startingBalance = config.getStartingBalance();
            BankAccount account = createAccount(uuid, BigDecimal.ZERO);
            if (startingBalance.compareTo(BigDecimal.ZERO) > 0) {
                account.credit(startingBalance, TransactionContext.startingBalance());
            } else if (backingData != null) {
                backingData.setBalance(uuid, BigDecimal.ZERO);
            }
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
        boolean removed = accounts.remove(player) != null;
        if (removed && backingData != null) {
            backingData.removeBalance(player);
        }
        return removed;
    }

    public Map<UUID, BankAccount> getAllAccounts() {
        return new HashMap<>(accounts);
    }

    public void loadAccounts(Map<UUID, BigDecimal> savedBalances) {
        accounts.clear();
        for (Map.Entry<UUID, BigDecimal> entry : savedBalances.entrySet()) {
            BankAccount account = createAccount(entry.getKey(), entry.getValue());
            accounts.put(entry.getKey(), account);
        }
    }
}
