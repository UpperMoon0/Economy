package com.nstut.economy.data;

import com.nstut.economy.api.EconomyId;
import com.nstut.economy.api.StorageReservation;
import com.nstut.economy.trading.EconomyFluidStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EconomyOrderData extends SavedData {
    private static final String NAME = "economy_orders";
    public static final int DATA_VERSION = 2;
    private final List<CompoundTag> quarantinedOrders = new ArrayList<>();

    public static final class OrderSnapshot {
        public final UUID orderId; public final UUID owner; public final String itemId;
        public final int quantity; public final int initialQuantity; public final String pricePerUnit; public final String type;
        public final long createdAt; public final long expiresAt; public final boolean hasExpiry;
        public final NonNullList<ItemStack> reservedItems; public final List<EconomyFluidStack> reservedFluids;
        public final boolean isInfinite; public final boolean isServerOrder; public final String commodityType;
        public final String commodityTypeId; public final int commodityPayloadVersion;
        public final Map<String, String> commodityPayload; public final StorageReservation externalReservation;
        public final Map<String, String> addonMetadata;

        public OrderSnapshot(UUID orderId, UUID owner, String itemId, int quantity, int initialQuantity,
                             String pricePerUnit, String type, long createdAt, long expiresAt, boolean hasExpiry,
                             NonNullList<ItemStack> reservedItems, List<EconomyFluidStack> reservedFluids,
                             boolean isServerOrder, boolean isInfinite, String commodityType,
                             String commodityTypeId, int commodityPayloadVersion, Map<String, String> commodityPayload,
                             StorageReservation externalReservation, Map<String, String> addonMetadata) {
            this.orderId = orderId; this.owner = owner; this.itemId = itemId; this.quantity = quantity;
            this.initialQuantity = initialQuantity > 0 ? initialQuantity : quantity; this.pricePerUnit = pricePerUnit;
            this.type = type; this.createdAt = createdAt; this.expiresAt = expiresAt; this.hasExpiry = hasExpiry;
            this.reservedItems = reservedItems != null ? reservedItems : NonNullList.create();
            this.reservedFluids = reservedFluids != null ? reservedFluids : new ArrayList<>();
            this.isServerOrder = isServerOrder; this.isInfinite = isInfinite;
            this.commodityType = commodityType != null ? commodityType : "ITEM";
            this.commodityTypeId = commodityTypeId != null && !commodityTypeId.isBlank() ? commodityTypeId : legacyTypeId(this.commodityType);
            this.commodityPayloadVersion = Math.max(1, commodityPayloadVersion);
            this.commodityPayload = commodityPayload == null ? Map.of() : Map.copyOf(commodityPayload);
            this.externalReservation = externalReservation; this.addonMetadata = addonMetadata == null ? Map.of() : Map.copyOf(addonMetadata);
        }
        public OrderSnapshot(UUID orderId, UUID owner, String itemId, int quantity, int initialQuantity, String pricePerUnit,
                             String type, long createdAt, long expiresAt, boolean hasExpiry, NonNullList<ItemStack> reservedItems,
                             List<EconomyFluidStack> reservedFluids, boolean isServerOrder, boolean isInfinite, String commodityType) {
            this(orderId, owner, itemId, quantity, initialQuantity, pricePerUnit, type, createdAt, expiresAt, hasExpiry,
                    reservedItems, reservedFluids, isServerOrder, isInfinite, commodityType, legacyTypeId(commodityType), 1, Map.of(), null, Map.of());
        }
        public OrderSnapshot(UUID orderId, UUID owner, String itemId, int quantity, int initialQuantity, String pricePerUnit,
                             String type, long createdAt, long expiresAt, boolean hasExpiry, NonNullList<ItemStack> reservedItems,
                             boolean isServerOrder, boolean isInfinite) {
            this(orderId, owner, itemId, quantity, initialQuantity, pricePerUnit, type, createdAt, expiresAt, hasExpiry,
                    reservedItems, new ArrayList<>(), isServerOrder, isInfinite, "ITEM");
        }
        public OrderSnapshot(UUID orderId, UUID owner, String itemId, int quantity, int initialQuantity, String pricePerUnit,
                             String type, long createdAt, long expiresAt, boolean hasExpiry, NonNullList<ItemStack> reservedItems, boolean isServerOrder) {
            this(orderId, owner, itemId, quantity, initialQuantity, pricePerUnit, type, createdAt, expiresAt, hasExpiry,
                    reservedItems, new ArrayList<>(), isServerOrder, false, "ITEM");
        }
        public OrderSnapshot(UUID orderId, UUID owner, String itemId, int quantity, String pricePerUnit, String type,
                             long createdAt, long expiresAt, boolean hasExpiry, NonNullList<ItemStack> reservedItems, boolean isServerOrder) {
            this(orderId, owner, itemId, quantity, quantity, pricePerUnit, type, createdAt, expiresAt, hasExpiry,
                    reservedItems, new ArrayList<>(), isServerOrder, false, "ITEM");
        }
        public boolean hasEscrow() { return !reservedItems.isEmpty() || !reservedFluids.isEmpty() || externalReservation != null; }
        private static String legacyTypeId(String legacy) { return "FLUID".equalsIgnoreCase(legacy) ? "economy:fluid" : "economy:item"; }
    }

    private final Map<UUID, OrderSnapshot> orders = new HashMap<>();
    public Map<UUID, OrderSnapshot> getOrders() { return orders; }
    public void putOrder(OrderSnapshot snap) { orders.put(snap.orderId, snap); setDirty(); }
    public void removeOrder(UUID orderId) { if (orders.remove(orderId) != null) setDirty(); }
    public void clearAll() { orders.clear(); setDirty(); }
    public List<CompoundTag> getQuarantinedOrders() { return List.copyOf(quarantinedOrders); }

    public static EconomyOrderData get(net.minecraft.server.level.ServerLevel level) {
        net.minecraft.server.level.ServerLevel target = level != null && level.getServer() != null ? level.getServer().overworld() : level;
        return target.getDataStorage().computeIfAbsent(new SavedData.Factory<>(EconomyOrderData::new, EconomyOrderData::load, null), NAME);
    }

    public static EconomyOrderData load(CompoundTag tag, HolderLookup.Provider registries) {
        EconomyOrderData data = new EconomyOrderData();
        int version = tag.contains("DataVersion", Tag.TAG_INT) ? tag.getInt("DataVersion") : 0;
        if (version > DATA_VERSION) com.nstut.Economy.LOGGER.error("Order data newer than supported ({} > {}); preserving unknown state best-effort", version, DATA_VERSION);
        ListTag quarantined = tag.getList("QuarantinedOrders", Tag.TAG_COMPOUND);
        for (int i = 0; i < quarantined.size(); i++) data.quarantinedOrders.add(quarantined.getCompound(i).copy());
        ListTag list = tag.getList("Orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            try {
                NonNullList<ItemStack> reserved = readItems(t, registries);
                List<EconomyFluidStack> fluids = readFluids(t);
                String legacy = t.contains("CommodityType") ? t.getString("CommodityType") : "ITEM";
                String typeId = t.contains("CommodityTypeId") ? t.getString("CommodityTypeId") : ("FLUID".equalsIgnoreCase(legacy) ? "economy:fluid" : "economy:item");
                int payloadVersion = t.contains("CommodityPayloadVersion", Tag.TAG_INT) ? t.getInt("CommodityPayloadVersion") : 1;
                UUID id = t.getUUID("OrderId"); UUID owner = t.hasUUID("Owner") ? t.getUUID("Owner") : null;
                int qty = t.getInt("Quantity"); int initial = t.contains("InitialQuantity", Tag.TAG_INT) ? t.getInt("InitialQuantity") : qty;
                data.orders.put(id, new OrderSnapshot(id, owner, t.getString("ItemId"), qty, initial, t.getString("PricePerUnit"),
                        t.getString("Type"), t.getLong("CreatedAt"), t.getLong("ExpiresAt"), t.getBoolean("HasExpiry"), reserved, fluids,
                        t.getBoolean("ServerOrder"), t.getBoolean("IsInfinite"), legacy, typeId, payloadVersion,
                        readStringMap(t, "CommodityPayload"), readReservation(t), readStringMap(t, "AddonMetadata")));
            } catch (Exception e) {
                com.nstut.Economy.LOGGER.error("Failed to load persisted order; quarantining raw snapshot without discarding extension state", e);
                data.quarantinedOrders.add(t.copy());
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("DataVersion", DATA_VERSION); ListTag list = new ListTag();
        for (OrderSnapshot s : orders.values()) list.add(writeSnapshot(s, registries));
        tag.put("Orders", list);
        if (!quarantinedOrders.isEmpty()) { ListTag q = new ListTag(); for (CompoundTag t : quarantinedOrders) q.add(t.copy()); tag.put("QuarantinedOrders", q); }
        return tag;
    }

    private static NonNullList<ItemStack> readItems(CompoundTag t, HolderLookup.Provider registries) {
        NonNullList<ItemStack> result = NonNullList.create(); if (!t.contains("Reserved", Tag.TAG_LIST)) return result;
        ListTag list = t.getList("Reserved", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) { ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i)); if (!stack.isEmpty()) result.add(stack); }
        return result;
    }
    private static List<EconomyFluidStack> readFluids(CompoundTag t) {
        List<EconomyFluidStack> result = new ArrayList<>(); if (!t.contains("ReservedFluids", Tag.TAG_LIST)) return result;
        ListTag list = t.getList("ReservedFluids", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) { EconomyFluidStack stack = EconomyFluidStack.loadFluidStackFromNBT(list.getCompound(i)); if (!stack.isEmpty()) result.add(stack); }
        return result;
    }
    private static StorageReservation readReservation(CompoundTag parent) {
        if (!parent.contains("ExternalReservation", Tag.TAG_COMPOUND)) return null;
        CompoundTag t = parent.getCompound("ExternalReservation");
        return new StorageReservation(EconomyId.parse(t.getString("ProviderId")), EconomyId.parse(t.getString("CommodityId")),
                t.getInt("Amount"), t.getString("Token"), readStringMap(t, "Metadata"));
    }
    private static Map<String, String> readStringMap(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) return Map.of(); CompoundTag map = parent.getCompound(key);
        Map<String, String> result = new HashMap<>(); for (String k : map.getAllKeys()) result.put(k, map.getString(k)); return Map.copyOf(result);
    }
    private static CompoundTag writeSnapshot(OrderSnapshot s, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag(); tag.putUUID("OrderId", s.orderId); if (s.owner != null) tag.putUUID("Owner", s.owner);
        tag.putString("ItemId", s.itemId); tag.putInt("Quantity", s.quantity); tag.putInt("InitialQuantity", s.initialQuantity);
        tag.putString("PricePerUnit", s.pricePerUnit); tag.putString("Type", s.type); tag.putLong("CreatedAt", s.createdAt);
        tag.putLong("ExpiresAt", s.expiresAt); tag.putBoolean("HasExpiry", s.hasExpiry); tag.putBoolean("ServerOrder", s.isServerOrder);
        tag.putBoolean("IsInfinite", s.isInfinite); tag.putString("CommodityType", s.commodityType); tag.putString("CommodityTypeId", s.commodityTypeId);
        tag.putInt("CommodityPayloadVersion", s.commodityPayloadVersion); writeStringMap(tag, "CommodityPayload", s.commodityPayload); writeStringMap(tag, "AddonMetadata", s.addonMetadata);
        if (!s.reservedItems.isEmpty()) { ListTag items = new ListTag(); for (ItemStack stack : s.reservedItems) if (!stack.isEmpty()) { CompoundTag item = new CompoundTag(); stack.save(registries, item); items.add(item); } tag.put("Reserved", items); }
        if (!s.reservedFluids.isEmpty()) { ListTag fluids = new ListTag(); for (EconomyFluidStack stack : s.reservedFluids) if (!stack.isEmpty()) { CompoundTag fluid = new CompoundTag(); stack.writeTo(fluid); fluids.add(fluid); } tag.put("ReservedFluids", fluids); }
        if (s.externalReservation != null) { CompoundTag r = new CompoundTag(); r.putString("ProviderId", s.externalReservation.providerId().toString()); r.putString("CommodityId", s.externalReservation.commodityId().toString()); r.putInt("Amount", s.externalReservation.amount()); r.putString("Token", s.externalReservation.token()); writeStringMap(r, "Metadata", s.externalReservation.metadata()); tag.put("ExternalReservation", r); }
        return tag;
    }
    private static void writeStringMap(CompoundTag parent, String key, Map<String, String> values) {
        if (values == null || values.isEmpty()) return; CompoundTag map = new CompoundTag();
        for (Map.Entry<String, String> e : values.entrySet()) map.putString(e.getKey(), e.getValue()); parent.put(key, map);
    }
}
