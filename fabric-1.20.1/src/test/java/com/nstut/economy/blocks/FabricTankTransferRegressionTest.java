package com.nstut.economy.blocks;

import com.nstut.economy.test.MinecraftTestBase;
import com.nstut.fabric.FabricTankStorage;
import net.fabricmc.fabric.impl.transfer.fluid.FluidVariantImpl;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FabricTankTransferRegressionTest extends MinecraftTestBase {
    private TankBlockEntity tank() {
        var type = BlockEntityType.Builder.<TankBlockEntity>of((pos, state) -> null, Blocks.IRON_BLOCK).build(null);
        return new TankBlockEntity(type, BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState());
    }

    @Test void unlimitedTransferClampsBeforeNarrowingAndCommitsExactAmounts() {
        var tank = tank();
        var storage = FabricTankStorage.get(tank);
        // The implementation constructor bypasses Fabric's mixin-injected variant cache in plain JUnit.
        var water = new FluidVariantImpl(Fluids.WATER, null);
        long capacity = storage.getCapacity();
        try (var transaction = Transaction.openOuter()) {
            assertEquals(capacity, storage.insert(water, Long.MAX_VALUE, transaction));
            assertEquals(0, tank.getFluidAmount());
            transaction.commit();
        }
        assertEquals(tank.getCapacity(), tank.getFluidAmount());
        try (var transaction = Transaction.openOuter()) {
            assertEquals(capacity, storage.extract(water, Long.MAX_VALUE, transaction));
        }
        assertEquals(tank.getCapacity(), tank.getFluidAmount());
        try (var transaction = Transaction.openOuter()) {
            assertEquals(capacity, storage.extract(water, Long.MAX_VALUE, transaction));
            transaction.commit();
        }
        assertEquals(0, tank.getFluidAmount());
    }

    @Test void taggedFluidCannotBeLaunderedIntoOrOutOfBaseFluid() {
        var tank = tank();
        var storage = FabricTankStorage.get(tank);
        var tag = new CompoundTag();
        tag.putString("variant", "special");
        var tagged = new FluidVariantImpl(Fluids.WATER, tag);
        var plain = new FluidVariantImpl(Fluids.WATER, null);
        try (var transaction = Transaction.openOuter()) {
            assertEquals(0, storage.insert(tagged, 81000, transaction));
            assertEquals(81000, storage.insert(plain, 81000, transaction));
            assertEquals(0, storage.extract(tagged, 81000, transaction));
            transaction.commit();
        }
        assertEquals(1000, tank.getFluidAmount());
    }
}
