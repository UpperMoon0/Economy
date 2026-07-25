package com.nstut.forge.network;

import com.nstut.Economy;
import com.nstut.economy.api.IAccountManager;
import com.nstut.economy.api.IOrder;
import com.nstut.economy.blocks.VaultManager;
import com.nstut.economy.config.EconomyConfig;
import com.nstut.economy.data.EconomyTradeData;
import com.nstut.economy.data.TradeLedger;
import com.nstut.economy.trading.ItemCommodity;
import com.nstut.economy.trading.Order;
import com.nstut.economy.trading.OrderManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class MarketNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Economy.MOD_ID, "market"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int packetId = 0;

    public static void init() {
        CHANNEL.registerMessage(packetId++, SyncItemListPacket.class, SyncItemListPacket::encode, SyncItemListPacket::decode, SyncItemListPacket::handle);
        CHANNEL.registerMessage(packetId++, RequestItemDetailPacket.class, RequestItemDetailPacket::encode, RequestItemDetailPacket::decode, RequestItemDetailPacket::handle);
        CHANNEL.registerMessage(packetId++, SyncItemDetailPacket.class, SyncItemDetailPacket::encode, SyncItemDetailPacket::decode, SyncItemDetailPacket::handle);
        CHANNEL.registerMessage(packetId++, CreateOrderPacket.class, CreateOrderPacket::encode, CreateOrderPacket::decode, CreateOrderPacket::handle);
        CHANNEL.registerMessage(packetId++, AcceptOrderPacket.class, AcceptOrderPacket::encode, AcceptOrderPacket::decode, AcceptOrderPacket::handle);
        CHANNEL.registerMessage(packetId++, CancelOrderPacket.class, CancelOrderPacket::encode, CancelOrderPacket::decode, CancelOrderPacket::handle);
        CHANNEL.registerMessage(packetId++, RequestRefreshPacket.class, RequestRefreshPacket::encode, RequestRefreshPacket::decode, RequestRefreshPacket::handle);
        CHANNEL.registerMessage(packetId++, RequestOrderHistoryPacket.class, RequestOrderHistoryPacket::encode, RequestOrderHistoryPacket::decode, RequestOrderHistoryPacket::handle);
        CHANNEL.registerMessage(packetId++, SyncOrderHistoryPacket.class, SyncOrderHistoryPacket::encode, SyncOrderHistoryPacket::decode, SyncOrderHistoryPacket::handle);
    }

    public static class ItemCardData {
        public final String itemId;
        public final String displayName;
        public final String globalPrice;
        public final int offerCount;

        public ItemCardData(String itemId, String displayName, String globalPrice, int offerCount) {
            this.itemId = itemId; this.displayName = displayName; this.globalPrice = globalPrice; this.offerCount = offerCount;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(itemId);
            buf.writeUtf(displayName);
            buf.writeUtf(globalPrice);
            buf.writeInt(offerCount);
        }

        public static ItemCardData read(FriendlyByteBuf buf) {
            return new ItemCardData(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readInt());
        }
    }

    public static class OrderEntry {
        public final UUID orderId;
        public final UUID ownerId;
        public final String sellerName;
        public final String price;
        public final int quantity;
        public final boolean isPlayerOwned;
        public final boolean isServerOrder;

        public OrderEntry(UUID orderId, UUID ownerId, String sellerName, String price, int quantity, boolean isPlayerOwned, boolean isServerOrder) {
            this.orderId = orderId; this.ownerId = ownerId; this.sellerName = sellerName; this.price = price; this.quantity = quantity; this.isPlayerOwned = isPlayerOwned; this.isServerOrder = isServerOrder;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(orderId);
            buf.writeUUID(ownerId);
            buf.writeUtf(sellerName);
            buf.writeUtf(price);
            buf.writeInt(quantity);
            buf.writeBoolean(isPlayerOwned);
            buf.writeBoolean(isServerOrder);
        }

        public static OrderEntry read(FriendlyByteBuf buf) {
            return new OrderEntry(buf.readUUID(), buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readBoolean(), buf.readBoolean());
        }
    }

    public static class ChartPoint {
        public final int price;
        public final int quantity;

        public ChartPoint(int price, int quantity) {
            this.price = price; this.quantity = quantity;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeInt(price);
            buf.writeInt(quantity);
        }

        public static ChartPoint read(FriendlyByteBuf buf) {
            return new ChartPoint(buf.readInt(), buf.readInt());
        }
    }

    public static class SyncItemListPacket {
        public final String balance;
        public final int vaultCount;
        public final List<ItemCardData> cards;

        public SyncItemListPacket(String balance, int vaultCount, List<ItemCardData> cards) {
            this.balance = balance; this.vaultCount = vaultCount; this.cards = cards;
        }

        public static void encode(SyncItemListPacket pkt, FriendlyByteBuf buf) {
            buf.writeUtf(pkt.balance);
            buf.writeInt(pkt.vaultCount);
            buf.writeInt(pkt.cards.size());
            for (ItemCardData c : pkt.cards) c.write(buf);
        }

        public static SyncItemListPacket decode(FriendlyByteBuf buf) {
            String balance = buf.readUtf();
            int vaultCount = buf.readInt();
            int count = buf.readInt();
            List<ItemCardData> cards = new ArrayList<>();
            for (int i = 0; i < count; i++) cards.add(ItemCardData.read(buf));
            return new SyncItemListPacket(balance, vaultCount, cards);
        }

        public static void handle(SyncItemListPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> com.nstut.forge.client.MarketScreen.handleSyncItemList(pkt));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class RequestItemDetailPacket {
        public final String itemId;

        public RequestItemDetailPacket(String itemId) { this.itemId = itemId; }

        public static void encode(RequestItemDetailPacket pkt, FriendlyByteBuf buf) { buf.writeUtf(pkt.itemId); }
        public static RequestItemDetailPacket decode(FriendlyByteBuf buf) { return new RequestItemDetailPacket(buf.readUtf()); }

        public static void handle(RequestItemDetailPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                sendItemDetail(player, pkt.itemId);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class SyncItemDetailPacket {
        public final String itemId;
        public final String displayName;
        public final int vaultCount;
        public final List<OrderEntry> asks;
        public final List<OrderEntry> bids;
        public final List<ChartPoint> chart;

        public SyncItemDetailPacket(String itemId, String displayName, int vaultCount, List<OrderEntry> asks, List<OrderEntry> bids, List<ChartPoint> chart) {
            this.itemId = itemId; this.displayName = displayName; this.vaultCount = vaultCount; this.asks = asks; this.bids = bids; this.chart = chart;
        }

        public static void encode(SyncItemDetailPacket pkt, FriendlyByteBuf buf) {
            buf.writeUtf(pkt.itemId);
            buf.writeUtf(pkt.displayName);
            buf.writeInt(pkt.vaultCount);
            buf.writeInt(pkt.asks.size());
            for (OrderEntry e : pkt.asks) e.write(buf);
            buf.writeInt(pkt.bids.size());
            for (OrderEntry e : pkt.bids) e.write(buf);
            buf.writeInt(pkt.chart.size());
            for (ChartPoint p : pkt.chart) p.write(buf);
        }

        public static SyncItemDetailPacket decode(FriendlyByteBuf buf) {
            String itemId = buf.readUtf();
            String displayName = buf.readUtf();
            int vaultCount = buf.readInt();
            int askCount = buf.readInt();
            List<OrderEntry> asks = new ArrayList<>();
            for (int i = 0; i < askCount; i++) asks.add(OrderEntry.read(buf));
            int bidCount = buf.readInt();
            List<OrderEntry> bids = new ArrayList<>();
            for (int i = 0; i < bidCount; i++) bids.add(OrderEntry.read(buf));
            int chartCount = buf.readInt();
            List<ChartPoint> chart = new ArrayList<>();
            for (int i = 0; i < chartCount; i++) chart.add(ChartPoint.read(buf));
            return new SyncItemDetailPacket(itemId, displayName, vaultCount, asks, bids, chart);
        }

        public static void handle(SyncItemDetailPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> com.nstut.forge.client.MarketScreen.handleSyncItemDetail(pkt));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class CreateOrderPacket {
        public final String itemId;
        public final int quantity;
        public final String pricePerUnit;
        public final boolean isSell;

        public CreateOrderPacket(String itemId, int quantity, String pricePerUnit, boolean isSell) {
            this.itemId = itemId; this.quantity = quantity; this.pricePerUnit = pricePerUnit; this.isSell = isSell;
        }

        public static void encode(CreateOrderPacket pkt, FriendlyByteBuf buf) {
            buf.writeUtf(pkt.itemId); buf.writeInt(pkt.quantity); buf.writeUtf(pkt.pricePerUnit); buf.writeBoolean(pkt.isSell);
        }

        public static CreateOrderPacket decode(FriendlyByteBuf buf) {
            return new CreateOrderPacket(buf.readUtf(), buf.readInt(), buf.readUtf(), buf.readBoolean());
        }

        public static void handle(CreateOrderPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                ServerLevel level = player.serverLevel();
                OrderManager orderManager = Economy.getOrderManager();

                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(pkt.itemId));
                if (item == net.minecraft.world.item.Items.AIR) { sendItemList(player); return; }
                BigDecimal price = new BigDecimal(pkt.pricePerUnit);
                ItemCommodity commodity = new ItemCommodity(new ResourceLocation(pkt.itemId), item, BigDecimal.ZERO);

                if (pkt.isSell) {
                    if (VaultManager.countItemInVaults(level, player.getUUID(), item) < pkt.quantity) { sendItemDetail(player, pkt.itemId); return; }
                    net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> reserved = net.minecraft.core.NonNullList.create();
                    if (!VaultManager.extractItemFromVaults(level, player.getUUID(), item, pkt.quantity, reserved)) { sendItemDetail(player, pkt.itemId); return; }
                    orderManager.createSellOrder(player.getUUID(), commodity, pkt.quantity, price, reserved, level);
                } else {
                    orderManager.createBuyOrder(player.getUUID(), commodity, pkt.quantity, price, level);
                }
                orderManager.matchAllPendingOrders(level);
                sendItemDetail(player, pkt.itemId);
                sendItemList(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class AcceptOrderPacket {
        public final UUID orderId;

        public AcceptOrderPacket(UUID orderId) { this.orderId = orderId; }

        public static void encode(AcceptOrderPacket pkt, FriendlyByteBuf buf) { buf.writeUUID(pkt.orderId); }
        public static AcceptOrderPacket decode(FriendlyByteBuf buf) { return new AcceptOrderPacket(buf.readUUID()); }

        public static void handle(AcceptOrderPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                OrderManager orderManager = Economy.getOrderManager();
                var opt = orderManager.getOrder(pkt.orderId);
                if (opt.isEmpty() || opt.get().getOwner().equals(player.getUUID())) { sendItemList(player); return; }
                Order order = opt.get();
                IOrder.TransactionResult result = order.execute(player.getUUID(), player.serverLevel());
                orderManager.cleanupOrders();
                if (order.getCommodity() instanceof ItemCommodity ic) {
                    sendItemDetail(player, ic.getId().toString());
                } else {
                    sendItemList(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class CancelOrderPacket {
        public final UUID orderId;

        public CancelOrderPacket(UUID orderId) { this.orderId = orderId; }

        public static void encode(CancelOrderPacket pkt, FriendlyByteBuf buf) { buf.writeUUID(pkt.orderId); }
        public static CancelOrderPacket decode(FriendlyByteBuf buf) { return new CancelOrderPacket(buf.readUUID()); }

        public static void handle(CancelOrderPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                OrderManager orderManager = Economy.getOrderManager();
                var opt = orderManager.getOrder(pkt.orderId);
                if (opt.isPresent() && opt.get().getOwner().equals(player.getUUID())) {
                    Order order = opt.get();
                    if (order.getType() == IOrder.OrderType.SELL && !order.getReservedItems().isEmpty()) {
                        VaultManager.insertItemStacksToVaults(player.serverLevel(), player.getUUID(), order.getReservedItems());
                    }
                    order.cancel();
                    orderManager.cleanupOrders();
                }
                if (opt.isPresent() && opt.get().getCommodity() instanceof ItemCommodity ic) {
                    sendItemDetail(player, ic.getId().toString());
                } else {
                    sendItemList(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class RequestRefreshPacket {
        public RequestRefreshPacket() {}
        public static void encode(RequestRefreshPacket pkt, FriendlyByteBuf buf) {}
        public static RequestRefreshPacket decode(FriendlyByteBuf buf) { return new RequestRefreshPacket(); }

        public static void handle(RequestRefreshPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) sendItemList(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    private static void sendItemList(ServerPlayer player) {
        OrderManager orderManager = Economy.getOrderManager();
        var account = IAccountManager.getInstance().getOrCreatePlayerAccount(player.getUUID());
        EconomyConfig config = EconomyConfig.getInstance();
        String balance = account.getBalance().setScale(2, RoundingMode.HALF_UP).toPlainString();
        int vaultCount = VaultManager.getVaultRecords(player.getUUID()).size();

        java.util.Map<String, String> displayNames = new java.util.LinkedHashMap<>();
        java.util.Map<String, BigDecimal> bestBids = new java.util.LinkedHashMap<>();
        java.util.Map<String, BigDecimal> bestAsks = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();

        UUID playerId = player.getUUID();
        for (Order order : orderManager.getAllOrders()) {
            if (!(order.getCommodity() instanceof ItemCommodity ic)) continue;
            String itemId = ic.getItem().builtInRegistryHolder().key().location().toString();
            displayNames.putIfAbsent(itemId, ic.getDisplayName().getString());
            counts.merge(itemId, 1, Integer::sum);

            BigDecimal price = order.getPricePerUnit();
            if (order.getType() == IOrder.OrderType.SELL) {
                BigDecimal cur = bestAsks.get(itemId);
                if (cur == null || price.compareTo(cur) < 0) bestAsks.put(itemId, price);
            } else {
                BigDecimal cur = bestBids.get(itemId);
                if (cur == null || price.compareTo(cur) > 0) bestBids.put(itemId, price);
            }
        }

        List<ItemCardData> cards = new ArrayList<>();
        for (String itemId : displayNames.keySet()) {
            BigDecimal ask = bestAsks.get(itemId);
            BigDecimal bid = bestBids.get(itemId);
            BigDecimal effectivePrice = ask != null ? ask : bid;
            if (effectivePrice == null) {
                var trades = TradeLedger.getRecentTrades(itemId, 1);
                if (!trades.isEmpty()) {
                    try {
                        effectivePrice = new BigDecimal(trades.get(0).price);
                    } catch (Exception ignored) {}
                }
            }
            String priceStr = effectivePrice != null ? effectivePrice.setScale(2, RoundingMode.HALF_UP).toPlainString() : "--";
            cards.add(new ItemCardData(itemId, displayNames.get(itemId), priceStr, counts.getOrDefault(itemId, 0)));
        }

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncItemListPacket(balance, vaultCount, cards));
    }

    private static void sendItemDetail(ServerPlayer player, String itemId) {
        OrderManager orderManager = Economy.getOrderManager();
        EconomyConfig config = EconomyConfig.getInstance();
        UUID playerId = player.getUUID();

        ResourceLocation rl = new ResourceLocation(itemId);
        Item item = BuiltInRegistries.ITEM.get(rl);
        String displayName = new net.minecraft.world.item.ItemStack(item).getHoverName().getString();

        int vaultCount = VaultManager.countItemInVaults(player.serverLevel(), playerId, item);

        List<OrderEntry> asks = new ArrayList<>();
        List<OrderEntry> bids = new ArrayList<>();

        for (Order order : orderManager.getAllOrders()) {
            if (!(order.getCommodity() instanceof ItemCommodity ic)) continue;
            if (!ic.getItem().builtInRegistryHolder().key().location().toString().equals(itemId)) continue;

            String sellerName = "?";
            if (order.isServerOrder()) {
                sellerName = "SERVER";
            } else {
                var profile = player.server.getProfileCache().get(order.getOwner());
                if (profile.isPresent()) sellerName = profile.get().getName();
            }

            OrderEntry entry = new OrderEntry(
                order.getOrderId(), order.getOwner(), sellerName,
                order.getPricePerUnit().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                order.getQuantity(), order.getOwner().equals(playerId), order.isServerOrder());

            if (order.getType() == IOrder.OrderType.SELL) {
                asks.add(entry);
            } else {
                bids.add(entry);
            }
        }

        asks.sort((a, b) -> {
            BigDecimal pa = new BigDecimal(a.price);
            BigDecimal pb = new BigDecimal(b.price);
            return pa.compareTo(pb);
        });
        bids.sort((a, b) -> {
            BigDecimal pa = new BigDecimal(a.price);
            BigDecimal pb = new BigDecimal(b.price);
            return pb.compareTo(pa);
        });

        List<ChartPoint> chart = new ArrayList<>();
        List<EconomyTradeData.TradeSnapshot> trades = TradeLedger.getRecentTrades(itemId, 20);
        for (int i = trades.size() - 1; i >= 0; i--) {
            EconomyTradeData.TradeSnapshot t = trades.get(i);
            chart.add(new ChartPoint(new BigDecimal(t.price).intValue(), t.quantity));
        }

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncItemDetailPacket(itemId, displayName, vaultCount, asks, bids, chart));
    }

    // ── Order History ────────────────────────────────────────────────────────

    public static class HistoryEntry {
        public final String itemId;
        public final String displayName;
        public final String price;
        public final int quantity;
        /** true = player was the seller, false = buyer */
        public final boolean wasSell;
        /** epoch-millis */
        public final long timestamp;
        public final String counterparty;

        public HistoryEntry(String itemId, String displayName, String price, int quantity,
                            boolean wasSell, long timestamp, String counterparty) {
            this.itemId = itemId; this.displayName = displayName; this.price = price;
            this.quantity = quantity; this.wasSell = wasSell;
            this.timestamp = timestamp; this.counterparty = counterparty;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(itemId);
            buf.writeUtf(displayName);
            buf.writeUtf(price);
            buf.writeInt(quantity);
            buf.writeBoolean(wasSell);
            buf.writeLong(timestamp);
            buf.writeUtf(counterparty);
        }

        public static HistoryEntry read(FriendlyByteBuf buf) {
            return new HistoryEntry(buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readInt(), buf.readBoolean(), buf.readLong(), buf.readUtf());
        }
    }

    public static class RequestOrderHistoryPacket {
        public RequestOrderHistoryPacket() {}
        public static void encode(RequestOrderHistoryPacket pkt, FriendlyByteBuf buf) {}
        public static RequestOrderHistoryPacket decode(FriendlyByteBuf buf) { return new RequestOrderHistoryPacket(); }

        public static void handle(RequestOrderHistoryPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) sendOrderHistory(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class SyncOrderHistoryPacket {
        public final List<HistoryEntry> entries;

        public SyncOrderHistoryPacket(List<HistoryEntry> entries) { this.entries = entries; }

        public static void encode(SyncOrderHistoryPacket pkt, FriendlyByteBuf buf) {
            buf.writeInt(pkt.entries.size());
            for (HistoryEntry e : pkt.entries) e.write(buf);
        }

        public static SyncOrderHistoryPacket decode(FriendlyByteBuf buf) {
            int count = buf.readInt();
            List<HistoryEntry> entries = new ArrayList<>();
            for (int i = 0; i < count; i++) entries.add(HistoryEntry.read(buf));
            return new SyncOrderHistoryPacket(entries);
        }

        public static void handle(SyncOrderHistoryPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> com.nstut.forge.client.MarketScreen.handleSyncOrderHistory(pkt));
            ctx.get().setPacketHandled(true);
        }
    }

    private static void sendOrderHistory(ServerPlayer player) {
        UUID playerId = player.getUUID();
        List<com.nstut.economy.data.EconomyTradeData.TradeSnapshot> all =
                com.nstut.economy.data.TradeLedger.getAllTrades();

        List<HistoryEntry> entries = new ArrayList<>();
        // Iterate newest-first
        for (int i = all.size() - 1; i >= 0; i--) {
            com.nstut.economy.data.EconomyTradeData.TradeSnapshot t = all.get(i);
            boolean isBuyer  = playerId.equals(t.buyer);
            boolean isSeller = playerId.equals(t.seller);
            if (!isBuyer && !isSeller) continue;

            // Resolve item display name
            net.minecraft.resources.ResourceLocation rl = new net.minecraft.resources.ResourceLocation(t.itemId);
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
            String displayName = new net.minecraft.world.item.ItemStack(item).getHoverName().getString();

            // Resolve counterparty name
            UUID counterUUID = isSeller ? t.buyer : t.seller;
            String counterName = "?";
            if (com.nstut.economy.trading.OrderManager.SERVER_ID.equals(counterUUID)) {
                counterName = "SERVER";
            } else {
                var profile = player.server.getProfileCache().get(counterUUID);
                if (profile.isPresent()) counterName = profile.get().getName();
            }

            entries.add(new HistoryEntry(t.itemId, displayName,
                    new java.math.BigDecimal(t.price).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                    t.quantity, isSeller, t.timestamp, counterName));
        }

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncOrderHistoryPacket(entries));
    }
}
