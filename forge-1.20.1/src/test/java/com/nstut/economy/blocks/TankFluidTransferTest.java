package com.nstut.economy.blocks;

import com.nstut.forge.test.MinecraftTestBase;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TankFluidTransferTest extends MinecraftTestBase {
    private static final BlockEntityType<TankBlockEntity> TEST_TANK_TYPE =
            BlockEntityType.Builder.<TankBlockEntity>of((pos, state) -> null, Blocks.IRON_BLOCK).build(null);

    private TankBlockEntity tankEntity() {
        return new TankBlockEntity(TEST_TANK_TYPE, BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState());
    }

    @Test
    @DisplayName("Fluid capability simulation reports transfer without mutating the tank")
    void capabilitySimulationDoesNotMutate() {
        TankBlockEntity tank = tankEntity();
        IFluidHandler handler = tank.fluidHandlerForTesting();

        assertEquals(1000, handler.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.SIMULATE));
        assertTrue(tank.getFluid().isEmpty());

        assertEquals(1000, handler.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE));
        FluidStack simulatedDrain = handler.drain(500, IFluidHandler.FluidAction.SIMULATE);
        assertEquals(500, simulatedDrain.getAmount());
        assertEquals(1000, tank.getFluidAmount());
    }

    @Test
    @DisplayName("Fluid capability rejects a different fluid in a non-empty tank")
    void capabilityRejectsMixedFluids() {
        TankBlockEntity tank = tankEntity();
        IFluidHandler handler = tank.fluidHandlerForTesting();
        handler.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);

        assertEquals(0, handler.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.SIMULATE));
        assertSame(Fluids.WATER, tank.getFluid().getFluid());
    }

    @Test
    @DisplayName("Draining the final 1,000 mB leaves a truly empty fluid handler")
    void drainsFinalBucketAmountCompletely() {
        TankBlockEntity tank = tankEntity();
        IFluidHandler handler = tank.fluidHandlerForTesting();
        handler.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);

        FluidStack drained = handler.drain(1000, IFluidHandler.FluidAction.EXECUTE);

        assertSame(Fluids.LAVA, drained.getFluid());
        assertEquals(1000, drained.getAmount());
        assertTrue(tank.getFluid().isEmpty());
        assertEquals(0, tank.getFluidAmount());
    }

    @Test
    @DisplayName("Filling a tank deposits exactly 1,000 mB")
    void fillsTankWithBucketAmount() {
        TankBlockEntity tank = tankEntity();
        IFluidHandler handler = tank.fluidHandlerForTesting();

        int filled = handler.fill(new FluidStack(Fluids.WATER, 1000),
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
