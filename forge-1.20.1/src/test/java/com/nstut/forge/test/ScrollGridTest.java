package com.nstut.forge.test;

import com.nstut.economy.ui.framework.ScrollGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScrollGridTest {
    @Test
    void rowCountRoundsUpPartialRows() {
        assertEquals(0, ScrollGrid.rowCount(0, 2));
        assertEquals(1, ScrollGrid.rowCount(1, 2));
        assertEquals(1, ScrollGrid.rowCount(2, 2));
        assertEquals(2, ScrollGrid.rowCount(3, 2));
        assertEquals(3, ScrollGrid.rowCount(6, 2));
    }

    @Test
    void cellCoordinatesMapToStableItemIndices() {
        assertEquals(0, ScrollGrid.indexForCell(0, 0, 2));
        assertEquals(1, ScrollGrid.indexForCell(0, 1, 2));
        assertEquals(4, ScrollGrid.indexForCell(2, 0, 2));
        assertEquals(5, ScrollGrid.indexForCell(2, 1, 2));
    }
}
