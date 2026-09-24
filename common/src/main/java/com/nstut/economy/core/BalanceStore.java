package com.nstut.economy.core;

import com.nstut.economy.api.AccountRef;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistence boundary for account balances. Implemented by the
 * version-specific saved-data classes so the core account logic stays
 * Minecraft-free.
 */
public interface BalanceStore {

    /** Legacy player-only balance view retained for compatibility. */
    Map<UUID, BigDecimal> getBalances();

    /** Legacy player-only write retained for compatibility. */
    void setBalance(UUID player, BigDecimal balance);

    default void removeBalance(UUID player) {}

    /**
     * Typed view. Legacy stores automatically expose their UUID balances as
     * PLAYER principals.
     */
    default Map<AccountRef, BigDecimal> getAccountBalances() {
        Map<AccountRef, BigDecimal> result = new HashMap<>();
        getBalances().forEach((id, balance) -> result.put(AccountRef.player(id), balance));
        return result;
    }

    /** Whether this store can durably persist non-player principals. */
    default boolean supportsTypedAccounts() {
        return false;
    }

    default void setAccountBalance(AccountRef account, BigDecimal balance) {
        if (account == null) throw new IllegalArgumentException("account cannot be null");
        if (account.kind() != com.nstut.economy.api.AccountKind.PLAYER) {
            throw new UnsupportedOperationException("This balance store only supports PLAYER accounts");
        }
        setBalance(account.id(), balance);
    }

    default void removeAccountBalance(AccountRef account) {
        if (account == null) return;
        if (account.kind() != com.nstut.economy.api.AccountKind.PLAYER) {
            throw new UnsupportedOperationException("This balance store only supports PLAYER accounts");
        }
        removeBalance(account.id());
    }
}
