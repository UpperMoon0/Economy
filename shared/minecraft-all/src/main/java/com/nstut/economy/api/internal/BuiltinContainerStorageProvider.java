package com.nstut.economy.api.internal;

import com.nstut.Economy;
import com.nstut.economy.api.*;
import com.nstut.economy.blocks.TankManager;
import com.nstut.economy.blocks.VaultInventoryOps;
import com.nstut.economy.blocks.VaultManager;
import com.nstut.economy.trading.EconomyFluidStack;
import com.nstut.economy.trading.FluidCommodity;
import com.nstut.economy.trading.ItemCommodity;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Built-in Vault/Tank adapter using durable, lossless provider-owned reservations. */
public final class BuiltinContainerStorageProvider implements IStorageProvider {
    public static final EconomyId ID = EconomyId.of("economy", "containers");
    private static final UUID SERVER_ID = new UUID(0L, 0L);
    private static final String OWNER = "owner", TYPE = "typeId", STACKS = "ItemStacks";

    @Override public EconomyId id() { return ID; }
    @Override public int priority() { return -100; }
    @Override public boolean supports(ICommodity commodity) { return commodity instanceof ItemCommodity || commodity instanceof FluidCommodity; }

    @Override public int available(ServerLevel level, UUID owner, ICommodity commodity) {
        if (commodity instanceof ItemCommodity item) return VaultManager.countItemInVaults(level, owner, item.getItem());
        if (commodity instanceof FluidCommodity fluid) return TankManager.countFluidInTanks(level, owner, fluid.getFluid());
        return 0;
    }

    @Override public int receivable(ServerLevel level, UUID owner, ICommodity commodity, int requestedAmount) {
        if (owner.equals(SERVER_ID)) return requestedAmount;
        if (commodity instanceof ItemCommodity item) {
            return VaultManager.countMaxAcceptableItems(level, owner, generateItemStacks(item.getItem(), requestedAmount));
        }
        if (commodity instanceof FluidCommodity fluid) {
            return TankManager.simulateInsertFluidToTanks(level, owner, new EconomyFluidStack(fluid.getFluid(), requestedAmount));
        }
        return 0;
    }

