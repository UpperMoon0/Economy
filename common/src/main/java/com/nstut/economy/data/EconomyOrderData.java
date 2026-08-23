package com.nstut.economy.data;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EconomyOrderData extends SavedData {

    private static final String NAME = "economy_orders";

    /**
     * Bump when the on-disk order format changes incompatibly. Older versions
     * load with best-effort migration; newer versions refuse to guess.
     */
    public static final int DATA_VERSION = 1;

    private final List<CompoundTag> quarantinedOrders = new ArrayList<>();

    public static final class OrderSnapshot {
        public final UUID orderId;
        public final UUID owner;
        public final String itemId;
        public final int quantity;
        public final int initialQuantity;
        public final String pricePerUnit;
        public final String type;
        public final long createdAt;
        public final long expiresAt;
        public final boolean hasExpiry;
        public final NonNullList<ItemStack> reservedItems;
        public final List<FluidStack> reservedFluids;
        public final boolean isInfinite;
        public final boolean isServerOrder;
        public final String commodityType;

        public OrderSnapshot(UUID orderId, UUID owner, String itemId, int quantity, int initialQuantity,
                             String pricePerUnit, String type, long createdAt,
                             long expiresAt, boolean hasExpiry,
                             NonNullList<ItemStack> reservedItems, List<FluidStack> reservedFluids, boolean isServerOrder, boolean isInfinite, String commodityType) {
            this.orderId = orderId;
            this.owner = owner;
            this.itemId = itemId;
            this.quantity = quantity;
            this.initialQuantity = initialQuantity > 0 ? initialQuantity : quantity;
            this.pricePerUnit = pricePerUnit;
            this.type = type;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.hasExpiry = hasExpiry;
            this.reservedItems = reservedItems != null ? reservedItems : NonNullList.create();
            this.reservedFluids = reservedFluids != null ? reservedFluids : new ArrayList<>();
            this.isServerOrder = isServerOrder;
            this.isInfinite = isInfinite;
            this.commodityType = commodityType != null ? commodityType : "ITEM";
        }

        public OrderSnapshot(UUID orderId, UUID owner, String itemId, int quantity, int initialQuantity,
                             String pricePerUnit, String type, long createdAt,
                             long expiresAt, boolean hasExpiry,
                             NonNullList<ItemStack> reservedItems, boolean isServerOrder, boolean isInfinite) {
            this(orderId, owner, itemId, quantity, initialQuantity, pricePerUnit, type, createdAt, expiresAt, hasExpiry, reservedItems, new ArrayList<>(), isServerOrder, isInfinite, "ITEM");
        }

        public OrderSnapshot(UUID orderId, UUID owner, String itemId, int quantity, int initialQuantity,
                             String pricePerUnit, String type, long createdAt,
                             long expiresAt, boolean hasExpiry,
                             NonNullList<ItemStack> reservedItems, boolean isServerOrder) {
            this(orderId, owner, itemId, quantity, initialQuantity, pricePerUnit, type, createdAt, expiresAt, hasExpiry, reservedItems, new ArrayList<>(), isServerOrder, false, "ITEM");
        }

        public OrderSnapshot(UUID orderId, UUID owner, String itemId, int quantity,
                             String pricePerUnit, String type, long createdAt,
                             long expiresAt, boolean hasExpiry,
                             NonNullList<ItemStack> reservedItems, boolean isServerOrder) {
            this(orderId, owner, itemId, quantity, quantity, pricePerUnit, type, createdAt, expiresAt, hasExpiry, reservedItems, new ArrayList<>(), isServerOrder, false, "ITEM");
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

    /**
     * Raw NBT snapshots of orders that failed to load. They are round-tripped
     * back into the save file so escrowed goods are never silently destroyed by
     * a corrupt or unreadable record.
     */
    public List<CompoundTag> getQuarantinedOrders() {
        return quarantinedOrders;
    }

    public static EconomyOrderData get(net.minecraft.server.level.ServerLevel level) {
        net.minecraft.server.level.ServerLevel target = (level != null && level.getServer() != null) ? level.getServer().overworld() : level;
        return target.getDataStorage().computeIfAbsent(EconomyOrderData::load, EconomyOrderData::new, NAME);
    }

    public static EconomyOrderData load(CompoundTag tag) {
        EconomyOrderData data = new EconomyOrderData();
        int version = tag.contains("DataVersion", Tag.TAG_INT) ? tag.getInt("DataVersion") : 0;
        if (version > DATA_VERSION) {
            com.nstut.Economy.LOGGER.error("Order data was written by a newer mod version ({} > {}); loading best-effort",
                    version, DATA_VERSION);
        }
        ListTag quarantined = tag.getList("QuarantinedOrders", Tag.TAG_COMPOUND);
        for (int i = 0; i < quarantined.size(); i++) {
            data.quarantinedOrders.add(quarantined.getCompound(i).copy());
        }
        ListTag list = tag.getList("Orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            try {
                NonNullList<ItemStack> reserved = NonNullList.create();
                if (t.contains("Reserved", Tag.TAG_LIST)) {
                    ListTag resList = t.getList("Reserved", Tag.TAG_COMPOUND);
                    for (int r = 0; r < resList.size(); r++) {
                        ItemStack stack = ItemStack.of(resList.getCompound(r));
                        if (!stack.isEmpty()) reserved.add(stack);
                    }
                }
                List<FluidStack> reservedFluids = new ArrayList<>();
                if (t.contains("ReservedFluids", Tag.TAG_LIST)) {
                    ListTag fluidList = t.getList("ReservedFluids", Tag.TAG_COMPOUND);
                    for (int r = 0; r < fluidList.size(); r++) {
                        FluidStack fs = FluidStack.loadFluidStackFromNBT(fluidList.getCompound(r));
                        if (!fs.isEmpty()) reservedFluids.add(fs);
                    }
                }
                boolean serverOrd = t.getBoolean("ServerOrder");
                boolean infOrd = t.getBoolean("IsInfinite");
                String commodityType = t.contains("CommodityType") ? t.getString("CommodityType") : "ITEM";
                UUID id = t.getUUID("OrderId");
                UUID owner = t.hasUUID("Owner") ? t.getUUID("Owner") : null;
                int qty = t.getInt("Quantity");
                int initQty = t.contains("InitialQuantity", Tag.TAG_INT) ? t.getInt("InitialQuantity") : qty;
                data.orders.put(id, new OrderSnapshot(
                    id,
                    owner,
                    t.getString("ItemId"),
                    qty,
                    initQty,
                    t.getString("PricePerUnit"),
                    t.getString("Type"),
                    t.getLong("CreatedAt"),
                    t.getLong("ExpiresAt"),
                    t.getBoolean("HasExpiry"),
                    reserved,
                    reservedFluids,
                    serverOrd,
                    infOrd,
                    commodityType
                ));
            } catch (Exception e) {
                boolean hasEscrow = (t.contains("Reserved", Tag.TAG_LIST) && !t.getList("Reserved", Tag.TAG_COMPOUND).isEmpty())
                        || (t.contains("ReservedFluids", Tag.TAG_LIST) && !t.getList("ReservedFluids", Tag.TAG_COMPOUND).isEmpty());
                com.nstut.Economy.LOGGER.error("Failed to load persisted order; quarantining snapshot (escrowed goods preserved): {}",
                        t, e);
                if (hasEscrow) {
                    data.quarantinedOrders.add(t.copy());
                }
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("DataVersion", DATA_VERSION);
        ListTag list = new ListTag();
        for (OrderSnapshot s : orders.values()) {
            CompoundTag sTag = new CompoundTag();
            sTag.putUUID("OrderId", s.orderId);
            if (s.owner != null) sTag.putUUID("Owner", s.owner);
            sTag.putString("ItemId", s.itemId);
            sTag.putInt("Quantity", s.quantity);
            sTag.putInt("InitialQuantity", s.initialQuantity);
            sTag.putString("PricePerUnit", s.pricePerUnit);
            sTag.putString("Type", s.type);
            sTag.putLong("CreatedAt", s.createdAt);
            sTag.putLong("ExpiresAt", s.expiresAt);
            sTag.putBoolean("HasExpiry", s.hasExpiry);
            sTag.putBoolean("ServerOrder", s.isServerOrder);
            sTag.putBoolean("IsInfinite", s.isInfinite);
            sTag.putString("CommodityType", s.commodityType);

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

            if (s.reservedFluids != null && !s.reservedFluids.isEmpty()) {
                ListTag fluidList = new ListTag();
                for (FluidStack fs : s.reservedFluids) {
                    if (!fs.isEmpty()) {
                        CompoundTag fluidTag = new CompoundTag();
                        fs.writeToNBT(fluidTag);
                        fluidList.add(fluidTag);
                    }
                }
                sTag.put("ReservedFluids", fluidList);
            }

            list.add(sTag);
        }
        tag.put("Orders", list);
        if (!quarantinedOrders.isEmpty()) {
            ListTag quarantine = new ListTag();
            for (CompoundTag t : quarantinedOrders) {
                quarantine.add(t.copy());
            }
            tag.put("QuarantinedOrders", quarantine);
        }
        return tag;
    }
}
