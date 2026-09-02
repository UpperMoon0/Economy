package com.nstut.economy.data;

import com.nstut.economy.api.EconomyEvents;
import com.nstut.economy.api.EconomyId;
import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.MarketEvents;
import com.nstut.economy.api.TradeView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TradeLedger {
    private static EconomyTradeData data;
    private TradeLedger() { }

    public static void setTradeData(EconomyTradeData tradeData) { data = tradeData; }
    public static void clearTradeData() { data = null; }

    public static void recordTrade(String itemId, BigDecimal price, int quantity, UUID buyer, UUID seller) {
        recordTrade(itemId, null, price, quantity, buyer, seller);
    }

    public static void recordTrade(String itemId, String commodityType, BigDecimal price,
                                   int quantity, UUID buyer, UUID seller) {
        EconomyTradeData current = data;
        if (current == null) return;
        current.recordTrade(itemId, commodityType, price, quantity, buyer, seller);
        TradeView view = new TradeView(EconomyId.parse(itemId), typeId(commodityType), price, quantity,
                buyer, seller, Instant.now());
        EconomyEvents.post(new MarketEvents.TradeCompleted(view));
    }

    public static List<EconomyTradeData.TradeSnapshot> getRecentTrades(String itemId, int limit) {
        EconomyTradeData current = data;
        if (current == null || limit <= 0) return List.of();
        return List.copyOf(current.getTradesForItem(itemId, limit));
    }

    public static List<EconomyTradeData.TradeSnapshot> getAllTrades() {
        EconomyTradeData current = data;
        return current == null ? List.of() : List.copyOf(current.getTrades());
    }

    private static EconomyId typeId(String stored) {
        if (stored == null || stored.isBlank() || "ITEM".equalsIgnoreCase(stored)) return ICommodity.ITEM_TYPE;
        if ("FLUID".equalsIgnoreCase(stored)) return ICommodity.FLUID_TYPE;
        if ("ENERGY".equalsIgnoreCase(stored)) return ICommodity.ENERGY_TYPE;
        return EconomyId.parse(stored);
    }
}
