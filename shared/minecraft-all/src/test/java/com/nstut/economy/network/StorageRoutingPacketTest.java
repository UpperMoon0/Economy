package com.nstut.economy.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorageRoutingPacketTest {
    @Test void routingPacketPreservesTargetFilterAndSignedPriority() {
        for (boolean tank : new boolean[]{false, true}) {
            var original = new MarketNetwork.SetStorageRoutingPacket("minecraft:the_nether", new BlockPos(-12, 90, 8), tank,
                    tank ? "minecraft:water,minecraft:lava" : "minecraft:iron_ingot,minecraft:gold_ingot", -9999);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                MarketNetwork.SetStorageRoutingPacket.encode(original, buffer);
                var decoded = MarketNetwork.SetStorageRoutingPacket.decode(buffer);
                assertEquals(original.dimension, decoded.dimension); assertEquals(original.pos, decoded.pos);
                assertEquals(original.tank, decoded.tank); assertEquals(original.filter, decoded.filter);
                assertEquals(original.priority, decoded.priority); assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }
    @Test void wireRejectsOversizedFilters() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            assertThrows(RuntimeException.class, () -> MarketNetwork.SetStorageRoutingPacket.encode(
                    new MarketNetwork.SetStorageRoutingPacket("minecraft:overworld", BlockPos.ZERO, false, "x".repeat(4097), 0), buffer));
        } finally { buffer.release(); }
    }
    @Test void bothFilterModesRoundTripWithTheSameListAndPriority() {
        for (boolean blacklist : new boolean[]{false, true}) {
            for (boolean tank : new boolean[]{false, true}) {
                var original = new MarketNetwork.SetStorageRoutingPacket("minecraft:overworld", new BlockPos(1, 70, -3),
                        tank, tank ? "minecraft:water" : "minecraft:iron_ingot,minecraft:gold_ingot", -42, blacklist);
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    MarketNetwork.SetStorageRoutingPacket.encode(original, buffer);
                    var decoded = MarketNetwork.SetStorageRoutingPacket.decode(buffer);
                    assertEquals(original.dimension, decoded.dimension);
                    assertEquals(original.pos, decoded.pos);
                    assertEquals(original.tank, decoded.tank);
                    assertEquals(original.filter, decoded.filter);
                    assertEquals(original.priority, decoded.priority);
                    assertEquals(blacklist, decoded.blacklist);
                    assertEquals(0, buffer.readableBytes());
                } finally { buffer.release(); }
            }
        }
    }
}
