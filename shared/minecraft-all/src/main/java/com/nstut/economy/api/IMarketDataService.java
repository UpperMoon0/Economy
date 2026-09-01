package com.nstut.economy.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Read-only market analytics surface for addons, dashboards, quests, and pricing integrations. */
public interface IMarketDataService {
    List<TradeView> recentTrades(int limit);
    List<TradeView> recentTrades(EconomyId commodityId, int limit);
    Optional<BigDecimal> lastTradePrice(EconomyId commodityId);
    long tradedVolume(EconomyId commodityId);
    int activeOrderCount(EconomyId commodityId);
}
