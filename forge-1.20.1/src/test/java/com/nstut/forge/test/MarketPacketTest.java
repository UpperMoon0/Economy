package com.nstut.economy.test;

import com.nstut.economy.network.MarketNetwork;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MarketPacketTest extends MinecraftTestBase {
    @Test
    @DisplayName("The unified create-order packet preserves fluid commodity type and mB quantity")
    void fluidCreateOrderPacketRoundTrips() {
        MarketNetwork.CreateOrderPacket original = new MarketNetwork.CreateOrderPacket(
                "minecraft:water", 16_000, "2.5", true, false, "FLUID");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        MarketNetwork.CreateOrderPacket.encode(original, buffer);
        MarketNetwork.CreateOrderPacket decoded = MarketNetwork.CreateOrderPacket.decode(buffer);

        assertEquals("minecraft:water", decoded.itemId);
        assertEquals(16_000, decoded.quantity);
        assertEquals("2.5", decoded.pricePerUnit);
        assertTrue(decoded.isSell);
        assertFalse(decoded.isInfinite);
        assertEquals("FLUID", decoded.commodityType);
    }

    @Test
    @DisplayName("Market cards preserve commodity type for browse filtering")
    void itemCardPacketRoundTripsCommodityType() {
        MarketNetwork.ItemCardData original = new MarketNetwork.ItemCardData(
                "minecraft:lava", "Lava", "4", 2, 12.5, "FLUID");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        original.write(buffer);
        MarketNetwork.ItemCardData decoded = MarketNetwork.ItemCardData.read(buffer);

        assertEquals(original.itemId, decoded.itemId);
        assertEquals(original.offerCount, decoded.offerCount);
        assertEquals("FLUID", decoded.commodityType);
    }

    @Test
    @DisplayName("Exact item variant ids survive browse and order packet round trips")
    void exactVariantIdentityRoundTripsAcrossMarketPackets() {
        String variantId = "minecraft:enchanted_book/variant/" + "a".repeat(64);

        MarketNetwork.ItemCardData card = new MarketNetwork.ItemCardData(
                variantId, "Enchanted Book", "15", 1, Double.NaN, "ITEM");
        FriendlyByteBuf cardBuffer = new FriendlyByteBuf(Unpooled.buffer());
        card.write(cardBuffer);
        MarketNetwork.ItemCardData decodedCard = MarketNetwork.ItemCardData.read(cardBuffer);
        assertEquals(variantId, decodedCard.itemId);
        assertEquals("ITEM", decodedCard.commodityType);

        MarketNetwork.RequestItemDetailPacket request = new MarketNetwork.RequestItemDetailPacket(variantId, "ITEM");
        FriendlyByteBuf requestBuffer = new FriendlyByteBuf(Unpooled.buffer());
        MarketNetwork.RequestItemDetailPacket.encode(request, requestBuffer);
        MarketNetwork.RequestItemDetailPacket decodedRequest = MarketNetwork.RequestItemDetailPacket.decode(requestBuffer);
        assertEquals(variantId, decodedRequest.itemId);
        assertEquals("ITEM", decodedRequest.commodityType);

        MarketNetwork.CreateOrderPacket create = new MarketNetwork.CreateOrderPacket(
                variantId, 1, "15", true, false, "ITEM");
        FriendlyByteBuf createBuffer = new FriendlyByteBuf(Unpooled.buffer());
        MarketNetwork.CreateOrderPacket.encode(create, createBuffer);
        MarketNetwork.CreateOrderPacket decodedCreate = MarketNetwork.CreateOrderPacket.decode(createBuffer);
        assertEquals(variantId, decodedCreate.itemId);
        assertEquals("ITEM", decodedCreate.commodityType);
    }

    @Test
    @DisplayName("Exact item variant ids survive detail responses")
    void exactVariantIdentityRoundTripsDetailResponse() {
        String variantId = "minecraft:enchanted_book/variant/" + "b".repeat(64);
        MarketNetwork.SyncItemDetailPacket original = new MarketNetwork.SyncItemDetailPacket(
                variantId, "Sharpness V", 2, List.of(), List.of(), List.of());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        MarketNetwork.SyncItemDetailPacket.encode(original, buffer);
        MarketNetwork.SyncItemDetailPacket decoded = MarketNetwork.SyncItemDetailPacket.decode(buffer);

        assertEquals(variantId, decoded.itemId);
        assertEquals("Sharpness V", decoded.displayName);
        assertEquals(2, decoded.vaultCount);
    }

    @Test
    @DisplayName("Exact item variant descriptors survive the client sync packet")
    void exactVariantDescriptorRoundTrips() {
        String variantId = "minecraft:enchanted_book/variant/" + "c".repeat(64);
        String canonical = "{Count:1b,id:\"minecraft:enchanted_book\",tag:{StoredEnchantments:[{id:\"minecraft:sharpness\",lvl:5s}]}}";
        MarketNetwork.SyncItemVariantDataPacket original =
                new MarketNetwork.SyncItemVariantDataPacket(Map.of(variantId, canonical));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        MarketNetwork.SyncItemVariantDataPacket.encode(original, buffer);
        MarketNetwork.SyncItemVariantDataPacket decoded = MarketNetwork.SyncItemVariantDataPacket.decode(buffer);

        assertEquals(Map.of(variantId, canonical), decoded.variants);
    }

    @Test
    @DisplayName("Chart packets preserve fractional bucket prices in protocol v2")
    void chartPointRoundTripsFractionalPrice() {
        MarketNetwork.ChartPoint original = new MarketNetwork.ChartPoint(0.00001, 1, 1234L);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        original.write(buffer);
        MarketNetwork.ChartPoint decoded = MarketNetwork.ChartPoint.read(buffer);

        assertEquals(0.00001, decoded.price);
        assertEquals(1, decoded.quantity);
        assertEquals(1234L, decoded.timestamp);
    }

    @Test
    @DisplayName("Action result packets preserve action, severity, key and args")
    void actionResultPacketRoundTrips() {
        MarketNetwork.MarketActionResultPacket original = new MarketNetwork.MarketActionResultPacket(
                MarketNetwork.Action.CREATE_ORDER, MarketNetwork.Result.WARNING,
                "ui.economy.error.price_above_max", List.of("1000000"));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        MarketNetwork.MarketActionResultPacket.encode(original, buffer);
        MarketNetwork.MarketActionResultPacket decoded = MarketNetwork.MarketActionResultPacket.decode(buffer);

        assertEquals(MarketNetwork.Action.CREATE_ORDER, decoded.action);
        assertEquals(MarketNetwork.Result.WARNING, decoded.result);
        assertEquals("ui.economy.error.price_above_max", decoded.messageKey);
        assertEquals(List.of("1000000"), decoded.args);
    }

    @Test
    @DisplayName("Action result packets preserve zero-argument messages")
    void actionResultPacketRoundTripsWithoutArgs() {
        MarketNetwork.MarketActionResultPacket original = new MarketNetwork.MarketActionResultPacket(
                MarketNetwork.Action.CANCEL_ORDER, MarketNetwork.Result.SUCCESS,
                "ui.economy.toast.order_cancelled", List.of());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        MarketNetwork.MarketActionResultPacket.encode(original, buffer);
        MarketNetwork.MarketActionResultPacket decoded = MarketNetwork.MarketActionResultPacket.decode(buffer);

        assertTrue(decoded.args.isEmpty());
        assertEquals(MarketNetwork.Action.CANCEL_ORDER, decoded.action);
        assertEquals(MarketNetwork.Result.SUCCESS, decoded.result);
    }

    @Test
    @DisplayName("Action result packets preserve the maximum argument count")
    void actionResultPacketRoundTripsMaxArgs() {
        List<String> args = List.of("1", "2", "3", "4", "5", "6", "7", "8");
        MarketNetwork.MarketActionResultPacket original = new MarketNetwork.MarketActionResultPacket(
                MarketNetwork.Action.EDIT_ORDER, MarketNetwork.Result.ERROR,
                "ui.economy.error.transaction_failed", args);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        MarketNetwork.MarketActionResultPacket.encode(original, buffer);
        MarketNetwork.MarketActionResultPacket decoded = MarketNetwork.MarketActionResultPacket.decode(buffer);

        assertEquals(args, decoded.args);
    }

    @Test
    @DisplayName("Action result packets reject malformed argument counts")
    void actionResultPacketRejectsInvalidArgCount() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeEnum(MarketNetwork.Action.CREATE_ORDER);
        buffer.writeEnum(MarketNetwork.Result.WARNING);
        buffer.writeUtf("ui.economy.error.order_rejected");
        buffer.writeInt(99);

        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> MarketNetwork.MarketActionResultPacket.decode(buffer));
    }

    @Test
    @DisplayName("Container overview packets preserve tank fluid and capacity")
    void tankContainerEntryRoundTrips() {
        MarketNetwork.VaultDetailEntry original = new MarketNetwork.VaultDetailEntry(
                10, 64, -20, "minecraft:the_nether",
                32_000, 128_000, 32_000, 2, true, "minecraft:lava");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        original.write(buffer);
        MarketNetwork.VaultDetailEntry decoded = MarketNetwork.VaultDetailEntry.read(buffer);

        assertTrue(decoded.tank);
        assertEquals("minecraft:lava", decoded.contentId);
        assertEquals(32_000, decoded.usedSlots);
        assertEquals(128_000, decoded.totalSlots);
        assertEquals(2, decoded.mode);
        assertEquals("minecraft:the_nether", decoded.dimension);
    }

    @Test
    @DisplayName("Container overview packets distinguish item vaults from tanks")
    void vaultContainerEntryRoundTrips() {
        MarketNetwork.VaultDetailEntry original = new MarketNetwork.VaultDetailEntry(
                1, 2, 3, "minecraft:overworld",
                7, 54, 448, 1, false, "");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        original.write(buffer);
        MarketNetwork.VaultDetailEntry decoded = MarketNetwork.VaultDetailEntry.read(buffer);

        assertFalse(decoded.tank);
        assertEquals("", decoded.contentId);
        assertEquals(7, decoded.usedSlots);
        assertEquals(54, decoded.totalSlots);
        assertEquals(448, decoded.totalItems);
        assertEquals(1, decoded.mode);
    }

    @Test
    @DisplayName("Detail request packets preserve item id and fluid type on the wire")
    void itemDetailRequestRoundTripsFluidType() {
        MarketNetwork.RequestItemDetailPacket original =
                new MarketNetwork.RequestItemDetailPacket("minecraft:water", "FLUID");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        MarketNetwork.RequestItemDetailPacket.encode(original, buffer);
        MarketNetwork.RequestItemDetailPacket decoded =
                MarketNetwork.RequestItemDetailPacket.decode(buffer);

        assertEquals("minecraft:water", decoded.itemId);
        assertEquals("FLUID", decoded.commodityType);
    }

    @Test
    @DisplayName("Detail request packets preserve item commodity types")
    void itemDetailRequestRoundTripsItemType() {
        MarketNetwork.RequestItemDetailPacket original =
                new MarketNetwork.RequestItemDetailPacket("minecraft:diamond", "ITEM");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        MarketNetwork.RequestItemDetailPacket.encode(original, buffer);
        MarketNetwork.RequestItemDetailPacket decoded =
                MarketNetwork.RequestItemDetailPacket.decode(buffer);

        assertEquals("minecraft:diamond", decoded.itemId);
        assertEquals("ITEM", decoded.commodityType);
    }

    @Test
    @DisplayName("Detail request packets treat an empty commodity type as untyped")
    void itemDetailRequestRoundTripsWithoutType() {
        MarketNetwork.RequestItemDetailPacket original =
                new MarketNetwork.RequestItemDetailPacket("minecraft:diamond");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        MarketNetwork.RequestItemDetailPacket.encode(original, buffer);
        MarketNetwork.RequestItemDetailPacket decoded =
                MarketNetwork.RequestItemDetailPacket.decode(buffer);

        assertEquals("minecraft:diamond", decoded.itemId);
        assertNull(decoded.commodityType);
    }
}
