package com.nstut.economy.api.internal;

import com.nstut.economy.api.CommodityKey;
import com.nstut.economy.api.EconomyId;
import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.IMarketDataService;
import com.nstut.economy.api.TradeView;
import com.nstut.economy.data.EconomyTradeData;
import com.nstut.economy.data.TradeLedger;
import com.nstut.economy.trading.OrderManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Internal adapter over the current order book and SavedData-backed trade ledger. */
public final class DefaultMarketDataService implements IMarketDataService {
    private final OrderManager orders;

    public DefaultMarketDataService(OrderManager orders) {
        this.orders = orders;
    }

    @Override
    public List<TradeView> recentTrades(int limit) {
        if (limit <= 0) return List.of();
        List<EconomyTradeData.TradeSnapshot> source = TradeLedger.getAllTrades();
        ArrayList<TradeView> result = new ArrayList<>();
        for (int i = source.size() - 1; i >= 0 && result.size() < limit; i--) {
            result.add(toView(source.get(i)));
        }
        return List.copyOf(result);
    }

    @Override
    public List<TradeView> recentTrades(CommodityKey commodity, int limit) {
        if (commodity == null || limit <= 0) return List.of();
        List<EconomyTradeData.TradeSnapshot> source = TradeLedger.getAllTrades();
        ArrayList<TradeView> result = new ArrayList<>();
        for (int i = source.size() - 1; i >= 0 && result.size() < limit; i--) {
            EconomyTradeData.TradeSnapshot trade = source.get(i);
            if (matches(trade, commodity)) result.add(toView(trade));
        }
        return List.copyOf(result);
    }

    @Override
    public Optional<BigDecimal> lastTradePrice(CommodityKey commodity) {
        List<TradeView> trades = recentTrades(commodity, 1);
        return trades.isEmpty() ? Optional.empty() : Optional.of(trades.get(0).pricePerUnit());
    }

    @Override
    public long tradedVolume(CommodityKey commodity) {
        if (commodity == null) return 0;
        long total = 0;
        for (EconomyTradeData.TradeSnapshot trade : TradeLedger.getAllTrades()) {
            if (matches(trade, commodity)) total += Math.max(0, trade.quantity);
        }
        return total;
    }

    @Override
    public int activeOrderCount(CommodityKey commodity) {
        if (commodity == null) return 0;
        int count = 0;
        for (var order : orders.getAllOrders()) {
            if (commodity.matches(order.getCommodity())) count++;
        }
        return count;
    }

    public static TradeView toView(EconomyTradeData.TradeSnapshot trade) {
        EconomyId type = typeId(trade.commodityType);
        return new TradeView(EconomyId.parse(trade.itemId), type, new BigDecimal(trade.price),
                trade.quantity, trade.buyer, trade.seller, Instant.ofEpochMilli(trade.timestamp));
    }

    private static boolean matches(EconomyTradeData.TradeSnapshot trade, CommodityKey commodity) {
        return commodity.commodityId().toString().equals(trade.itemId)
                && commodity.commodityTypeId().equals(typeId(trade.commodityType));
    }

    private static EconomyId typeId(String stored) {
        if (stored == null || stored.isBlank() || "ITEM".equalsIgnoreCase(stored)) return ICommodity.ITEM_TYPE;
        if ("FLUID".equalsIgnoreCase(stored)) return ICommodity.FLUID_TYPE;
        if ("ENERGY".equalsIgnoreCase(stored)) return ICommodity.ENERGY_TYPE;
        return EconomyId.parse(stored);
    }
}
