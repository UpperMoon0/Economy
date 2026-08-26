package com.nstut.fabric;

import com.nstut.economy.test.MinecraftTestBase;
import com.nstut.economy.trading.EconomyFluidStack;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the null-snapshot encoding used by {@link FabricTankStorage}.
 *
 * <p><strong>Why not test FabricTankStorage directly?</strong><br>
 * {@code FabricTankStorage.insert()}/{@code extract()} accept {@code FluidVariant}. Constructing
 * a non-blank {@code FluidVariant} requires {@code FluidVariant.of(Fluid)}, which internally casts
 * the vanilla {@code Fluid} to {@code FluidVariantCache} — an interface Fabric injects at startup
 * via its mixin agent. Plain JUnit tests run without the mixin agent, so
 * {@code WaterFluid$Source} never acquires {@code FluidVariantCache} and the call always throws
 * {@code ClassCastException}. Integration-level transaction behaviour is therefore validated by
 * the live-join CI workflow instead of headless unit tests.</p>
 *
 * <p>These tests check the same holder-wrapped snapshot pattern used in {@link FabricTankStorage} —
 * with a minimal {@link SnapshotParticipant} that exercises the same encoding logic and
 * transaction callbacks without needing {@code FluidVariant}.</p>
 */
class FabricTankTransactionSemanticsTest extends MinecraftTestBase {

    /**
     * Mirror of the production {@link FabricTankSnapshot} holder. Declared at this (outer)
     * class level — not inside {@link TestParticipant} — because a type nested in
     * {@code TestParticipant} would not be in scope for its own superclass declaration.
     */
    private record Snapshot(EconomyFluidStack staged) {
    }

    /**
     * Minimal participant using the same holder-wrapped snapshot encoding as {@link FabricTankStorage}:
     * <ul>
     *   <li>{@code createSnapshot()} always returns a non-null wrapper (required by
     *       {@code SnapshotParticipant}); the wrapped staged state may be {@code null},
     *       meaning "no staged change".</li>
     *   <li>{@code readSnapshot(wrapper-with-null)} restores "no staged state" (outer-abort case).</li>
     *   <li>{@code readSnapshot(wrapper-with-value)} restores the staged value (nested-inner-abort case).</li>
     * </ul>
     */
    static class TestParticipant extends SnapshotParticipant<Snapshot> {
        private EconomyFluidStack staged = null;
        private final AtomicReference<EconomyFluidStack> committed;

        TestParticipant(int initialAmount) {
            committed = new AtomicReference<>(new EconomyFluidStack(Fluids.WATER, initialAmount));
        }

        EconomyFluidStack getCurrent() {
            return staged != null ? staged : committed.get();
        }

        void stageAmount(int amount, Transaction tx) {
            updateSnapshots(tx);
            staged = new EconomyFluidStack(Fluids.WATER, amount);
        }

        @Override protected Snapshot createSnapshot() { return new Snapshot(staged); }
        @Override protected void readSnapshot(Snapshot snapshot) { staged = snapshot.staged(); }
        @Override protected void onFinalCommit() { if (staged != null) { committed.set(staged); staged = null; } }
    }

    @Test
    @DisplayName("Commit writes staged value to backing store and clears staged state")
    void commitFlushesToBackingStore() {
        TestParticipant p = new TestParticipant(1000);

        try (Transaction tx = Transaction.openOuter()) {
            p.stageAmount(500, tx);
            assertEquals(500, p.getCurrent().getAmount());
            assertEquals(1000, p.committed.get().getAmount()); // backing unchanged
            tx.commit();
        }

        assertNull(p.staged);
        assertEquals(500, p.committed.get().getAmount());
        assertEquals(500, p.getCurrent().getAmount());
    }

    @Test
    @DisplayName("Abort clears staged state — getCurrent() delegates to backing store afterwards")
    void abortClearsStagedState() {
        TestParticipant p = new TestParticipant(1000);

        try (Transaction tx = Transaction.openOuter()) {
            p.stageAmount(500, tx);
            assertEquals(500, p.getCurrent().getAmount());
            // abort by not committing
        }

        // staged must be null so a later direct BE change is visible
        assertNull(p.staged, "staged must be null after abort so backing store is authoritative");
        assertEquals(1000, p.getCurrent().getAmount());

        // Simulate a concurrent direct change to backing store — must be visible
        p.committed.set(new EconomyFluidStack(Fluids.WATER, 2000));
        assertEquals(2000, p.getCurrent().getAmount());
    }

    @Test
    @DisplayName("Nested inner abort restores outer staged state, not null")
    void nestedAbortRestoresOuterState() {
        TestParticipant p = new TestParticipant(1000);

        try (Transaction outer = Transaction.openOuter()) {
            p.stageAmount(800, outer);     // outer stages 800
            assertEquals(800, p.getCurrent().getAmount());

            try (Transaction inner = outer.openNested()) {
                p.stageAmount(500, inner); // inner stages 500
                assertEquals(500, p.getCurrent().getAmount());
                // inner aborts
            }

            // Inner aborted: outer's 800 should be restored
            assertEquals(800, p.getCurrent().getAmount(), "outer staged state must survive inner abort");
            assertNotNull(p.staged, "staged must be non-null (outer's value) after inner abort");

            outer.commit();
        }

        assertEquals(800, p.committed.get().getAmount());
        assertNull(p.staged);
    }

    @Test
    @DisplayName("Nested inner commit accumulates into outer staged state")
    void nestedCommitAccumulatesIntoOuter() {
        TestParticipant p = new TestParticipant(1000);

        try (Transaction outer = Transaction.openOuter()) {
            p.stageAmount(800, outer);

            try (Transaction inner = outer.openNested()) {
                p.stageAmount(500, inner);
                inner.commit();
            }

            assertEquals(500, p.getCurrent().getAmount());
            outer.commit();
        }

        assertEquals(500, p.committed.get().getAmount());
    }
}
