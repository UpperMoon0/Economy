package com.nstut.economy.core;

import com.nstut.economy.api.AccountKind;
import com.nstut.economy.api.AccountRef;
import com.nstut.economy.api.IAccountManager;
import com.nstut.economy.api.IBankAccount;
import com.nstut.economy.config.EconomyConfig;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AccountManager implements IAccountManager {
    public static final UUID SERVER_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID TAX_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final BigDecimal SERVER_INITIAL_BALANCE = new BigDecimal("999999999999");

    private final Map<AccountRef, BankAccount> accounts;
    private final BankAccount serverAccount;
    private final BankAccount taxAccount;
    private BalanceStore backingData;

    public AccountManager() {
        this.accounts = new HashMap<>();
        this.serverAccount = new BankAccount(AccountRef.server(SERVER_ACCOUNT_ID), SERVER_INITIAL_BALANCE);
        this.taxAccount = new BankAccount(AccountRef.tax(TAX_ACCOUNT_ID), BigDecimal.ZERO);
        AccountManagerHolder.setInstance(this);
    }

    private BankAccount createAccount(AccountRef owner, BigDecimal initialBalance) {
        return new BankAccount(owner, initialBalance, bal -> persistBalance(owner, bal));
    }

    private void persistBalance(AccountRef owner, BigDecimal balance) {
        BalanceStore data = backingData;
        if (data == null) return;
        if (owner.kind() == AccountKind.PLAYER) {
            data.setBalance(owner.id(), balance);
        } else if (data.supportsTypedAccounts()) {
            data.setAccountBalance(owner, balance);
        }
    }

    private void persistAccount(BalanceStore data, AccountRef owner, BigDecimal balance) {
        if (owner.kind() == AccountKind.PLAYER) {
            data.setBalance(owner.id(), balance);
        } else if (data.supportsTypedAccounts()) {
            data.setAccountBalance(owner, balance);
        }
    }

    public void setAccountData(BalanceStore data) {
        this.backingData = data;
        if (data == null) return;
        for (Map.Entry<AccountRef, BankAccount> entry : accounts.entrySet()) {
            persistAccount(data, entry.getKey(), entry.getValue().getBalance());
        }
        persistAccount(data, serverAccount.getAccountRef(), serverAccount.getBalance());
        persistAccount(data, taxAccount.getAccountRef(), taxAccount.getBalance());
    }

    public void loadFrom(BalanceStore data) {
        Map<AccountRef, BigDecimal> saved = data == null
                ? Map.of() : new HashMap<>(data.getAccountBalances());

        // Suppress persistence callbacks while reconstructing the in-memory view.
        this.backingData = null;
        accounts.clear();
        serverAccount.setBalance(saved.getOrDefault(serverAccount.getAccountRef(), SERVER_INITIAL_BALANCE));
        taxAccount.setBalance(saved.getOrDefault(taxAccount.getAccountRef(), BigDecimal.ZERO));

        for (Map.Entry<AccountRef, BigDecimal> entry : saved.entrySet()) {
            AccountRef ref = entry.getKey();
            if (ref.kind() == AccountKind.PLAYER || ref.kind() == AccountKind.TEAM) {
                accounts.put(ref, createAccount(ref, entry.getValue()));
            }
        }
        this.backingData = data;
    }

    public void saveAll() {
        if (backingData == null) return;
        for (Map.Entry<AccountRef, BankAccount> entry : accounts.entrySet()) {
            persistAccount(backingData, entry.getKey(), entry.getValue().getBalance());
        }
        persistAccount(backingData, serverAccount.getAccountRef(), serverAccount.getBalance());
        persistAccount(backingData, taxAccount.getAccountRef(), taxAccount.getBalance());
    }

    @Override
    public Optional<IBankAccount> getPlayerAccount(UUID player) {
        return player == null ? Optional.empty() : Optional.ofNullable(accounts.get(AccountRef.player(player)));
    }

    @Override
    public IBankAccount getOrCreatePlayerAccount(UUID player) {
        if (player == null) throw new IllegalArgumentException("player cannot be null");
        AccountRef ref = AccountRef.player(player);
        return accounts.computeIfAbsent(ref, key -> {
            BigDecimal startingBalance = EconomyConfig.getInstance().getStartingBalance();
            BankAccount account = createAccount(key, BigDecimal.ZERO);
            if (startingBalance.compareTo(BigDecimal.ZERO) > 0) {
                account.credit(startingBalance, TransactionContext.startingBalance());
            } else {
                persistBalance(key, BigDecimal.ZERO);
            }
            return account;
        });
    }

    @Override
    public Optional<IBankAccount> getAccount(AccountRef account) {
        if (account == null) return Optional.empty();
        return switch (account.kind()) {
            case PLAYER, TEAM -> Optional.ofNullable(accounts.get(account));
            case SERVER -> serverAccount.getAccountRef().equals(account) ? Optional.of(serverAccount) : Optional.empty();
            case TAX -> taxAccount.getAccountRef().equals(account) ? Optional.of(taxAccount) : Optional.empty();
        };
    }

    @Override
    public IBankAccount getOrCreateAccount(AccountRef account) {
        if (account == null) throw new IllegalArgumentException("account cannot be null");
        return switch (account.kind()) {
            case PLAYER -> getOrCreatePlayerAccount(account.id());
            case TEAM -> accounts.computeIfAbsent(account, key -> {
                BankAccount created = createAccount(key, BigDecimal.ZERO);
                persistBalance(key, BigDecimal.ZERO);
                return created;
            });
            case SERVER -> {
                if (!serverAccount.getAccountRef().equals(account)) throw new IllegalArgumentException("Unknown server account");
                yield serverAccount;
            }
            case TAX -> {
                if (!taxAccount.getAccountRef().equals(account)) throw new IllegalArgumentException("Unknown tax account");
                yield taxAccount;
            }
        };
    }

    @Override
    public boolean hasAccount(UUID player) {
        return player != null && accounts.containsKey(AccountRef.player(player));
    }

    @Override
    public boolean hasAccount(AccountRef account) {
        return getAccount(account).isPresent();
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
        return player != null && deleteAccount(AccountRef.player(player));
    }

    @Override
    public boolean deleteAccount(AccountRef account) {
        if (account == null || (account.kind() != AccountKind.PLAYER && account.kind() != AccountKind.TEAM)) {
            return false;
        }
        boolean removed = accounts.remove(account) != null;
        if (removed && backingData != null) {
            if (account.kind() == AccountKind.PLAYER) backingData.removeBalance(account.id());
            else if (backingData.supportsTypedAccounts()) backingData.removeAccountBalance(account);
        }
        return removed;
    }

    /** Legacy player-only view retained for implementation compatibility. */
    public Map<UUID, BankAccount> getAllAccounts() {
        Map<UUID, BankAccount> players = new HashMap<>();
        accounts.forEach((ref, account) -> {
            if (ref.kind() == AccountKind.PLAYER) players.put(ref.id(), account);
        });
        return players;
    }

    public Map<AccountRef, BankAccount> getAllTypedAccounts() {
        Map<AccountRef, BankAccount> result = new HashMap<>(accounts);
        result.put(serverAccount.getAccountRef(), serverAccount);
        result.put(taxAccount.getAccountRef(), taxAccount);
        return result;
    }

    /** Legacy load helper: every UUID is deterministically a PLAYER principal. */
    public void loadAccounts(Map<UUID, BigDecimal> savedBalances) {
        accounts.clear();
        if (savedBalances == null) return;
        for (Map.Entry<UUID, BigDecimal> entry : savedBalances.entrySet()) {
            AccountRef ref = AccountRef.player(entry.getKey());
            accounts.put(ref, createAccount(ref, entry.getValue()));
        }
    }
}
