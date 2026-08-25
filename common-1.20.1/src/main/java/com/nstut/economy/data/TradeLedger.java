package com.nstut.economy.data;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class TradeLedger {

    private static EconomyTradeData data;

    public static void setTradeData(EconomyTradeData tradeData) {
        data = tradeData;
    }

    public static void recordTrade(String itemId, BigDecimal price, int quantity,
                                    UUID buyer, UUID seller) {
        if (data != null) {
            data.recordTrade(itemId, price, quantity, buyer, seller);
        }
    }

    public static List<EconomyTradeData.TradeSnapshot> getRecentTrades(String itemId, int limit) {
        if (data == null) return Collections.emptyList();
        return data.getTradesForItem(itemId, limit);
    }

    public static List<EconomyTradeData.TradeSnapshot> getAllTrades() {
        if (data == null) return Collections.emptyList();
        return data.getTrades();
    }
}
