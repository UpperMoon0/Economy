package com.nstut.economy.network;

import com.nstut.Economy;
import com.nstut.economy.api.IAccountManager;
import com.nstut.economy.api.IOrder;
import com.nstut.economy.blocks.TankBlockEntity;
import com.nstut.economy.blocks.TankManager;
import com.nstut.economy.blocks.TankMenu;
import com.nstut.economy.blocks.VaultBlockEntity;
import com.nstut.economy.blocks.VaultManager;
import com.nstut.economy.config.EconomyConfig;
import com.nstut.economy.data.EconomyTradeData;
import com.nstut.economy.data.TradeLedger;
import com.nstut.economy.trading.CreateOrderResult;
import com.nstut.economy.trading.FluidCommodity;
import com.nstut.economy.trading.ItemCommodity;
import com.nstut.economy.trading.Order;
import com.nstut.economy.trading.OrderManager;
import com.nstut.economy.util.CommodityUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import dev.architectury.networking.NetworkManager;
import com.nstut.economy.platform.Services;
import java.util.function.Supplier;

public class MarketNetwork {
    public static final NetworkChannel CHANNEL = NetworkChannel.create(
            ResourceLocation.fromNamespaceAndPath(Economy.MOD_ID, "market_v2"));

    private static boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        CHANNEL.registerC2S(RequestItemDetailPacket.class, RequestItemDetailPacket::encode, RequestItemDetailPacket::decode, RequestItemDetailPacket::handle);
        CHANNEL.registerS2C(SyncItemListPacket.class, SyncItemListPacket::encode, SyncItemListPacket::decode, SyncItemListPacket::handle);
        CHANNEL.registerS2C(SyncItemDetailPacket.class, SyncItemDetailPacket::encode, SyncItemDetailPacket::decode, SyncItemDetailPacket::handle);
        CHANNEL.registerC2S(CreateOrderPacket.class, CreateOrderPacket::encode, CreateOrderPacket::decode, CreateOrderPacket::handle);
        CHANNEL.registerC2S(AcceptOrderPacket.class, AcceptOrderPacket::encode, AcceptOrderPacket::decode, AcceptOrderPacket::handle);
        CHANNEL.registerC2S(CancelOrderPacket.class, CancelOrderPacket::encode, CancelOrderPacket::decode, CancelOrderPacket::handle);
        CHANNEL.registerC2S(RequestRefreshPacket.class, RequestRefreshPacket::encode, RequestRefreshPacket::decode, RequestRefreshPacket::handle);
        CHANNEL.registerC2S(RequestOrderHistoryPacket.class, RequestOrderHistoryPacket::encode, RequestOrderHistoryPacket::decode, RequestOrderHistoryPacket::handle);
        CHANNEL.registerS2C(SyncOrderHistoryPacket.class, SyncOrderHistoryPacket::encode, SyncOrderHistoryPacket::decode, SyncOrderHistoryPacket::handle);
        CHANNEL.registerC2S(RequestVaultInfoPacket.class, RequestVaultInfoPacket::encode, RequestVaultInfoPacket::decode, RequestVaultInfoPacket::handle);
        CHANNEL.registerS2C(SyncVaultInfoPacket.class, SyncVaultInfoPacket::encode, SyncVaultInfoPacket::decode, SyncVaultInfoPacket::handle);
        CHANNEL.registerC2S(ToggleVaultModePacket.class, ToggleVaultModePacket::encode, ToggleVaultModePacket::decode, ToggleVaultModePacket::handle);
        CHANNEL.registerC2S(ToggleTankModePacket.class, ToggleTankModePacket::encode, ToggleTankModePacket::decode, ToggleTankModePacket::handle);
        CHANNEL.registerC2S(RequestPortfolioPacket.class, RequestPortfolioPacket::encode, RequestPortfolioPacket::decode, RequestPortfolioPacket::handle);
        CHANNEL.registerS2C(SyncPortfolioPacket.class, SyncPortfolioPacket::encode, SyncPortfolioPacket::decode, SyncPortfolioPacket::handle);
        CHANNEL.registerC2S(EditOrderPacket.class, EditOrderPacket::encode, EditOrderPacket::decode, EditOrderPacket::handle);
        CHANNEL.registerC2S(RequestActiveOrdersPacket.class, RequestActiveOrdersPacket::encode, RequestActiveOrdersPacket::decode, RequestActiveOrdersPacket::handle);
        CHANNEL.registerS2C(SyncActiveOrdersPacket.class, SyncActiveOrdersPacket::encode, SyncActiveOrdersPacket::decode, SyncActiveOrdersPacket::handle);
        CHANNEL.registerS2C(MarketActionResultPacket.class, MarketActionResultPacket::encode, MarketActionResultPacket::decode, MarketActionResultPacket::handle);
    }

    public enum Action { CREATE_ORDER, ACCEPT_ORDER, CANCEL_ORDER, EDIT_ORDER }
    public enum Result { SUCCESS, WARNING, ERROR }
    // Enum identity is serialized by ordinal; never reorder or insert constants.
    public static final int MAX_RESULT_ARGS = 8;
    public static final class MarketActionResultPacket {
        public final Action action; public final Result result; public final String messageKey; public final List<String> args;
        public MarketActionResultPacket(Action action, Result result, String messageKey, List<String> args) { this.action = action; this.result = result; this.messageKey = messageKey; this.args = List.copyOf(args); }
        public static void encode(MarketActionResultPacket pkt, FriendlyByteBuf buf) { buf.writeEnum(pkt.action); buf.writeEnum(pkt.result); buf.writeUtf(pkt.messageKey); buf.writeInt(Math.min(pkt.args.size(), MAX_RESULT_ARGS)); for (int i = 0; i < Math.min(pkt.args.size(), MAX_RESULT_ARGS); i++) buf.writeUtf(pkt.args.get(i)); }
        public static MarketActionResultPacket decode(FriendlyByteBuf buf) { Action a=buf.readEnum(Action.class); Result r=buf.readEnum(Result.class); String k=buf.readUtf(); int n=buf.readInt(); if (n < 0 || n > MAX_RESULT_ARGS) throw new io.netty.handler.codec.DecoderException("Invalid action result argument count: " + n); List<String> args=new ArrayList<>(); for(int i=0;i<n;i++) args.add(buf.readUtf()); return new MarketActionResultPacket(a,r,k,args); }
        public static void handle(MarketActionResultPacket pkt, Supplier<NetworkManager.PacketContext> ctx) { ctx.get().queue(() -> com.nstut.economy.client.MarketScreen.handleActionResult(pkt)); }
    }
    private static void sendActionResult(ServerPlayer player, Action action, Result result, String key, String... args) { CHANNEL.sendToPlayer(player, new MarketActionResultPacket(action, result, key, List.of(args))); }

    private static void sendCreateResult(ServerPlayer player, CreateOrderResult creation) {
        switch (creation.status()) {
            case POSTED -> sendActionResult(player, Action.CREATE_ORDER, Result.SUCCESS, "ui.economy.toast.order_placed");
            case PARTIALLY_FILLED -> sendActionResult(player, Action.CREATE_ORDER, Result.SUCCESS, "ui.economy.toast.order_partially_filled", String.valueOf(creation.filledQuantity()));
            case FILLED -> sendActionResult(player, Action.CREATE_ORDER, Result.SUCCESS, "ui.economy.toast.order_filled");
            case REJECTED -> sendActionResult(player, Action.CREATE_ORDER, Result.WARNING, creation.errorKey(), creation.errorArgs().toArray(String[]::new));
        }
    }

    public static class ItemCardData {
        public final String itemId;
        public final String displayName;
        public final String globalPrice;
        public final int offerCount;
        public final double priceChangePercent;
        public final String commodityType;

        public ItemCardData(String itemId, String displayName, String globalPrice, int offerCount, double priceChangePercent, String commodityType) {
            this.itemId = itemId; this.displayName = displayName; this.globalPrice = globalPrice; this.offerCount = offerCount;
            this.priceChangePercent = priceChangePercent; this.commodityType = commodityType;
        }

        public ItemCardData(String itemId, String displayName, String globalPrice, int offerCount, double priceChangePercent) {
            this(itemId, displayName, globalPrice, offerCount, priceChangePercent, "ITEM");
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(itemId);
            buf.writeUtf(displayName);
            buf.writeUtf(globalPrice);
            buf.writeInt(offerCount);
            buf.writeDouble(priceChangePercent);
            buf.writeUtf(commodityType);
        }

        public static ItemCardData read(FriendlyByteBuf buf) {
            return new ItemCardData(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readDouble(), buf.readUtf());
        }
    }

    // ── Server-side input validation ─────────────────────────────────────────

    public static ResourceLocation parseCommodityId(String raw) {
        return com.nstut.economy.util.OrderInputValidator.parseCommodityId(raw);
    }

    public static BigDecimal parsePrice(String raw) {
        return com.nstut.economy.util.OrderInputValidator.parsePrice(raw);
    }

    private static String priceForClient(BigDecimal pricePerUnit, boolean fluid) {
        BigDecimal quoted = fluid
                ? FluidCommodity.pricePerBucket(pricePerUnit)
                : pricePerUnit;
        return quoted.stripTrailingZeros().toPlainString();
    }

    private static String exactDecimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public static boolean isValidQuantity(int quantity) {
        return com.nstut.economy.util.OrderInputValidator.isValidQuantity(quantity);
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
        public final double price;
        public final int quantity;
        public final long timestamp;

        public ChartPoint(double price, int quantity, long timestamp) {
            this.price = price; this.quantity = quantity; this.timestamp = timestamp;
        }

        public ChartPoint(double price, int quantity) {
            this(price, quantity, System.currentTimeMillis());
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeDouble(price);
            buf.writeInt(quantity);
            buf.writeLong(timestamp);
        }

        public static ChartPoint read(FriendlyByteBuf buf) {
            return new ChartPoint(buf.readDouble(), buf.readInt(), buf.readLong());
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

        public static void handle(SyncItemListPacket pkt, Supplier<NetworkManager.PacketContext> ctx) {
            ctx.get().queue(() -> com.nstut.economy.client.MarketScreen.handleSyncItemList(pkt));
        }
    }

    public static class RequestItemDetailPacket {
        public final String itemId;

        public RequestItemDetailPacket(String itemId) { this.itemId = itemId; }

        public static void encode(RequestItemDetailPacket pkt, FriendlyByteBuf buf) { buf.writeUtf(pkt.itemId); }
        public static RequestItemDetailPacket decode(FriendlyByteBuf buf) { return new RequestItemDetailPacket(buf.readUtf()); }

        public static void handle(RequestItemDetailPacket pkt, Supplier<NetworkManager.PacketContext> ctx) {
            ctx.get().queue(() -> {
                ServerPlayer player = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null;
                if (player == null) return;
                sendItemDetail(player, pkt.itemId);
            });
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

        public static void handle(SyncItemDetailPacket pkt, Supplier<NetworkManager.PacketContext> ctx) {
            ctx.get().queue(() -> com.nstut.economy.client.MarketScreen.handleSyncItemDetail(pkt));
        }
    }

    public static class CreateOrderPacket {
        public final String itemId;
        public final int quantity;
        public final String pricePerUnit;
        public final boolean isSell;
        public final boolean isInfinite;
        public final String commodityType;

        public CreateOrderPacket(String itemId, int quantity, String pricePerUnit, boolean isSell, boolean isInfinite, String commodityType) {
            this.itemId = itemId; this.quantity = quantity; this.pricePerUnit = pricePerUnit; this.isSell = isSell; this.isInfinite = isInfinite; this.commodityType = commodityType;
        }

        public CreateOrderPacket(String itemId, int quantity, String pricePerUnit, boolean isSell, boolean isInfinite) {
            this(itemId, quantity, pricePerUnit, isSell, isInfinite, "ITEM");
        }

        public CreateOrderPacket(String itemId, int quantity, String pricePerUnit, boolean isSell) {
            this(itemId, quantity, pricePerUnit, isSell, false, "ITEM");
        }

        public static void encode(CreateOrderPacket pkt, FriendlyByteBuf buf) {
            buf.writeUtf(pkt.itemId); buf.writeInt(pkt.quantity); buf.writeUtf(pkt.pricePerUnit); buf.writeBoolean(pkt.isSell); buf.writeBoolean(pkt.isInfinite); buf.writeUtf(pkt.commodityType);
        }

        public static CreateOrderPacket decode(FriendlyByteBuf buf) {
            return new CreateOrderPacket(buf.readUtf(), buf.readInt(), buf.readUtf(), buf.readBoolean(), buf.readBoolean(), buf.readUtf());
        }

        public static void handle(CreateOrderPacket pkt, Supplier<NetworkManager.PacketContext> ctx) {
            ctx.get().queue(() -> {
                ServerPlayer player = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null;
                if (player == null) return;
                try {
                    if (!"ITEM".equals(pkt.commodityType) && !"FLUID".equals(pkt.commodityType)) {
                        com.nstut.Economy.LOGGER.warn("Rejected order packet with unknown commodity type {} from {}", pkt.commodityType, player.getName().getString());
                        sendActionResult(player, Action.CREATE_ORDER, Result.ERROR, "ui.economy.error.commodity_invalid");
                        return;
                    }
                    if (!isValidQuantity(pkt.quantity)) {
                        com.nstut.Economy.LOGGER.warn("Rejected order packet with out-of-range quantity {} from {}", pkt.quantity, player.getName().getString());
                        var qtyError = com.nstut.economy.util.OrderInputValidator.validateQuantity(pkt.quantity);
                        sendActionResult(player, Action.CREATE_ORDER, Result.WARNING, qtyError.key(), qtyError.args().toArray(String[]::new));
                        sendItemDetail(player, pkt.itemId);
                        return;
                    }
                    var priceCheck = com.nstut.economy.util.OrderInputValidator.validatePrice(pkt.pricePerUnit);
                    if (!priceCheck.valid()) {
                        com.nstut.Economy.LOGGER.warn("Rejected order packet with invalid price from {}", player.getName().getString());
                        sendActionResult(player, Action.CREATE_ORDER, Result.WARNING, priceCheck.error().messageKey, priceCheck.error().args().toArray(String[]::new));
                        sendItemDetail(player, pkt.itemId);
                        return;
                    }
                    BigDecimal quotedPrice = priceCheck.value();
                    BigDecimal price = "FLUID".equals(pkt.commodityType)
                            ? FluidCommodity.pricePerMb(quotedPrice)
                            : quotedPrice;
                    ResourceLocation commodityId = parseCommodityId(pkt.itemId);
                    if (commodityId == null) {
                        sendActionResult(player, Action.CREATE_ORDER, Result.ERROR, "ui.economy.error.commodity_invalid");
                        sendItemList(player);
                        return;
                    }

                    ServerLevel level = player.serverLevel();
                    OrderManager orderManager = Economy.getOrderManager();
                    CreateOrderResult creation;

                    if ("FLUID".equals(pkt.commodityType)) {
                        Fluid fluid = BuiltInRegistries.FLUID.get(commodityId);
                        if (!CommodityUtil.isCanonicalFluid(fluid)) {
                            sendActionResult(player, Action.CREATE_ORDER, Result.ERROR, "ui.economy.error.commodity_invalid");
                            sendItemList(player);
                            return;
                        }
                        FluidCommodity commodity = new FluidCommodity(commodityId, fluid, BigDecimal.ZERO);

                        if (pkt.isSell) {
                            if (TankManager.countFluidInTanks(level, player.getUUID(), fluid) < pkt.quantity) {
                                sendActionResult(player, Action.CREATE_ORDER, Result.WARNING, "ui.economy.error.insufficient_stock");
                                sendItemDetail(player, pkt.itemId);
                                return;
                            }
                            List<com.nstut.economy.trading.EconomyFluidStack> reservedFluids = new ArrayList<>();
                            int drained = TankManager.extractFluidFromTanks(level, player.getUUID(), fluid, pkt.quantity, reservedFluids);
                            if (drained < pkt.quantity) {
                                for (var reservedFluid : reservedFluids) {
                                    TankManager.restoreFluidToTanks(level, player.getUUID(), reservedFluid);
                                }
                                sendActionResult(player, Action.CREATE_ORDER, Result.WARNING, "ui.economy.error.insufficient_stock");
                                sendItemDetail(player, pkt.itemId);
                                return;
                            }
                            creation = orderManager.createSellOrder(player.getUUID(), commodity, pkt.quantity, price,
                                    net.minecraft.core.NonNullList.create(), reservedFluids, level);
                        } else {
                            creation = orderManager.createBuyOrder(player.getUUID(), commodity, pkt.quantity, price, pkt.isInfinite, level);
                        }
                    } else {
                        Item item = BuiltInRegistries.ITEM.get(commodityId);
                        if (item == net.minecraft.world.item.Items.AIR) {
                            sendActionResult(player, Action.CREATE_ORDER, Result.ERROR, "ui.economy.error.commodity_invalid");
                            sendItemList(player);
                            return;
                        }
                        ItemCommodity commodity = new ItemCommodity(commodityId, item, BigDecimal.ZERO);

                        if (pkt.isSell) {
                            if (VaultManager.countItemInVaults(level, player.getUUID(), item) < pkt.quantity) {
                                sendActionResult(player, Action.CREATE_ORDER, Result.WARNING, "ui.economy.error.insufficient_stock");
                                sendItemDetail(player, pkt.itemId);
                                return;
                            }
                            net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> reserved = net.minecraft.core.NonNullList.create();
                            if (!VaultManager.extractItemFromVaults(level, player.getUUID(), item, pkt.quantity, reserved)) {
                                VaultManager.insertItemStacksToVaults(level, player.getUUID(), reserved);
                                sendActionResult(player, Action.CREATE_ORDER, Result.WARNING, "ui.economy.error.insufficient_stock");
                                sendItemDetail(player, pkt.itemId);
                                return;
                            }
                            creation = orderManager.createSellOrder(player.getUUID(), commodity, pkt.quantity, price, reserved, level);
                        } else {
                            creation = orderManager.createBuyOrder(player.getUUID(), commodity, pkt.quantity, price, pkt.isInfinite, level);
                        }
                    }
                    sendCreateResult(player, creation);
                    orderManager.matchAllPendingOrders(level);
                    sendItemDetail(player, pkt.itemId);
                    sendItemList(player);
                } catch (Exception e) {
                    com.nstut.Economy.LOGGER.warn("Error handling order packet from {}", player.getName().getString(), e);
                }
            });
        }
    }

    public static class AcceptOrderPacket {
        public final UUID orderId;

        public AcceptOrderPacket(UUID orderId) { this.orderId = orderId; }

        public static void encode(AcceptOrderPacket pkt, FriendlyByteBuf buf) { buf.writeUUID(pkt.orderId); }
        public static AcceptOrderPacket decode(FriendlyByteBuf buf) { return new AcceptOrderPacket(buf.readUUID()); }

        public static void handle(AcceptOrderPacket pkt, Supplier<NetworkManager.PacketContext> ctx) {
            ctx.get().queue(() -> {
                ServerPlayer player = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null;
                if (player == null) return;
                OrderManager orderManager = Economy.getOrderManager();
                var opt = orderManager.getOrder(pkt.orderId);
                if (opt.isEmpty() || opt.get().getOwner().equals(player.getUUID())) { sendItemList(player); return; }
                Order order = opt.get();
                IOrder.TransactionResult result = order.execute(player.getUUID(), player.serverLevel());
                sendActionResult(player, Action.ACCEPT_ORDER, result.success ? Result.SUCCESS : Result.ERROR, result.success ? "ui.economy.toast.order_completed" : "ui.economy.error.transaction_failed");
                orderManager.cleanupOrders();
                if (order.getCommodity() instanceof ItemCommodity ic) {
                    sendItemDetail(player, ic.getId().toString());
                } else if (order.getCommodity() instanceof FluidCommodity fc) {
                    sendItemDetail(player, fc.getId().toString());
                } else {
                    sendItemList(player);
                }
            });
        }
    }

    public static class CancelOrderPacket {
        public final UUID orderId;

        public CancelOrderPacket(UUID orderId) { this.orderId = orderId; }

        public static void encode(CancelOrderPacket pkt, FriendlyByteBuf buf) { buf.writeUUID(pkt.orderId); }
        public static CancelOrderPacket decode(FriendlyByteBuf buf) { return new CancelOrderPacket(buf.readUUID()); }

        public static void handle(CancelOrderPacket pkt, Supplier<NetworkManager.PacketContext> ctx) {
            ctx.get().queue(() -> {
                ServerPlayer player = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null;
                if (player == null) return;
                try {
                    OrderManager orderManager = Economy.getOrderManager();
                    var opt = orderManager.getOrder(pkt.orderId);
                    if (opt.isPresent() && opt.get().getOwner().equals(player.getUUID())) {
                        boolean cancelled = orderManager.cancelOrder(pkt.orderId, player.getUUID(), player.serverLevel());
                        if (!cancelled) sendActionResult(player, Action.CANCEL_ORDER, Result.WARNING, "ui.economy.error.cancel_storage_full");
                        else sendActionResult(player, Action.CANCEL_ORDER, Result.SUCCESS, "ui.economy.toast.order_cancelled");
                    }
                    sendActiveOrders(player);
                    if (opt.isPresent() && opt.get().getCommodity() instanceof ItemCommodity ic) {
                        sendItemDetail(player, ic.getId().toString());
                    } else if (opt.isPresent() && opt.get().getCommodity() instanceof FluidCommodity fc) {
                        sendItemDetail(player, fc.getId().toString());
                    } else {
                        sendItemList(player);
                    }
                } catch (Exception e) {
                    com.nstut.Economy.LOGGER.warn("Error handling cancel packet from {}", player.getName().getString(), e);
                }
            });
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

        public static void handle(EditOrderPacket pkt, Supplier<NetworkManager.PacketContext> ctx) {
            ctx.get().queue(() -> {
                ServerPlayer player = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null;
                if (player == null) return;
                try {
                    ServerLevel level = player.serverLevel();
                    OrderManager orderManager = Economy.getOrderManager();
                    var opt = orderManager.getOrder(pkt.orderId);
                    boolean fluidOrder = opt.isPresent() && opt.get().getCommodity() instanceof FluidCommodity;
                    BigDecimal quotedPrice = parsePrice(pkt.pricePerUnit);
                    BigDecimal price = quotedPrice == null ? null
                            : fluidOrder ? FluidCommodity.pricePerMb(quotedPrice) : quotedPrice;
                    // Quantity 0 is only meaningful for infinite buy orders
                    // (price-only edits); everything else needs a real quantity.
                    boolean isInfiniteBuyEdit = pkt.quantity == 0 && opt.isPresent()
                            && opt.get().isInfinite()
                            && opt.get().getType() == com.nstut.economy.api.IOrder.OrderType.BUY;
                    boolean valid = price != null && (isValidQuantity(pkt.quantity) || isInfiniteBuyEdit);
                    if (!valid) {
                        com.nstut.Economy.LOGGER.warn("Rejected edit packet with invalid quantity/price from {}", player.getName().getString());
                    } else {
                        boolean edited = orderManager.editOrder(pkt.orderId, player.getUUID(), pkt.quantity, price, pkt.isInfinite, level);
                        sendActionResult(player, Action.EDIT_ORDER, edited ? Result.SUCCESS : Result.ERROR, edited ? "ui.economy.toast.order_edited" : "ui.economy.error.transaction_failed");
                    }
                    sendActiveOrders(player);
                    sendItemList(player);
                    if (opt.isPresent() && opt.get().getCommodity() instanceof ItemCommodity ic) {
                        sendItemDetail(player, ic.getId().toString());
                    } else if (opt.isPresent() && opt.get().getCommodity() instanceof FluidCommodity fc) {
                        sendItemDetail(player, fc.getId().toString());
                    }
                } catch (Exception e) {
                    com.nstut.Economy.LOGGER.warn("Error handling edit packet from {}", player.getName().getString(), e);
                }
            });
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

        public static void handle(RequestActiveOrdersPacket pkt, Supplier<NetworkManager.PacketContext> ctx) {
            ctx.get().queue(() -> {
                ServerPlayer player = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null;
                if (player != null) sendActiveOrders(player);
            });
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

        public static void handle(SyncActiveOrdersPacket pkt, Supplier<NetworkManager.PacketContext> ctx) {
            ctx.get().queue(() -> com.nstut.economy.client.MarketScreen.handleSyncActiveOrders(pkt));
        }
    }

    public static void sendActiveOrders(ServerPlayer player) {
        OrderManager orderManager = Economy.getOrderManager();
        UUID playerId = player.getUUID();
        List<Order> playerOrders = orderManager.getPlayerOrders(playerId);

        List<ActiveOrderEntry> entries = new ArrayList<>();
        for (Order o : playerOrders) {
            String itemId;
            String displayName;
            if (o.getCommodity() instanceof ItemCommodity ic) {
                itemId = ic.getItem().builtInRegistryHolder().key().location().toString();
                displayName = new ItemStack(ic.getItem()).getHoverName().getString();
            } else if (o.getCommodity() instanceof FluidCommodity fc) {
                itemId = fc.getId().toString();
                displayName = com.nstut.economy.platform.Services.FLUID.displayName(fc.getFluid()).getString();
            } else {
                continue;
            }
            String priceStr = priceForClient(o.getPricePerUnit(), o.getCommodity() instanceof FluidCommodity);
            boolean isSell = o.getType() == IOrder.OrderType.SELL;

            entries.add(new ActiveOrderEntry(
                o.getOrderId(), itemId, displayName, priceStr, o.getQuantity(), o.getInitialQuantity(),
                isSell, o.isInfinite(), o.getCreatedAt().toEpochMilli()
            ));
        }

        CHANNEL.sendToPlayer(player, new SyncActiveOrdersPacket(entries));
    }

    public static class RequestRefreshPacket {
        public RequestRefreshPacket() {}
        public static void encode(RequestRefreshPacket pkt, FriendlyByteBuf buf) {}
        public static RequestRefreshPacket decode(FriendlyByteBuf buf) { return new RequestRefreshPacket(); }

        public static void handle(RequestRefreshPacket pkt, Supplier<NetworkManager.PacketContext> ctx) {
            ctx.get().queue(() -> {
                ServerPlayer player = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null;
                if (player != null) sendItemList(player);
            });
        }
    }

    public static BigDecimal getGlobalPrice(OrderManager orderManager, String itemId) {
        BigDecimal cheapestAsk = null;
        BigDecimal highestBid = null;

        for (Order order : orderManager.getAllOrders()) {
            String id;
            if (order.getCommodity() instanceof ItemCommodity ic) {
                id = ic.getItem().builtInRegistryHolder().key().location().toString();
            } else if (order.getCommodity() instanceof FluidCommodity fc) {
                id = fc.getId().toString();
            } else {
                continue;
            }
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
        String balance = exactDecimal(account.getBalance());
        int vaultCount = VaultManager.getVaultRecords(player.getUUID()).size();

        java.util.Set<String> itemIds = new java.util.LinkedHashSet<>();
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();

        // 1. Items with active orders
        for (Order order : orderManager.getAllOrders()) {
            String itemId;
            if (order.getCommodity() instanceof ItemCommodity ic) {
                itemId = ic.getItem().builtInRegistryHolder().key().location().toString();
            } else if (order.getCommodity() instanceof FluidCommodity fc) {
                itemId = fc.getId().toString();
            } else {
                continue;
            }
            itemIds.add(itemId);
            counts.merge(itemId, 1, Integer::sum);
        }

        // 2. Items in trade ledger history
        for (var trade : TradeLedger.getAllTrades()) {
            if (trade.itemId != null && !trade.itemId.isEmpty()) {
                itemIds.add(trade.itemId);
            }
        }

        List<ItemCardData> cards = new ArrayList<>();
        for (String commodityId : itemIds) {
            ResourceLocation rl = ResourceLocation.parse(commodityId);

            Fluid fluid = BuiltInRegistries.FLUID.get(rl);
            Item item = BuiltInRegistries.ITEM.get(rl);
            String displayName;
            boolean isFluid = false;

            if (fluid != net.minecraft.world.level.material.Fluids.EMPTY && !com.nstut.economy.platform.Services.FLUID.isAir(fluid)) {
                isFluid = true;
                displayName = com.nstut.economy.platform.Services.FLUID.displayName(fluid).getString();
                com.nstut.Economy.LOGGER.debug("[sendItemList] Detected FLUID: id={}, name={}", commodityId, displayName);
            } else if (item != net.minecraft.world.item.Items.AIR) {
                displayName = new net.minecraft.world.item.ItemStack(item).getHoverName().getString();
            } else {
                continue;
            }

            BigDecimal effectivePrice = getGlobalPrice(orderManager, commodityId);
            if (effectivePrice == null) {
                effectivePrice = BigDecimal.ZERO;
            }

            double priceChange = Double.NaN;
            var trades = TradeLedger.getRecentTrades(commodityId, 50);
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

            String priceStr = priceForClient(effectivePrice, isFluid);
            String typeStr = isFluid ? "FLUID" : "ITEM";
            cards.add(new ItemCardData(commodityId, displayName, priceStr, counts.getOrDefault(commodityId, 0), priceChange, typeStr));
        }

        CHANNEL.sendToPlayer(player, new SyncItemListPacket(balance, vaultCount, cards));
    }

    private static void sendItemDetail(ServerPlayer player, String itemId) {
        OrderManager orderManager = Economy.getOrderManager();
        UUID playerId = player.getUUID();

        ResourceLocation rl = ResourceLocation.parse(itemId);
        String displayName;
        int vaultCount;

        Fluid fluid = BuiltInRegistries.FLUID.get(rl);
        Item item = BuiltInRegistries.ITEM.get(rl);

        boolean isFluid = fluid != net.minecraft.world.level.material.Fluids.EMPTY && !com.nstut.economy.platform.Services.FLUID.isAir(fluid);
        if (isFluid) {
            displayName = com.nstut.economy.platform.Services.FLUID.displayName(fluid).getString();
            vaultCount = TankManager.countFluidInTanks(player.serverLevel(), playerId, fluid);
        } else if (item != net.minecraft.world.item.Items.AIR) {
            displayName = new net.minecraft.world.item.ItemStack(item).getHoverName().getString();
            vaultCount = VaultManager.countItemInVaults(player.serverLevel(), playerId, item);
        } else {
            sendItemList(player);
            return;
        }

        List<OrderEntry> asks = new ArrayList<>();
        List<OrderEntry> bids = new ArrayList<>();

        for (Order order : orderManager.getAllOrders()) {
            String orderItemId;
            if (order.getCommodity() instanceof ItemCommodity ic) {
                orderItemId = ic.getItem().builtInRegistryHolder().key().location().toString();
            } else if (order.getCommodity() instanceof FluidCommodity fc) {
                orderItemId = fc.getId().toString();
            } else {
                continue;
            }
            if (!orderItemId.equals(itemId)) continue;

            String sellerName = "?";
            if (order.isServerOrder()) {
                sellerName = "SERVER";
            } else {
                var profile = player.server.getProfileCache().get(order.getOwner());
                if (profile.isPresent()) sellerName = profile.get().getName();
            }

            OrderEntry entry = new OrderEntry(
                order.getOrderId(), order.getOwner(), sellerName,
                priceForClient(order.getPricePerUnit(), order.getCommodity() instanceof FluidCommodity),
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
            BigDecimal tradePrice = new BigDecimal(t.price);
            chart.add(new ChartPoint((isFluid ? FluidCommodity.pricePerBucket(tradePrice) : tradePrice).doubleValue(), t.quantity, t.timestamp));
        }
        BigDecimal globalPrice = getGlobalPrice(orderManager, itemId);
        if (globalPrice != null) {
            chart.add(new ChartPoint((isFluid ? FluidCommodity.pricePerBucket(globalPrice) : globalPrice).doubleValue(), 1, System.currentTimeMillis()));
        }

        CHANNEL.sendToPlayer(player, new SyncItemDetailPacket(itemId, displayName, vaultCount, asks, bids, chart));
    }

    // ── Order History ────────────────────────────────────────────────────────

    public static class RequestOrderHistoryPacket {
        public RequestOrderHistoryPacket() {}
        public static void encode(RequestOrderHistoryPacket pkt, FriendlyByteBuf buf) {}
        public static RequestOrderHistoryPacket decode(FriendlyByteBuf buf) { return new RequestOrderHistoryPacket(); }

        public static void handle(RequestOrderHistoryPacket pkt, Supplier<NetworkManager.PacketContext> ctx) {
            ctx.get().queue(() -> {
                ServerPlayer player = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null;
                if (player != null) sendOrderHistory(player);
            });
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

        public static void handle(SyncOrderHistoryPacket pkt, Supplier<NetworkManager.PacketContext> ctx) {
            ctx.get().queue(() -> com.nstut.economy.client.MarketScreen.handleSyncOrderHistory(pkt));
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
            net.minecraft.resources.ResourceLocation rl = ResourceLocation.parse(t.itemId);
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
            String displayName;
            boolean fluidCommodity = false;
            if (item != net.minecraft.world.item.Items.AIR) {
                displayName = new net.minecraft.world.item.ItemStack(item).getHoverName().getString();
            } else {
                net.minecraft.world.level.material.Fluid fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(rl);
                if (fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                    fluidCommodity = true;
                    displayName = com.nstut.economy.platform.Services.FLUID.displayName(fluid).getString();
                } else {
                    displayName = t.itemId;
                }
            }

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
                    priceForClient(new java.math.BigDecimal(t.price), fluidCommodity),
                    t.quantity, isSeller, t.timestamp, counterName));
        }

        CHANNEL.sendToPlayer(player, new SyncOrderHistoryPacket(entries));
    }

    // ── Vault Details ──────────────────────────────────────────────────────────

    public static class VaultDetailEntry {
        public final int x, y, z;
        public final String dimension;
        public final int usedSlots;
        public final int totalSlots;
        public final int totalItems;
        public final int mode;
        public final boolean tank;
        public final String contentId;

        public VaultDetailEntry(int x, int y, int z, String dimension, int usedSlots, int totalSlots,
                                int totalItems, int mode, boolean tank, String contentId) {
            this.x = x; this.y = y; this.z = z; this.dimension = dimension;
            this.usedSlots = usedSlots; this.totalSlots = totalSlots; this.totalItems = totalItems;
            this.mode = mode;
            this.tank = tank;
            this.contentId = contentId != null ? contentId : "";
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
            buf.writeUtf(dimension);
            buf.writeInt(usedSlots); buf.writeInt(totalSlots); buf.writeInt(totalItems);
            buf.writeInt(mode);
            buf.writeBoolean(tank);
            buf.writeUtf(contentId);
        }

        public static VaultDetailEntry read(FriendlyByteBuf buf) {
            return new VaultDetailEntry(buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readBoolean(), buf.readUtf());
        }
    }

    public static class RequestVaultInfoPacket {
        public RequestVaultInfoPacket() {}
        public static void encode(RequestVaultInfoPacket pkt, FriendlyByteBuf buf) {}
        public static RequestVaultInfoPacket decode(FriendlyByteBuf buf) { return new RequestVaultInfoPacket(); }

        public static void handle(RequestVaultInfoPacket pkt, Supplier<NetworkManager.PacketContext> ctx) {
            ctx.get().queue(() -> {
                ServerPlayer player = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null;
                if (player != null) sendVaultInfo(player);
            });
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

        public static void handle(SyncVaultInfoPacket pkt, Supplier<NetworkManager.PacketContext> ctx) {
            ctx.get().queue(() -> com.nstut.economy.client.MarketScreen.handleSyncVaultInfo(pkt));
        }
    }

    public static void sendVaultInfo(ServerPlayer player) {
        UUID playerId = player.getUUID();
        List<VaultDetailEntry> entries = new ArrayList<>();

        for (com.nstut.economy.data.EconomyAccountData.VaultRecord r :
                com.nstut.economy.blocks.VaultManager.getVaultRecords(playerId)) {
            int used = 0;
            int total = 54;
            int items = 0;
            int mode = 0;
            ServerLevel recordLevel = resolveRecordLevel(player, r.dimension);
            net.minecraft.world.level.block.entity.BlockEntity be =
                    recordLevel != null ? recordLevel.getBlockEntity(r.pos) : null;
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
            entries.add(new VaultDetailEntry(r.pos.getX(), r.pos.getY(), r.pos.getZ(),
                    r.dimension, used, total, items, mode, false, ""));
        }

        for (com.nstut.economy.data.EconomyAccountData.VaultRecord r :
                com.nstut.economy.blocks.TankManager.getTankRecords(playerId)) {
            int amount = 0;
            int capacity = TankBlockEntity.DEFAULT_CAPACITY;
            int mode = 0;
            String contentId = "";
            ServerLevel recordLevel = resolveRecordLevel(player, r.dimension);
            net.minecraft.world.level.block.entity.BlockEntity be =
                    recordLevel != null ? recordLevel.getBlockEntity(r.pos) : null;
            if (be instanceof TankBlockEntity tank) {
                amount = tank.getFluidAmount();
                capacity = tank.getCapacity();
                mode = tank.getMode().id;
                if (!tank.getFluid().isEmpty()) {
                    contentId = BuiltInRegistries.FLUID.getKey(tank.getFluid().getFluid()).toString();
                }
            }
            entries.add(new VaultDetailEntry(r.pos.getX(), r.pos.getY(), r.pos.getZ(),
                    r.dimension, amount, capacity, amount, mode, true, contentId));
        }
        CHANNEL.sendToPlayer(player, new SyncVaultInfoPacket(entries));
    }

    private static ServerLevel resolveRecordLevel(ServerPlayer player, String dimension) {
        if (player.getServer() == null || dimension == null || dimension.isEmpty()) {
            return player.serverLevel();
        }
        ResourceLocation dimensionId = ResourceLocation.parse(dimension);
        net.minecraft.resources.ResourceKey<Level> key =
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionId);
        return player.getServer().getLevel(key);
    }

    public static class ToggleVaultModePacket {
        public final net.minecraft.core.BlockPos pos;

        public ToggleVaultModePacket(net.minecraft.core.BlockPos pos) { this.pos = pos; }

        public static void encode(ToggleVaultModePacket pkt, FriendlyByteBuf buf) { buf.writeBlockPos(pkt.pos); }
        public static ToggleVaultModePacket decode(FriendlyByteBuf buf) { return new ToggleVaultModePacket(buf.readBlockPos()); }

        public static void handle(ToggleVaultModePacket pkt, Supplier<NetworkManager.PacketContext> ctx) {
            ctx.get().queue(() -> {
                ServerPlayer player = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null;
                if (player != null && player.level().getBlockEntity(pkt.pos) instanceof com.nstut.economy.blocks.VaultBlockEntity vault) {
                    if (vault.getOwner() != null && vault.getOwner().equals(player.getUUID())) {
                        vault.cycleMode();
                        if (player.containerMenu instanceof com.nstut.economy.blocks.VaultMenu vm) {
                            vm.setData(0, vault.getMode().id);
                        }
                    }
                }
            });
        }
    }

    public static class ToggleTankModePacket {
        public final net.minecraft.core.BlockPos pos;

        public ToggleTankModePacket(net.minecraft.core.BlockPos pos) { this.pos = pos; }

        public static void encode(ToggleTankModePacket pkt, FriendlyByteBuf buf) { buf.writeBlockPos(pkt.pos); }
        public static ToggleTankModePacket decode(FriendlyByteBuf buf) { return new ToggleTankModePacket(buf.readBlockPos()); }

        public static void handle(ToggleTankModePacket pkt, Supplier<NetworkManager.PacketContext> ctx) {
            ctx.get().queue(() -> {
                ServerPlayer player = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null;
                if (player != null && player.level().getBlockEntity(pkt.pos) instanceof TankBlockEntity tank) {
                    if (tank.getOwner() != null && tank.getOwner().equals(player.getUUID())) {
                        tank.cycleMode();
                        if (player.containerMenu instanceof TankMenu tm) {
                            tm.setMode(tank.getMode().id);
                        }
                    }
                }
            });
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

        public static void handle(RequestPortfolioPacket pkt, Supplier<NetworkManager.PacketContext> ctx) {
            ctx.get().queue(() -> {
                ServerPlayer player = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null;
                if (player != null) sendPortfolioInfo(player);
            });
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

        public static void handle(SyncPortfolioPacket pkt, Supplier<NetworkManager.PacketContext> ctx) {
            ctx.get().queue(() -> com.nstut.economy.client.MarketScreen.handleSyncPortfolio(pkt));
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
                exactDecimal(pt.netWorth),
                exactDecimal(pt.balance),
                exactDecimal(pt.assets)));
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
        for (var tank : TankManager.getTanks(player.serverLevel(), player.getUUID())) {
            var EconomyFluidStack = tank.getFluid();
            if (!EconomyFluidStack.isEmpty()) {
                String id = BuiltInRegistries.FLUID.getKey(EconomyFluidStack.getFluid()).toString();
                itemCounts.put(id, itemCounts.getOrDefault(id, 0) + EconomyFluidStack.getAmount());
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
            Fluid fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(id));
            String name;
            if (fluid != net.minecraft.world.level.material.Fluids.EMPTY && !com.nstut.economy.platform.Services.FLUID.isAir(fluid)) {
                name = com.nstut.economy.platform.Services.FLUID.displayName(fluid).getString();
            } else {
                net.minecraft.world.item.Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
                name = new ItemStack(it).getHoverName().getString();
            }
            holdings.add(new AssetHoldingData(id, name, qty, exactDecimal(totalVal)));
        }

        holdings.sort((a, b) -> new BigDecimal(b.totalValue).compareTo(new BigDecimal(a.totalValue)));
        CHANNEL.sendToPlayer(player, new SyncPortfolioPacket(points, holdings));
    }
}






