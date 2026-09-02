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
import net.minecraft.nbt.Tag;
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
            NonNullList<ItemStack> leftover = VaultManager.insertItemStacksToVaults(level, owner, stacks);
            if (!leftover.isEmpty()) {
                Economy.LOGGER.error("Vault restore diverged from successful simulation for reservation {}; refusing to report release success",
                        reservation.token());
                return false;
            }
            return true;
        }

        if (commodity instanceof FluidCommodity fluid) {
            EconomyFluidStack payload = new EconomyFluidStack(fluid.getFluid(), reservation.amount());
            if (TankManager.simulateInsertFluidToTanks(level, owner, payload) < reservation.amount()) return false;
            int inserted = TankManager.insertFluidToTanks(level, owner, payload);
            if (inserted != reservation.amount()) {
                Economy.LOGGER.error("Tank restore diverged from successful simulation for reservation {}: {}/{} mB",
                        reservation.token(), inserted, reservation.amount());
                return false;
            }
            return true;
        }
        return false;
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
        if (!state.contains(STACKS, Tag.TAG_LIST)) return null;
        ListTag list = state.getList(STACKS, Tag.TAG_COMPOUND);
        NonNullList<ItemStack> result = NonNullList.create();
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = com.nstut.economy.compat.Compat.deserializeItemStackTag(level, list.getCompound(i));
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
