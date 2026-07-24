package com.nstut.forge.network;

import com.nstut.Economy;
import com.nstut.economy.api.IAccountManager;
import com.nstut.economy.api.IOffer;
import com.nstut.economy.blocks.VaultManager;
import com.nstut.economy.config.EconomyConfig;
import com.nstut.economy.data.EconomyTradeData;
import com.nstut.economy.data.TradeLedger;
import com.nstut.economy.trading.ItemCommodity;
import com.nstut.economy.trading.Offer;
import com.nstut.economy.trading.OfferManager;
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
        CHANNEL.registerMessage(packetId++, CreateOfferPacket.class, CreateOfferPacket::encode, CreateOfferPacket::decode, CreateOfferPacket::handle);
        CHANNEL.registerMessage(packetId++, AcceptOfferPacket.class, AcceptOfferPacket::encode, AcceptOfferPacket::decode, AcceptOfferPacket::handle);
        CHANNEL.registerMessage(packetId++, CancelOfferPacket.class, CancelOfferPacket::encode, CancelOfferPacket::decode, CancelOfferPacket::handle);
        CHANNEL.registerMessage(packetId++, RequestRefreshPacket.class, RequestRefreshPacket::encode, RequestRefreshPacket::decode, RequestRefreshPacket::handle);
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
        public final UUID offerId;
        public final UUID ownerId;
        public final String sellerName;
        public final String price;
        public final int quantity;
        public final boolean isPlayerOwned;
        public final boolean isServerOrder;

        public OrderEntry(UUID offerId, UUID ownerId, String sellerName, String price, int quantity, boolean isPlayerOwned, boolean isServerOrder) {
            this.offerId = offerId; this.ownerId = ownerId; this.sellerName = sellerName; this.price = price; this.quantity = quantity; this.isPlayerOwned = isPlayerOwned; this.isServerOrder = isServerOrder;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(offerId);
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
        public final boolean hasVault;
        public final List<ItemCardData> cards;

        public SyncItemListPacket(String balance, boolean hasVault, List<ItemCardData> cards) {
            this.balance = balance; this.hasVault = hasVault; this.cards = cards;
        }

        public static void encode(SyncItemListPacket pkt, FriendlyByteBuf buf) {
            buf.writeUtf(pkt.balance);
            buf.writeBoolean(pkt.hasVault);
            buf.writeInt(pkt.cards.size());
            for (ItemCardData c : pkt.cards) c.write(buf);
        }

        public static SyncItemListPacket decode(FriendlyByteBuf buf) {
            String balance = buf.readUtf();
            boolean hasVault = buf.readBoolean();
            int count = buf.readInt();
            List<ItemCardData> cards = new ArrayList<>();
            for (int i = 0; i < count; i++) cards.add(ItemCardData.read(buf));
            return new SyncItemListPacket(balance, hasVault, cards);
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

    public static class CreateOfferPacket {
        public final String itemId;
        public final int quantity;
        public final String pricePerUnit;
        public final boolean isSell;

        public CreateOfferPacket(String itemId, int quantity, String pricePerUnit, boolean isSell) {
            this.itemId = itemId; this.quantity = quantity; this.pricePerUnit = pricePerUnit; this.isSell = isSell;
        }

        public static void encode(CreateOfferPacket pkt, FriendlyByteBuf buf) {
            buf.writeUtf(pkt.itemId); buf.writeInt(pkt.quantity); buf.writeUtf(pkt.pricePerUnit); buf.writeBoolean(pkt.isSell);
        }

        public static CreateOfferPacket decode(FriendlyByteBuf buf) {
            return new CreateOfferPacket(buf.readUtf(), buf.readInt(), buf.readUtf(), buf.readBoolean());
        }

        public static void handle(CreateOfferPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                ServerLevel level = player.serverLevel();
                OfferManager offerManager = Economy.getOfferManager();

                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(pkt.itemId));
                if (item == net.minecraft.world.item.Items.AIR) { sendItemList(player); return; }
                BigDecimal price = new BigDecimal(pkt.pricePerUnit);
                ItemCommodity commodity = new ItemCommodity(new ResourceLocation(pkt.itemId), item, BigDecimal.ZERO);

                if (pkt.isSell) {
                    if (VaultManager.countItemInVaults(level, player.getUUID(), item) < pkt.quantity) { sendItemDetail(player, pkt.itemId); return; }
                    net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> reserved = net.minecraft.core.NonNullList.create();
                    if (!VaultManager.extractItemFromVaults(level, player.getUUID(), item, pkt.quantity, reserved)) { sendItemDetail(player, pkt.itemId); return; }
                    offerManager.createSellOffer(player.getUUID(), commodity, pkt.quantity, price, reserved, level);
                } else {
                    offerManager.createBuyOffer(player.getUUID(), commodity, pkt.quantity, price, level);
                }
                offerManager.matchAllPendingOrders(level);
                sendItemDetail(player, pkt.itemId);
                sendItemList(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class AcceptOfferPacket {
        public final UUID offerId;

        public AcceptOfferPacket(UUID offerId) { this.offerId = offerId; }

        public static void encode(AcceptOfferPacket pkt, FriendlyByteBuf buf) { buf.writeUUID(pkt.offerId); }
        public static AcceptOfferPacket decode(FriendlyByteBuf buf) { return new AcceptOfferPacket(buf.readUUID()); }

        public static void handle(AcceptOfferPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                OfferManager offerManager = Economy.getOfferManager();
                var opt = offerManager.getOffer(pkt.offerId);
                if (opt.isEmpty() || opt.get().getOwner().equals(player.getUUID())) { sendItemList(player); return; }
                Offer offer = opt.get();
                IOffer.TransactionResult result = offer.execute(player.getUUID(), player.serverLevel());
                offerManager.cleanupOffers();
                if (offer.getCommodity() instanceof ItemCommodity ic) {
                    sendItemDetail(player, ic.getId().toString());
                } else {
                    sendItemList(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class CancelOfferPacket {
        public final UUID offerId;

        public CancelOfferPacket(UUID offerId) { this.offerId = offerId; }

        public static void encode(CancelOfferPacket pkt, FriendlyByteBuf buf) { buf.writeUUID(pkt.offerId); }
        public static CancelOfferPacket decode(FriendlyByteBuf buf) { return new CancelOfferPacket(buf.readUUID()); }

        public static void handle(CancelOfferPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                OfferManager offerManager = Economy.getOfferManager();
                var opt = offerManager.getOffer(pkt.offerId);
                if (opt.isPresent() && opt.get().getOwner().equals(player.getUUID())) {
                    Offer offer = opt.get();
                    if (offer.getType() == IOffer.OfferType.SELL && !offer.getReservedItems().isEmpty()) {
                        VaultManager.insertItemStacksToVaults(player.serverLevel(), player.getUUID(), offer.getReservedItems());
                    }
                    offer.cancel();
                    offerManager.cleanupOffers();
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
        OfferManager offerManager = Economy.getOfferManager();
        var account = IAccountManager.getInstance().getOrCreatePlayerAccount(player.getUUID());
        EconomyConfig config = EconomyConfig.getInstance();
        String balance = account.getBalance().setScale(2, RoundingMode.HALF_UP).toPlainString();
        boolean hasVault = VaultManager.hasVault(player.getUUID());

        java.util.Map<String, String> displayNames = new java.util.LinkedHashMap<>();
        java.util.Map<String, BigDecimal> bestBids = new java.util.LinkedHashMap<>();
        java.util.Map<String, BigDecimal> bestAsks = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();

        UUID playerId = player.getUUID();
        for (Offer offer : offerManager.getAllOffers()) {
            if (!(offer.getCommodity() instanceof ItemCommodity ic)) continue;
            String itemId = ic.getItem().builtInRegistryHolder().key().location().toString();
            displayNames.putIfAbsent(itemId, ic.getDisplayName().getString());
            counts.merge(itemId, 1, Integer::sum);

            BigDecimal price = offer.getPricePerUnit();
            if (offer.getType() == IOffer.OfferType.SELL) {
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

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncItemListPacket(balance, hasVault, cards));
    }

    private static void sendItemDetail(ServerPlayer player, String itemId) {
        OfferManager offerManager = Economy.getOfferManager();
        EconomyConfig config = EconomyConfig.getInstance();
        UUID playerId = player.getUUID();

        ResourceLocation rl = new ResourceLocation(itemId);
        Item item = BuiltInRegistries.ITEM.get(rl);
        String displayName = new net.minecraft.world.item.ItemStack(item).getHoverName().getString();

        int vaultCount = VaultManager.countItemInVaults(player.serverLevel(), playerId, item);

        List<OrderEntry> asks = new ArrayList<>();
        List<OrderEntry> bids = new ArrayList<>();

        for (Offer offer : offerManager.getAllOffers()) {
            if (!(offer.getCommodity() instanceof ItemCommodity ic)) continue;
            if (!ic.getItem().builtInRegistryHolder().key().location().toString().equals(itemId)) continue;

            String sellerName = "?";
            if (offer.isServerOrder()) {
                sellerName = "SERVER";
            } else {
                var profile = player.server.getProfileCache().get(offer.getOwner());
                if (profile.isPresent()) sellerName = profile.get().getName();
            }

            OrderEntry entry = new OrderEntry(
                offer.getOfferId(), offer.getOwner(), sellerName,
                offer.getPricePerUnit().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                offer.getQuantity(), offer.getOwner().equals(playerId), offer.isServerOrder());

            if (offer.getType() == IOffer.OfferType.SELL) {
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
}
