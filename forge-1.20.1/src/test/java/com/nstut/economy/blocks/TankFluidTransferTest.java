package com.nstut.economy.blocks;

import com.nstut.forge.test.MinecraftTestBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TankFluidTransferTest extends MinecraftTestBase {
    @Test
    @DisplayName("Draining the final 1,000 mB leaves a truly empty fluid handler")
    void drainsFinalBucketAmountCompletely() {
        FluidTank tank = new FluidTank(TankBlockEntity.DEFAULT_CAPACITY);
        tank.setFluid(new FluidStack(Fluids.LAVA, 1000));

        FluidStack drained = tank.drain(1000, IFluidHandler.FluidAction.EXECUTE);

        assertSame(Fluids.LAVA, drained.getFluid());
        assertEquals(1000, drained.getAmount());
        assertTrue(tank.getFluid().isEmpty());
        assertEquals(0, tank.getFluidAmount());
    }

    @Test
    @DisplayName("Filling a tank deposits exactly 1,000 mB")
    void fillsTankWithBucketAmount() {
        FluidTank tank = new FluidTank(TankBlockEntity.DEFAULT_CAPACITY);

        int filled = tank.fill(new FluidStack(Fluids.WATER, 1000),
                IFluidHandler.FluidAction.EXECUTE);

        assertEquals(1000, filled);
        assertSame(Fluids.WATER, tank.getFluid().getFluid());
        assertEquals(1000, tank.getFluidAmount());
    }

    @Test
    @DisplayName("An update tag without Fluid clears the previous client snapshot")
    void missingFluidTagMeansEmptyTank() {
        assertTrue(TankBlockEntity.loadFluidFromTag(new CompoundTag()).isEmpty());
    }

    @Test
    @DisplayName("A serialized fluid update restores its exact type and amount")
    void fluidTagRoundTrips() {
        CompoundTag root = new CompoundTag();
        CompoundTag fluidTag = new CompoundTag();
        new FluidStack(Fluids.LAVA, 37_500).writeToNBT(fluidTag);
        root.put("Fluid", fluidTag);

        FluidStack loaded = TankBlockEntity.loadFluidFromTag(root);
        assertSame(Fluids.LAVA, loaded.getFluid());
        assertEquals(37_500, loaded.getAmount());
    }
}
