package com.nstut.economy.client;

import com.nstut.economy.config.StorageRoutingSettings;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class StorageFilterPickerModelTest {
    private final List<StorageFilterPickerModel.Entry> catalog = List.of(
            new StorageFilterPickerModel.Entry("minecraft:iron_ingot", "Iron Ingot"),
            new StorageFilterPickerModel.Entry("minecraft:gold_ingot", "Gold Ingot"),
            new StorageFilterPickerModel.Entry("mod:iron_dust", "Iron Dust"));
    @Test void searchMatchesReadableNamesIdsAndAllWordsWithoutSelecting() {
        var picker = new StorageFilterPickerModel(catalog, StorageRoutingSettings.DEFAULT);
        assertEquals(1, picker.search("IRON ingot").size());
        assertEquals("minecraft:iron_ingot", picker.search("IRON ingot").get(0).id());
        assertEquals("mod:iron_dust", picker.search("mod:iron").get(0).id());
        assertTrue(picker.search("not a product").isEmpty());
        assertEquals(0, picker.size());
        assertTrue(picker.settings(0).accepts("minecraft:gold_ingot"));
    }
    @Test void explicitSelectionsAreDeduplicatedRemovableAndIndependentOfTheSavedSettings() {
        var saved = StorageRoutingSettings.parse("minecraft:iron_ingot", 5);
        var picker = new StorageFilterPickerModel(catalog, saved);
        assertEquals(1, picker.search("").size());
        assertTrue(picker.toggle("minecraft:gold_ingot"));
        assertEquals(2, picker.size());
        assertEquals(Set.of("minecraft:iron_ingot"), saved.allowedIds());
        assertTrue(picker.toggle("minecraft:iron_ingot"));
        assertEquals("minecraft:gold_ingot", picker.search("").get(0).id());
        assertTrue(picker.toggle("minecraft:gold_ingot"));
        assertTrue(picker.search("").isEmpty());
        assertTrue(picker.settings(-7).accepts("any:item"));
        assertEquals(-7, picker.settings(-7).priority());
        assertFalse(picker.toggle("missing:item"));
    }
    @Test void searchingAndChangingModeKeepTheSelectedListIndependent() {
        var saved = StorageRoutingSettings.parse("minecraft:iron_ingot", 5, true);
        var picker = new StorageFilterPickerModel(catalog, saved);
        picker.search("gold");
        assertEquals(List.of(catalog.get(0)), picker.search(""));
        var whitelist = picker.settings(5).withBlacklist(false);
        assertEquals(saved.allowedIds(), whitelist.allowedIds());
        assertTrue(whitelist.accepts("minecraft:iron_ingot"));
        assertFalse(saved.accepts("minecraft:iron_ingot"));
        assertTrue(picker.toggle("minecraft:gold_ingot"));
        assertEquals(2, picker.search("").size());
        assertEquals(1, saved.allowedIds().size());
    }
    @Test void selectionLimitSearchCapAndUnavailableSavedEntriesStaySafe() {
        var entries = IntStream.range(0, 70).mapToObj(i -> new StorageFilterPickerModel.Entry("mod:item" + i, "Item " + i)).toList();
        var picker = new StorageFilterPickerModel(entries, StorageRoutingSettings.parse("missing:item", 0));
        assertEquals("missing:item", picker.search("").get(0).id());
        assertTrue(picker.toggle("missing:item"));
        assertEquals(50, picker.search("item").size());
        for (int i = 0; i < 32; i++) assertTrue(picker.toggle("mod:item" + i));
        assertFalse(picker.toggle("mod:item32"));
        assertTrue(picker.toggle("mod:item0"));
        assertTrue(picker.toggle("mod:item32"));
        assertEquals(32, picker.settings(0).allowedIds().size());
    }
    @Test void searchDoesNotDependOnTheUsersLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(1, new StorageFilterPickerModel(catalog, StorageRoutingSettings.DEFAULT).search("IRON INGOT").size());
        } finally { Locale.setDefault(previous); }
    }
}
