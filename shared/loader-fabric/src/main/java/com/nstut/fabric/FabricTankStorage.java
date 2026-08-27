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
 *
 * <p>Snapshot encoding: {@code createSnapshot()} always returns a non-null {@link FabricTankSnapshot},
 * as required by {@code SnapshotParticipant}. The staged value itself may be {@code null}
 * (no staged transactional fluid), so it is wrapped: {@code readSnapshot(snapshot)} restores
 * both "no staged state" and a previously staged fluid — correctly handling nested transactions.</p>
 */
public class FabricTankStorage extends SnapshotParticipant<FabricTankSnapshot> implements SingleSlotStorage<FluidVariant> {

    public static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000; // 81 droplets = 1 mB

    private final TankBlockEntity tank;
    /** Non-null only while a transaction has staged a change; null means the BE is authoritative. */
    private EconomyFluidStack txnFluid = null;

    public FabricTankStorage(TankBlockEntity tank) {
        this.tank = tank;
    }

    public static FabricTankStorage get(TankBlockEntity tank) {
        if (tank == null) return null;
        if (tank.getPlatformFluidStorage() instanceof FabricTankStorage storage) {
            return storage;
        }
        FabricTankStorage storage = new FabricTankStorage(tank);
        tank.setPlatformFluidStorage(storage);
        return storage;
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

    /**
     * Returns a non-null snapshot wrapping the current staged state (which may be {@code null}
     * when no staged change exists). {@code SnapshotParticipant} requires snapshots to never
     * be null, so the nullable staged value is wrapped in a holder record. Capturing the
     * {@code txnFluid} reference is safe because staged stacks are immutable in practice:
     * every write replaces the reference instead of mutating it.
     */
    @Override
    protected FabricTankSnapshot createSnapshot() {
        return new FabricTankSnapshot(txnFluid);
    }

    /**
     * Restores staged state from snapshot.
     *
     * <ul>
     *   <li>{@code snapshot.staged() == null} → no staged state existed; clear {@code txnFluid} so the BE is authoritative.</li>
     *   <li>non-null → a staged fluid existed; restore it as the staged value.</li>
     * </ul>
     *
     * This correctly handles outer-abort (restores to none) and nested-inner-abort
     * (restores to the outer's staged fluid).
     */
    @Override
    protected void readSnapshot(FabricTankSnapshot snapshot) {
        this.txnFluid = snapshot.staged();
    }

    @Override
    protected void onFinalCommit() {
        if (txnFluid != null) {
            tank.setFluid(txnFluid);
            txnFluid = null;
        }
    }
}
