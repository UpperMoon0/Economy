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
     * Typed account lookup. The default implementation preserves compatibility
     * with older account-manager implementations; built-in Economy supports
     * every {@link AccountKind}.
     */
    default Optional<IBankAccount> getAccount(AccountRef account) {
        if (account == null) return Optional.empty();
        return switch (account.kind()) {
            case PLAYER -> getPlayerAccount(account.id());
            case SERVER -> Optional.of(getServerAccount()).filter(a -> a.getOwner().equals(account.id()));
            case TAX -> Optional.of(getTaxAccount()).filter(a -> a.getOwner().equals(account.id()));
            case TEAM -> Optional.empty();
        };
    }

    /**
     * Gets or creates a typed account. Legacy third-party implementations may
     * reject TEAM principals until they implement the typed account API.
     */
    default IBankAccount getOrCreateAccount(AccountRef account) {
        if (account == null) throw new IllegalArgumentException("account cannot be null");
        return switch (account.kind()) {
            case PLAYER -> getOrCreatePlayerAccount(account.id());
            case SERVER -> {
                IBankAccount value = getServerAccount();
                if (!value.getOwner().equals(account.id())) throw new IllegalArgumentException("Unknown server account");
                yield value;
            }
            case TAX -> {
                IBankAccount value = getTaxAccount();
                if (!value.getOwner().equals(account.id())) throw new IllegalArgumentException("Unknown tax account");
                yield value;
            }
            case TEAM -> throw new UnsupportedOperationException("TEAM accounts are not supported by this account manager");
        };
    }

    default Optional<IBankAccount> getTeamAccount(UUID team) {
        return team == null ? Optional.empty() : getAccount(AccountRef.team(team));
    }

    default IBankAccount getOrCreateTeamAccount(UUID team) {
        if (team == null) throw new IllegalArgumentException("team cannot be null");
        return getOrCreateAccount(AccountRef.team(team));
    }

    default boolean hasAccount(AccountRef account) {
        return getAccount(account).isPresent();
    }

    default boolean deleteAccount(AccountRef account) {
        return account != null && account.kind() == AccountKind.PLAYER && deleteAccount(account.id());
    }

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

    default boolean transfer(AccountRef source, AccountRef target, BigDecimal amount,
                             ITransactionContext context) {
        if (source == null || target == null) return false;
        return transfer(getOrCreateAccount(source), getOrCreateAccount(target), amount, context);
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
