package com.nstut.economy.config;

import org.junit.jupiter.api.Test;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class StorageRoutingSettingsTest {
    @Test void emptyFiltersAcceptEverything() {
        assertTrue(StorageRoutingSettings.DEFAULT.accepts("minecraft:iron_ingot"));
        assertEquals(0, StorageRoutingSettings.DEFAULT.priority());
    }
    @Test void allowlistTrimsDeduplicatesAndRoundTripsWithoutChangingOrder() {
        var settings = StorageRoutingSettings.parse(" minecraft:iron_ingot,mod:part,minecraft:iron_ingot ", -7);
        assertEquals("minecraft:iron_ingot,mod:part", settings.filterText());
        assertTrue(settings.accepts("mod:part"));
        assertFalse(settings.accepts("minecraft:gold_ingot"));
        assertEquals(settings, StorageRoutingSettings.parse(settings.filterText(), settings.priority()));
        assertThrows(UnsupportedOperationException.class, () -> settings.allowedIds().clear());
    }
    @Test void malformedAndUnboundedInputsAreRejected() {
        for (String text : new String[]{"iron_ingot", "Minecraft:water", "minecraft:water,", ",", "minecraft::water", "#minecraft:logs", "x".repeat(4097)})
            assertThrows(IllegalArgumentException.class, () -> StorageRoutingSettings.parse(text, 0), text);
        assertThrows(IllegalArgumentException.class, () -> StorageRoutingSettings.parse("", Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> StorageRoutingSettings.parse("", 10000));
        assertEquals(-9999, StorageRoutingSettings.parse("", -9999).priority());
        String many = IntStream.range(0, 33).mapToObj(i -> "test:item" + i).collect(Collectors.joining(","));
        assertThrows(IllegalArgumentException.class, () -> StorageRoutingSettings.parse(many, 0));
    }
}
