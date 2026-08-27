package com.nstut.economy.core;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Persistence boundary for account balances. Implemented by the
 * version-specific saved-data classes so the core account logic stays
 * Minecraft-free.
 */
public interface BalanceStore {

    Map<UUID, BigDecimal> getBalances();

    void setBalance(UUID player, BigDecimal balance);

    default void removeBalance(UUID player) {}
}
