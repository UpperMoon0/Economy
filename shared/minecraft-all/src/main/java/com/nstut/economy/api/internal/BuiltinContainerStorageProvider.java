package com.nstut.economy.api.internal;

import com.nstut.economy.api.CommodityPayload;
import com.nstut.economy.api.EconomyApi;
import com.nstut.economy.api.EconomyId;
import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.IStorageProvider;
import com.nstut.economy.api.StorageReservation;
import com.nstut.economy.blocks.TankManager;
import com.nstut.economy.blocks.VaultInventoryOps;
import com.nstut.economy.blocks.VaultManager;
import com.nstut.economy.trading.EconomyFluidStack;
import com.nstut.economy.trading.FluidCommodity;
import com.nstut.economy.trading.ItemCommodity;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Built-in Vault/Tank adapter using the same reservation API exposed to addons. */
public final class BuiltinContainerStorageProvider implements IStorageProvider {
    public static final EconomyId ID = EconomyId.of("economy", "containers");
    private static final UUID SERVER_ID = new UUID(0L, 0L);

    @Override public EconomyId id() { return ID; }
    @Override public int priority() { return -100; }
    @Override public boolean supports(ICommodity commodity) { return commodity instanceof ItemCommodity || commodity instanceof FluidCommodity; }

    @Override
    public int available(ServerLevel level, UUID owner, ICommodity commodity) {
        if (commodity instanceof ItemCommodity item) return VaultManager.countItemInVaults(level, owner, item.getItem());
        if (commodity instanceof FluidCommodity fluid) return TankManager.countFluidInTanks(level, owner, fluid.getFluid());
        return 0;
    }

    @Override
    public int receivable(ServerLevel level, UUID owner, ICommodity commodity, int requestedAmount) {
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
        if (commodity instanceof ItemCommodity item) {
            NonNullList<ItemStack> extracted = NonNullList.create();
            if (!VaultManager.extractItemFromVaults(level, owner, item.getItem(), amount, extracted)
                    || VaultInventoryOps.total(extracted) != amount) {
                if (!extracted.isEmpty()) VaultManager.insertItemStacksToVaults(level, owner, extracted);
                return Optional.empty();
            }
        } else if (commodity instanceof FluidCommodity fluid) {
            List<EconomyFluidStack> drained = new ArrayList<>();
            int actual = TankManager.extractFluidFromTanks(level, owner, fluid.getFluid(), amount, drained);
            if (actual != amount) {
                for (EconomyFluidStack stack : drained) TankManager.restoreFluidToTanks(level, owner, stack);
                return Optional.empty();
            }
        } else return Optional.empty();

        return Optional.of(new StorageReservation(ID, commodity.getId(), amount, UUID.randomUUID().toString(),
                Map.of("owner", owner.toString(), "typeId", commodity.getTypeId().toString())));
    }

    @Override
    public int deliverReserved(ServerLevel level, StorageReservation reservation, UUID receiver, int amount) {
        int deliver = Math.min(amount, reservation.amount());
        if (deliver <= 0) return 0;
        if (receiver.equals(SERVER_ID)) return deliver;
        ICommodity commodity = decode(reservation);
        if (commodity instanceof ItemCommodity item) {
            NonNullList<ItemStack> stacks = generateItemStacks(item.getItem(), deliver);
            NonNullList<ItemStack> leftover = VaultManager.insertItemStacksToVaults(level, receiver, stacks);
            return deliver - VaultInventoryOps.total(leftover);
        }
        if (commodity instanceof FluidCommodity fluid) {
            return TankManager.insertFluidToTanks(level, receiver, new EconomyFluidStack(fluid.getFluid(), deliver));
        }
        return 0;
    }

    @Override
    public boolean release(ServerLevel level, StorageReservation reservation) {
        String ownerValue = reservation.metadata().get("owner");
        if (ownerValue == null) return false;
        UUID owner = UUID.fromString(ownerValue);
        ICommodity commodity = decode(reservation);
        if (commodity instanceof ItemCommodity item) {
            return VaultManager.insertItemStacksToVaults(level, owner, generateItemStacks(item.getItem(), reservation.amount())).isEmpty();
        }
        if (commodity instanceof FluidCommodity fluid) {
            return TankManager.insertFluidToTanks(level, owner,
                    new EconomyFluidStack(fluid.getFluid(), reservation.amount())) == reservation.amount();
        }
        return false;
    }

    @Override public String describe(ServerLevel level, UUID owner) { return "Economy Vaults and Tanks"; }

    private static ICommodity decode(StorageReservation reservation) {
        EconomyId typeId = EconomyId.parse(reservation.metadata().getOrDefault("typeId", ICommodity.ITEM_TYPE.toString()));
        return EconomyApi.commodityTypes().require(typeId)
                .decode(reservation.commodityId(), CommodityPayload.empty(1));
    }

    private static NonNullList<ItemStack> generateItemStacks(Item item, int amount) {
        NonNullList<ItemStack> result = NonNullList.create();
        int max = com.nstut.economy.compat.Compat.maxStackSize(item);
        for (int remaining = amount; remaining > 0;) {
            int count = Math.min(remaining, max); result.add(new ItemStack(item, count)); remaining -= count;
        }
        return result;
    }
}
