package com.nstut.economy.data;

import com.nstut.economy.api.EconomyId;
import com.nstut.economy.api.StorageReservation;
import com.nstut.economy.trading.EconomyFluidStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EconomyOrderData extends SavedData {
    private static final String NAME = "economy_orders";
    private static final SavedDataType<EconomyOrderData> TYPE = new SavedDataType<>(Identifier.withDefaultNamespace(NAME),
            level -> new EconomyOrderData(), level -> {
                HolderLookup.Provider registries = level.registryAccess();
                return CompoundTag.CODEC.xmap(tag -> EconomyOrderData.load(tag, registries), data -> data.save(new CompoundTag(), registries));
            });
    public static final int DATA_VERSION = 2;
    private final List<CompoundTag> quarantinedOrders = new ArrayList<>();

    public static final class OrderSnapshot {
        public final UUID orderId; public final UUID owner; public final String itemId;
        public final int quantity; public final int initialQuantity; public final String pricePerUnit; public final String type;
        public final long createdAt; public final long expiresAt; public final boolean hasExpiry;
        public final NonNullList<ItemStack> reservedItems; public final List<EconomyFluidStack> reservedFluids;
        public final boolean isInfinite; public final boolean isServerOrder; public final String commodityType;
        public final String commodityTypeId; public final int commodityPayloadVersion; public final Map<String, String> commodityPayload;
        public final StorageReservation externalReservation; public final Map<String, String> addonMetadata;

        public OrderSnapshot(UUID orderId, UUID owner, String itemId, int quantity, int initialQuantity, String pricePerUnit,
                             String type, long createdAt, long expiresAt, boolean hasExpiry, NonNullList<ItemStack> reservedItems,
                             List<EconomyFluidStack> reservedFluids, boolean isServerOrder, boolean isInfinite, String commodityType,
                             String commodityTypeId, int commodityPayloadVersion, Map<String, String> commodityPayload,
                             StorageReservation externalReservation, Map<String, String> addonMetadata) {
            this.orderId = orderId; this.owner = owner; this.itemId = itemId; this.quantity = quantity;
            this.initialQuantity = initialQuantity > 0 ? initialQuantity : quantity; this.pricePerUnit = pricePerUnit; this.type = type;
            this.createdAt = createdAt; this.expiresAt = expiresAt; this.hasExpiry = hasExpiry;
            this.reservedItems = reservedItems != null ? reservedItems : NonNullList.create();
            this.reservedFluids = reservedFluids != null ? reservedFluids : new ArrayList<>();
            this.isServerOrder = isServerOrder; this.isInfinite = isInfinite; this.commodityType = commodityType != null ? commodityType : "ITEM";
            this.commodityTypeId = commodityTypeId != null && !commodityTypeId.isBlank() ? commodityTypeId : legacyTypeId(this.commodityType);
            this.commodityPayloadVersion = Math.max(1, commodityPayloadVersion); this.commodityPayload = commodityPayload == null ? Map.of() : Map.copyOf(commodityPayload);
            this.externalReservation = externalReservation; this.addonMetadata = addonMetadata == null ? Map.of() : Map.copyOf(addonMetadata);
        }
        public OrderSnapshot(UUID orderId, UUID owner, String itemId, int quantity, int initialQuantity, String pricePerUnit, String type,
                             long createdAt, long expiresAt, boolean hasExpiry, NonNullList<ItemStack> reservedItems,
                             List<EconomyFluidStack> reservedFluids, boolean isServerOrder, boolean isInfinite, String commodityType) {
            this(orderId, owner, itemId, quantity, initialQuantity, pricePerUnit, type, createdAt, expiresAt, hasExpiry,
                    reservedItems, reservedFluids, isServerOrder, isInfinite, commodityType, legacyTypeId(commodityType), 1, Map.of(), null, Map.of());
        }
        public OrderSnapshot(UUID orderId, UUID owner, String itemId, int quantity, int initialQuantity, String pricePerUnit, String type,
                             long createdAt, long expiresAt, boolean hasExpiry, NonNullList<ItemStack> reservedItems, boolean isServerOrder, boolean isInfinite) {
            this(orderId, owner, itemId, quantity, initialQuantity, pricePerUnit, type, createdAt, expiresAt, hasExpiry,
                    reservedItems, new ArrayList<>(), isServerOrder, isInfinite, "ITEM");
        }
        public OrderSnapshot(UUID orderId, UUID owner, String itemId, int quantity, int initialQuantity, String pricePerUnit, String type,
                             long createdAt, long expiresAt, boolean hasExpiry, NonNullList<ItemStack> reservedItems, boolean isServerOrder) {
            this(orderId, owner, itemId, quantity, initialQuantity, pricePerUnit, type, createdAt, expiresAt, hasExpiry,
                    reservedItems, new ArrayList<>(), isServerOrder, false, "ITEM");
        }
        public OrderSnapshot(UUID orderId, UUID owner, String itemId, int quantity, String pricePerUnit, String type,
                             long createdAt, long expiresAt, boolean hasExpiry, NonNullList<ItemStack> reservedItems, boolean isServerOrder) {
            this(orderId, owner, itemId, quantity, quantity, pricePerUnit, type, createdAt, expiresAt, hasExpiry,
                    reservedItems, new ArrayList<>(), isServerOrder, false, "ITEM");
        }
        public boolean hasEscrow() {
            return !reservedItems.isEmpty() || !reservedFluids.isEmpty() || externalReservation != null
                    || requiresCodecRecovery();
        }
        private boolean requiresCodecRecovery() {
            return !"economy:item".equals(commodityTypeId) && !"economy:fluid".equals(commodityTypeId);
        }
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
        return target.getDataStorage().computeIfAbsent(TYPE);
    }

    public static EconomyOrderData load(CompoundTag tag, HolderLookup.Provider registries) {
        EconomyOrderData data = new EconomyOrderData();
        int version = tag.contains("DataVersion") ? tag.getIntOr("DataVersion", 0) : 0;
        if (version > DATA_VERSION) com.nstut.Economy.LOGGER.error("Order data newer than supported ({} > {}); preserving unknown state best-effort", version, DATA_VERSION);
        ListTag quarantined = tag.getListOrEmpty("QuarantinedOrders");
        for (int i = 0; i < quarantined.size(); i++) data.quarantinedOrders.add(quarantined.getCompoundOrEmpty(i).copy());
        ListTag list = tag.getListOrEmpty("Orders");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompoundOrEmpty(i);
            try {
                NonNullList<ItemStack> items = readItems(t, registries); List<EconomyFluidStack> fluids = readFluids(t);
                String legacy = t.contains("CommodityType") ? t.getStringOr("CommodityType", "ITEM") : "ITEM";
                String typeId = t.contains("CommodityTypeId") ? t.getStringOr("CommodityTypeId", "") : ("FLUID".equalsIgnoreCase(legacy) ? "economy:fluid" : "economy:item");
                int payloadVersion = t.contains("CommodityPayloadVersion") ? t.getIntOr("CommodityPayloadVersion", 1) : 1;
                UUID id = com.nstut.economy.util.NbtCompat.getUuid(t, "OrderId");
                UUID owner = com.nstut.economy.util.NbtCompat.hasUuid(t, "Owner") ? com.nstut.economy.util.NbtCompat.getUuid(t, "Owner") : null;
                int qty = t.getIntOr("Quantity", 0); int initial = t.contains("InitialQuantity") ? t.getIntOr("InitialQuantity", qty) : qty;
                data.orders.put(id, new OrderSnapshot(id, owner, t.getStringOr("ItemId", ""), qty, initial,
                        t.getStringOr("PricePerUnit", ""), t.getStringOr("Type", ""), t.getLongOr("CreatedAt", 0L),
                        t.getLongOr("ExpiresAt", 0L), t.getBooleanOr("HasExpiry", false), items, fluids,
                        t.getBooleanOr("ServerOrder", false), t.getBooleanOr("IsInfinite", false), legacy, typeId, payloadVersion,
                        readStringMap(t, "CommodityPayload"), readReservation(t), readStringMap(t, "AddonMetadata")));
            } catch (Exception e) {
                com.nstut.Economy.LOGGER.error("Failed to load persisted order; quarantining raw snapshot without discarding extension state", e);
                data.quarantinedOrders.add(t.copy());
            }
        }
        return data;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("DataVersion", DATA_VERSION); ListTag list = new ListTag();
        for (OrderSnapshot s : orders.values()) list.add(writeSnapshot(s, registries));
        tag.put("Orders", list);
        if (!quarantinedOrders.isEmpty()) { ListTag q = new ListTag(); for (CompoundTag t : quarantinedOrders) q.add(t.copy()); tag.put("QuarantinedOrders", q); }
        return tag;
    }

    private static NonNullList<ItemStack> readItems(CompoundTag t, HolderLookup.Provider registries) {
        NonNullList<ItemStack> result = NonNullList.create(); if (!t.contains("Reserved")) return result;
        ListTag list = t.getListOrEmpty("Reserved");
        for (int i = 0; i < list.size(); i++) { ItemStack stack = com.nstut.economy.util.ItemStackNbtCompat.parseOptional(registries, list.getCompoundOrEmpty(i)); if (!stack.isEmpty()) result.add(stack); }
        return result;
    }
    private static List<EconomyFluidStack> readFluids(CompoundTag t) {
        List<EconomyFluidStack> result = new ArrayList<>(); if (!t.contains("ReservedFluids")) return result;
        ListTag list = t.getListOrEmpty("ReservedFluids");
        for (int i = 0; i < list.size(); i++) { EconomyFluidStack stack = EconomyFluidStack.loadFluidStackFromNBT(list.getCompoundOrEmpty(i)); if (!stack.isEmpty()) result.add(stack); }
        return result;
    }
    private static StorageReservation readReservation(CompoundTag parent) {
        if (!parent.contains("ExternalReservation")) return null; CompoundTag t = parent.getCompoundOrEmpty("ExternalReservation");
        CompoundTag providerState = t.contains("ProviderState") ? t.getCompoundOrEmpty("ProviderState").copy() : new CompoundTag();
        return new StorageReservation(EconomyId.parse(t.getStringOr("ProviderId", "")), EconomyId.parse(t.getStringOr("CommodityId", "")),
                t.getIntOr("Amount", 0), t.getStringOr("Token", ""), readStringMap(t, "Metadata"), providerState);
    }
    private static Map<String, String> readStringMap(CompoundTag parent, String key) {
        if (!parent.contains(key)) return Map.of(); ListTag list = parent.getListOrEmpty(key); Map<String, String> result = new HashMap<>();
        for (int i = 0; i < list.size(); i++) { CompoundTag e = list.getCompoundOrEmpty(i); String k = e.getStringOr("Key", ""); if (!k.isEmpty()) result.put(k, e.getStringOr("Value", "")); }
        return Map.copyOf(result);
    }
    private static CompoundTag writeSnapshot(OrderSnapshot s, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag(); com.nstut.economy.util.NbtCompat.putUuid(tag, "OrderId", s.orderId);
        if (s.owner != null) com.nstut.economy.util.NbtCompat.putUuid(tag, "Owner", s.owner);
        tag.putString("ItemId", s.itemId); tag.putInt("Quantity", s.quantity); tag.putInt("InitialQuantity", s.initialQuantity);
        tag.putString("PricePerUnit", s.pricePerUnit); tag.putString("Type", s.type); tag.putLong("CreatedAt", s.createdAt); tag.putLong("ExpiresAt", s.expiresAt);
        tag.putBoolean("HasExpiry", s.hasExpiry); tag.putBoolean("ServerOrder", s.isServerOrder); tag.putBoolean("IsInfinite", s.isInfinite);
        tag.putString("CommodityType", s.commodityType); tag.putString("CommodityTypeId", s.commodityTypeId); tag.putInt("CommodityPayloadVersion", s.commodityPayloadVersion);
        writeStringMap(tag, "CommodityPayload", s.commodityPayload); writeStringMap(tag, "AddonMetadata", s.addonMetadata);
        if (!s.reservedItems.isEmpty()) { ListTag items = new ListTag(); for (ItemStack stack : s.reservedItems) if (!stack.isEmpty()) items.add(com.nstut.economy.util.ItemStackNbtCompat.save(registries, stack)); tag.put("Reserved", items); }
        if (!s.reservedFluids.isEmpty()) { ListTag fluids = new ListTag(); for (EconomyFluidStack stack : s.reservedFluids) if (!stack.isEmpty()) { CompoundTag f = new CompoundTag(); stack.writeTo(f); fluids.add(f); } tag.put("ReservedFluids", fluids); }
        if (s.externalReservation != null) {
            CompoundTag r = new CompoundTag();
            r.putString("ProviderId", s.externalReservation.providerId().toString());
            r.putString("CommodityId", s.externalReservation.commodityId().toString());
            r.putInt("Amount", s.externalReservation.amount());
            r.putString("Token", s.externalReservation.token());
            writeStringMap(r, "Metadata", s.externalReservation.metadata());
            CompoundTag providerState = s.externalReservation.providerState();
            if (!providerState.isEmpty()) r.put("ProviderState", providerState);
            tag.put("ExternalReservation", r);
        }
        return tag;
    }
    private static void writeStringMap(CompoundTag parent, String key, Map<String, String> values) {
        if (values == null || values.isEmpty()) return; ListTag list = new ListTag();
        for (Map.Entry<String, String> e : values.entrySet()) { CompoundTag entry = new CompoundTag(); entry.putString("Key", e.getKey()); entry.putString("Value", e.getValue()); list.add(entry); }
        parent.put(key, list);
    }
}
