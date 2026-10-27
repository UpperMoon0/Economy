package com.nstut.economy.core;

import com.nstut.economy.api.AccountRef;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PortfolioStoreCacheTest {
    private final AccountRef player = AccountRef.player(UUID.randomUUID());
    private final AccountRef team = AccountRef.team(player.id());
    private PortfolioStoreCache cache() { return new PortfolioStoreCache(() -> {}); }
    private PortfolioStoreCache.Source source(boolean loaded, long count, AtomicInteger lookups) {
        return new PortfolioStoreCache.Source() {
            public boolean loaded() { return loaded; }
            public Map<String, Long> holdings() {
                if (!loaded) fail("Unloaded source must never perform a block-entity/chunk lookup");
                lookups.incrementAndGet(); return count == 0 ? Map.of() : Map.of("test:good", count);
            }
        };
    }

    @Test void unloadedGuardRetainsKnownMarkAndMakesUnknownExplicitAcrossRestart() {
        var cache = cache(); AtomicInteger lookups = new AtomicInteger();
        cache.snapshot(player, 10, () -> Map.of("known", source(true, 7, lookups)));
        var unloaded = cache.snapshot(player, 1010, () -> Map.of("known", source(false, 0, lookups), "unknown", source(false, 0, lookups)));
        assertEquals(1, lookups.get()); assertEquals(7L, unloaded.holdings().get("test:good"));
        assertEquals(1, unloaded.stale()); assertEquals(1, unloaded.unknown());
        var restarted = cache(); restarted.decode(cache.encode());
        var restored = restarted.snapshot(player, 2010, () -> Map.of("known", source(false, 0, lookups), "unknown", source(false, 0, lookups)));
        assertEquals(unloaded.holdings(), restored.holdings());
        assertEquals(1, restored.stale()); assertEquals(1, restored.unknown()); assertEquals(1, lookups.get());
    }

    @Test void thousandHotCashOrUiRequestsDoOneStoreEnumerationAndLookup() {
        var cache = cache(); AtomicInteger enumerations = new AtomicInteger(), lookups = new AtomicInteger();
        java.util.function.Supplier<Map<String, PortfolioStoreCache.Source>> stores = () -> {
            enumerations.incrementAndGet(); return Map.of("vault", source(true, 7, lookups));
        };
        for (int i = 0; i < 1000; i++) assertEquals(7L, cache.snapshot(player, 100 + i / 2, stores).holdings().get("test:good"));
        assertEquals(1, enumerations.get()); assertEquals(1, lookups.get());
        cache.snapshot(player, 1100, stores);
        assertEquals(2, enumerations.get()); assertEquals(2, lookups.get());
    }

    @Test void loadedMutationUpdatesSingleStoreWithoutReenumeratingOtherStores() {
        var cache = cache(); AtomicInteger enumerations = new AtomicInteger(), lookups = new AtomicInteger();
        java.util.function.Supplier<Map<String, PortfolioStoreCache.Source>> stores = () -> {
            enumerations.incrementAndGet(); return Map.of("first", source(true, 7, lookups), "second", source(true, 5, lookups));
        };
        assertEquals(12L, cache.snapshot(player, 100, stores).holdings().get("test:good"));
        cache.update(player, "first", Map.of("test:good", 2L), 200);
        assertEquals(7L, cache.snapshot(player, 200, stores).holdings().get("test:good"));
        assertEquals(1, enumerations.get()); assertEquals(2, lookups.get());
    }

    @Test void unloadedSuccessionMovesPhysicalMarkBetweenTypedOwnersExactlyOnce() {
        var cache = cache(); cache.update(team, "vault|world|1", Map.of("test:good", 9L), 10);
        cache.reassign("vault|world|1", player); cache.reassign("vault|world|1", player);
        assertTrue(cache.keys(team).isEmpty()); assertEquals(1, cache.keys(player).size());
        assertTrue(cache.snapshot(team, 100, Map::of).holdings().isEmpty());
        var inherited = cache.snapshot(player, 100, () -> Map.of("vault|world|1", source(false, 0, new AtomicInteger())));
        assertEquals(9L, inherited.holdings().get("test:good")); assertEquals(1, inherited.stale());
    }

    @Test void loadedMissingOrDestroyedStoreClearsOldValueAndUnloadedStoreDoesNot() {
        var cache = cache(); AtomicInteger lookups = new AtomicInteger();
        cache.update(player, "store", Map.of("test:good", 9L), 10);
        assertEquals(9L, cache.snapshot(player, 20, () -> Map.of("store", source(false, 0, lookups))).holdings().get("test:good"));
        cache.invalidate(player);
        assertTrue(cache.snapshot(player, 21, () -> Map.of("store", source(true, 0, lookups))).holdings().isEmpty());
        assertEquals(1, lookups.get());
    }

    @Test void binaryPersistenceExceedsNbtStringLimitWithoutLosingStores() {
        var cache = cache();
        for (int i = 0; i < 2500; i++) cache.update(player, "vault|minecraft:overworld|" + i, Map.of("test:good", 1L), 10);
        byte[] encoded = cache.encode(); assertTrue(encoded.length > 65535);
        var restored = cache(); restored.decode(encoded);
        assertEquals(2500, restored.keys(player).size());
        assertEquals(2500L, restored.snapshot(player, 20, Map::of).holdings().get("test:good"));
    }

    @Test void confirmedDestroyedStoreIsPrunedRatherThanQueriedForever() {
        var cache = cache(); cache.update(player, "destroyed", Map.of("test:good", 9L), 10);
        AtomicInteger absentChecks = new AtomicInteger();
        PortfolioStoreCache.Source missing = new PortfolioStoreCache.Source() {
            public boolean loaded() { return true; }
            public boolean exists() { absentChecks.incrementAndGet(); return false; }
            public Map<String, Long> holdings() { fail("Missing store has no inventory to read"); return Map.of(); }
        };
        assertTrue(cache.snapshot(player, 20, () -> Map.of("destroyed", missing)).holdings().isEmpty());
        assertTrue(cache.keys(player).isEmpty());
        cache.snapshot(player, 1020, () -> {
            assertTrue(cache.keys(player).isEmpty()); return Map.of();
        });
        assertEquals(1, absentChecks.get());
    }

    @Test void staleOldOwnerIndexCannotDeletePhysicalMarkReassignedToNewOwner() {
        var cache = cache(); cache.update(team, "physical-store", Map.of("test:good", 9L), 10);
        cache.reassign("physical-store", player);
        PortfolioStoreCache.Source wrongOwner = new PortfolioStoreCache.Source() {
            public boolean loaded() { return true; }
            public boolean exists() { return false; }
            public Map<String, Long> holdings() { throw new AssertionError("Wrong owner must not read contents"); }
        };
        assertTrue(cache.snapshot(team, 20, () -> Map.of("physical-store", wrongOwner)).holdings().isEmpty());
        assertEquals(9L, cache.snapshot(player, 21, Map::of).holdings().get("test:good"));
        assertEquals(1, cache.keys(player).size());
    }
}
