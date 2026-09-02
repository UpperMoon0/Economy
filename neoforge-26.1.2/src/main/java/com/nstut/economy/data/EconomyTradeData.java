package com.nstut.economy.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EconomyTradeData extends SavedData {
    private static final String NAME = "economy_trades";
    private static final SavedDataType<EconomyTradeData> TYPE = new SavedDataType<>(Identifier.withDefaultNamespace(NAME),
            level -> new EconomyTradeData(), level -> CompoundTag.CODEC.xmap(EconomyTradeData::load, data -> data.save(new CompoundTag())));

    public static final class TradeSnapshot {
        public final String itemId; public final String commodityType; public final String price;
        public final int quantity; public final UUID buyer; public final UUID seller; public final long timestamp;
        public TradeSnapshot(String itemId, String price, int quantity, UUID buyer, UUID seller, long timestamp) {
            this(itemId, null, price, quantity, buyer, seller, timestamp);
        }
        public TradeSnapshot(String itemId, String commodityType, String price, int quantity, UUID buyer, UUID seller, long timestamp) {
            this.itemId = itemId; this.commodityType = commodityType; this.price = price; this.quantity = quantity;
            this.buyer = buyer; this.seller = seller; this.timestamp = timestamp;
        }
    }

    private final List<TradeSnapshot> trades = new ArrayList<>();
    private static final int MAX_TRADES = 1000;

    public void recordTrade(String itemId, BigDecimal price, int quantity, UUID buyer, UUID seller) { recordTrade(itemId, null, price, quantity, buyer, seller); }
    public void recordTrade(String itemId, String commodityType, BigDecimal price, int quantity, UUID buyer, UUID seller) {
        trades.add(new TradeSnapshot(itemId, commodityType, price.toPlainString(), quantity, buyer, seller, System.currentTimeMillis()));
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
        return target.getDataStorage().computeIfAbsent(TYPE);
    }

    public static EconomyTradeData load(CompoundTag tag) {
        EconomyTradeData data = new EconomyTradeData();
        ListTag list = tag.getListOrEmpty("Trades");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompoundOrEmpty(i);
            try {
                data.trades.add(new TradeSnapshot(t.getStringOr("ItemId", ""), t.contains("CommodityType") ? t.getStringOr("CommodityType", "") : null,
                        t.getStringOr("Price", ""), t.getIntOr("Quantity", 0), com.nstut.economy.util.NbtCompat.getUuid(t, "Buyer"),
                        com.nstut.economy.util.NbtCompat.getUuid(t, "Seller"), t.getLongOr("Timestamp", 0L)));
            } catch (Exception ignored) { }
        }
        return data;
    }

    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (TradeSnapshot t : trades) {
            CompoundTag tTag = new CompoundTag(); tTag.putString("ItemId", t.itemId); tTag.putString("Price", t.price); tTag.putInt("Quantity", t.quantity);
            if (t.commodityType != null) tTag.putString("CommodityType", t.commodityType);
            com.nstut.economy.util.NbtCompat.putUuid(tTag, "Buyer", t.buyer); com.nstut.economy.util.NbtCompat.putUuid(tTag, "Seller", t.seller);
            tTag.putLong("Timestamp", t.timestamp); list.add(tTag);
        }
        tag.put("Trades", list); return tag;
    }
}
