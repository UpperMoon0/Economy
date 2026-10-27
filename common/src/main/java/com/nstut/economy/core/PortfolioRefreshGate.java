package com.nstut.economy.core;

/** Per-view/session throttle. A caller supplies monotonic milliseconds; no scheduled scans are needed. */
public final class PortfolioRefreshGate {
    public static final long INTERVAL_MILLIS = 1000;
    private long lastRefresh;
    private boolean refreshed;

    public boolean tryAcquire(long now) {
        if (refreshed && now - lastRefresh >= 0 && now - lastRefresh < INTERVAL_MILLIS) return false;
        refreshed = true;
        lastRefresh = now;
        return true;
    }
}
