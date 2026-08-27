package com.nstut.fabric;

import com.nstut.economy.trading.EconomyFluidStack;

/**
 * Immutable capture of {@link FabricTankStorage}'s staged transactional state.
 *
 * <p>Declared top-level because a type nested inside {@code FabricTankStorage} cannot be
 * referenced from that class's own superclass declaration ({@code extends
 * SnapshotParticipant<...>}) — the class's member types are not in scope there.</p>
 *
 * <p>Instances are always non-null (required by {@code SnapshotParticipant}); the wrapped
 * {@code staged} value is nullable and represents "no staged change", in which case the
 * backing block entity is authoritative.</p>
 */
record FabricTankSnapshot(EconomyFluidStack staged) {
}
