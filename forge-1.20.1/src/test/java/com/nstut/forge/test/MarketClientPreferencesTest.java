package com.nstut.forge.test;

import com.nstut.forge.client.MarketClientPreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

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
}
