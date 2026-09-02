package com.nstut.economy.core;

import com.nstut.economy.api.IAccountManager;

import java.util.Optional;

/** Internal compatibility holder; addons should use EconomyApi. */
public final class AccountManagerHolder {
    private static volatile IAccountManager instance;

    private AccountManagerHolder() { }

    public static Optional<IAccountManager> current() {
        return Optional.ofNullable(instance);
    }

    public static IAccountManager require() {
        IAccountManager value = instance;
        if (value == null) {
            throw new IllegalStateException("Economy account service is not ready");
        }
        return value;
    }

    /**
     * Replaces the compatibility account-manager instance.
     *
     * <p>This remains public for loader bootstrap and controlled test fixtures.
     * Addons should obtain the service through {@code EconomyApi} instead.</p>
     */
    public static void setInstance(IAccountManager value) {
        instance = value;
    }

    static void clearInstance(IAccountManager expected) {
        if (instance == expected) {
            instance = null;
        }
    }
}
