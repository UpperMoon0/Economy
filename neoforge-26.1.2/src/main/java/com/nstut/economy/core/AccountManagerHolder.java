package com.nstut.economy.core;

import com.nstut.economy.api.IAccountManager;

public final class AccountManagerHolder {
    public static IAccountManager INSTANCE;

    public static void setInstance(IAccountManager instance) {
        INSTANCE = instance;
    }
}
