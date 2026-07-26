package com.nstut.forge.network;

import com.nstut.Economy;
import com.nstut.economy.api.IAccountManager;
import com.nstut.economy.api.IOrder;
import com.nstut.economy.blocks.VaultBlockEntity;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        CHANNEL.registerMessage(packetId++, RequestVaultInfoPacket.class, RequestVaultInfoPacket::encode, RequestVaultInfoPacket::decode, RequestVaultInfoPacket::handle);
        CHANNEL.registerMessage(packetId++, SyncVaultInfoPacket.class, SyncVaultInfoPacket::encode, SyncVaultInfoPacket::decode, SyncVaultInfoPacket::handle);
        CHANNEL.registerMessage(packetId++, ToggleVaultModePacket.class, ToggleVaultModePacket::encode, ToggleVaultModePacket::decode, ToggleVaultModePacket::handle);
        CHANNEL.registerMessage(packetId++, RequestPortfolioPacket.class, RequestPortfolioPacket::encode, RequestPortfolioPacket::decode, RequestPortfolioPacket::handle);
        CHANNEL.registerMessage(packetId++, SyncPortfolioPacket.class, SyncPortfolioPacket::encode, SyncPortfolioPacket::decode, SyncPortfolioPacket::handle);
        CHANNEL.registerMessage(packetId++, EditOrderPacket.class, EditOrderPacket::encode, EditOrderPacket::decode, EditOrderPacket::handle);
        CHANNEL.registerMessage(packetId++, RequestActiveOrdersPacket.class, RequestActiveOrdersPacket::encode, RequestActiveOrdersPacket::decode, RequestActiveOrdersPacket::handle);
        CHANNEL.registerMessage(packetId++, SyncActiveOrdersPacket.class, SyncActiveOrdersPacket::encode, SyncActiveOrdersPacket::decode, SyncActiveOrdersPacket::handle);
    }

    public static class ItemCardData {
        public final String itemId;
        public final String displayName;
        public final String globalPrice;
        public final int offerCount;
        public final double priceChangePercent;

        public ItemCardData(String itemId, String displayName, String globalPrice, int offerCount, double priceChangePercent) {
            this.itemId = itemId; this.displayName = displayName; this.globalPrice = globalPrice; this.offerCount = offerCount;
            this.priceChangePercent = priceChangePercent;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(itemId);
            buf.writeUtf(displayName);
            buf.writeUtf(globalPrice);
            buf.writeInt(offerCount);
            buf.writeDouble(priceChangePercent);
        }

        public static ItemCardData read(FriendlyByteBuf buf) {
            return new ItemCardData(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readDouble());
        }
    }

    public static class OrderEntry {
        public final UUID orderId;
        public final UUID ownerId;
        public final String sellerName;
        public final String price;
        public final int quantity;
        public final int initialQuantity;
        public final boolean isPlayerOwned;
        public final boolean isServerOrder;
        public final boolean isInfinite;

        public OrderEntry(UUID orderId, UUID ownerId, String sellerName, String price, int quantity, int initialQuantity, boolean isPlayerOwned, boolean isServerOrder, boolean isInfinite) {
            this.orderId = orderId; this.ownerId = ownerId; this.sellerName = sellerName; this.price = price;
            this.quantity = quantity; this.initialQuantity = initialQuantity > 0 ? initialQuantity : quantity;
            this.isPlayerOwned = isPlayerOwned; this.isServerOrder = isServerOrder; this.isInfinite = isInfinite;
        }

        public OrderEntry(UUID orderId, UUID ownerId, String sellerName, String price, int quantity, int initialQuantity, boolean isPlayerOwned, boolean isServerOrder) {
            this(orderId, ownerId, sellerName, price, quantity, initialQuantity, isPlayerOwned, isServerOrder, false);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(orderId);
            buf.writeUUID(ownerId);
            buf.writeUtf(sellerName);
            buf.writeUtf(price);
            buf.writeInt(quantity);
            buf.writeInt(initialQuantity);
            buf.writeBoolean(isPlayerOwned);
            buf.writeBoolean(isServerOrder);
            buf.writeBoolean(isInfinite);
        }

        public static OrderEntry read(FriendlyByteBuf buf) {
            return new OrderEntry(buf.readUUID(), buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
        }
    }

    public static class ChartPoint {
        public final int price;
        public final int quantity;
        public final long timestamp;

        public ChartPoint(int price, int quantity, long timestamp) {
            this.price = price; this.quantity = quantity; this.timestamp = timestamp;
        }

        public ChartPoint(int price, int quantity) {
            this(price, quantity, System.currentTimeMillis());
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeInt(price);
            buf.writeInt(quantity);
            buf.writeLong(timestamp);
        }

        public static ChartPoint read(FriendlyByteBuf buf) {
            return new ChartPoint(buf.readInt(), buf.readInt(), buf.readLong());
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
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.nstut.forge.client.MarketScreen.handleSyncItemList(pkt)));
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
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.nstut.forge.client.MarketScreen.handleSyncItemDetail(pkt)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class CreateOrderPacket {
        public final String itemId;
        public final int quantity;
        public final String pricePerUnit;
        public final boolean isSell;
        public final boolean isInfinite;

        public CreateOrderPacket(String itemId, int quantity, String pricePerUnit, boolean isSell, boolean isInfinite) {
            this.itemId = itemId; this.quantity = quantity; this.pricePerUnit = pricePerUnit; this.isSell = isSell; this.isInfinite = isInfinite;
        }

        public CreateOrderPacket(String itemId, int quantity, String pricePerUnit, boolean isSell) {
            this(itemId, quantity, pricePerUnit, isSell, false);
        }

        public static void encode(CreateOrderPacket pkt, FriendlyByteBuf buf) {
            buf.writeUtf(pkt.itemId); buf.writeInt(pkt.quantity); buf.writeUtf(pkt.pricePerUnit); buf.writeBoolean(pkt.isSell); buf.writeBoolean(pkt.isInfinite);
        }

        public static CreateOrderPacket decode(FriendlyByteBuf buf) {
            return new CreateOrderPacket(buf.readUtf(), buf.readInt(), buf.readUtf(), buf.readBoolean(), buf.readBoolean());
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
                    orderManager.createBuyOrder(player.getUUID(), commodity, pkt.quantity, price, pkt.isInfinite, level);
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
                sendActiveOrders(player);
                if (opt.isPresent() && opt.get().getCommodity() instanceof ItemCommodity ic) {
                    sendItemDetail(player, ic.getId().toString());
                } else {
                    sendItemList(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class EditOrderPacket {
        public final UUID orderId;
        public final int quantity;
        public final String pricePerUnit;
        public final boolean isInfinite;

        public EditOrderPacket(UUID orderId, int quantity, String pricePerUnit, boolean isInfinite) {
            this.orderId = orderId; this.quantity = quantity; this.pricePerUnit = pricePerUnit; this.isInfinite = isInfinite;
        }

        public static void encode(EditOrderPacket pkt, FriendlyByteBuf buf) {
            buf.writeUUID(pkt.orderId); buf.writeInt(pkt.quantity); buf.writeUtf(pkt.pricePerUnit); buf.writeBoolean(pkt.isInfinite);
        }

        public static EditOrderPacket decode(FriendlyByteBuf buf) {
            return new EditOrderPacket(buf.readUUID(), buf.readInt(), buf.readUtf(), buf.readBoolean());
        }

        public static void handle(EditOrderPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                ServerLevel level = player.serverLevel();
                OrderManager orderManager = Economy.getOrderManager();
                BigDecimal price = new BigDecimal(pkt.pricePerUnit);
                orderManager.editOrder(pkt.orderId, player.getUUID(), pkt.quantity, price, pkt.isInfinite, level);
                sendActiveOrders(player);
                sendItemList(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class ActiveOrderEntry {
        public final UUID orderId;
        public final String itemId;
        public final String displayName;
        public final String price;
        public final int quantity;
        public final int initialQuantity;
        public final boolean isSell;
        public final boolean isInfinite;
        public final long createdAt;

        public ActiveOrderEntry(UUID orderId, String itemId, String displayName, String price, int quantity, int initialQuantity, boolean isSell, boolean isInfinite, long createdAt) {
            this.orderId = orderId; this.itemId = itemId; this.displayName = displayName; this.price = price;
            this.quantity = quantity; this.initialQuantity = initialQuantity > 0 ? initialQuantity : quantity;
            this.isSell = isSell; this.isInfinite = isInfinite; this.createdAt = createdAt;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(orderId);
            buf.writeUtf(itemId);
            buf.writeUtf(displayName);
            buf.writeUtf(price);
            buf.writeInt(quantity);
            buf.writeInt(initialQuantity);
            buf.writeBoolean(isSell);
            buf.writeBoolean(isInfinite);
            buf.writeLong(createdAt);
        }

        public static ActiveOrderEntry read(FriendlyByteBuf buf) {
            return new ActiveOrderEntry(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readBoolean(), buf.readLong());
        }
    }

    public static class RequestActiveOrdersPacket {
        public RequestActiveOrdersPacket() {}
        public static void encode(RequestActiveOrdersPacket pkt, FriendlyByteBuf buf) {}
        public static RequestActiveOrdersPacket decode(FriendlyByteBuf buf) { return new RequestActiveOrdersPacket(); }

        public static void handle(RequestActiveOrdersPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) sendActiveOrders(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class SyncActiveOrdersPacket {
        public final List<ActiveOrderEntry> entries;

        public SyncActiveOrdersPacket(List<ActiveOrderEntry> entries) { this.entries = entries; }

        public static void encode(SyncActiveOrdersPacket pkt, FriendlyByteBuf buf) {
            buf.writeInt(pkt.entries.size());
            for (ActiveOrderEntry e : pkt.entries) e.write(buf);
        }

        public static SyncActiveOrdersPacket decode(FriendlyByteBuf buf) {
            int count = buf.readInt();
            List<ActiveOrderEntry> entries = new ArrayList<>();
            for (int i = 0; i < count; i++) entries.add(ActiveOrderEntry.read(buf));
            return new SyncActiveOrdersPacket(entries);
        }

        public static void handle(SyncActiveOrdersPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.nstut.forge.client.MarketScreen.handleSyncActiveOrders(pkt)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static void sendActiveOrders(ServerPlayer player) {
        OrderManager orderManager = Economy.getOrderManager();
        UUID playerId = player.getUUID();
        List<Order> playerOrders = orderManager.getPlayerOrders(playerId);

        List<ActiveOrderEntry> entries = new ArrayList<>();
        for (Order o : playerOrders) {
            if (!(o.getCommodity() instanceof ItemCommodity ic)) continue;
            String itemId = ic.getItem().builtInRegistryHolder().key().location().toString();
            String displayName = new ItemStack(ic.getItem()).getHoverName().getString();
            String priceStr = o.getPricePerUnit().setScale(0, RoundingMode.HALF_UP).toPlainString();
            boolean isSell = o.getType() == IOrder.OrderType.SELL;

            entries.add(new ActiveOrderEntry(
                o.getOrderId(), itemId, displayName, priceStr, o.getQuantity(), o.getInitialQuantity(),
                isSell, o.isInfinite(), o.getCreatedAt().toEpochMilli()
            ));
        }

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncActiveOrdersPacket(entries));
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

    public static BigDecimal getGlobalPrice(OrderManager orderManager, String itemId) {
        BigDecimal cheapestAsk = null;
        BigDecimal highestBid = null;

        for (Order order : orderManager.getAllOrders()) {
            if (!(order.getCommodity() instanceof ItemCommodity ic)) continue;
            String id = ic.getItem().builtInRegistryHolder().key().location().toString();
            if (!id.equals(itemId)) continue;

            BigDecimal price = order.getPricePerUnit();
            if (order.getType() == IOrder.OrderType.SELL) {
                if (cheapestAsk == null || price.compareTo(cheapestAsk) < 0) {
                    cheapestAsk = price;
                }
            } else {
                if (highestBid == null || price.compareTo(highestBid) > 0) {
                    highestBid = price;
                }
            }
        }

        if (cheapestAsk != null) {
            return cheapestAsk;
        }
        if (highestBid != null) {
            return highestBid;
        }

        var trades = TradeLedger.getRecentTrades(itemId, 1);
        if (!trades.isEmpty()) {
            try {
                return new BigDecimal(trades.get(0).price);
            } catch (Exception ignored) {}
        }

        return null;
    }

    private static void sendItemList(ServerPlayer player) {
        OrderManager orderManager = Economy.getOrderManager();
        var account = IAccountManager.getInstance().getOrCreatePlayerAccount(player.getUUID());
        String balance = account.getBalance().setScale(0, RoundingMode.HALF_UP).toPlainString();
        int vaultCount = VaultManager.getVaultRecords(player.getUUID()).size();

        java.util.Set<String> itemIds = new java.util.LinkedHashSet<>();
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();

        // 1. Items with active orders
        for (Order order : orderManager.getAllOrders()) {
            if (!(order.getCommodity() instanceof ItemCommodity ic)) continue;
            String itemId = ic.getItem().builtInRegistryHolder().key().location().toString();
            itemIds.add(itemId);
            counts.merge(itemId, 1, Integer::sum);
        }

        // 2. Items in trade ledger history
        for (var trade : TradeLedger.getAllTrades()) {
            if (trade.itemId != null && !trade.itemId.isEmpty()) {
                itemIds.add(trade.itemId);
            }
        }

        // 3. Items inside player's Vaults
        for (var vault : VaultManager.getVaults(player.serverLevel(), player.getUUID())) {
            for (int s = 0; s < vault.getContainerSize(); s++) {
                net.minecraft.world.item.ItemStack stack = vault.getItem(s);
                if (!stack.isEmpty()) {
                    String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    itemIds.add(id);
                }
            }
        }

        // 4. Default commodity catalog items
        itemIds.add("minecraft:diamond");
        itemIds.add("minecraft:iron_ingot");
        itemIds.add("minecraft:gold_ingot");
        itemIds.add("minecraft:emerald");
        itemIds.add("minecraft:netherite_ingot");
        itemIds.add("minecraft:copper_ingot");
        itemIds.add("minecraft:coal");
        itemIds.add("minecraft:redstone");
        itemIds.add("minecraft:lapis_lazuli");
        itemIds.add("minecraft:quartz");
        itemIds.add("minecraft:amethyst_shard");

        List<ItemCardData> cards = new ArrayList<>();
        for (String itemId : itemIds) {
            ResourceLocation rl = new ResourceLocation(itemId);
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == net.minecraft.world.item.Items.AIR) continue;

            String displayName = new net.minecraft.world.item.ItemStack(item).getHoverName().getString();

            BigDecimal effectivePrice = getGlobalPrice(orderManager, itemId);
            if (effectivePrice == null) continue;

            double priceChange = Double.NaN;
            var trades = TradeLedger.getRecentTrades(itemId, 50);
            if (!trades.isEmpty()) {
                try {
                    double curP = effectivePrice.doubleValue();
                    double prevP = curP;
                    boolean foundDiff = false;
                    for (int t = 0; t < trades.size(); t++) {
                        double p = Double.parseDouble(trades.get(t).price);
                        if (p != curP) {
                            prevP = p;
                            foundDiff = true;
                            break;
                        }
                    }
                    if (foundDiff && prevP > 0) {
                        priceChange = ((curP - prevP) / prevP) * 100.0;
                    }
                } catch (Exception ignored) {}
            }

            String priceStr = effectivePrice.setScale(0, RoundingMode.HALF_UP).toPlainString();
            cards.add(new ItemCardData(itemId, displayName, priceStr, counts.getOrDefault(itemId, 0), priceChange));
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
                order.getPricePerUnit().setScale(0, RoundingMode.HALF_UP).toPlainString(),
                order.getQuantity(), order.getInitialQuantity(), order.getOwner().equals(playerId), order.isServerOrder(), order.isInfinite());

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
        List<EconomyTradeData.TradeSnapshot> trades = TradeLedger.getRecentTrades(itemId, 50);
        for (int i = trades.size() - 1; i >= 0; i--) {
            EconomyTradeData.TradeSnapshot t = trades.get(i);
            chart.add(new ChartPoint(new BigDecimal(t.price).intValue(), t.quantity, t.timestamp));
        }

        BigDecimal globalPrice = getGlobalPrice(orderManager, itemId);
        if (globalPrice != null) {
            chart.add(new ChartPoint(globalPrice.intValue(), 1, System.currentTimeMillis()));
        }

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncItemDetailPacket(itemId, displayName, vaultCount, asks, bids, chart));
    }

    // ── Order History ────────────────────────────────────────────────────────

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
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.nstut.forge.client.MarketScreen.handleSyncOrderHistory(pkt)));
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
                    new java.math.BigDecimal(t.price).setScale(0, java.math.RoundingMode.HALF_UP).toPlainString(),
                    t.quantity, isSeller, t.timestamp, counterName));
        }

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncOrderHistoryPacket(entries));
    }

    // ── Vault Details ──────────────────────────────────────────────────────────

    public static class VaultDetailEntry {
        public final int x, y, z;
        public final String dimension;
        public final int usedSlots;
        public final int totalSlots;
        public final int totalItems;
        public final int mode;

        public VaultDetailEntry(int x, int y, int z, String dimension, int usedSlots, int totalSlots, int totalItems, int mode) {
            this.x = x; this.y = y; this.z = z; this.dimension = dimension;
            this.usedSlots = usedSlots; this.totalSlots = totalSlots; this.totalItems = totalItems;
            this.mode = mode;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
            buf.writeUtf(dimension);
            buf.writeInt(usedSlots); buf.writeInt(totalSlots); buf.writeInt(totalItems);
            buf.writeInt(mode);
        }

        public static VaultDetailEntry read(FriendlyByteBuf buf) {
            return new VaultDetailEntry(buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }
    }

    public static class RequestVaultInfoPacket {
        public RequestVaultInfoPacket() {}
        public static void encode(RequestVaultInfoPacket pkt, FriendlyByteBuf buf) {}
        public static RequestVaultInfoPacket decode(FriendlyByteBuf buf) { return new RequestVaultInfoPacket(); }

        public static void handle(RequestVaultInfoPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) sendVaultInfo(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class SyncVaultInfoPacket {
        public final List<VaultDetailEntry> entries;

        public SyncVaultInfoPacket(List<VaultDetailEntry> entries) { this.entries = entries; }

        public static void encode(SyncVaultInfoPacket pkt, FriendlyByteBuf buf) {
            buf.writeInt(pkt.entries.size());
            for (VaultDetailEntry e : pkt.entries) e.write(buf);
        }

        public static SyncVaultInfoPacket decode(FriendlyByteBuf buf) {
            int count = buf.readInt();
            List<VaultDetailEntry> entries = new ArrayList<>();
            for (int i = 0; i < count; i++) entries.add(VaultDetailEntry.read(buf));
            return new SyncVaultInfoPacket(entries);
        }

        public static void handle(SyncVaultInfoPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.nstut.forge.client.MarketScreen.handleSyncVaultInfo(pkt)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static void sendVaultInfo(ServerPlayer player) {
        UUID playerId = player.getUUID();
        List<com.nstut.economy.data.EconomyAccountData.VaultRecord> records =
            com.nstut.economy.blocks.VaultManager.getVaultRecords(playerId);

        List<VaultDetailEntry> entries = new ArrayList<>();
        for (com.nstut.economy.data.EconomyAccountData.VaultRecord r : records) {
            int used = 0;
            int total = 54;
            int items = 0;
            int mode = 0;
            net.minecraft.world.level.block.entity.BlockEntity be = player.serverLevel().getBlockEntity(r.pos);
            if (be instanceof com.nstut.economy.blocks.VaultBlockEntity vault) {
                total = vault.getContainerSize();
                mode = vault.getMode().id;
                for (int slot = 0; slot < total; slot++) {
                    net.minecraft.world.item.ItemStack stack = vault.getItem(slot);
                    if (!stack.isEmpty()) {
                        used++;
                        items += stack.getCount();
                    }
                }
            }
            entries.add(new VaultDetailEntry(r.pos.getX(), r.pos.getY(), r.pos.getZ(), r.dimension, used, total, items, mode));
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncVaultInfoPacket(entries));
    }

    public static class ToggleVaultModePacket {
        public final net.minecraft.core.BlockPos pos;

        public ToggleVaultModePacket(net.minecraft.core.BlockPos pos) { this.pos = pos; }

        public static void encode(ToggleVaultModePacket pkt, FriendlyByteBuf buf) { buf.writeBlockPos(pkt.pos); }
        public static ToggleVaultModePacket decode(FriendlyByteBuf buf) { return new ToggleVaultModePacket(buf.readBlockPos()); }

        public static void handle(ToggleVaultModePacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null && player.level().getBlockEntity(pkt.pos) instanceof com.nstut.economy.blocks.VaultBlockEntity vault) {
                    if (vault.getOwner() != null && vault.getOwner().equals(player.getUUID())) {
                        vault.cycleMode();
                        if (player.containerMenu instanceof com.nstut.economy.blocks.VaultMenu vm) {
                            vm.setData(0, vault.getMode().id);
                        }
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ── Portfolio Details ──────────────────────────────────────────────────────

    public static class PortfolioPointData {
        public final long timestamp;
        public final String netWorth;
        public final String balance;
        public final String assets;

        public PortfolioPointData(long timestamp, String netWorth, String balance, String assets) {
            this.timestamp = timestamp; this.netWorth = netWorth; this.balance = balance; this.assets = assets;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeLong(timestamp);
            buf.writeUtf(netWorth);
            buf.writeUtf(balance);
            buf.writeUtf(assets);
        }

        public static PortfolioPointData read(FriendlyByteBuf buf) {
            return new PortfolioPointData(buf.readLong(), buf.readUtf(), buf.readUtf(), buf.readUtf());
        }
    }

    public static class AssetHoldingData {
        public final String itemId;
        public final String displayName;
        public final int quantity;
        public final String totalValue;

        public AssetHoldingData(String itemId, String displayName, int quantity, String totalValue) {
            this.itemId = itemId; this.displayName = displayName; this.quantity = quantity; this.totalValue = totalValue;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(itemId);
            buf.writeUtf(displayName);
            buf.writeInt(quantity);
            buf.writeUtf(totalValue);
        }

        public static AssetHoldingData read(FriendlyByteBuf buf) {
            return new AssetHoldingData(buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readUtf());
        }
    }

    public static class RequestPortfolioPacket {
        public RequestPortfolioPacket() {}
        public static void encode(RequestPortfolioPacket pkt, FriendlyByteBuf buf) {}
        public static RequestPortfolioPacket decode(FriendlyByteBuf buf) { return new RequestPortfolioPacket(); }

        public static void handle(RequestPortfolioPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) sendPortfolioInfo(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class SyncPortfolioPacket {
        public final List<PortfolioPointData> points;
        public final List<AssetHoldingData> holdings;

        public SyncPortfolioPacket(List<PortfolioPointData> points, List<AssetHoldingData> holdings) {
            this.points = points; this.holdings = holdings;
        }

        public static void encode(SyncPortfolioPacket pkt, FriendlyByteBuf buf) {
            buf.writeInt(pkt.points.size());
            for (PortfolioPointData p : pkt.points) p.write(buf);
            buf.writeInt(pkt.holdings.size());
            for (AssetHoldingData h : pkt.holdings) h.write(buf);
        }

        public static SyncPortfolioPacket decode(FriendlyByteBuf buf) {
            int pCount = buf.readInt();
            List<PortfolioPointData> points = new ArrayList<>();
            for (int i = 0; i < pCount; i++) points.add(PortfolioPointData.read(buf));
            int hCount = buf.readInt();
            List<AssetHoldingData> holdings = new ArrayList<>();
            for (int i = 0; i < hCount; i++) holdings.add(AssetHoldingData.read(buf));
            return new SyncPortfolioPacket(points, holdings);
        }

        public static void handle(SyncPortfolioPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.nstut.forge.client.MarketScreen.handleSyncPortfolio(pkt)));
            ctx.get().setPacketHandled(true);
        }
    }

    @SuppressWarnings("removal")
    public static void sendPortfolioInfo(ServerPlayer player) {
        com.nstut.economy.data.EconomyAccountData.recordSnapshot(player.getUUID(), player.serverLevel());

        com.nstut.economy.data.EconomyAccountData accountData = com.nstut.economy.data.EconomyAccountData.get(player.serverLevel());
        List<com.nstut.economy.data.EconomyAccountData.PortfolioPoint> rawPoints = accountData.getPortfolioHistory(player.getUUID());

        List<PortfolioPointData> points = new ArrayList<>();
        for (var pt : rawPoints) {
            points.add(new PortfolioPointData(pt.timestamp,
                pt.netWorth.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString(),
                pt.balance.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString(),
                pt.assets.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString()));
        }

        List<com.nstut.economy.blocks.VaultBlockEntity> vaults = com.nstut.economy.blocks.VaultManager.getVaults(player.serverLevel(), player.getUUID());
        Map<String, Integer> itemCounts = new HashMap<>();
        for (var v : vaults) {
            for (int s = 0; s < v.getContainerSize(); s++) {
                ItemStack stack = v.getItem(s);
                if (!stack.isEmpty()) {
                    String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    itemCounts.put(id, itemCounts.getOrDefault(id, 0) + stack.getCount());
                }
            }
        }

        com.nstut.economy.data.EconomyTradeData historyData = com.nstut.economy.data.EconomyTradeData.get(player.serverLevel());
        List<AssetHoldingData> holdings = new ArrayList<>();
        for (var entry : itemCounts.entrySet()) {
            String id = entry.getKey();
            int qty = entry.getValue();
            BigDecimal unitPrice = BigDecimal.ZERO;
            List<com.nstut.economy.data.EconomyTradeData.TradeSnapshot> trades = historyData.getTrades();
            for (int i = trades.size() - 1; i >= 0; i--) {
                if (trades.get(i).itemId.equalsIgnoreCase(id)) {
                    unitPrice = new BigDecimal(trades.get(i).price);
                    break;
                }
            }
            BigDecimal totalVal = unitPrice.multiply(BigDecimal.valueOf(qty));
            net.minecraft.world.item.Item it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation(id));
            String name = new ItemStack(it).getHoverName().getString();
            holdings.add(new AssetHoldingData(id, name, qty, totalVal.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString()));
        }

        holdings.sort((a, b) -> new BigDecimal(b.totalValue).compareTo(new BigDecimal(a.totalValue)));
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncPortfolioPacket(points, holdings));
    }
}
