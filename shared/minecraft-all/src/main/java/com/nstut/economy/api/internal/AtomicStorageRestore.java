package com.nstut.economy.api.internal;

import com.nstut.economy.blocks.TankManager;
import com.nstut.economy.blocks.VaultManager;
import com.nstut.economy.trading.EconomyFluidStack;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Transactional restoration for legacy pre-extracted Vault/Tank escrow. */
public final class AtomicStorageRestore {
    private AtomicStorageRestore() { }

    public static boolean restoreEscrow(ServerLevel level, UUID owner,
                                        Collection<ItemStack> items,
                                        Collection<EconomyFluidStack> fluids) {
        if (level == null || owner == null) return false;

        NonNullList<ItemStack> itemPayload = copyItems(items);
        List<EconomyFluidStack> fluidPayload = copyFluids(fluids);
        if (itemPayload.isEmpty() && fluidPayload.isEmpty()) return true;

        if (!itemPayload.isEmpty()
                && !VaultManager.simulateInsertItemStacksToVaults(level, owner, itemPayload).isEmpty()) {
            return false;
        }

        int fluidTotal = fluidPayload.stream().mapToInt(EconomyFluidStack::getAmount).sum();
        if (fluidTotal > 0) {
            EconomyFluidStack merged = TankManager.mergeFluids(fluidPayload);
            if (TankManager.simulateInsertFluidToTanks(level, owner, merged) < fluidTotal) return false;
        }

        var vaults = itemPayload.isEmpty() ? List.<Container>of() : VaultManager.getVaults(level, owner);
        List<NonNullList<ItemStack>> vaultSnapshots = snapshotVaults(vaults);
        var tanks = fluidPayload.isEmpty() ? List.of() : TankManager.getTanks(level, owner);
        List<EconomyFluidStack> tankSnapshots = snapshotTanks(tanks);

        Runnable rollback = () -> {
            restoreVaultSnapshots(vaults, vaultSnapshots);
            restoreTankSnapshots(tanks, tankSnapshots);
        };

        return commitWithRollback(() -> {
            if (!itemPayload.isEmpty()
                    && !VaultManager.insertItemStacksToVaults(level, owner, itemPayload).isEmpty()) {
                return false;
            }
            if (fluidTotal > 0) {
                int restored = 0;
                for (EconomyFluidStack stack : fluidPayload) {
                    restored += TankManager.restoreFluidToTanks(level, owner, stack.copy());
                }
                if (restored != fluidTotal) return false;
            }
            return true;
        }, rollback);
    }

    static boolean commitWithRollback(BooleanSupplier commit, Runnable rollback) {
        try {
            if (commit.getAsBoolean()) return true;
        } catch (RuntimeException failure) {
            rollback.run();
            throw failure;
        }
        rollback.run();
        return false;
    }

    private static NonNullList<ItemStack> copyItems(Collection<ItemStack> stacks) {
        NonNullList<ItemStack> copy = NonNullList.create();
        if (stacks != null) {
            for (ItemStack stack : stacks) {
                if (stack != null && !stack.isEmpty()) copy.add(stack.copy());
            }
        }
        return copy;
    }

    private static List<EconomyFluidStack> copyFluids(Collection<EconomyFluidStack> stacks) {
        List<EconomyFluidStack> copy = new ArrayList<>();
        if (stacks != null) {
            for (EconomyFluidStack stack : stacks) {
                if (stack != null && !stack.isEmpty()) copy.add(stack.copy());
            }
        }
        return copy;
    }

    private static List<NonNullList<ItemStack>> snapshotVaults(List<? extends Container> vaults) {
        List<NonNullList<ItemStack>> snapshots = new ArrayList<>(vaults.size());
        for (Container vault : vaults) {
            NonNullList<ItemStack> snapshot = NonNullList.withSize(vault.getContainerSize(), ItemStack.EMPTY);
            for (int slot = 0; slot < vault.getContainerSize(); slot++) {
                ItemStack current = vault.getItem(slot);
                snapshot.set(slot, current == null ? ItemStack.EMPTY : current.copy());
            }
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    private static void restoreVaultSnapshots(List<? extends Container> vaults,
                                              List<NonNullList<ItemStack>> snapshots) {
        if (vaults.size() != snapshots.size()) {
            throw new IllegalStateException("Vault topology changed during atomic escrow rollback");
        }
        for (int i = 0; i < vaults.size(); i++) {
            Container vault = vaults.get(i);
            NonNullList<ItemStack> snapshot = snapshots.get(i);
            if (vault.getContainerSize() != snapshot.size()) {
                throw new IllegalStateException("Vault size changed during atomic escrow rollback");
            }
            for (int slot = 0; slot < snapshot.size(); slot++) vault.setItem(slot, snapshot.get(slot).copy());
            for (int slot = 0; slot < snapshot.size(); slot++) {
                if (!com.nstut.economy.compat.Compat.stacksEqual(vault.getItem(slot), snapshot.get(slot))) {
                    throw new IllegalStateException("Could not roll back Vault escrow mutation atomically");
                }
            }
        }
    }

    private static List<EconomyFluidStack> snapshotTanks(List<?> tanks) {
        List<EconomyFluidStack> snapshots = new ArrayList<>(tanks.size());
        for (Object tank : tanks) {
            try {
                java.lang.reflect.Method getFluid = tank.getClass().getMethod("getFluid");
                snapshots.add(((EconomyFluidStack) getFluid.invoke(tank)).copy());
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not snapshot Tank for atomic escrow restore", failure);
            }
        }
        return snapshots;
    }

    private static void restoreTankSnapshots(List<?> tanks, List<EconomyFluidStack> snapshots) {
        if (tanks.size() != snapshots.size()) {
            throw new IllegalStateException("Tank topology changed during atomic escrow rollback");
        }
        for (int i = 0; i < tanks.size(); i++) {
            Object tank = tanks.get(i);
            EconomyFluidStack snapshot = snapshots.get(i);
            try {
                java.lang.reflect.Method setFluid = tank.getClass().getMethod("setFluid", EconomyFluidStack.class);
                java.lang.reflect.Method getFluid = tank.getClass().getMethod("getFluid");
                setFluid.invoke(tank, snapshot.copy());
                EconomyFluidStack restored = (EconomyFluidStack) getFluid.invoke(tank);
                if (!sameFluid(restored, snapshot)) {
                    throw new IllegalStateException("Could not roll back Tank escrow mutation atomically");
                }
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not roll back Tank escrow mutation atomically", failure);
            }
        }
    }

    private static boolean sameFluid(EconomyFluidStack left, EconomyFluidStack right) {
        if (left == null || left.isEmpty()) return right == null || right.isEmpty();
        if (right == null || right.isEmpty()) return false;
        return left.getAmount() == right.getAmount() && left.isFluidEqual(right);
    }
}
