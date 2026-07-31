package com.nstut.forge.client;

import com.nstut.Economy;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Small client-only preference store for market presentation choices.
 */
public final class MarketClientPreferences {
    private static final String FILE_NAME = "economy-client.properties";
    private static final String BROWSE_GRID_KEY = "market.browse.grid";
    private static Boolean cachedBrowseGrid;

    private MarketClientPreferences() {}

    public static synchronized boolean isBrowseGridView() {
        if (cachedBrowseGrid == null) {
            cachedBrowseGrid = readBrowseGridView(preferencesPath());
        }
        return cachedBrowseGrid;
    }

    public static synchronized void setBrowseGridView(boolean gridView) {
        cachedBrowseGrid = gridView;
        writeBrowseGridView(preferencesPath(), gridView);
    }

    static Path preferencesPath() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    public static boolean readBrowseGridView(Path path) {
        if (path == null || !Files.isRegularFile(path)) return true;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            return Boolean.parseBoolean(properties.getProperty(BROWSE_GRID_KEY, "true"));
        } catch (IOException ex) {
            Economy.LOGGER.warn("Could not read market client preferences from {}", path, ex);
            return true;
        }
    }

    public static void writeBrowseGridView(Path path, boolean gridView) {
        if (path == null) return;
        Properties properties = new Properties();
        if (Files.isRegularFile(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            } catch (IOException ex) {
                Economy.LOGGER.warn("Could not preserve existing market client preferences from {}", path, ex);
            }
        }
        properties.setProperty(BROWSE_GRID_KEY, Boolean.toString(gridView));
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
