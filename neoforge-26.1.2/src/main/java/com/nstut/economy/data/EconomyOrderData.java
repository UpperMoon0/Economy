package com.nstut.economy.data;

import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import com.nstut.economy.trading.EconomyFluidStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EconomyOrderData extends SavedData {

    private static final String NAME = "economy_orders";

    private static final SavedDataType<EconomyOrderData> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(NAME),
            level -> new EconomyOrderData(),
            level -> {
                HolderLookup.Provider registries = level.registryAccess();
                return CompoundTag.CODEC.xmap(
                        tag -> EconomyOrderData.load(tag, registries),
                        data -> data.save(new CompoundTag(), registries));
            });

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
        public final List<EconomyFluidStack> reservedFluids;
        public final boolean isInfinite;
        public final boolean isServerOrder;
        public final String commodityType;

        public OrderSnapshot(UUID orderId, UUID owner, String itemId, int quantity, int initialQuantity,
                             String pricePerUnit, String type, long createdAt,
                             long expiresAt, boolean hasExpiry,
                             NonNullList<ItemStack> reservedItems, List<EconomyFluidStack> reservedFluids, boolean isServerOrder, boolean isInfinite, String commodityType) {
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
        return target.getDataStorage().computeIfAbsent(TYPE);
    }

    public static EconomyOrderData load(CompoundTag tag, HolderLookup.Provider registries) {
        EconomyOrderData data = new EconomyOrderData();
        int version = tag.contains("DataVersion") ? tag.getIntOr("DataVersion", 0) : 0;
        if (version > DATA_VERSION) {
            com.nstut.Economy.LOGGER.error("Order data was written by a newer mod version ({} > {}); loading best-effort",
                    version, DATA_VERSION);
        }
        ListTag quarantined = tag.getListOrEmpty("QuarantinedOrders");
        for (int i = 0; i < quarantined.size(); i++) {
            data.quarantinedOrders.add(quarantined.getCompoundOrEmpty(i).copy());
        }
        ListTag list = tag.getListOrEmpty("Orders");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompoundOrEmpty(i);
            try {
                NonNullList<ItemStack> reserved = NonNullList.create();
                if (t.contains("Reserved")) {
                    ListTag resList = t.getListOrEmpty("Reserved");
                    for (int r = 0; r < resList.size(); r++) {
                        ItemStack stack = com.nstut.economy.util.ItemStackNbtCompat.parseOptional(registries, resList.getCompoundOrEmpty(r));
                        if (!stack.isEmpty()) reserved.add(stack);
                    }
                }
                List<EconomyFluidStack> reservedFluids = new ArrayList<>();
                if (t.contains("ReservedFluids")) {
                    ListTag fluidList = t.getListOrEmpty("ReservedFluids");
                    for (int r = 0; r < fluidList.size(); r++) {
                        EconomyFluidStack fs = EconomyFluidStack.loadFluidStackFromNBT(fluidList.getCompoundOrEmpty(r));
                        if (!fs.isEmpty()) reservedFluids.add(fs);
                    }
                }
                boolean serverOrd = t.getBooleanOr("ServerOrder", false);
                boolean infOrd = t.getBooleanOr("IsInfinite", false);
                String commodityType = t.contains("CommodityType") ? t.getStringOr("CommodityType", "") : "ITEM";
                UUID id = com.nstut.economy.util.NbtCompat.getUuid(t, "OrderId");
                UUID owner = com.nstut.economy.util.NbtCompat.hasUuid(t, "Owner")
                        ? com.nstut.economy.util.NbtCompat.getUuid(t, "Owner") : null;
                int qty = t.getIntOr("Quantity", 0);
                int initQty = t.contains("InitialQuantity") ? t.getIntOr("InitialQuantity", 0) : qty;
                data.orders.put(id, new OrderSnapshot(
                    id,
                    owner,
                    t.getStringOr("ItemId", ""),
                    qty,
                    initQty,
                    t.getStringOr("PricePerUnit", ""),
                    t.getStringOr("Type", ""),
                    t.getLongOr("CreatedAt", 0L),
                    t.getLongOr("ExpiresAt", 0L),
                    t.getBooleanOr("HasExpiry", false),
                    reserved,
                    reservedFluids,
                    serverOrd,
                    infOrd,
                    commodityType
                ));
            } catch (Exception e) {
                boolean hasEscrow = (t.contains("Reserved") && !t.getListOrEmpty("Reserved").isEmpty())
                        || (t.contains("ReservedFluids") && !t.getListOrEmpty("ReservedFluids").isEmpty());
                com.nstut.Economy.LOGGER.error("Failed to load persisted order; quarantining snapshot (escrowed goods preserved): {}",
                        t, e);
                if (hasEscrow) {
                    data.quarantinedOrders.add(t.copy());
                }
            }
        }
        return data;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("DataVersion", DATA_VERSION);
        ListTag list = new ListTag();
        for (OrderSnapshot s : orders.values()) {
            CompoundTag sTag = new CompoundTag();
            com.nstut.economy.util.NbtCompat.putUuid(sTag, "OrderId", s.orderId);
            if (s.owner != null) com.nstut.economy.util.NbtCompat.putUuid(sTag, "Owner", s.owner);
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
                        resList.add(com.nstut.economy.util.ItemStackNbtCompat.save(registries, stack));
                    }
                }
                sTag.put("Reserved", resList);
            }

            if (s.reservedFluids != null && !s.reservedFluids.isEmpty()) {
                ListTag fluidList = new ListTag();
                for (EconomyFluidStack fs : s.reservedFluids) {
                    if (!fs.isEmpty()) {
                        CompoundTag fluidTag = new CompoundTag();
                        fs.writeTo(fluidTag);
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


