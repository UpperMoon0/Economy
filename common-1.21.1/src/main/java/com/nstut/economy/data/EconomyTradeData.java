package com.nstut.economy.data;

import net.minecraft.core.HolderLookup;
import com.nstut.economy.api.AccountRef;
import com.nstut.economy.api.MarketIdentity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EconomyTradeData extends SavedData {
    private static final String NAME = "economy_trades";

    public static final class TradeSnapshot {
        public final MarketIdentity buyerIdentity; public final MarketIdentity sellerIdentity;
        public final String itemId; public final String commodityType; public final String variantData; public final String displayName; public final String price;
        public final int quantity; public final UUID buyer; public final UUID seller; public final long timestamp;
        public TradeSnapshot(String itemId, String price, int quantity, UUID buyer, UUID seller, long timestamp) {
            this(itemId, null, "", "", price, quantity, buyer, seller, timestamp);
        }
        public TradeSnapshot(String itemId, String commodityType, String price, int quantity, UUID buyer, UUID seller, long timestamp) {
            this(itemId, commodityType, "", "", price, quantity, buyer, seller, timestamp);
        }
        public TradeSnapshot(String itemId, String commodityType, String variantData, String displayName,
                             String price, int quantity, UUID buyer, UUID seller, long timestamp) {
            this(itemId, commodityType, variantData, displayName, price, quantity, buyer, seller, timestamp,
                    MarketIdentity.personal(buyer), MarketIdentity.personal(seller));
        }
        public TradeSnapshot(String itemId, String commodityType, String variantData, String displayName,
                             String price, int quantity, UUID buyer, UUID seller, long timestamp,
                             MarketIdentity buyerIdentity, MarketIdentity sellerIdentity) {
            this.buyerIdentity = buyerIdentity; this.sellerIdentity = sellerIdentity;
            this.itemId = itemId; this.commodityType = commodityType; this.variantData = variantData; this.displayName = displayName;
            this.price = price; this.quantity = quantity; this.buyer = buyer; this.seller = seller; this.timestamp = timestamp;
        }
    }

    private final List<TradeSnapshot> trades = new ArrayList<>();
    private static final int MAX_TRADES = 1000;

    public void recordTrade(String itemId, BigDecimal price, int quantity, UUID buyer, UUID seller) {
        recordTrade(itemId, null, price, quantity, buyer, seller);
    }
    public void recordTrade(String itemId, String commodityType, BigDecimal price, int quantity, UUID buyer, UUID seller) {
        recordTrade(itemId, commodityType, "", "", price, quantity, buyer, seller);
    }
    public void recordTrade(String itemId, String commodityType, String variantData, String displayName,
                            BigDecimal price, int quantity, UUID buyer, UUID seller) {
        recordTrade(itemId, commodityType, variantData, displayName, price, quantity,
                MarketIdentity.personal(buyer), MarketIdentity.personal(seller));
    }
    public void recordTrade(String itemId, String commodityType, String variantData, String displayName,
                            BigDecimal price, int quantity, MarketIdentity buyer, MarketIdentity seller) {
        trades.add(new TradeSnapshot(itemId, commodityType, variantData, displayName, price.toPlainString(), quantity, buyer.actor(), seller.actor(), System.currentTimeMillis(), buyer, seller));
        while (trades.size() > MAX_TRADES) trades.remove(0);
        setDirty();
    }

    public List<TradeSnapshot> getTrades() { return List.copyOf(trades); }
    public List<TradeSnapshot> getTradesForItem(String itemId, int limit) {
        List<TradeSnapshot> result = new ArrayList<>();
        for (int i = trades.size() - 1; i >= 0 && result.size() < limit; i--) {
            TradeSnapshot t = trades.get(i); if (t.itemId.equals(itemId)) result.add(t);
        }
        return List.copyOf(result);
    }

    public static EconomyTradeData get(net.minecraft.server.level.ServerLevel level) {
        net.minecraft.server.level.ServerLevel target = (level != null && level.getServer() != null) ? level.getServer().overworld() : level;
        return target.getDataStorage().computeIfAbsent(new SavedData.Factory<>(EconomyTradeData::new, EconomyTradeData::load, null), NAME);
    }

    private static MarketIdentity readIdentity(CompoundTag t, String prefix) {
        if (!t.contains(prefix + "Principal")) return MarketIdentity.personal(t.getUUID(prefix));
        return new MarketIdentity(AccountRef.parse(identityString(t, prefix + "Principal")),
                UUID.fromString(identityString(t, prefix + "Actor")),
                UUID.fromString(identityString(t, prefix + "StorageOwner")));
    }
    private static String identityString(CompoundTag t, String key) { return t.getString(key); }
    private static void writeIdentity(CompoundTag t, String prefix, MarketIdentity identity) {
        t.putString(prefix + "Principal", identity.principal().toString());
        t.putString(prefix + "Actor", identity.actor().toString());
        t.putString(prefix + "StorageOwner", identity.storageOwner().toString());
    }

    public static EconomyTradeData load(CompoundTag tag, HolderLookup.Provider registries) {
        EconomyTradeData data = new EconomyTradeData();
        ListTag list = tag.getList("Trades", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            try {
                data.trades.add(new TradeSnapshot(t.getString("ItemId"), t.contains("CommodityType") ? t.getString("CommodityType") : null,
                        t.contains("VariantData") ? t.getString("VariantData") : "", t.contains("DisplayName") ? t.getString("DisplayName") : "",
                        t.getString("Price"), t.getInt("Quantity"), t.getUUID("Buyer"), t.getUUID("Seller"), t.getLong("Timestamp"), readIdentity(t, "Buyer"), readIdentity(t, "Seller")));
            } catch (Exception ignored) { }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (TradeSnapshot t : trades) {
            CompoundTag tTag = new CompoundTag();
            tTag.putString("ItemId", t.itemId); tTag.putString("Price", t.price); tTag.putInt("Quantity", t.quantity);
            writeIdentity(tTag, "Buyer", t.buyerIdentity); writeIdentity(tTag, "Seller", t.sellerIdentity);
            if (t.commodityType != null) tTag.putString("CommodityType", t.commodityType);
            if (t.variantData != null && !t.variantData.isBlank()) tTag.putString("VariantData", t.variantData);
            if (t.displayName != null && !t.displayName.isBlank()) tTag.putString("DisplayName", t.displayName);
            tTag.putUUID("Buyer", t.buyer); tTag.putUUID("Seller", t.seller); tTag.putLong("Timestamp", t.timestamp); list.add(tTag);
        }
        tag.putInt("DataVersion", 2);
        tag.put("Trades", list); return tag;
    }
}
