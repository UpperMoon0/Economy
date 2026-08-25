package com.nstut.economy.blocks;

import com.nstut.economy.test.MinecraftTestBase;
import com.nstut.economy.trading.EconomyFluidStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
    @DisplayName("Simulated fill reports transfer without mutating the tank")
    void simulateFillDoesNotMutate() {
        TankBlockEntity tank = tankEntity();

        assertEquals(1000, tank.simulateFill(new EconomyFluidStack(Fluids.WATER, 1000)));
        assertTrue(tank.getFluid().isEmpty());

        assertEquals(1000, tank.fill(new EconomyFluidStack(Fluids.WATER, 1000)));
        assertEquals(0, tank.simulateFill(new EconomyFluidStack(Fluids.LAVA, 500)));
        assertEquals(1000, tank.getFluidAmount());
    }

    @Test
    @DisplayName("A non-empty tank rejects a different fluid")
    void tankRejectsMixedFluids() {
        TankBlockEntity tank = tankEntity();
        tank.fill(new EconomyFluidStack(Fluids.WATER, 1000));

        assertEquals(0, tank.simulateFill(new EconomyFluidStack(Fluids.LAVA, 1000)));
        assertEquals(0, tank.fill(new EconomyFluidStack(Fluids.LAVA, 1000)));
        assertSame(Fluids.WATER, tank.getFluid().getFluid());
    }

    @Test
    @DisplayName("Draining the final 1,000 mB leaves a truly empty tank")
    void drainsFinalBucketAmountCompletely() {
        TankBlockEntity tank = tankEntity();
        tank.fill(new EconomyFluidStack(Fluids.LAVA, 1000));

        EconomyFluidStack drained = tank.drain(1000);

        assertSame(Fluids.LAVA, drained.getFluid());
        assertEquals(1000, drained.getAmount());
        assertTrue(tank.getFluid().isEmpty());
        assertEquals(0, tank.getFluidAmount());
    }

    @Test
    @DisplayName("Filling a tank deposits exactly 1,000 mB")
    void fillsTankWithBucketAmount() {
        TankBlockEntity tank = tankEntity();

        int filled = tank.fill(new EconomyFluidStack(Fluids.WATER, 1000));

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
        new EconomyFluidStack(Fluids.LAVA, 37_500).writeTo(fluidTag);
        root.put("Fluid", fluidTag);

        EconomyFluidStack loaded = TankBlockEntity.loadFluidFromTag(root);
        assertSame(Fluids.LAVA, loaded.getFluid());
        assertEquals(37_500, loaded.getAmount());
    }
}
