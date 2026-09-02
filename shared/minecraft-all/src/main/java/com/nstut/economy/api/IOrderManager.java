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

    /** Server orders use the same concrete order object and return null only when domain validation rejects creation. */
    IOrder createServerBuyOrder(ICommodity commodity, int quantity, BigDecimal pricePerUnit);
    IOrder createServerSellOrder(ICommodity commodity, int quantity, BigDecimal pricePerUnit);

    Optional<? extends IOrder> getOrder(UUID orderId);
    List<? extends IOrder> getAllOrders();
    List<? extends IOrder> getOrders(ICommodity commodity);
    List<? extends IOrder> getPlayerOrders(UUID player);
    List<? extends IOrder> getBuyOrders(ICommodity commodity);
    List<? extends IOrder> getSellOrders(ICommodity commodity);

    boolean cancelOrder(UUID orderId, UUID requester);
    boolean editOrder(UUID orderId, UUID requester, int newQuantity, BigDecimal newPrice, boolean infinite);
}
