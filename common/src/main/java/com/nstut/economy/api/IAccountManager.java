package com.nstut.economy.api;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Central service for virtual Economy bank accounts. */
public interface IAccountManager {
    Optional<IBankAccount> getPlayerAccount(UUID player);
    IBankAccount getOrCreatePlayerAccount(UUID player);
    boolean hasAccount(UUID player);
    IBankAccount getServerAccount();
    IBankAccount getTaxAccount();
    boolean deleteAccount(UUID player);

    /**
     * Performs a transfer through the account contract. Implementations must
     * guarantee that a rejected/throwing target credit does not debit source.
     */
    default boolean transfer(IBankAccount source, IBankAccount target, BigDecimal amount,
                             ITransactionContext context) {
        if (source == null || target == null) {
            return false;
        }
        return source.transferTo(target, amount, context);
    }

    default boolean transfer(UUID sourcePlayer, UUID targetPlayer, BigDecimal amount,
                             ITransactionContext context) {
        if (sourcePlayer == null || targetPlayer == null) {
            return false;
        }
        return transfer(getOrCreatePlayerAccount(sourcePlayer), getOrCreatePlayerAccount(targetPlayer),
                amount, context);
    }

    /**
     * Compatibility accessor for older addons. New code should use
     * {@code EconomyApi.accounts()} from the Minecraft-facing API facade.
     */
    @Deprecated
    static IAccountManager getInstance() {
        return com.nstut.economy.core.AccountManagerHolder.require();
    }
}
