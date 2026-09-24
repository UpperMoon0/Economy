package dev.ftb.mods.ftbteams.api;

/**
 * Test contract matching the public FTB Teams API surface used by Economy on
 * the supported 1.20.1, 1.21.1, and 26.1.2 release families.
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
