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

public class EconomyOfferData extends SavedData {

    private static final String NAME = "economy_offers";

    public static final class OfferSnapshot {
        public final UUID offerId;
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

        public OfferSnapshot(UUID offerId, UUID owner, String itemId, int quantity,
                             String pricePerUnit, String type, long createdAt,
                             long expiresAt, boolean hasExpiry, NonNullList<ItemStack> reservedItems,
                             boolean isServerOrder) {
            this.offerId = offerId;
            this.owner = owner;
            this.itemId = itemId;
            this.quantity = quantity;
            this.pricePerUnit = pricePerUnit;
            this.type = type;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.hasExpiry = hasExpiry;
            this.reservedItems = reservedItems;
            this.isServerOrder = isServerOrder;
        }
    }

    private final Map<UUID, OfferSnapshot> offers = new HashMap<>();

    public void putOffer(OfferSnapshot snapshot) {
        offers.put(snapshot.offerId, snapshot);
        setDirty();
    }

    public void removeOffer(UUID offerId) {
        if (offers.remove(offerId) != null) {
            setDirty();
        }
    }

    public Map<UUID, OfferSnapshot> getOffers() {
        return offers;
    }

    public void clearAll() {
        offers.clear();
        setDirty();
    }

    public static EconomyOfferData get(net.minecraft.server.level.ServerLevel level) {
        net.minecraft.server.level.ServerLevel target = (level != null && level.getServer() != null) ? level.getServer().overworld() : level;
        return target.getDataStorage().computeIfAbsent(EconomyOfferData::load, EconomyOfferData::new, NAME);
    }

    public static EconomyOfferData load(CompoundTag tag) {
        EconomyOfferData data = new EconomyOfferData();
        ListTag list = tag.getList("Offers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag offerTag = list.getCompound(i);
            try {
                UUID offerId = offerTag.getUUID("OfferId");
                UUID owner = offerTag.getUUID("Owner");
                String itemId = offerTag.getString("ItemId");
                int quantity = offerTag.getInt("Quantity");
                String pricePerUnit = offerTag.getString("Price");
                String type = offerTag.getString("Type");
                long createdAt = offerTag.getLong("CreatedAt");
                boolean hasExpiry = offerTag.getBoolean("HasExpiry");
                long expiresAt = hasExpiry ? offerTag.getLong("ExpiresAt") : 0;

                NonNullList<ItemStack> reservedItems = NonNullList.create();
                if (offerTag.contains("ReservedItems")) {
                    ListTag itemsTag = offerTag.getList("ReservedItems", Tag.TAG_COMPOUND);
                    for (int j = 0; j < itemsTag.size(); j++) {
                        CompoundTag itemTag = itemsTag.getCompound(j);
                        ItemStack stack = ItemStack.of(itemTag);
                        reservedItems.add(stack);
                    }
                }

                data.offers.put(offerId, new OfferSnapshot(offerId, owner, itemId,
                    quantity, pricePerUnit, type, createdAt, expiresAt, hasExpiry, reservedItems,
                    offerTag.getBoolean("ServerOrder")));
            } catch (Exception e) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (OfferSnapshot snap : offers.values()) {
            CompoundTag offerTag = new CompoundTag();
            offerTag.putUUID("OfferId", snap.offerId);
            offerTag.putUUID("Owner", snap.owner);
            offerTag.putString("ItemId", snap.itemId);
            offerTag.putInt("Quantity", snap.quantity);
            offerTag.putString("Price", snap.pricePerUnit);
            offerTag.putString("Type", snap.type);
            offerTag.putLong("CreatedAt", snap.createdAt);
            offerTag.putBoolean("HasExpiry", snap.hasExpiry);
            if (snap.hasExpiry) {
                offerTag.putLong("ExpiresAt", snap.expiresAt);
            }
            offerTag.putBoolean("ServerOrder", snap.isServerOrder);
            ListTag itemsTag = new ListTag();
            for (ItemStack stack : snap.reservedItems) {
                CompoundTag itemTag = new CompoundTag();
                stack.save(itemTag);
                itemsTag.add(itemTag);
            }
            offerTag.put("ReservedItems", itemsTag);
            list.add(offerTag);
        }
        tag.put("Offers", list);
        return tag;
    }
}
