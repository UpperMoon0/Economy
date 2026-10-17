package com.nstut.economy.blocks;

import com.nstut.economy.config.StorageRoutingSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.util.ProblemReporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class StorageRoutingPersistenceTest {
    @Test void managersRouteFilteredPurchasesInPriorityOrder(MinecraftServer server) {
        server.submit(() -> {
            try {
                // NeoForge's ephemeral server initializes registries but deliberately creates no levels.
                if (server.overworld() == null) {
                    try {
                        var createLevels = MinecraftServer.class.getDeclaredMethod("createLevels");
                        createLevels.setAccessible(true);
                        createLevels.invoke(server);
                    } catch (ReflectiveOperationException failure) {
                        throw new IllegalStateException("Could not initialize the disposable routing-test world", failure);
                    }
                }
                var level = server.overworld();
                var owner = com.nstut.economy.api.AccountRef.player(java.util.UUID.randomUUID());
                var lowPos = new BlockPos(0, 80, 0); var highPos = new BlockPos(1, 80, 0);
                var rejectPos = new BlockPos(2, 80, 0); var tankPos = new BlockPos(0, 80, 1);
                for (var pos : java.util.List.of(lowPos, highPos, rejectPos, tankPos)) level.getChunkAt(pos);
                for (var pos : java.util.List.of(lowPos, highPos, rejectPos)) level.setBlockAndUpdate(pos, BlockRegistries.VAULT.get().defaultBlockState());
                level.setBlockAndUpdate(tankPos, BlockRegistries.TANK.get().defaultBlockState());
                var low = (VaultBlockEntity) level.getBlockEntity(lowPos);
                var high = (VaultBlockEntity) level.getBlockEntity(highPos);
                var reject = (VaultBlockEntity) level.getBlockEntity(rejectPos);
                var tank = (TankBlockEntity) level.getBlockEntity(tankPos);
                low.setOwner(owner); high.setOwner(owner); reject.setOwner(owner); tank.setOwner(owner);
                high.setRoutingSettings(StorageRoutingSettings.parse("minecraft:iron_ingot", 10));
                reject.setRoutingSettings(StorageRoutingSettings.parse("minecraft:gold_ingot", 20));
                var payload = java.util.List.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 12));
                assertTrue(VaultManager.simulateInsertItemStacksToVaults(level, owner, payload).isEmpty());
                assertTrue(high.isEmpty());
                assertTrue(VaultManager.insertItemStacksToVaults(level, owner, payload).isEmpty());
                assertEquals(12, high.countItem(net.minecraft.world.item.Items.IRON_INGOT));
                assertTrue(low.isEmpty()); assertTrue(reject.isEmpty());
                var extracted = net.minecraft.core.NonNullList.<net.minecraft.world.item.ItemStack>create();
                assertTrue(VaultManager.extractItemFromVaults(level, owner, net.minecraft.world.item.Items.IRON_INGOT, 5, extracted));
                assertEquals(7, high.countItem(net.minecraft.world.item.Items.IRON_INGOT));
                tank.setRoutingSettings(StorageRoutingSettings.parse("minecraft:lava", -5));
                var water = new com.nstut.economy.trading.EconomyFluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000);
                assertEquals(0, TankManager.countAvailableFluidSpaceInTanks(level, owner, water.getFluid()));
                assertEquals(0, TankManager.simulateInsertFluidToTanks(level, owner, water));
                assertEquals(0, TankManager.insertFluidToTanks(level, owner, water));
                tank.setMode(TankBlockEntity.TankMode.INPUT);
                assertEquals(1000, TankManager.simulateInsertFluidToTanks(level, owner, water, false));
                assertEquals(1000, TankManager.restoreFluidToTanks(level, owner, water));
            } finally {
                // Level creation binds the mod runtime; do not leak it into pure settlement cases.
                com.nstut.economy.server.EconomyServerLifecycle.stop();
            }
        }).join();
    }

    @Test void modeChangesAndDiskRoundtripRetainRouting(MinecraftServer server) {
        var vault = new VaultBlockEntity(BlockPos.ZERO, BlockRegistries.VAULT.get().defaultBlockState());
        var tank = new TankBlockEntity(BlockPos.ZERO, BlockRegistries.TANK.get().defaultBlockState());
        var items = StorageRoutingSettings.parse("minecraft:iron_ingot,minecraft:gold_ingot", 55, true);
        var fluids = StorageRoutingSettings.parse("minecraft:water", -7, true);
        vault.setRoutingSettings(items); tank.setRoutingSettings(fluids);
        for (int i = 0; i < 3; i++) { vault.cycleMode(); tank.cycleMode(); }
        var vaultTag = vault.getUpdateTag(server.registryAccess());
        var tankTag = tank.getUpdateTag(server.registryAccess());
        vault.setRoutingSettings(StorageRoutingSettings.DEFAULT); tank.setRoutingSettings(StorageRoutingSettings.DEFAULT);
        vault.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), vaultTag));
        tank.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), tankTag));
        assertEquals(items, vault.getRoutingSettings()); assertEquals(fluids, tank.getRoutingSettings());
        vaultTag.remove("PurchaseFilterBlacklist"); tankTag.remove("PurchaseFilterBlacklist");
        vault.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), vaultTag));
        tank.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), tankTag));
        assertEquals(items.withBlacklist(false), vault.getRoutingSettings());
        assertEquals(fluids.withBlacklist(false), tank.getRoutingSettings());
        vault.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), new CompoundTag()));
        tank.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), new CompoundTag()));
        assertEquals(StorageRoutingSettings.DEFAULT, vault.getRoutingSettings());
        assertEquals(StorageRoutingSettings.DEFAULT, tank.getRoutingSettings());
    }
}
