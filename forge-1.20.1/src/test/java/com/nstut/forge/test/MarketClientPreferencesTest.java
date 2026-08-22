package com.nstut.forge.test;

import com.nstut.forge.client.EconomyUiThemeMode;
import com.nstut.forge.client.MarketClientPreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketClientPreferencesTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void gridIsTheDefaultWhenNoPreferenceExists() {
        assertTrue(MarketClientPreferences.readBrowseGridView(
                temporaryDirectory.resolve("missing.properties")));
    }

    @Test
    void rowAndGridChoicesPersistAcrossReads() {
        Path preferences = temporaryDirectory.resolve("economy-client.properties");

        MarketClientPreferences.writeBrowseGridView(preferences, false);
        assertFalse(MarketClientPreferences.readBrowseGridView(preferences));

        MarketClientPreferences.writeBrowseGridView(preferences, true);
        assertTrue(MarketClientPreferences.readBrowseGridView(preferences));
    }

    @Test
    void themeDefaultsToDarkWhenMissing() {
        assertEquals(EconomyUiThemeMode.DARK,
                MarketClientPreferences.readThemeMode(
                        temporaryDirectory.resolve("missing.properties")));
    }

    @Test
    void darkThemeLoadsAndSaves() {
        Path preferences = temporaryDirectory.resolve("economy-client.properties");
        MarketClientPreferences.writeThemeMode(preferences, EconomyUiThemeMode.DARK);
        assertEquals(EconomyUiThemeMode.DARK, MarketClientPreferences.readThemeMode(preferences));
    }

    @Test
    void lightThemeLoadsAndSaves() {
        Path preferences = temporaryDirectory.resolve("economy-client.properties");
        MarketClientPreferences.writeThemeMode(preferences, EconomyUiThemeMode.LIGHT);
        assertEquals(EconomyUiThemeMode.LIGHT, MarketClientPreferences.readThemeMode(preferences));
    }

    @Test
    void invalidThemeFallsBackToDark() {
        Path preferences = temporaryDirectory.resolve("economy-client.properties");
        MarketClientPreferences.writeRaw(preferences, "ui.theme", "neon");
        assertEquals(EconomyUiThemeMode.DARK, MarketClientPreferences.readThemeMode(preferences));
    }

    @Test
    void settingThemePreservesBrowseGrid() {
        Path preferences = temporaryDirectory.resolve("economy-client.properties");
        MarketClientPreferences.writeBrowseGridView(preferences, false);
        MarketClientPreferences.writeThemeMode(preferences, EconomyUiThemeMode.LIGHT);
        assertEquals(EconomyUiThemeMode.LIGHT, MarketClientPreferences.readThemeMode(preferences));
        assertFalse(MarketClientPreferences.readBrowseGridView(preferences));
    }

    @Test
    void settingBrowseGridPreservesTheme() {
        Path preferences = temporaryDirectory.resolve("economy-client.properties");
        MarketClientPreferences.writeThemeMode(preferences, EconomyUiThemeMode.LIGHT);
        MarketClientPreferences.writeBrowseGridView(preferences, false);
        assertEquals(EconomyUiThemeMode.LIGHT, MarketClientPreferences.readThemeMode(preferences));
        assertFalse(MarketClientPreferences.readBrowseGridView(preferences));
    }

    @Test
    void nextCyclesBetweenModes() {
        assertEquals(EconomyUiThemeMode.LIGHT, EconomyUiThemeMode.DARK.next());
        assertEquals(EconomyUiThemeMode.DARK, EconomyUiThemeMode.LIGHT.next());
    }

    @Test
    void fromStringParsesKnownValues() {
        assertEquals(EconomyUiThemeMode.LIGHT, EconomyUiThemeMode.fromString("LIGHT"));
        assertEquals(EconomyUiThemeMode.LIGHT, EconomyUiThemeMode.fromString("light"));
        assertEquals(EconomyUiThemeMode.DARK, EconomyUiThemeMode.fromString("dark"));
        assertEquals(EconomyUiThemeMode.DARK, EconomyUiThemeMode.fromString("garbage"));
        assertEquals(EconomyUiThemeMode.DARK, EconomyUiThemeMode.fromString(null));
        assertEquals("dark", EconomyUiThemeMode.DARK.storageValue());
        assertEquals("light", EconomyUiThemeMode.LIGHT.storageValue());
    }
}
