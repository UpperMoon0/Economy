package com.nstut.forge;

import com.nstut.economy.blocks.TankBlockEntity;
import com.nstut.economy.config.EconomyConfig;
import com.nstut.economy.trading.EconomyFluidStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Forge tank block entity that re-exposes the tank's contents through the
 * Forge fluid capability so pipes and other automation can interact when the
 * config allows external automation.
 */
public class ForgeTankBlockEntity extends TankBlockEntity {

    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull net.minecraftforge.fluids.FluidStack getFluidInTank(int tank) {
            if (tank != 0 || getFluid().isEmpty()) {
                return net.minecraftforge.fluids.FluidStack.EMPTY;
            }
            return new net.minecraftforge.fluids.FluidStack(getFluid().getFluid(), getFluidAmount());
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0 ? getCapacity() : 0;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull net.minecraftforge.fluids.FluidStack stack) {
            return tank == 0 && !stack.isEmpty()
                    && (getFluid().isEmpty() || getFluid().getFluid() == stack.getFluid());
        }

        @Override
        public int fill(net.minecraftforge.fluids.FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            EconomyFluidStack probe = new EconomyFluidStack(resource.getFluid(), resource.getAmount());
            return action.simulate() ? simulateFill(probe) : ForgeTankBlockEntity.this.fill(probe);
        }

        @Override
        public @NotNull net.minecraftforge.fluids.FluidStack drain(net.minecraftforge.fluids.FluidStack resource, FluidAction action) {
            if (action.simulate()) {
                EconomyFluidStack have = getFluid();
                if (have.isEmpty() || have.getFluid() != resource.getFluid()) {
                    return net.minecraftforge.fluids.FluidStack.EMPTY;
                }
                int take = Math.min(have.getAmount(), resource.getAmount());
                return take <= 0 ? net.minecraftforge.fluids.FluidStack.EMPTY
                        : new net.minecraftforge.fluids.FluidStack(have.getFluid(), take);
            }
            EconomyFluidStack drained = ForgeTankBlockEntity.this.drain(new EconomyFluidStack(resource.getFluid(), resource.getAmount()));
            if (drained.isEmpty()) return net.minecraftforge.fluids.FluidStack.EMPTY;
            return new net.minecraftforge.fluids.FluidStack(drained.getFluid(), drained.getAmount());
        }

        @Override
        public @NotNull net.minecraftforge.fluids.FluidStack drain(int maxDrain, FluidAction action) {
            EconomyFluidStack have = getFluid();
            if (have.isEmpty()) return net.minecraftforge.fluids.FluidStack.EMPTY;
            return drain(new net.minecraftforge.fluids.FluidStack(have.getFluid(),
                    Math.min(maxDrain, have.getAmount())), action);
        }
    };

    private LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> fluidHandler);

    public ForgeTankBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            if (!EconomyConfig.getInstance().isExternalAutomationAllowed()) {
                return LazyOptional.empty();
            }
            return fluidCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        fluidCapability = LazyOptional.of(() -> fluidHandler);
    }
}
