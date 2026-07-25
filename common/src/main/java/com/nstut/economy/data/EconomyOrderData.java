package com.nstut.economy.data;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyOrderData extends SavedData {

    private static final String NAME = "economy_orders";

    public static final class OrderSnapshot {
        public final UUID orderId;
        public final UUID owner;
        public final String itemId;
        public final int quantity;
        public final String pricePerUnit;
        public final String type;
        public final long createdAt;
        public final long expiresAt;
        public final boolean hasExpiry;
        public final NonNullList<ItemStack> reservedItems;
        public final boolean isServerOrder;

        public OrderSnapshot(UUID orderId, UUID owner, String itemId, int quantity,
                             String pricePerUnit, String type, long createdAt,
                             long expiresAt, boolean hasExpiry,
                             NonNullList<ItemStack> reservedItems, boolean isServerOrder) {
            this.orderId = orderId;
            this.owner = owner;
            this.itemId = itemId;
            this.quantity = quantity;
            this.pricePerUnit = pricePerUnit;
            this.type = type;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.hasExpiry = hasExpiry;
            this.reservedItems = reservedItems != null ? reservedItems : NonNullList.create();
            this.isServerOrder = isServerOrder;
        }
    }

    private final Map<UUID, OrderSnapshot> orders = new HashMap<>();

    public Map<UUID, OrderSnapshot> getOrders() {
        return orders;
    }

    public void putOrder(OrderSnapshot snap) {
        orders.put(snap.orderId, snap);
        setDirty();
    }

    public void removeOrder(UUID orderId) {
        if (orders.remove(orderId) != null) {
            setDirty();
        }
    }

    public void clearAll() {
        orders.clear();
        setDirty();
    }

    public static EconomyOrderData get(net.minecraft.server.level.ServerLevel level) {
        net.minecraft.server.level.ServerLevel target = (level != null && level.getServer() != null) ? level.getServer().overworld() : level;
        return target.getDataStorage().computeIfAbsent(EconomyOrderData::load, EconomyOrderData::new, NAME);
    }

    public static EconomyOrderData load(CompoundTag tag) {
        EconomyOrderData data = new EconomyOrderData();
        ListTag list = tag.getList("Orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            try {
                NonNullList<ItemStack> reserved = NonNullList.create();
                if (t.contains("Reserved", Tag.TAG_LIST)) {
                    ListTag resList = t.getList("Reserved", Tag.TAG_COMPOUND);
                    for (int r = 0; r < resList.size(); r++) {
                        reserved.add(ItemStack.of(resList.getCompound(r)));
                    }
                }
                boolean serverOrd = t.getBoolean("ServerOrder");
                UUID id = t.getUUID("OrderId");
                UUID owner = t.hasUUID("Owner") ? t.getUUID("Owner") : null;
                data.orders.put(id, new OrderSnapshot(
                    id,
                    owner,
                    t.getString("ItemId"),
                    t.getInt("Quantity"),
                    t.getString("PricePerUnit"),
                    t.getString("Type"),
                    t.getLong("CreatedAt"),
                    t.getLong("ExpiresAt"),
                    t.getBoolean("HasExpiry"),
                    reserved,
                    serverOrd
                ));
            } catch (Exception e) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (OrderSnapshot s : orders.values()) {
            CompoundTag sTag = new CompoundTag();
            sTag.putUUID("OrderId", s.orderId);
            if (s.owner != null) sTag.putUUID("Owner", s.owner);
            sTag.putString("ItemId", s.itemId);
            sTag.putInt("Quantity", s.quantity);
            sTag.putString("PricePerUnit", s.pricePerUnit);
            sTag.putString("Type", s.type);
            sTag.putLong("CreatedAt", s.createdAt);
            sTag.putLong("ExpiresAt", s.expiresAt);
            sTag.putBoolean("HasExpiry", s.hasExpiry);
            sTag.putBoolean("ServerOrder", s.isServerOrder);

            if (!s.reservedItems.isEmpty()) {
                ListTag resList = new ListTag();
                for (ItemStack stack : s.reservedItems) {
                    if (!stack.isEmpty()) {
                        CompoundTag itemTag = new CompoundTag();
                        stack.save(itemTag);
                        resList.add(itemTag);
                    }
                }
                sTag.put("Reserved", resList);
            }

            list.add(sTag);
        }
        tag.put("Orders", list);
        return tag;
    }
}
