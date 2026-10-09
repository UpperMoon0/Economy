package com.nstut.economy.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Runs with the real OpenUI signals and enums on every Minecraft family. */
class MarketFilterPersistenceTest {
    @TempDir Path directory;

    @Test
    void allMarketControlsRestoreEverySelectionInNewScreenState() {
        Path path = directory.resolve("economy-client.properties");
        MarketClientPreferences.writeThemeMode(path, EconomyUiThemeMode.LIGHT);
        MarketClientPreferences.writeBrowseGridView(path, false);
        checkSelections(path, "market.browse.activity", MarketScreen.BrowseActivityFilter.ACTIVE);
        checkSelections(path, "market.browse.product", MarketScreen.CommodityTypeFilter.ALL);
        checkSelections(path, "market.browse.sort", MarketScreen.BrowseSort.PRICE_ASC);
        checkSelections(path, "market.orders.order", MarketScreen.ActiveOrderFilter.ALL);
        checkSelections(path, "market.orders.product", MarketScreen.CommodityTypeFilter.ALL);
        checkSelections(path, "market.orders.sort", MarketScreen.ActiveOrderSort.NEWEST);
        checkSelections(path, "market.history.trade", MarketScreen.HistoryFilter.ALL);
        checkSelections(path, "market.history.product", MarketScreen.CommodityTypeFilter.ALL);
        checkSelections(path, "market.history.sort", MarketScreen.HistorySort.NEWEST);
        assertEquals(EconomyUiThemeMode.LIGHT, MarketClientPreferences.readThemeMode(path));
        assertFalse(MarketClientPreferences.readBrowseGridView(path));
    }

    private <T extends Enum<T>> void checkSelections(Path path, String key, T defaultValue) {
        var control = MarketClientPreferences.filterSignal(path, key, defaultValue);
        assertEquals(defaultValue, control.get(), "First open: " + key);
        for (T selection : defaultValue.getDeclaringClass().getEnumConstants()) {
            control.set(selection);
            var reopened = MarketClientPreferences.filterSignal(path, key, defaultValue);
            assertEquals(selection, reopened.get(), "Reopen: " + key);
            // Updating unrelated preferences must not reset this filter.
            MarketClientPreferences.writeBrowseGridView(path, true);
            MarketClientPreferences.writeThemeMode(path, EconomyUiThemeMode.DARK);
            assertEquals(selection, MarketClientPreferences.filterSignal(path, key, defaultValue).get());
        }
        MarketClientPreferences.writeThemeMode(path, EconomyUiThemeMode.LIGHT);
        MarketClientPreferences.writeBrowseGridView(path, false);
    }

    @Test
    void freshAndInvalidActivityUseActiveAndDoNotClobberOtherFilters() {
        Path path = directory.resolve("nested/economy-client.properties");
        String activity = "market.browse.activity";
        assertEquals(MarketScreen.BrowseActivityFilter.ACTIVE,
                MarketClientPreferences.filterSignal(path, activity, MarketScreen.BrowseActivityFilter.ACTIVE).get());
        var product = MarketClientPreferences.filterSignal(path, "market.browse.product", MarketScreen.CommodityTypeFilter.ALL);
        product.set(MarketScreen.CommodityTypeFilter.FLUIDS);
        MarketClientPreferences.writeRaw(path, activity, "OBSOLETE_VALUE");
        var restored = MarketClientPreferences.filterSignal(path, activity, MarketScreen.BrowseActivityFilter.ACTIVE);
        assertEquals(MarketScreen.BrowseActivityFilter.ACTIVE, restored.get());
        restored.set(MarketScreen.BrowseActivityFilter.ALL);
        assertEquals(MarketScreen.BrowseActivityFilter.ALL,
                MarketClientPreferences.filterSignal(path, activity, MarketScreen.BrowseActivityFilter.ACTIVE).get());
        assertEquals(MarketScreen.CommodityTypeFilter.FLUIDS,
                MarketClientPreferences.filterSignal(path, "market.browse.product", MarketScreen.CommodityTypeFilter.ALL).get());
    }
}
