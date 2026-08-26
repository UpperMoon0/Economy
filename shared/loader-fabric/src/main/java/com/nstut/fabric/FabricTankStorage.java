package com.nstut.fabric;

import com.nstut.economy.blocks.TankBlockEntity;
import com.nstut.economy.trading.EconomyFluidStack;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

/**
 * Fabric Transfer API storage exposing {@link TankBlockEntity} to pipes and automation.
 * Converts between Fabric Transfer API droplets (81,000 / bucket) and Economy mB (1,000 / bucket).
 */
public class FabricTankStorage extends SnapshotParticipant<EconomyFluidStack> implements SingleSlotStorage<FluidVariant> {

    public static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000; // 81 droplets = 1 mB

    private final TankBlockEntity tank;

    public FabricTankStorage(TankBlockEntity tank) {
        this.tank = tank;
    }

    @Override
    public boolean isResourceBlank() {
        return getResource().isBlank();
    }

    @Override
    public FluidVariant getResource() {
        return tank.getFluid().isEmpty() ? FluidVariant.blank() : FluidVariant.of(tank.getFluid().getFluid());
    }

    @Override
    public long getAmount() {
        return (long) tank.getFluidAmount() * DROPLETS_PER_MB;
    }

    @Override
    public long getCapacity() {
        return (long) tank.getCapacity() * DROPLETS_PER_MB;
    }

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        if (resource.isBlank() || maxAmount <= 0) return 0;
        if (!tank.getFluid().isEmpty() && tank.getFluid().getFluid() != resource.getFluid()) {
            return 0;
        }

        int maxMb = (int) (maxAmount / DROPLETS_PER_MB);
        if (maxMb <= 0) return 0;

        int room = tank.getCapacity() - tank.getFluidAmount();
        int toInsert = Math.min(room, maxMb);
        if (toInsert <= 0) return 0;

        updateSnapshots(transaction);
        tank.fill(new EconomyFluidStack(resource.getFluid(), toInsert));
        return (long) toInsert * DROPLETS_PER_MB;
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        if (resource.isBlank() || maxAmount <= 0 || tank.getFluid().isEmpty()) return 0;
        if (tank.getFluid().getFluid() != resource.getFluid()) return 0;

        int maxMb = (int) (maxAmount / DROPLETS_PER_MB);
        if (maxMb <= 0) return 0;

        int toExtract = Math.min(tank.getFluidAmount(), maxMb);
        if (toExtract <= 0) return 0;

        updateSnapshots(transaction);
        tank.drain(toExtract);
        return (long) toExtract * DROPLETS_PER_MB;
    }

    @Override
    protected EconomyFluidStack createSnapshot() {
        return tank.getFluid().copy();
    }

    @Override
    protected void readSnapshot(EconomyFluidStack snapshot) {
        tank.setFluid(snapshot);
    }
}
