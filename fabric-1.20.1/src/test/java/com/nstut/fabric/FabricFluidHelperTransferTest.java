package com.nstut.fabric;

import com.nstut.economy.platform.services.IFluidHelper;
import com.nstut.economy.test.MinecraftTestBase;
import com.nstut.economy.trading.EconomyFluidStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FabricFluidHelperTransferTest extends MinecraftTestBase {

    private final FabricFluidHelper helper = new FabricFluidHelper();

    @Test
    @DisplayName("Emptying a water bucket into an empty tank yields exactly 1,000 mB and an empty bucket")
    void emptyWaterBucketIntoEmptyTank() {
        ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET);
        Optional<IFluidHelper.BucketTransfer> result = helper.tryEmptyContainerIntoTank(
                waterBucket, 128_000, EconomyFluidStack.EMPTY);

        assertTrue(result.isPresent());
        assertEquals(Items.BUCKET, result.get().resultContainer().getItem());
        assertEquals(Fluids.WATER, result.get().resultTankFluid().getFluid());
        assertEquals(1000, result.get().resultTankFluid().getAmount());
    }

    @Test
    @DisplayName("Emptying a water bucket into a tank with insufficient space is rejected without destroying fluid")
    void emptyWaterBucketIntoNearlyFullTankIsRejected() {
        ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET);
        EconomyFluidStack nearlyFull = new EconomyFluidStack(Fluids.WATER, 127_500);
        Optional<IFluidHelper.BucketTransfer> result = helper.tryEmptyContainerIntoTank(
                waterBucket, 128_000, nearlyFull);

        // Must be rejected because only 500 mB is available, but a bucket requires 1000 mB
        assertFalse(result.isPresent(), "Bucket transfer must be atomic: refuse rather than accept partial fluid");
    }

    @Test
    @DisplayName("Filling an empty bucket from a tank with at least 1,000 mB yields a water bucket")
    void fillEmptyBucketFromWaterTank() {
        ItemStack emptyBucket = new ItemStack(Items.BUCKET);
        EconomyFluidStack tankFluid = new EconomyFluidStack(Fluids.WATER, 2500);
        Optional<IFluidHelper.BucketTransfer> result = helper.tryFillContainerFromTank(
                emptyBucket, 128_000, tankFluid);

        assertTrue(result.isPresent());
        assertEquals(Items.WATER_BUCKET, result.get().resultContainer().getItem());
        assertEquals(Fluids.WATER, result.get().resultTankFluid().getFluid());
        assertEquals(1500, result.get().resultTankFluid().getAmount());
    }

    @Test
    @DisplayName("Filling a bucket from a tank with less than 1,000 mB is rejected")
    void fillEmptyBucketFromLowTankIsRejected() {
        ItemStack emptyBucket = new ItemStack(Items.BUCKET);
        EconomyFluidStack tankFluid = new EconomyFluidStack(Fluids.WATER, 750);
        Optional<IFluidHelper.BucketTransfer> result = helper.tryFillContainerFromTank(
                emptyBucket, 128_000, tankFluid);

        assertFalse(result.isPresent(), "Cannot fill a 1000 mB bucket when tank has less than 1000 mB");
    }
}
