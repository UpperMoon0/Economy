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
 * Defers client sync and BE modification until transaction final commit to avoid packet churn
 * and rendering flicker during simulated transfers.
 */
public class FabricTankStorage extends SnapshotParticipant<EconomyFluidStack> implements SingleSlotStorage<FluidVariant> {

    public static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000; // 81 droplets = 1 mB

    private final TankBlockEntity tank;
    private EconomyFluidStack txnFluid = null;

    public FabricTankStorage(TankBlockEntity tank) {
        this.tank = tank;
    }

    private EconomyFluidStack getCurrentFluid() {
        return txnFluid != null ? txnFluid : tank.getFluid();
    }

    @Override
    public boolean isResourceBlank() {
        return getResource().isBlank();
    }

    @Override
    public FluidVariant getResource() {
        EconomyFluidStack cur = getCurrentFluid();
        return cur.isEmpty() ? FluidVariant.blank() : FluidVariant.of(cur.getFluid());
    }

    @Override
    public long getAmount() {
        return (long) getCurrentFluid().getAmount() * DROPLETS_PER_MB;
    }

    @Override
    public long getCapacity() {
        return (long) tank.getCapacity() * DROPLETS_PER_MB;
    }

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        if (resource.isBlank() || maxAmount <= 0) return 0;
        EconomyFluidStack cur = getCurrentFluid();
        if (!cur.isEmpty() && cur.getFluid() != resource.getFluid()) {
            return 0;
        }

        int maxMb = (int) (maxAmount / DROPLETS_PER_MB);
        if (maxMb <= 0) return 0;

        int room = tank.getCapacity() - cur.getAmount();
        int toInsert = Math.min(room, maxMb);
        if (toInsert <= 0) return 0;

        updateSnapshots(transaction);
        txnFluid = cur.isEmpty()
                ? new EconomyFluidStack(resource.getFluid(), toInsert)
                : new EconomyFluidStack(cur.getFluid(), cur.getAmount() + toInsert);
        return (long) toInsert * DROPLETS_PER_MB;
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        EconomyFluidStack cur = getCurrentFluid();
        if (resource.isBlank() || maxAmount <= 0 || cur.isEmpty()) return 0;
        if (cur.getFluid() != resource.getFluid()) return 0;

        int maxMb = (int) (maxAmount / DROPLETS_PER_MB);
        if (maxMb <= 0) return 0;

        int toExtract = Math.min(cur.getAmount(), maxMb);
        if (toExtract <= 0) return 0;

        updateSnapshots(transaction);
        int remaining = cur.getAmount() - toExtract;
        txnFluid = remaining > 0 ? new EconomyFluidStack(cur.getFluid(), remaining) : EconomyFluidStack.EMPTY;
        return (long) toExtract * DROPLETS_PER_MB;
    }

    @Override
    protected EconomyFluidStack createSnapshot() {
        return getCurrentFluid().copy();
    }

    @Override
    protected void readSnapshot(EconomyFluidStack snapshot) {
        txnFluid = snapshot;
    }

    @Override
    protected void onFinalCommit() {
        if (txnFluid != null) {
            tank.setFluid(txnFluid);
            txnFluid = null;
        }
    }
}
