package com.nstut.economy.api;

/**
 * Binary-compat baseline extracted from Economy v0.0.13. Keep this source set isolated:
 * it exists only so the legacy consumer fixture compiles an invokevirtual/static link
 * against the old public descriptor rather than against the current source tree.
 */
public final class EconomyEvents {
    private EconomyEvents() {}

    public static void clearListeners() {}
}
