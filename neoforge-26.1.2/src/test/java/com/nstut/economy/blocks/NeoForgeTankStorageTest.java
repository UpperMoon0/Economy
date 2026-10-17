package com.nstut.economy.blocks;

import com.nstut.neoforge.NeoForgeTankStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class NeoForgeTankStorageTest {
    @Test void transactionsCommitAbortAndShareStateAcrossLookups(net.minecraft.server.MinecraftServer server) {
        var tank = new TankBlockEntity(BlockPos.ZERO, BlockRegistries.TANK.get().defaultBlockState());
        var storage = NeoForgeTankStorage.get(tank);
        assertSame(storage, NeoForgeTankStorage.get(tank));
        var water = FluidResource.of(Fluids.WATER);
        var lava = FluidResource.of(Fluids.LAVA);
        try (var transaction = Transaction.openRoot()) {
            assertEquals(1000, storage.insert(0, water, 1000, transaction));
            assertEquals(1000, storage.getAmountAsLong(0));
            assertEquals(0, tank.getFluidAmount());
        }
        assertEquals(0, storage.getAmountAsLong(0));
        assertEquals(0, tank.getFluidAmount());
        try (var transaction = Transaction.openRoot()) {
            assertEquals(1000, storage.insert(0, water, 1000, transaction));
            try (var nested = Transaction.open(transaction)) {
                assertEquals(250, storage.extract(0, water, 250, nested));
            }
            assertEquals(1000, storage.getAmountAsLong(0));
            assertEquals(0, storage.insert(0, lava, 1000, transaction));
            transaction.commit();
        }
        assertEquals(1000, tank.getFluidAmount());
        try (var transaction = Transaction.openRoot()) {
            assertEquals(1000, storage.extract(0, water, 2000, transaction));
        }
        assertEquals(1000, tank.getFluidAmount());
        try (var transaction = Transaction.openRoot()) {
            assertEquals(1000, storage.extract(0, water, 2000, transaction));
            transaction.commit();
        }
        assertEquals(0, tank.getFluidAmount());
        assertTrue(storage.getResource(0).isEmpty());
    }
}


