package dev.ftb.mods.ftbteams.api;

/**
 * Behavioral test double for mutable membership/rank and failure scenarios.
 * Published API compatibility is checked separately by :common:ftbContractTest.
 */
public final class FTBTeamsAPI {
    private static final API INSTANCE = new API();

    private FTBTeamsAPI() {}

    public static API api() {
        return INSTANCE;
    }

    public static void configure(boolean loaded, TeamManager manager) {
        INSTANCE.loaded = loaded;
        INSTANCE.manager = manager;
        INSTANCE.failManagerLookup = false;
    }

    public static void failManagerLookup(boolean fail) {
        INSTANCE.failManagerLookup = fail;
    }

    public static final class API {
        private boolean loaded;
        private TeamManager manager;
        private boolean failManagerLookup;

        public boolean isManagerLoaded() {
            return loaded;
        }

        public TeamManager getManager() {
            if (failManagerLookup) throw new IllegalStateException("simulated incompatible/not-ready manager");
            return manager;
        }
    }
}
