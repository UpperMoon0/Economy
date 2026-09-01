package com.nstut.economy.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Supported addon-facing order book service. World/storage details are resolved
 * by Economy from the currently bound server lifecycle.
 */
public interface IOrderManager {
    OrderCreateResult createBuyOrder(UUID owner, ICommodity commodity, int quantity, BigDecimal pricePerUnit);
    OrderCreateResult createSellOrder(UUID owner, ICommodity commodity, int quantity, BigDecimal pricePerUnit);
    OrderCreateResult createServerBuyOrder(ICommodity commodity, int quantity, BigDecimal pricePerUnit);
    OrderCreateResult createServerSellOrder(ICommodity commodity, int quantity, BigDecimal pricePerUnit);

    Optional<IOrder> getOrder(UUID orderId);
    List<IOrder> getAllOrders();
    List<IOrder> getOrders(ICommodity commodity);
    List<IOrder> getPlayerOrders(UUID player);
    List<IOrder> getBuyOrders(ICommodity commodity);
    List<IOrder> getSellOrders(ICommodity commodity);

    boolean cancelOrder(UUID orderId, UUID requester);
    boolean editOrder(UUID orderId, UUID requester, int newQuantity, BigDecimal newPrice, boolean infinite);
}
