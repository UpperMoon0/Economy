package com.nstut.economy.client;

/**
 * Small loader- and Minecraft-version-neutral guard for render hooks that can
 * be re-entered indirectly by evolving screen APIs.
 */
public final class RenderReentryGuard {
    private boolean rendering;

    /** Returns {@code true} only for the outermost render invocation. */
    public boolean enter() {
        if (rendering) {
            return false;
        }
        rendering = true;
        return true;
    }

    /** Releases the guard after a successful {@link #enter()}. */
    public void exit() {
        rendering = false;
    }

    public boolean isRendering() {
        return rendering;
    }
}
