package com.nstut.economy.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shared preference contract compiled against every 1.20.1 and 1.21.1 common module. */
class MarketPreferencesParityTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void themeAndBrowseModeRoundTripWithoutClobberingEachOther() {
        Path preferences = temporaryDirectory.resolve("economy-client.properties");

        MarketClientPreferences.writeThemeMode(preferences, EconomyUiThemeMode.LIGHT);
        MarketClientPreferences.writeBrowseGridView(preferences, false);

        assertEquals(EconomyUiThemeMode.LIGHT, MarketClientPreferences.readThemeMode(preferences));
        assertFalse(MarketClientPreferences.readBrowseGridView(preferences));

        MarketClientPreferences.writeBrowseGridView(preferences, true);
        assertEquals(EconomyUiThemeMode.LIGHT, MarketClientPreferences.readThemeMode(preferences));
        assertTrue(MarketClientPreferences.readBrowseGridView(preferences));
    }

    @Test
    void missingPreferencesUseSafeDefaults() {
        Path missing = temporaryDirectory.resolve("missing.properties");
        assertEquals(EconomyUiThemeMode.DARK, MarketClientPreferences.readThemeMode(missing));
        assertTrue(MarketClientPreferences.readBrowseGridView(missing));
    }
}
