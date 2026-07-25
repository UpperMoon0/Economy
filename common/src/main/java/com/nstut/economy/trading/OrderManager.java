package com.nstut.economy.trading;

import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.IOrder;
import com.nstut.economy.data.EconomyOrderData;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class OrderManager {

    private final Map<UUID, Order> orders;
    private final Map<ICommodity, List<Order>> commodityIndex;
    private EconomyOrderData backingData;

    public OrderManager() {
        this.orders = new ConcurrentHashMap<>();
        this.commodityIndex = new ConcurrentHashMap<>();
    }

    public void setOrderData(EconomyOrderData data) {
        this.backingData = data;
    }

    public void loadFrom(EconomyOrderData data) {
        orders.clear();
        commodityIndex.clear();
        this.backingData = data;
        for (EconomyOrderData.OrderSnapshot snap : data.getOrders().values()) {
            try {
                Order order = Order.fromSnapshot(snap);
                if (order.isValid()) {
                    orders.put(order.getOrderId(), order);
                    commodityIndex.computeIfAbsent(order.getCommodity(), k -> new ArrayList<>()).add(order);
                }
            } catch (Exception e) {
            }
        }
    }

    public void saveAll() {
        if (backingData == null) return;
        backingData.clearAll();
        for (Order order : orders.values()) {
            if (order.isValid()) {
                backingData.putOrder(order.toSnapshot());
            }
        }
    }

    public Order createSellOrder(UUID owner, ICommodity commodity, int quantity,
                                  java.math.BigDecimal pricePerUnit) {
        return createSellOrder(owner, commodity, quantity, pricePerUnit, NonNullList.create(), null);
    }

    public Order createSellOrder(UUID owner, ICommodity commodity, int quantity,
                                  java.math.BigDecimal pricePerUnit, NonNullList<ItemStack> reservedItems) {
        return createSellOrder(owner, commodity, quantity, pricePerUnit, reservedItems, null);
    }

    public Order createSellOrder(UUID owner, ICommodity commodity, int quantity,
                                  java.math.BigDecimal pricePerUnit, NonNullList<ItemStack> reservedItems,
                                  net.minecraft.server.level.ServerLevel level) {
        Order order = new Order(owner, commodity, quantity, pricePerUnit, IOrder.OrderType.SELL, null, copyStacks(reservedItems));

        List<Order> matchingBuyOrders = getBuyOrders(commodity).stream()
                .filter(b -> b.getPricePerUnit().compareTo(pricePerUnit) >= 0 && !b.getOwner().equals(owner))
                .sorted(Comparator.comparing(Order::getPricePerUnit).reversed().thenComparing(Order::getCreatedAt))
                .collect(Collectors.toList());

        for (Order buyOrder : matchingBuyOrders) {
            if (order.getQuantity() <= 0) break;
            int matchQty = Math.min(order.getQuantity(), buyOrder.getQuantity());
            IOrder.TransactionResult result = order.executePartial(buyOrder.getOwner(), matchQty, level);
            if (result.success) {
                buyOrder.reduceQuantity(matchQty);
                if (backingData != null) {
                    if (buyOrder.getQuantity() == 0) backingData.removeOrder(buyOrder.getOrderId());
                    else backingData.putOrder(buyOrder.toSnapshot());
                }
            }
        }
        cleanupOrders();

        if (order.getQuantity() > 0) {
            registerOrder(order);
            return order;
        } else if (backingData != null) {
            backingData.removeOrder(order.getOrderId());
        }
        return null;
    }

    public Order createBuyOrder(UUID owner, ICommodity commodity, int quantity,
                                 java.math.BigDecimal pricePerUnit) {
        return createBuyOrder(owner, commodity, quantity, pricePerUnit, null);
    }

    public Order createBuyOrder(UUID owner, ICommodity commodity, int quantity,
                                 java.math.BigDecimal pricePerUnit, net.minecraft.server.level.ServerLevel level) {
        Order order = new Order(owner, commodity, quantity, pricePerUnit, IOrder.OrderType.BUY, null);

        List<Order> matchingSellOrders = getSellOrders(commodity).stream()
                .filter(s -> s.getPricePerUnit().compareTo(pricePerUnit) <= 0 && !s.getOwner().equals(owner))
                .sorted(Comparator.comparing(Order::getPricePerUnit).thenComparing(Order::getCreatedAt))
                .collect(Collectors.toList());

        for (Order sellOrder : matchingSellOrders) {
            if (order.getQuantity() <= 0) break;
            int matchQty = Math.min(order.getQuantity(), sellOrder.getQuantity());
            IOrder.TransactionResult result = sellOrder.executePartial(owner, matchQty, level);
            if (result.success) {
                order.reduceQuantity(matchQty);
                if (backingData != null) {
                    if (sellOrder.getQuantity() == 0) backingData.removeOrder(sellOrder.getOrderId());
                    else backingData.putOrder(sellOrder.toSnapshot());
                }
            }
        }
        cleanupOrders();

        if (order.getQuantity() > 0) {
            registerOrder(order);
            return order;
        } else if (backingData != null) {
            backingData.removeOrder(order.getOrderId());
        }
        return null;
    }

    private static NonNullList<ItemStack> copyStacks(NonNullList<ItemStack> original) {
        NonNullList<ItemStack> copy = NonNullList.create();
        for (ItemStack stack : original) {
            copy.add(stack.copy());
        }
        return copy;
    }

    public static final UUID SERVER_ID = new UUID(0, 0);

    public Order createServerBuyOrder(ICommodity commodity, int quantity,
                                       java.math.BigDecimal pricePerUnit) {
        Order order = new Order(SERVER_ID, commodity, quantity, pricePerUnit,
                                IOrder.OrderType.BUY, null);
        order.setServerOrder(true);
        registerOrder(order);
        return order;
    }

    public Order createServerSellOrder(ICommodity commodity, int quantity,
                                        java.math.BigDecimal pricePerUnit) {
        Order order = new Order(SERVER_ID, commodity, quantity, pricePerUnit,
                                IOrder.OrderType.SELL, null);
        order.setServerOrder(true);
        registerOrder(order);
        return order;
    }

    private void registerOrder(Order order) {
        orders.put(order.getOrderId(), order);
        commodityIndex.computeIfAbsent(order.getCommodity(), k -> new ArrayList<>()).add(order);
        if (backingData != null) {
            backingData.putOrder(order.toSnapshot());
        }
    }

    public Optional<Order> getOrder(UUID orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    public boolean cancelOrder(UUID orderId, UUID requester) {
        Order order = orders.get(orderId);
        if (order == null) {
            return false;
        }
        if (!order.getOwner().equals(requester)) {
            return false;
        }
        if (order.cancel()) {
            removeOrder(order);
            return true;
        }
        return false;
    }

    private void removeOrder(Order order) {
        orders.remove(order.getOrderId());
        List<Order> commodityOrders = commodityIndex.get(order.getCommodity());
        if (commodityOrders != null) {
            commodityOrders.remove(order);
        }
        if (backingData != null) {
            backingData.removeOrder(order.getOrderId());
        }
    }

    public List<Order> getSellOrders(ICommodity commodity) {
        return getOrdersByType(commodity, IOrder.OrderType.SELL).stream()
            .sorted(Comparator.comparing(Order::getPricePerUnit))
            .collect(Collectors.toList());
    }

    public List<Order> getBuyOrders(ICommodity commodity) {
        return getOrdersByType(commodity, IOrder.OrderType.BUY).stream()
            .sorted(Comparator.comparing(Order::getPricePerUnit).reversed())
            .collect(Collectors.toList());
    }

    public List<Order> getAllOrders(ICommodity commodity) {
        return commodityIndex.getOrDefault(commodity, Collections.emptyList()).stream()
            .filter(Order::isValid)
            .collect(Collectors.toList());
    }

    public List<Order> getAllOrders() {
        return orders.values().stream()
            .filter(Order::isValid)
            .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
            .collect(Collectors.toList());
    }

    private List<Order> getOrdersByType(ICommodity commodity, IOrder.OrderType type) {
        return commodityIndex.getOrDefault(commodity, Collections.emptyList()).stream()
            .filter(order -> order.isValid() && order.getType() == type)
            .collect(Collectors.toList());
    }

    public List<Order> getPlayerOrders(UUID player) {
        return orders.values().stream()
            .filter(order -> order.getOwner().equals(player) && order.isValid())
            .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
            .collect(Collectors.toList());
    }

    public Optional<Order> getPlayerOrderByIndex(UUID player, int index) {
        List<Order> playerOrders = getPlayerOrders(player);
        if (index < 0 || index >= playerOrders.size()) return Optional.empty();
        return Optional.of(playerOrders.get(index));
    }

    public Optional<Order> getGlobalOrderByIndex(int index) {
        List<Order> all = getAllOrders();
        if (index < 0 || index >= all.size()) return Optional.empty();
        return Optional.of(all.get(index));
    }

    public Optional<Order> getGlobalOrderByIndex(ICommodity commodity, int index) {
        List<Order> all = getAllOrders(commodity).stream()
            .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
            .collect(Collectors.toList());
        if (index < 0 || index >= all.size()) return Optional.empty();
        return Optional.of(all.get(index));
    }

    public void cleanupOrders() {
        List<Order> toRemove = orders.values().stream()
            .filter(order -> !order.isValid())
            .collect(Collectors.toList());
        for (Order order : toRemove) {
            removeOrder(order);
        }
    }

    public Optional<java.math.BigDecimal> getBestSellPrice(ICommodity commodity) {
        return getSellOrders(commodity).stream()
            .findFirst()
            .map(Order::getPricePerUnit);
    }

    public Optional<java.math.BigDecimal> getBestBuyPrice(ICommodity commodity) {
        return getBuyOrders(commodity).stream()
            .findFirst()
            .map(Order::getPricePerUnit);
    }

    public void matchAllPendingOrders(net.minecraft.server.level.ServerLevel level) {
        if (orders.isEmpty()) return;
        List<Order> allSell = orders.values().stream()
                .filter(o -> o.isValid() && o.getType() == IOrder.OrderType.SELL)
                .sorted(Comparator.comparing(Order::getPricePerUnit).thenComparing(Order::getCreatedAt))
                .collect(Collectors.toList());

        for (Order sellOrder : allSell) {
            if (!sellOrder.isValid()) continue;
            List<Order> matchingBuyOrders = getBuyOrders(sellOrder.getCommodity()).stream()
                    .filter(b -> b.isValid() && b.getPricePerUnit().compareTo(sellOrder.getPricePerUnit()) >= 0 && !b.getOwner().equals(sellOrder.getOwner()))
                    .sorted(Comparator.comparing(Order::getPricePerUnit).reversed().thenComparing(Order::getCreatedAt))
                    .collect(Collectors.toList());

            for (Order buyOrder : matchingBuyOrders) {
                if (!sellOrder.isValid()) break;
                int matchQty = Math.min(sellOrder.getQuantity(), buyOrder.getQuantity());
                if (matchQty <= 0) continue;

                IOrder.TransactionResult result = sellOrder.executePartial(buyOrder.getOwner(), matchQty, level);
                if (result.success) {
                    buyOrder.reduceQuantity(matchQty);
                    if (backingData != null) {
                        if (buyOrder.getQuantity() == 0) backingData.removeOrder(buyOrder.getOrderId());
                        else backingData.putOrder(buyOrder.toSnapshot());
                        if (sellOrder.getQuantity() == 0) backingData.removeOrder(sellOrder.getOrderId());
                        else backingData.putOrder(sellOrder.toSnapshot());
                    }
                }
            }
        }
        cleanupOrders();
    }

    public Map<ICommodity, List<Order>> getCommodityIndex() {
        return Collections.unmodifiableMap(commodityIndex);
    }
}
