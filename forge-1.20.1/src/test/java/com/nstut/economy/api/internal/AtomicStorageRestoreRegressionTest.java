package com.nstut.economy.api.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AtomicStorageRestoreRegressionTest {
    @Test
    @DisplayName("Commit divergence rolls storage back before reporting restore failure")
    void failedCommitRollsBackMutation() {
        AtomicInteger storage = new AtomicInteger(10);
        boolean restored = AtomicStorageRestore.commitWithRollback(
                () -> { storage.set(14); return false; },
                () -> storage.set(10));

        assertFalse(restored);
        assertEquals(10, storage.get(), "failed commit must not leave partially restored units in storage");
    }

    @Test
    @DisplayName("Commit exceptions also roll storage back")
    void throwingCommitRollsBackMutation() {
        AtomicInteger storage = new AtomicInteger(10);
        assertThrows(IllegalStateException.class, () -> AtomicStorageRestore.commitWithRollback(
                () -> { storage.set(14); throw new IllegalStateException("commit divergence"); },
                () -> storage.set(10)));
        assertEquals(10, storage.get());
    }

    @Test
    @DisplayName("Cancel and quantity-decrease paths use the atomic escrow restorer on both implementations")
    void managerRestorePathsAreWiredAtomically() throws IOException {
        Path root = Path.of(System.getProperty("economy.repoRoot"));
        for (String relative : new String[] {
                "shared/minecraft-1.20plus/src/main/java/com/nstut/economy/trading/OrderManager.java",
                "neoforge-26.1.2/src/main/java/com/nstut/economy/trading/OrderManager.java"
        }) {
            String source = Files.readString(root.resolve(relative));
            assertTrue(source.contains("AtomicStorageRestore.restoreEscrow(level, requester, returnItems, List.of())"), relative);
            assertTrue(source.contains("AtomicStorageRestore.restoreEscrow(level, requester, List.of(), parts)"), relative);
            assertTrue(source.contains("AtomicStorageRestore.restoreEscrow(level, requester,\n                    order.getReservedItems(), order.getReservedFluids())"), relative);
        }
    }
}
