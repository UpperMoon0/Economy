package com.nstut.economy.trading;

import com.nstut.economy.api.EconomyApi;
import com.nstut.economy.api.EconomyEvents;
import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.IOrder;
import com.nstut.economy.api.IOrderManager;
import com.nstut.economy.api.MarketEvents;
import com.nstut.economy.api.StorageReservation;
import com.nstut.economy.blocks.VaultInventoryOps;
import com.nstut.economy.data.EconomyOrderData;
import com.nstut.Economy;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class OrderManager implements IOrderManager {

    private final Map<UUID, Order> orders;
    private final Map<ICommodity, List<Order>> commodityIndex;
    private final Map<UUID, EconomyOrderData.OrderSnapshot> quarantinedOrders;
    private EconomyOrderData backingData;

    public OrderManager() {
        this.orders = new ConcurrentHashMap<>();
        this.commodityIndex = new ConcurrentHashMap<>();
        this.quarantinedOrders = new ConcurrentHashMap<>();
    }

    public void setOrderData(EconomyOrderData data) {
        this.backingData = data;
    }

    public void loadFrom(EconomyOrderData data) {
        orders.clear();
        commodityIndex.clear();
        quarantinedOrders.clear();
        this.backingData = data;
        for (EconomyOrderData.OrderSnapshot snap : data.getOrders().values()) {
            try {
                Order order = Order.fromSnapshot(snap);
                if (order.isValid()) {
                    orders.put(order.getOrderId(), order);
                    commodityIndex.computeIfAbsent(order.getCommodity(), k -> new ArrayList<>()).add(order);
                } else if (snap.hasEscrow()) {
                    quarantineOrder(snap, "invalid persisted order (likely expired while offline)");
                }
            } catch (Exception e) {
                Economy.LOGGER.error("Failed to load persisted order {} for item {}; escrow snapshot preserved in world data",
                        snap.orderId, snap.itemId, e);
                quarantineOrder(snap, "order failed to deserialize");
            }
        }
    }

    /**
     * Preserves a snapshot of an order that can no longer be active but still
     * holds escrowed goods. Quarantined snapshots are re-persisted on every
     * save so escrowed items/fluids are never silently destroyed; an admin
     * can resolve them manually from the saved data.
     */
    private void quarantineOrder(EconomyOrderData.OrderSnapshot snap, String reason) {
        if (snap == null || !snap.hasEscrow()) {
            return;
        }
        if (quarantinedOrders.put(snap.orderId, snap) == null) {
            Economy.LOGGER.error(
                    "Quarantined {} ({}): {}. Escrow preserved - {} item stack(s), {} mB",
                    reason, snap.orderId, snap.itemId, snap.reservedItems.size(),
                    snap.reservedFluids.stream().mapToInt(f -> f.getAmount()).sum());
        }
        if (backingData != null) {
            backingData.putOrder(snap);
        }
    }

    public void saveAll() {
        if (backingData == null) return;
        backingData.clearAll();
        for (Order order : orders.values()) {
            if (order.isValid()) {
                backingData.putOrder(order.toSnapshot());
            } else {
                quarantineOrder(order.toSnapshot(), "order no longer valid at save time");
            }
        }
        for (EconomyOrderData.OrderSnapshot snap : quarantinedOrders.values()) {
            backingData.putOrder(snap);
        }
    }

    /**
     * Exact rejection for an invalid new order, or null when the order passes
     * domain validation. The network layer validates earlier with the same
     * rules, so a modified client is never the only line of defense.
     */
    private static CreateOrderResult rejection(ICommodity commodity, int quantity,
                                               java.math.BigDecimal pricePerUnit) {
        com.nstut.economy.util.OrderInputValidator.Rejection invalid =
                com.nstut.economy.util.OrderInputValidator.validateNewOrder(
                        quantity, pricePerUnit, commodity instanceof FluidCommodity);
        if (invalid == null) {
            return null;
        }
        return CreateOrderResult.rejected(quantity, invalid.key(), invalid.args());
    }

    public CreateOrderResult createSellOrder(UUID owner, ICommodity commodity, int quantity,
                                   java.math.BigDecimal pricePerUnit) {
        return createSellOrder(owner, commodity, quantity, pricePerUnit, NonNullList.create(), EconomyApi.serverLevel().orElse(null));
    }

    public CreateOrderResult createSellOrder(UUID owner, ICommodity commodity, int quantity,
                                   java.math.BigDecimal pricePerUnit, NonNullList<ItemStack> reservedItems) {
        return createSellOrder(owner, commodity, quantity, pricePerUnit, reservedItems, null);
    }

    public CreateOrderResult createSellOrder(UUID owner, ICommodity commodity, int quantity,
                                   java.math.BigDecimal pricePerUnit, NonNullList<ItemStack> reservedItems,
                                   net.minecraft.server.level.ServerLevel level) {
        return createSellOrder(owner, commodity, quantity, pricePerUnit, reservedItems, new ArrayList<>(), level);
    }

    public CreateOrderResult createSellOrder(UUID owner, ICommodity commodity, int quantity,
                                   java.math.BigDecimal pricePerUnit, NonNullList<ItemStack> reservedItems,
                                   List<com.nstut.economy.trading.EconomyFluidStack> reservedFluids,
                                   net.minecraft.server.level.ServerLevel level) {
        CreateOrderResult invalid = rejection(commodity, quantity, pricePerUnit);
        if (invalid != null) return invalid;
        MarketEvents.OrderCreatePre pre = EconomyEvents.post(new MarketEvents.OrderCreatePre(owner, commodity, IOrder.OrderType.SELL, quantity, pricePerUnit));
        if (pre.isCancelled()) {
            if (level != null) {
                if (reservedItems != null && !reservedItems.isEmpty()) com.nstut.economy.blocks.VaultManager.insertItemStacksToVaults(level, owner, copyStacks(reservedItems));
                if (reservedFluids != null) for (var fs : reservedFluids) if (fs != null && !fs.isEmpty()) com.nstut.economy.blocks.TankManager.restoreFluidToTanks(level, owner, fs.copy());
            }
            return CreateOrderResult.rejected(quantity, "ui.economy.error.order_cancelled", List.of());
        }
        StorageReservation external = null;
        if (level != null && (reservedItems == null || reservedItems.isEmpty()) && (reservedFluids == null || reservedFluids.isEmpty())) {
            external = EconomyApi.storage().reserve(level, owner, commodity, quantity).orElse(null);
            if (external == null) return CreateOrderResult.rejected(quantity, "ui.economy.error.insufficient_storage", List.of());
        }
        Order order = new Order(owner, commodity, quantity, quantity, pricePerUnit, IOrder.OrderType.SELL, null,
                copyStacks(reservedItems), copyFluidStacks(reservedFluids), false);
        if (external != null) order.setExternalReservation(external);

        List<Order> matchingBuyOrders = getBuyOrders(commodity).stream()
                .filter(b -> b.getPricePerUnit().compareTo(pricePerUnit) >= 0 && !b.getOwner().equals(owner))
                .sorted(Comparator.comparing(Order::getPricePerUnit).reversed().thenComparing(Order::getCreatedAt))
                .collect(Collectors.toList());

        int filled = 0;
        for (Order buyOrder : matchingBuyOrders) {
            if (order.getQuantity() <= 0) break;
            int matchQty = Math.min(order.getQuantity(), buyOrder.getQuantity());
                IOrder.TransactionResult result = order.executePartial(buyOrder.getOwner(), matchQty, level);
                if (result.success) {
                    filled += result.quantityTransferred;
                    buyOrder.reduceQuantity(result.quantityTransferred);
                if (backingData != null) {
                    if (buyOrder.getQuantity() == 0) backingData.removeOrder(buyOrder.getOrderId());
                    else backingData.putOrder(buyOrder.toSnapshot());
                }
            }
        }
        cleanupOrders();

        if (order.getQuantity() > 0) {
            registerOrder(order);
            EconomyEvents.post(new MarketEvents.OrderCreated(order, quantity, filled));
            return CreateOrderResult.posted(order, quantity, filled);
        } else if (filled > 0) {
            EconomyEvents.post(new MarketEvents.OrderCreated(order, quantity, filled));
            return CreateOrderResult.filled(quantity, filled);
        } else if (backingData != null) {
            backingData.removeOrder(order.getOrderId());
        }
        return CreateOrderResult.rejected(quantity, "ui.economy.error.order_rejected", List.of());
    }

    private static List<com.nstut.economy.trading.EconomyFluidStack> copyFluidStacks(
            List<com.nstut.economy.trading.EconomyFluidStack> stacks) {
        List<com.nstut.economy.trading.EconomyFluidStack> copy = new ArrayList<>();
        if (stacks != null) {
            for (com.nstut.economy.trading.EconomyFluidStack stack : stacks) {
                if (stack != null && !stack.isEmpty()) {
                    copy.add(stack.copy());
                }
            }
        }
        return copy;
    }

    public CreateOrderResult createBuyOrder(UUID owner, ICommodity commodity, int quantity,
                                 java.math.BigDecimal pricePerUnit) {
        return createBuyOrder(owner, commodity, quantity, pricePerUnit, false, EconomyApi.serverLevel().orElse(null));
    }

    public CreateOrderResult createBuyOrder(UUID owner, ICommodity commodity, int quantity,
                                 java.math.BigDecimal pricePerUnit, net.minecraft.server.level.ServerLevel level) {
        return createBuyOrder(owner, commodity, quantity, pricePerUnit, false, level);
    }

    public CreateOrderResult createBuyOrder(UUID owner, ICommodity commodity, int quantity,
                                 java.math.BigDecimal pricePerUnit, boolean isInfinite, net.minecraft.server.level.ServerLevel level) {
        CreateOrderResult invalid = rejection(commodity, quantity, pricePerUnit);
        if (invalid != null) return invalid;
        MarketEvents.OrderCreatePre pre = EconomyEvents.post(new MarketEvents.OrderCreatePre(owner, commodity, IOrder.OrderType.BUY, quantity, pricePerUnit));
        if (pre.isCancelled()) return CreateOrderResult.rejected(quantity, "ui.economy.error.order_cancelled", List.of());
        Order order = new Order(owner, commodity, quantity, quantity, pricePerUnit, IOrder.OrderType.BUY, null, NonNullList.create(), isInfinite);

        List<Order> matchingSellOrders = getSellOrders(commodity).stream()
                .filter(s -> s.getPricePerUnit().compareTo(pricePerUnit) <= 0 && !s.getOwner().equals(owner))
                .sorted(Comparator.comparing(Order::getPricePerUnit).thenComparing(Order::getCreatedAt))
                .collect(Collectors.toList());

        int filled = 0;
        for (Order sellOrder : matchingSellOrders) {
            if (!order.isInfinite() && order.getQuantity() <= 0) break;
            int matchQty = order.isInfinite() ? sellOrder.getQuantity() : Math.min(order.getQuantity(), sellOrder.getQuantity());
            IOrder.TransactionResult result = sellOrder.executePartial(owner, matchQty, level);
            if (result.success) {
                filled += result.quantityTransferred;
                if (!order.isInfinite()) {
                    order.reduceQuantity(result.quantityTransferred);
                }
                if (backingData != null) {
                    if (sellOrder.getQuantity() == 0) backingData.removeOrder(sellOrder.getOrderId());
                    else backingData.putOrder(sellOrder.toSnapshot());
                }
            }
        }
        cleanupOrders();

        if (order.isValid()) {
            registerOrder(order);
            EconomyEvents.post(new MarketEvents.OrderCreated(order, quantity, filled));
            return CreateOrderResult.posted(order, quantity, filled);
        } else if (filled > 0) {
            EconomyEvents.post(new MarketEvents.OrderCreated(order, quantity, filled));
            return CreateOrderResult.filled(quantity, filled);
        } else if (backingData != null) {
            backingData.removeOrder(order.getOrderId());
        }
        return CreateOrderResult.rejected(quantity, "ui.economy.error.order_rejected", List.of());
    }

    @Override
    public boolean editOrder(UUID orderId, UUID requester, int newQuantity, java.math.BigDecimal newPrice, boolean isInfinite) {
        return editOrder(orderId, requester, newQuantity, newPrice, isInfinite, EconomyApi.serverLevel().orElse(null));
    }

    public boolean editOrder(UUID orderId, UUID requester, int newQuantity, java.math.BigDecimal newPrice, boolean isInfinite, net.minecraft.server.level.ServerLevel level) {
        Order order = orders.get(orderId);
        if (order == null || !order.getOwner().equals(requester) || !order.isValid()) {
            return false;
        }
        if (!com.nstut.economy.util.OrderInputValidator.isValidNewOrder(
                Math.max(1, newQuantity), newPrice, order.getCommodity() instanceof FluidCommodity)) {
            return false;
        }
        boolean requiresQuantity = order.getType() == IOrder.OrderType.SELL || !isInfinite;
        if (requiresQuantity) {
            if (newQuantity <= 0 || newQuantity > com.nstut.economy.config.EconomyConfig.getInstance().getMaxOrderQuantity()) {
                return false;
            }
        } else if (newQuantity < 0 || newQuantity > com.nstut.economy.config.EconomyConfig.getInstance().getMaxOrderQuantity()) {
            return false;
        }

        if (order.getType() == IOrder.OrderType.SELL) {
            if (order.getCommodity() instanceof ItemCommodity ic && level != null) {
                net.minecraft.world.item.Item item = ic.getItem();
                int currentQty = order.getQuantity();
                if (newQuantity > currentQty) {
                    int needed = newQuantity - currentQty;
                    if (needed > com.nstut.economy.config.EconomyConfig.getInstance().getMaxOrderQuantity()) {
                        return false;
                    }
                    int available = com.nstut.economy.blocks.VaultManager.countItemInVaults(level, requester, item);
                    if (available < needed) {
                        return false;
                    }
                    NonNullList<ItemStack> extracted = NonNullList.create();
                    if (!com.nstut.economy.blocks.VaultManager.extractItemFromVaults(level, requester, item, needed, extracted)) {
                        if (!extracted.isEmpty()) {
                            com.nstut.economy.blocks.VaultManager.insertItemStacksToVaults(level, requester, extracted);
                        }
                        return false;
                    }
                    order.getReservedItems().addAll(extracted);
                } else if (newQuantity < currentQty) {
                    int excess = currentQty - newQuantity;
                    if (order.getEscrowedItemCount() < excess) {
                        return false;
                    }
                    if (!returnItemsToVaults(level, requester, order, excess)) {
                        return false;
                    }
                }
            } else if (order.getCommodity() instanceof FluidCommodity fc && level != null) {
                net.minecraft.world.level.material.Fluid fluid = fc.getFluid();
                int currentQty = order.getQuantity();
                if (newQuantity > currentQty) {
                    int needed = newQuantity - currentQty;
                    if (needed > com.nstut.economy.config.EconomyConfig.getInstance().getMaxOrderQuantity()) {
                        return false;
                    }
                    int available = com.nstut.economy.blocks.TankManager.countFluidInTanks(level, requester, fluid);
                    if (available < needed) {
                        return false;
                    }
                    java.util.List<com.nstut.economy.trading.EconomyFluidStack> drained = new java.util.ArrayList<>();
                    int drainedAmount = com.nstut.economy.blocks.TankManager.extractFluidFromTanks(level, requester, fluid, needed, drained);
                    if (drainedAmount < needed) {
                        for (var fs : drained) {
                            com.nstut.economy.blocks.TankManager.restoreFluidToTanks(level, requester, fs);
                        }
                        return false;
                    }
                    order.getReservedFluids().addAll(drained);
                } else if (newQuantity < currentQty) {
                    int excess = currentQty - newQuantity;
                    if (!returnFluidToTanks(level, requester, order, excess)) {
                        return false;
                    }
                }
            }
            order.setQuantity(newQuantity);
            order.setPricePerUnit(newPrice);
            order.setInfinite(false);
        } else {
            order.setPricePerUnit(newPrice);
            order.setInfinite(isInfinite);
            if (!isInfinite) {
                order.setQuantity(newQuantity);
                order.setInitialQuantity(Math.max(newQuantity, order.getInitialQuantity()));
            } else {
                order.setQuantity(newQuantity > 0 ? newQuantity : 1);
            }
        }

        if (backingData != null) {
            backingData.putOrder(order.toSnapshot());
        }
        EconomyEvents.post(new MarketEvents.OrderEdited(order));

        matchAllPendingOrders(level);
        return true;
    }

    /**
     * Returns {@code qty} units of escrowed items to the player's vaults
     * transactionally: copies are simulated and committed first, and escrow is
     * only shrunk once the full amount is verifiably back in storage.
     */
    private static boolean returnItemsToVaults(net.minecraft.server.level.ServerLevel level, UUID requester,
                                               Order order, int qty) {
        NonNullList<ItemStack> returnItems = NonNullList.create();
        int countToReturn = qty;
        for (ItemStack stack : order.getReservedItems()) {
            if (countToReturn <= 0) break;
            if (stack == null || stack.isEmpty()) continue;
            int take = Math.min(countToReturn, stack.getCount());
            ItemStack part = stack.copy();
            part.setCount(take);
            returnItems.add(part);
            countToReturn -= take;
        }
        if (countToReturn > 0 || returnItems.isEmpty()) {
            return false;
        }
        NonNullList<ItemStack> leftover = com.nstut.economy.blocks.VaultManager.simulateInsertItemStacksToVaults(level, requester, returnItems);
        if (!leftover.isEmpty()) {
            return false;
        }
        leftover = com.nstut.economy.blocks.VaultManager.insertItemStacksToVaults(level, requester, returnItems);
        if (!leftover.isEmpty()) {
            Economy.LOGGER.error("Vault insertion diverged from simulation while editing order {}; escrow left untouched", orderIdSafe(order));
            return false;
        }
        order.consumeEscrow(qty);
        return true;
    }

    private static boolean returnFluidToTanks(net.minecraft.server.level.ServerLevel level, UUID requester,
                                              Order order, int qty) {
        java.util.List<com.nstut.economy.trading.EconomyFluidStack> parts = new java.util.ArrayList<>();
        int toTake = qty;
        for (com.nstut.economy.trading.EconomyFluidStack fs : order.getReservedFluids()) {
            if (toTake <= 0) break;
            if (fs == null || fs.isEmpty()) continue;
            com.nstut.economy.trading.EconomyFluidStack part = fs.copy();
            part.setAmount(Math.min(toTake, fs.getAmount()));
            parts.add(part);
            toTake -= part.getAmount();
        }
        if (toTake > 0 || parts.isEmpty()) {
            return false;
        }
        com.nstut.economy.trading.EconomyFluidStack merged = com.nstut.economy.blocks.TankManager.mergeFluids(parts);
        if (com.nstut.economy.blocks.TankManager.simulateInsertFluidToTanks(level, requester, merged) < qty) {
            return false;
        }
        int restored = 0;
        for (com.nstut.economy.trading.EconomyFluidStack part : parts) {
            restored += com.nstut.economy.blocks.TankManager.restoreFluidToTanks(level, requester, part);
        }
        if (restored < qty) {
            Economy.LOGGER.error("Tank restoration diverged from simulation while editing order {}; escrow left untouched", orderIdSafe(order));
            return false;
        }
        order.consumeEscrow(qty);
        return true;
    }

    private static UUID orderIdSafe(Order order) {
        return order.getOrderId();
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
        if (!com.nstut.economy.util.OrderInputValidator.isValidNewOrder(
                quantity, pricePerUnit, commodity instanceof FluidCommodity)) return null;
        if (EconomyEvents.post(new MarketEvents.OrderCreatePre(SERVER_ID, commodity, IOrder.OrderType.BUY, quantity, pricePerUnit)).isCancelled()) return null;
        Order order = new Order(SERVER_ID, commodity, quantity, pricePerUnit,
                                IOrder.OrderType.BUY, null);
        order.setServerOrder(true);
        registerOrder(order);
        EconomyEvents.post(new MarketEvents.OrderCreated(order, quantity, 0));
        return order;
    }

    public Order createServerSellOrder(ICommodity commodity, int quantity,
                                        java.math.BigDecimal pricePerUnit) {
        if (!com.nstut.economy.util.OrderInputValidator.isValidNewOrder(
                quantity, pricePerUnit, commodity instanceof FluidCommodity)) return null;
        if (EconomyEvents.post(new MarketEvents.OrderCreatePre(SERVER_ID, commodity, IOrder.OrderType.SELL, quantity, pricePerUnit)).isCancelled()) return null;
        Order order = new Order(SERVER_ID, commodity, quantity, pricePerUnit,
                                IOrder.OrderType.SELL, null);
        order.setServerOrder(true);
        registerOrder(order);
        EconomyEvents.post(new MarketEvents.OrderCreated(order, quantity, 0));
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
        return cancelOrder(orderId, requester, null);
    }

    /**
     * Cancels an order after transactionally returning any escrowed goods.
     * Restoration is attempted with copies first; the order (and its escrow)
     * is only discarded once every unit is verifiably back in the player's
     * storage. Without a level, orders holding escrow are refused rather than
     * destroyed.
     */
    public boolean cancelOrder(UUID orderId, UUID requester, net.minecraft.server.level.ServerLevel level) {
        Order order = orders.get(orderId);
        if (order == null) {
            return false;
        }
        if (!order.getOwner().equals(requester)) {
            return false;
        }
        // Verify the order is actually cancellable before touching escrow;
        // restoring goods for a failed cancel would duplicate them.
        if (!order.canCancel()) {
            return false;
        }

        if (order.getType() == IOrder.OrderType.SELL && !order.isServerOrder() && order.getExternalReservation() != null) {
            if (level == null) return false;
            var provider = EconomyApi.storage().provider(order.getExternalReservation().providerId()).orElse(null);
            if (provider == null || !provider.release(level, order.getExternalReservation())) return false;
            order.setExternalReservation(null);
        }

        if (order.getType() == IOrder.OrderType.SELL && !order.isServerOrder()
                && (!order.getReservedItems().isEmpty() || !order.getReservedFluids().isEmpty())) {
            if (level == null) {
                Economy.LOGGER.warn("Refusing to cancel order {} without a world to restore escrow into", orderId);
                return false;
            }
            if (!order.getReservedItems().isEmpty()) {
                NonNullList<ItemStack> copies = NonNullList.create();
                for (ItemStack stack : order.getReservedItems()) {
                    if (stack != null && !stack.isEmpty()) copies.add(stack.copy());
                }
                NonNullList<ItemStack> leftover = com.nstut.economy.blocks.VaultManager.simulateInsertItemStacksToVaults(level, requester, copies);
                if (!leftover.isEmpty()) {
                    return false;
                }
                leftover = com.nstut.economy.blocks.VaultManager.insertItemStacksToVaults(level, requester, copies);
                if (!leftover.isEmpty()) {
                    Economy.LOGGER.error("Vault restoration diverged from simulation while cancelling order {}; order kept intact", orderId);
                    return false;
                }
            }
            if (!order.getReservedFluids().isEmpty()) {
                int escrowed = 0;
                for (com.nstut.economy.trading.EconomyFluidStack fs : order.getReservedFluids()) {
                    escrowed += fs.getAmount();
                }
                com.nstut.economy.trading.EconomyFluidStack merged = com.nstut.economy.blocks.TankManager.mergeFluids(order.getReservedFluids());
                if (com.nstut.economy.blocks.TankManager.simulateInsertFluidToTanks(level, requester, merged) < escrowed) {
                    return false;
                }
                int restored = 0;
                for (com.nstut.economy.trading.EconomyFluidStack fs : new ArrayList<>(order.getReservedFluids())) {
                    restored += com.nstut.economy.blocks.TankManager.restoreFluidToTanks(level, requester, fs);
                }
                if (restored < escrowed) {
                    Economy.LOGGER.error("Tank restoration diverged from simulation while cancelling order {}; order kept intact", orderId);
                    return false;
                }
            }
        }

        if (order.cancel()) {
            removeOrder(order);
            EconomyEvents.post(new MarketEvents.OrderCancelled(orderId, requester));
            return true;
        }
        Economy.LOGGER.error("Order {} passed cancellability check but cancel() failed; keeping order intact", orderId);
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

    @Override
    public List<Order> getOrders(ICommodity commodity) {
        return getAllOrders(commodity);
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
            boolean holdsEscrow = !order.getReservedItems().isEmpty() || !order.getReservedFluids().isEmpty() || order.getExternalReservation() != null;
            removeOrder(order);
            if (holdsEscrow) {
                quarantineOrder(order.toSnapshot(), "invalid order still held escrow");
            }
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
                int matchQty = buyOrder.isInfinite() ? sellOrder.getQuantity() : Math.min(sellOrder.getQuantity(), buyOrder.getQuantity());
                if (matchQty <= 0) continue;

                IOrder.TransactionResult result = sellOrder.executePartial(buyOrder.getOwner(), matchQty, level);
                if (result.success) {
                    if (!buyOrder.isInfinite()) {
                        buyOrder.reduceQuantity(result.quantityTransferred);
                    }
                    if (backingData != null) {
                        if (!buyOrder.isInfinite() && buyOrder.getQuantity() == 0) backingData.removeOrder(buyOrder.getOrderId());
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

