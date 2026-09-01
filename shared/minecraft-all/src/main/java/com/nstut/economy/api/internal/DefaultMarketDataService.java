package com.nstut.economy.api.internal;

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
    public List<TradeView> recentTrades(EconomyId commodityId, int limit) {
        if (limit <= 0) return List.of();
        ArrayList<TradeView> result = new ArrayList<>();
        for (EconomyTradeData.TradeSnapshot trade : TradeLedger.getRecentTrades(commodityId.toString(), limit)) {
            result.add(toView(trade));
        }
        return List.copyOf(result);
    }

    @Override
    public Optional<BigDecimal> lastTradePrice(EconomyId commodityId) {
        List<TradeView> trades = recentTrades(commodityId, 1);
        return trades.isEmpty() ? Optional.empty() : Optional.of(trades.get(0).pricePerUnit());
    }

    @Override
    public long tradedVolume(EconomyId commodityId) {
        long total = 0;
        for (EconomyTradeData.TradeSnapshot trade : TradeLedger.getAllTrades()) {
            if (commodityId.toString().equals(trade.itemId)) total += Math.max(0, trade.quantity);
        }
        return total;
    }

    @Override
    public int activeOrderCount(EconomyId commodityId) {
        int count = 0;
        for (var order : orders.getAllOrders()) {
            if (commodityId.equals(order.getCommodity().getId())) count++;
        }
        return count;
    }

    public static TradeView toView(EconomyTradeData.TradeSnapshot trade) {
        EconomyId type = typeId(trade.commodityType);
        return new TradeView(EconomyId.parse(trade.itemId), type, new BigDecimal(trade.price),
                trade.quantity, trade.buyer, trade.seller, Instant.ofEpochMilli(trade.timestamp));
    }

    private static EconomyId typeId(String stored) {
        if (stored == null || stored.isBlank() || "ITEM".equalsIgnoreCase(stored)) return ICommodity.ITEM_TYPE;
        if ("FLUID".equalsIgnoreCase(stored)) return ICommodity.FLUID_TYPE;
        if ("ENERGY".equalsIgnoreCase(stored)) return ICommodity.ENERGY_TYPE;
        return EconomyId.parse(stored);
    }
}
