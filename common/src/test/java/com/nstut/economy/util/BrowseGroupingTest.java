package com.nstut.economy.util;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BrowseGroupingTest {
    private record Variant(String product, int offers) {}

    @Test
    void activeBrowsePreservesInactiveSiblingsForPicker() {
        var catalog = List.of(new Variant("sword", 1), new Variant("sword", 2), new Variant("sword", 0),
                new Variant("axe", 0));
        var active = catalog.stream().filter(card -> card.offers() > 0).toList();
        var groups = BrowseGrouping.visibleCatalogGroups(active, catalog, Variant::product);
        assertEquals(List.of("sword"), List.copyOf(groups.keySet()));
        assertEquals(catalog.subList(0, 3), groups.get("sword"));
        assertEquals(1, groups.get("sword").stream().filter(card -> card.offers() == 0).count());
    }

    @Test
    void oneActiveVariantStillHasMultiplePickerChoices() {
        var catalog = List.of(new Variant("sword", 1), new Variant("sword", 0));
        var group = BrowseGrouping.visibleCatalogGroups(catalog.subList(0, 1), catalog, Variant::product);
        assertEquals(catalog, group.get("sword"));
        assertThrows(UnsupportedOperationException.class, () -> group.get("sword").clear());
    }

    @Test
    void allBrowseRecoversInactiveProductsButEmptyMatchesStayEmpty() {
        var catalog = List.of(new Variant("sword", 0), new Variant("axe", 0));
        assertTrue(BrowseGrouping.visibleCatalogGroups(List.<Variant>of(), catalog, Variant::product).isEmpty());
        var groups = BrowseGrouping.visibleCatalogGroups(catalog, catalog, Variant::product);
        assertEquals(List.of("sword", "axe"), List.copyOf(groups.keySet()));
        assertEquals(List.of(catalog.get(1)), groups.get("axe"));
    }
}
