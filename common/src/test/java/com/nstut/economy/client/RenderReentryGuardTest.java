package com.nstut.economy.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderReentryGuardTest {
    @Test
    void rejectsNestedRenderAndAllowsTheNextFrameAfterExit() {
        RenderReentryGuard guard = new RenderReentryGuard();

        assertTrue(guard.enter());
        assertTrue(guard.isRendering());
        assertFalse(guard.enter(), "nested background rendering must be rejected");

        guard.exit();

        assertFalse(guard.isRendering());
        assertTrue(guard.enter(), "the guard must be reusable on the next frame");
        guard.exit();
    }
}
