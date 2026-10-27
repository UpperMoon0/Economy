package com.nstut.economy.data;

import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class PortfolioValuation2612Test extends PortfolioValuationCases {
    @BeforeEach void initializeItemComponents(MinecraftServer server) {
        server.registryAccess();
    }

    @Test void unloadedRegisteredStoreUsesDurableMarkWithoutLoadingItsChunk(MinecraftServer server) {
        server.submit(() -> {
            try {
                // The ephemeral server provides registries but intentionally no worlds.
                // Initialize this test's world independently of other test classes/order.
                if (server.overworld() == null) {
                    try {
                        var createLevels = MinecraftServer.class.getDeclaredMethod("createLevels");
                        createLevels.setAccessible(true);
                        createLevels.invoke(server);
                    } catch (ReflectiveOperationException failure) {
                        throw new IllegalStateException("Could not initialize the disposable valuation-test world", failure);
                    }
                }
                var level = server.overworld();
                var owner = com.nstut.economy.api.AccountRef.player(java.util.UUID.randomUUID());
                var far = new net.minecraft.core.BlockPos(1_000_000, 64, 1_000_000);
                String dimension = level.dimension().identifier().toString();
                String key = com.nstut.economy.api.internal.BuiltinContainerStorageProvider.storeKey("vault", dimension, far);
                assertFalse(level.hasChunkAt(far));
                com.nstut.economy.blocks.VaultManager.register(owner, far, dimension);
                try {
                    EconomyAccountData.get(level).portfolioStores().update(owner, key, java.util.Map.of("test:good", 7L), 10);
                    var snapshot = com.nstut.economy.api.internal.BuiltinContainerStorageProvider.portfolioSnapshot(level, owner);
                    assertEquals(7L, snapshot.holdings().get("test:good"));
                    assertEquals(1, snapshot.stale());
                    assertEquals(0, snapshot.unknown());
                    assertFalse(level.hasChunkAt(far), "portfolio reads must not request an unloaded chunk");
                } finally {
                    com.nstut.economy.blocks.VaultManager.unregister(owner, far, dimension);
                }
            } finally {
                // World creation binds mod runtime; preserve isolation for pure settlement tests.
                com.nstut.economy.server.EconomyServerLifecycle.stop();
            }
        }).join();
    }
}
