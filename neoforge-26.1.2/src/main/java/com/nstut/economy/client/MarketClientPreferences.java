package com.nstut.economy.client;
import com.nstut.economy.platform.Services;

import com.nstut.Economy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Small client-only preference store for market presentation choices.
 * Both settings live in the same economy-client.properties file so that
 * writing one never clobbers the other.
 */
public final class MarketClientPreferences {
    private static final String FILE_NAME = "economy-client.properties";
    private static final String BROWSE_GRID_KEY = "market.browse.grid";
    private static final String THEME_KEY = "ui.theme";

    private static EconomyUiThemeMode cachedThemeMode;
    private static Boolean cachedBrowseGrid;

    private MarketClientPreferences() {}

    static Path preferencesPath() {
        return Services.PLATFORM.configDir().resolve(FILE_NAME);
    }

    // ── Theme ────────────────────────────────────────────────────────────

    public static synchronized EconomyUiThemeMode getThemeMode() {
        if (cachedThemeMode == null) {
            cachedThemeMode = readThemeMode(preferencesPath());
        }
        return cachedThemeMode;
    }

    public static synchronized void setThemeMode(EconomyUiThemeMode mode) {
        cachedThemeMode = mode;
        writePreference(preferencesPath(), THEME_KEY, mode.storageValue());
    }

    public static void writeThemeMode(Path path, EconomyUiThemeMode mode) {
        writePreference(path, THEME_KEY, mode.storageValue());
    }

    public static EconomyUiThemeMode readThemeMode(Path path) {
        if (path == null || !Files.isRegularFile(path)) return EconomyUiThemeMode.DARK;
        try (InputStream input = Files.newInputStream(path)) {
            Properties properties = new Properties();
            properties.load(input);
            return EconomyUiThemeMode.fromString(properties.getProperty(THEME_KEY));
        } catch (IOException ex) {
            Economy.LOGGER.warn("Could not read ui theme from {}", path, ex);
            return EconomyUiThemeMode.DARK;
        }
    }

    // ── Browse grid (existing API, unchanged) ─────────────────────────────

    public static synchronized boolean isBrowseGridView() {
        if (cachedBrowseGrid == null) {
            cachedBrowseGrid = readBrowseGridView(preferencesPath());
        }
        return cachedBrowseGrid;
    }

    public static synchronized void setBrowseGridView(boolean gridView) {
        cachedBrowseGrid = gridView;
        writePreference(preferencesPath(), BROWSE_GRID_KEY, Boolean.toString(gridView));
    }

    public static void writeBrowseGridView(Path path, boolean gridView) {
        writePreference(path, BROWSE_GRID_KEY, Boolean.toString(gridView));
    }

    public static boolean readBrowseGridView(Path path) {
        if (path == null || !Files.isRegularFile(path)) return true;
        try (InputStream input = Files.newInputStream(path)) {
            Properties properties = new Properties();
            properties.load(input);
            return Boolean.parseBoolean(properties.getProperty(BROWSE_GRID_KEY, "true"));
        } catch (IOException ex) {
            Economy.LOGGER.warn("Could not read market client preferences from {}", path, ex);
            return true;
        }
    }

    // ── Shared writer (preserves every other key) ─────────────────────────

    /** Test-only helper for writing an arbitrary key without clobbering others. */
    public static void writeRaw(Path path, String key, String value) {
        writePreference(path, key, value);
    }

    private static synchronized void writePreference(Path path, String key, String value) {
        if (path == null) return;
        Properties properties = new Properties();
        if (Files.isRegularFile(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            } catch (IOException ex) {
                Economy.LOGGER.warn("Could not preserve existing market client preferences from {}", path, ex);
            }
        }
        properties.setProperty(key, value);
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "Economy client preferences");
            }
        } catch (IOException ex) {
            Economy.LOGGER.warn("Could not save market client preferences to {}", path, ex);
        }
    }
}

