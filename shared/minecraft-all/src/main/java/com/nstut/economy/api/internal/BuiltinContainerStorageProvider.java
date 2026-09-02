package com.nstut.economy.api.internal;

import com.nstut.economy.api.*;
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

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Built-in Vault/Tank adapter using durable, lossless provider-owned reservations. */
public final class BuiltinContainerStorageProvider implements IStorageProvider {
    public static final EconomyId ID = EconomyId.of("economy", "containers");
    private static final UUID SERVER_ID = new UUID(0L, 0L);
    private static final String OWNER = "owner", TYPE = "typeId", STACKS = "itemStacks";

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
        if (commodity instanceof ItemCommodity item) return VaultManager.countMaxAcceptableItems(level, owner, generateItemStacks(item.getItem(), requestedAmount));
        if (commodity instanceof FluidCommodity fluid) return TankManager.simulateInsertFluidToTanks(level, owner, new EconomyFluidStack(fluid.getFluid(), requestedAmount));
        return 0;
    }

    @Override public Optional<StorageReservation> reserve(ServerLevel level, UUID owner, ICommodity commodity, int amount) {
        if (amount <= 0 || available(level, owner, commodity) < amount) return Optional.empty();
        Map<String,String> meta = new HashMap<>(); meta.put(OWNER, owner.toString()); meta.put(TYPE, commodity.getTypeId().toString());
        if (commodity instanceof ItemCommodity item) {
            NonNullList<ItemStack> extracted = NonNullList.create();
            if (!VaultManager.extractItemFromVaults(level, owner, item.getItem(), amount, extracted) || VaultInventoryOps.total(extracted) != amount) {
                if (!extracted.isEmpty()) VaultManager.insertItemStacksToVaults(level, owner, extracted);
                return Optional.empty();
            }
            meta.put(STACKS, encodeStacks(level, extracted));
        } else if (commodity instanceof FluidCommodity fluid) {
            List<EconomyFluidStack> drained = new ArrayList<>();
            int actual = TankManager.extractFluidFromTanks(level, owner, fluid.getFluid(), amount, drained);
            if (actual != amount) { for (EconomyFluidStack stack : drained) TankManager.restoreFluidToTanks(level, owner, stack); return Optional.empty(); }
        } else return Optional.empty();
        return Optional.of(new StorageReservation(ID, commodity.getId(), amount, UUID.randomUUID().toString(), Map.copyOf(meta)));
    }

    @Override public int deliverReserved(ServerLevel level, StorageReservation reservation, UUID receiver, int amount) {
        int wanted = Math.min(amount, reservation.amount()); if (wanted <= 0) return 0;
        ICommodity commodity = decode(reservation);
        if (commodity instanceof ItemCommodity) {
            NonNullList<ItemStack> escrow = decodeStacks(level, reservation); if (escrow == null) return 0;
            NonNullList<ItemStack> delivery = takePrefix(escrow, wanted);
            if (VaultInventoryOps.total(delivery) != wanted) return 0;
            if (receiver.equals(SERVER_ID)) return wanted;
            NonNullList<ItemStack> leftover = VaultManager.insertItemStacksToVaults(level, receiver, delivery);
            return wanted - VaultInventoryOps.total(leftover);
        }
        if (commodity instanceof FluidCommodity fluid) {
            if (receiver.equals(SERVER_ID)) return wanted;
            return TankManager.insertFluidToTanks(level, receiver, new EconomyFluidStack(fluid.getFluid(), wanted));
        }
        return 0;
    }

    @Override public Optional<StorageReservation> remainingAfterDelivery(ServerLevel level, StorageReservation reservation, int deliveredAmount) {
        if (deliveredAmount < 0 || deliveredAmount > reservation.amount()) throw new IllegalArgumentException("deliveredAmount outside reservation bounds");
        int remaining = reservation.amount() - deliveredAmount; if (remaining == 0) return Optional.empty(); if (deliveredAmount == 0) return Optional.of(reservation);
        Map<String,String> meta = new HashMap<>(reservation.metadata());
        if (decode(reservation) instanceof ItemCommodity) {
            NonNullList<ItemStack> escrow = decodeStacks(level, reservation); if (escrow == null) throw new IllegalStateException("Missing exact stack escrow for " + reservation.token());
            NonNullList<ItemStack> rest = dropPrefix(escrow, deliveredAmount);
            if (VaultInventoryOps.total(rest) != remaining) throw new IllegalStateException("Escrow amount mismatch for " + reservation.token());
            meta.put(STACKS, encodeStacks(level, rest));
        }
        return Optional.of(new StorageReservation(ID, reservation.commodityId(), remaining, reservation.token(), Map.copyOf(meta)));
    }

    @Override public boolean release(ServerLevel level, StorageReservation reservation) {
        String ownerValue = reservation.metadata().get(OWNER); if (ownerValue == null) return false; UUID owner = UUID.fromString(ownerValue);
        ICommodity commodity = decode(reservation);
        if (commodity instanceof ItemCommodity) { NonNullList<ItemStack> stacks = decodeStacks(level, reservation); return stacks != null && VaultManager.insertItemStacksToVaults(level, owner, stacks).isEmpty(); }
        if (commodity instanceof FluidCommodity fluid) return TankManager.insertFluidToTanks(level, owner, new EconomyFluidStack(fluid.getFluid(), reservation.amount())) == reservation.amount();
        return false;
    }

    @Override public String describe(ServerLevel level, UUID owner) { return "Economy Vaults and Tanks"; }
    private static ICommodity decode(StorageReservation reservation) { EconomyId typeId = EconomyId.parse(reservation.metadata().getOrDefault(TYPE, ICommodity.ITEM_TYPE.toString())); return EconomyApi.commodityTypes().require(typeId).decode(reservation.commodityId(), CommodityPayload.empty(1)); }
    private static String encodeStacks(ServerLevel level, Collection<ItemStack> stacks) { List<String> out=new ArrayList<>(); for(ItemStack stack:stacks) if(stack!=null&&!stack.isEmpty()) out.add(Base64.getEncoder().encodeToString(com.nstut.economy.compat.Compat.serializeItemStack(level, stack.copy()).getBytes(StandardCharsets.UTF_8))); return String.join(",",out); }
    private static NonNullList<ItemStack> decodeStacks(ServerLevel level, StorageReservation reservation) { String raw=reservation.metadata().get(STACKS); if(raw==null) return null; NonNullList<ItemStack> out=NonNullList.create(); if(raw.isEmpty()) return out; for(String part:raw.split(",")) { ItemStack stack=com.nstut.economy.compat.Compat.deserializeItemStack(level,new String(Base64.getDecoder().decode(part),StandardCharsets.UTF_8)); if(!stack.isEmpty()) out.add(stack); } return out; }
    private static NonNullList<ItemStack> takePrefix(Collection<ItemStack> stacks,int amount){ NonNullList<ItemStack> out=NonNullList.create(); int left=amount; for(ItemStack s:stacks){ if(left<=0)break; int n=Math.min(left,s.getCount()); ItemStack c=s.copy(); c.setCount(n); out.add(c); left-=n;} return out; }
    private static NonNullList<ItemStack> dropPrefix(Collection<ItemStack> stacks,int amount){ NonNullList<ItemStack> out=NonNullList.create(); int drop=amount; for(ItemStack s:stacks){ if(drop>=s.getCount()){drop-=s.getCount();continue;} ItemStack c=s.copy(); if(drop>0){c.shrink(drop);drop=0;} if(!c.isEmpty())out.add(c);} return out; }
    private static NonNullList<ItemStack> generateItemStacks(Item item,int amount){ NonNullList<ItemStack> result=NonNullList.create(); int max=com.nstut.economy.compat.Compat.maxStackSize(item); for(int remaining=amount;remaining>0;){int count=Math.min(remaining,max);result.add(new ItemStack(item,count));remaining-=count;} return result; }
}
