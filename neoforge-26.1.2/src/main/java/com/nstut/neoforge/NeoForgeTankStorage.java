package com.nstut.neoforge;

import com.nstut.economy.blocks.TankBlockEntity;
import com.nstut.economy.trading.EconomyFluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Transactional fluid access; probes and aborted transfers never modify the tank. */
public final class NeoForgeTankStorage extends SnapshotJournal<NeoForgeTankStorage.Snapshot>
        implements ResourceHandler<FluidResource> {
    record Snapshot(EconomyFluidStack staged) {}
    private final TankBlockEntity tank;
    private EconomyFluidStack staged;

    private NeoForgeTankStorage(TankBlockEntity tank) { this.tank = tank; }

    public static NeoForgeTankStorage get(TankBlockEntity tank) {
        if (tank.getPlatformFluidStorage() instanceof NeoForgeTankStorage storage) return storage;
        var storage = new NeoForgeTankStorage(tank);
        tank.setPlatformFluidStorage(storage);
        return storage;
    }

    private EconomyFluidStack current() { return staged == null ? tank.getFluid() : staged; }
    private void checkIndex(int index) { java.util.Objects.checkIndex(index, 1); }
    @Override public int size() { return 1; }
    @Override public FluidResource getResource(int index) {
        checkIndex(index);
        return current().isEmpty() ? FluidResource.EMPTY : FluidResource.of(current().getFluid());
    }
    @Override public long getAmountAsLong(int index) { checkIndex(index); return current().getAmount(); }
    @Override public long getCapacityAsLong(int index, FluidResource resource) { checkIndex(index); return tank.getCapacity(); }
    @Override public boolean isValid(int index, FluidResource resource) {
        checkIndex(index);
        return !resource.isEmpty() && resource.isComponentsPatchEmpty();
    }
    @Override public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        checkIndex(index);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!isValid(index, resource)) return 0;
        var have = current();
        if (!have.isEmpty() && have.getFluid() != resource.getFluid()) return 0;
        int accepted = Math.min(amount, tank.getCapacity() - have.getAmount());
        if (accepted <= 0) return 0;
        updateSnapshots(transaction);
        staged = new EconomyFluidStack(resource.getFluid(), have.getAmount() + accepted);
        return accepted;
    }
    @Override public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        checkIndex(index);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        var have = current();
        if (!isValid(index, resource) || have.isEmpty() || have.getFluid() != resource.getFluid()) return 0;
        int taken = Math.min(amount, have.getAmount());
        if (taken <= 0) return 0;
        updateSnapshots(transaction);
        staged = taken == have.getAmount() ? EconomyFluidStack.EMPTY
                : new EconomyFluidStack(have.getFluid(), have.getAmount() - taken);
        return taken;
    }
    @Override protected Snapshot createSnapshot() { return new Snapshot(staged); }
    @Override protected void revertToSnapshot(Snapshot snapshot) { staged = snapshot.staged(); }
    @Override protected void onRootCommit(Snapshot original) {
        if (staged != null) {
            var committed = staged;
            staged = null;
            tank.setFluid(committed);
        }
    }
}