    @Override
    public Optional<StorageReservation> reserve(ServerLevel level, UUID owner, ICommodity commodity, int amount) {
        if (amount <= 0 || available(level, owner, commodity) < amount) return Optional.empty();
        Map<String, String> metadata = Map.of(OWNER, owner.toString(), TYPE, commodity.getTypeId().toString());
        CompoundTag state = new CompoundTag();

        if (commodity instanceof ItemCommodity item) {
            NonNullList<ItemStack> extracted = NonNullList.create();
            if (!VaultManager.extractItemFromVaults(level, owner, item.getItem(), amount, extracted)
                    || VaultInventoryOps.total(extracted) != amount) {
                if (!extracted.isEmpty()) restoreExtractedItems(level, owner, extracted);
                return Optional.empty();
            }
            state = encodeStacks(level, extracted);
        } else if (commodity instanceof FluidCommodity fluid) {
            List<EconomyFluidStack> drained = new ArrayList<>();
            int actual = TankManager.extractFluidFromTanks(level, owner, fluid.getFluid(), amount, drained);
            if (actual != amount) {
                for (EconomyFluidStack stack : drained) TankManager.restoreFluidToTanks(level, owner, stack);
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }

        return Optional.of(new StorageReservation(ID, commodity.getId(), amount,
                UUID.randomUUID().toString(), metadata, state));
    }

    @Override
    public StorageDeliveryResult deliverReserved(ServerLevel level, StorageReservation reservation,
                                                 UUID receiver, int amount) {
        int wanted = Math.min(Math.max(0, amount), reservation.amount());
        if (wanted <= 0) return StorageDeliveryResult.unchanged(reservation);
        ICommodity commodity = decode(reservation);

        if (commodity instanceof ItemCommodity) {
            NonNullList<ItemStack> escrow = decodeStacks(level, reservation);
            if (escrow == null || VaultInventoryOps.total(escrow) != reservation.amount()) {
                throw new IllegalStateException("Exact item escrow is missing or inconsistent for " + reservation.token());
            }
            NonNullList<ItemStack> attempted = takePrefix(escrow, wanted);
            NonNullList<ItemStack> untouched = dropPrefix(escrow, wanted);
            if (VaultInventoryOps.total(attempted) != wanted) {
                throw new IllegalStateException("Exact item escrow is shorter than reservation amount for " + reservation.token());
            }

            NonNullList<ItemStack> rejected = receiver.equals(SERVER_ID)
                    ? NonNullList.create()
                    : VaultManager.insertItemStacksToVaults(level, receiver, attempted);
            int delivered = wanted - VaultInventoryOps.total(rejected);
            if (delivered < 0) throw new IllegalStateException("Vault returned more item remainder than was offered");

            NonNullList<ItemStack> exactRemaining = NonNullList.create();
            for (ItemStack stack : rejected) if (stack != null && !stack.isEmpty()) exactRemaining.add(stack.copy());
            for (ItemStack stack : untouched) if (stack != null && !stack.isEmpty()) exactRemaining.add(stack.copy());
            return deliveryResult(reservation, delivered, exactRemaining, level);
        }

        if (commodity instanceof FluidCommodity fluid) {
            int delivered = receiver.equals(SERVER_ID)
                    ? wanted
                    : TankManager.insertFluidToTanks(level, receiver, new EconomyFluidStack(fluid.getFluid(), wanted));
            delivered = Math.max(0, Math.min(wanted, delivered));
            int remaining = reservation.amount() - delivered;
            if (remaining == reservation.amount()) return StorageDeliveryResult.unchanged(reservation);
            if (remaining == 0) return StorageDeliveryResult.complete(delivered);
            StorageReservation rest = new StorageReservation(ID, reservation.commodityId(), remaining,
                    UUID.randomUUID().toString(), reservation.metadata(), reservation.providerState());
            return StorageDeliveryResult.partial(delivered, rest);
        }

        return StorageDeliveryResult.unchanged(reservation);
    }

    @Override
    public boolean release(ServerLevel level, StorageReservation reservation) {
        String ownerValue = reservation.metadata().get(OWNER);
        if (ownerValue == null) return false;
        UUID owner = UUID.fromString(ownerValue);
        ICommodity commodity = decode(reservation);

        if (commodity instanceof ItemCommodity) {
            NonNullList<ItemStack> stacks = decodeStacks(level, reservation);
            if (stacks == null || VaultInventoryOps.total(stacks) != reservation.amount()) return false;
            if (!VaultManager.simulateInsertItemStacksToVaults(level, owner, stacks).isEmpty()) return false;

            var vaults = VaultManager.getVaults(level, owner);
            List<NonNullList<ItemStack>> snapshots = new ArrayList<>(vaults.size());
            for (var vault : vaults) {
                NonNullList<ItemStack> snapshot = NonNullList.withSize(vault.getContainerSize(), ItemStack.EMPTY);
                for (int slot = 0; slot < vault.getContainerSize(); slot++) {
                    ItemStack current = vault.getItem(slot);
                    snapshot.set(slot, current == null ? ItemStack.EMPTY : current.copy());
                }
                snapshots.add(snapshot);
            }

            NonNullList<ItemStack> leftover = VaultManager.insertItemStacksToVaults(level, owner, stacks);
            if (leftover.isEmpty()) return true;

            restoreVaultSnapshots(vaults, snapshots);
            Economy.LOGGER.error("Vault restore diverged from successful simulation for reservation {}; committed mutation rolled back",
                    reservation.token());
            return false;
        }

        if (commodity instanceof FluidCommodity fluid) {
            EconomyFluidStack payload = new EconomyFluidStack(fluid.getFluid(), reservation.amount());
            if (TankManager.simulateInsertFluidToTanks(level, owner, payload) < reservation.amount()) return false;

            var tanks = TankManager.getTanks(level, owner);
            List<EconomyFluidStack> snapshots = new ArrayList<>(tanks.size());
            for (var tank : tanks) snapshots.add(tank.getFluid().copy());

            int inserted = TankManager.insertFluidToTanks(level, owner, payload);
            if (inserted == reservation.amount()) return true;

            restoreTankSnapshots(tanks, snapshots);
            Economy.LOGGER.error("Tank restore diverged from successful simulation for reservation {}: {}/{} mB; committed mutation rolled back",
                    reservation.token(), inserted, reservation.amount());
            return false;
        }
        return false;
    }

    private static void restoreVaultSnapshots(List<? extends net.minecraft.world.Container> vaults,
                                              List<NonNullList<ItemStack>> snapshots) {
        if (vaults.size() != snapshots.size()) {
            throw new IllegalStateException("Vault topology changed during atomic reservation release");
        }
        for (int i = 0; i < vaults.size(); i++) {
            var vault = vaults.get(i);
            NonNullList<ItemStack> snapshot = snapshots.get(i);
            if (vault.getContainerSize() != snapshot.size()) {
                throw new IllegalStateException("Vault size changed during atomic reservation release");
            }
            for (int slot = 0; slot < snapshot.size(); slot++) vault.setItem(slot, snapshot.get(slot).copy());
            for (int slot = 0; slot < snapshot.size(); slot++) {
                if (!com.nstut.economy.compat.Compat.stacksEqual(vault.getItem(slot), snapshot.get(slot))) {
                    throw new IllegalStateException("Could not roll back vault mutation during reservation release");
                }
            }
        }
    }

    private static <T> void restoreTankSnapshots(List<T> tanks, List<EconomyFluidStack> snapshots) {
        if (tanks.size() != snapshots.size()) {
            throw new IllegalStateException("Tank topology changed during atomic reservation release");
        }
        for (int i = 0; i < tanks.size(); i++) {
            Object value = tanks.get(i);
            EconomyFluidStack snapshot = snapshots.get(i);
            try {
                java.lang.reflect.Method setFluid = value.getClass().getMethod("setFluid", EconomyFluidStack.class);
                java.lang.reflect.Method getFluid = value.getClass().getMethod("getFluid");
                setFluid.invoke(value, snapshot.copy());
                EconomyFluidStack restored = (EconomyFluidStack) getFluid.invoke(value);
                if (!sameFluid(restored, snapshot)) {
                    throw new IllegalStateException("Could not roll back tank mutation during reservation release");
                }
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not roll back tank mutation during reservation release", failure);
            }
        }
    }

    private static boolean sameFluid(EconomyFluidStack left, EconomyFluidStack right) {
        if (left == null || left.isEmpty()) return right == null || right.isEmpty();
        if (right == null || right.isEmpty()) return false;
        return left.getAmount() == right.getAmount() && left.isFluidEqual(right);
    }

    @Override public String describe(ServerLevel level, UUID owner) { return "Economy Vaults and Tanks"; }

    private static StorageDeliveryResult deliveryResult(StorageReservation before, int delivered,
                                                        Collection<ItemStack> exactRemaining, ServerLevel level) {
        int remaining = VaultInventoryOps.total(new ArrayList<>(exactRemaining));
        if (remaining == before.amount()) return StorageDeliveryResult.unchanged(before);
        if (remaining == 0) return StorageDeliveryResult.complete(delivered);
        StorageReservation rest = new StorageReservation(ID, before.commodityId(), remaining,
                UUID.randomUUID().toString(), before.metadata(), encodeStacks(level, exactRemaining));
        return StorageDeliveryResult.partial(delivered, rest);
    }

    private static ICommodity decode(StorageReservation reservation) {
        EconomyId typeId = EconomyId.parse(reservation.metadata().getOrDefault(TYPE, ICommodity.ITEM_TYPE.toString()));
        return EconomyApi.commodityTypes().require(typeId)
                .decode(reservation.commodityId(), CommodityPayload.empty(1));
    }

    private static CompoundTag encodeStacks(ServerLevel level, Collection<ItemStack> stacks) {
        CompoundTag state = new CompoundTag();
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                list.add(com.nstut.economy.compat.Compat.serializeItemStackTag(level, stack.copy()));
            }
        }
        state.put(STACKS, list);
        return state;
    }

    private static NonNullList<ItemStack> decodeStacks(ServerLevel level, StorageReservation reservation) {
        CompoundTag state = reservation.providerState();
        ListTag list = com.nstut.economy.compat.Compat.getCompoundList(state, STACKS);
        if (list.size() == 0) return null;
        NonNullList<ItemStack> result = NonNullList.create();
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = com.nstut.economy.compat.Compat.deserializeItemStackTag(
                    level, com.nstut.economy.compat.Compat.getCompoundAt(list, i));
            if (!stack.isEmpty()) result.add(stack);
        }
        return result;
    }

    private static NonNullList<ItemStack> takePrefix(Collection<ItemStack> stacks, int amount) {
        NonNullList<ItemStack> result = NonNullList.create();
        int remaining = amount;
        for (ItemStack stack : stacks) {
            if (remaining <= 0) break;
            int count = Math.min(remaining, stack.getCount());
            ItemStack copy = stack.copy();
            copy.setCount(count);
            result.add(copy);
            remaining -= count;
        }
        return result;
    }

    private static NonNullList<ItemStack> dropPrefix(Collection<ItemStack> stacks, int amount) {
        NonNullList<ItemStack> result = NonNullList.create();
        int drop = amount;
        for (ItemStack stack : stacks) {
            if (drop >= stack.getCount()) {
                drop -= stack.getCount();
                continue;
            }
            ItemStack copy = stack.copy();
            if (drop > 0) {
                copy.shrink(drop);
                drop = 0;
            }
            if (!copy.isEmpty()) result.add(copy);
        }
        return result;
    }

    private static void restoreExtractedItems(ServerLevel level, UUID owner, Collection<ItemStack> extracted) {
        if (!VaultManager.simulateInsertItemStacksToVaults(level, owner, new ArrayList<>(extracted)).isEmpty()) {
            Economy.LOGGER.error("Could not transactionally roll back a failed built-in item reservation for {}", owner);
            return;
        }
        NonNullList<ItemStack> leftover = VaultManager.insertItemStacksToVaults(level, owner, new ArrayList<>(extracted));
        if (!leftover.isEmpty()) {
            Economy.LOGGER.error("Rollback diverged from simulation while restoring failed built-in item reservation for {}", owner);
        }
    }

    private static NonNullList<ItemStack> generateItemStacks(Item item, int amount) {
        NonNullList<ItemStack> result = NonNullList.create();
        int max = com.nstut.economy.compat.Compat.maxStackSize(item);
        for (int remaining = amount; remaining > 0;) {
            int count = Math.min(remaining, max);
            result.add(new ItemStack(item, count));
            remaining -= count;
        }
        return result;
    }
}
