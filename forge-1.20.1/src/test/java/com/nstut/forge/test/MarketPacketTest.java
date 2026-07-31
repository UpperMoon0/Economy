package com.nstut.forge.test;

import com.nstut.forge.network.MarketNetwork;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
