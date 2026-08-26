package com.nstut.economy.testing;

import java.util.concurrent.atomic.AtomicBoolean;

/** Shared state and markers for the opt-in real-client join smoke test. */
public final class LiveJoinTestProtocol {
    public static final String SYSTEM_PROPERTY = "economy.liveJoinTest";
    public static final String PASS_MARKER = "ECONOMY_LIVE_JOIN_TEST_PASS";

    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    private LiveJoinTestProtocol() {
    }

    public static boolean isEnabled() {
        return Boolean.getBoolean(SYSTEM_PROPERTY);
    }

    public static boolean markReported() {
        return REPORTED.compareAndSet(false, true);
    }

    /** Prefer Minecraft's clean shutdown, with a test-only fallback for loader hangs. */
    public static void stopClient(Runnable cleanStop) {
        Thread fallback = new Thread(() -> {
            try {
                Thread.sleep(15_000L);
                System.exit(0);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }, "economy-live-test-exit");
        fallback.setDaemon(true);
        fallback.start();
        cleanStop.run();
    }

    static void reset() {
        REPORTED.set(false);
    }
}
