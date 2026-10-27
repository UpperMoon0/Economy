package com.nstut.economy.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioPacketTest {
    @Test void displayLimitKeepsHighestValueHoldingsAndLeavesFullValuationInputIntact() {
        List<MarketNetwork.AssetHoldingData> full = new ArrayList<>();
        for (int i = 0; i < 600; i++) full.add(new MarketNetwork.AssetHoldingData("addon:asset_" + i, "Asset", 1, "" + i));
        var displayed = MarketNetwork.topPortfolioHoldings(full);
        assertEquals(512, displayed.size());
        assertEquals("599", displayed.get(0).totalValue);
        assertEquals("88", displayed.get(511).totalValue);
        assertEquals(600, full.size());
        assertEquals("0", full.get(0).totalValue);
    }

    @Test void byteBudgetKeepsRecentHistoryAndReportsOmittedRowsWithoutChangingInputs() {
        List<MarketNetwork.PortfolioPointData> points = new ArrayList<>();
        for (int i = 0; i < 1024; i++) points.add(new MarketNetwork.PortfolioPointData(i,
                "10", "5", "5", "界".repeat(256), "界".repeat(1024), "1", "1", true));
        List<MarketNetwork.AssetHoldingData> holdings = new ArrayList<>();
        for (int i = 0; i < 512; i++) holdings.add(new MarketNetwork.AssetHoldingData(
                "addon:asset_" + i + "x".repeat(12000), "Asset", 1, "5"));
        var packet = MarketNetwork.boundedPortfolioPacket(points, holdings, "PLAYER", "fresh");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            MarketNetwork.SyncPortfolioPacket.encode(packet, buffer);
            assertTrue(buffer.readableBytes() <= MarketNetwork.MAX_PORTFOLIO_BYTES);
            var decoded = MarketNetwork.SyncPortfolioPacket.decode(buffer);
            assertFalse(decoded.points.isEmpty());
            assertFalse(decoded.holdings.isEmpty());
            assertEquals(1023, decoded.points.get(decoded.points.size() - 1).timestamp);
            assertTrue(decoded.valuationStatus.contains("saved history retained"));
            assertTrue(decoded.valuationStatus.contains("totals include all"));
            assertEquals(1024, points.size());
            assertEquals(512, holdings.size());
        } finally { buffer.release(); }
    }

    @Test void normalResponseRetainsAllRowsAndUnencodableRowIsExplicitlyOmitted() {
        var point = new MarketNetwork.PortfolioPointData(1, "3", "1", "2");
        var normal = new MarketNetwork.AssetHoldingData("addon:good", "Good", 1, "2");
        var packet = MarketNetwork.boundedPortfolioPacket(List.of(point), List.of(normal), "TEAM", "fresh");
        assertEquals(List.of(point), packet.points);
        assertEquals(List.of(normal), packet.holdings);
        assertEquals("fresh", packet.valuationStatus);
        var tooLong = new MarketNetwork.AssetHoldingData("x".repeat(32768), "Long", 1, "3");
        packet = MarketNetwork.boundedPortfolioPacket(List.of(point), List.of(normal, tooLong), "TEAM", "fresh");
        assertEquals(List.of(normal), packet.holdings);
        assertTrue(packet.valuationStatus.contains("showing 1 of 2 holdings"));
    }

    @Test void roundTripPreservesCashFlowAttributionAndExactLargeAmounts() {
        var point = new MarketNetwork.PortfolioPointData(123456789L,
                "99999999999999999999.123456789", "-2.000000001", "99999999999999999999.123456790",
                "addon:salary", "Treasury payout", "100.000000001", "100.000000001", true);
        var holding = new MarketNetwork.AssetHoldingData("addon:asset", "Asset", 5_000_000_000L, 3_000_000_000L, "123.000000001");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            MarketNetwork.SyncPortfolioPacket.encode(new MarketNetwork.SyncPortfolioPacket(List.of(point), List.of(holding),
                    "TEAM", "Fresh: 1, stale: 2, unknown: 3"), buffer);
            var decoded = MarketNetwork.SyncPortfolioPacket.decode(buffer);
            var result = decoded.points.get(0);
            assertEquals(point.timestamp, result.timestamp);
            assertEquals("TEAM", decoded.marketPrincipal);
            assertEquals("Fresh: 1, stale: 2, unknown: 3", decoded.valuationStatus);
            assertEquals(point.netWorth, result.netWorth);
            assertEquals(point.balance, result.balance);
            assertEquals(point.assets, result.assets);
            assertEquals(point.source, result.source);
            assertEquals(point.reason, result.reason);
            assertEquals(point.cashDelta, result.cashDelta);
            assertEquals(point.externalFlow, result.externalFlow);
            assertTrue(result.attributed);
            assertEquals(holding.quantity, decoded.holdings.get(0).quantity);
            assertEquals(holding.storedQuantity, decoded.holdings.get(0).storedQuantity);
            assertEquals(holding.totalValue, decoded.holdings.get(0).totalValue);
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void legacySnapshotRemainsExplicitlyUnattributed() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new MarketNetwork.PortfolioPointData(1, "3", "1", "2").write(buffer);
            var point = MarketNetwork.PortfolioPointData.read(buffer);
            assertFalse(point.attributed);
            assertEquals("legacy", point.source);
            assertEquals("0", point.externalFlow);
        } finally { buffer.release(); }
    }

    @Test void decodeRejectsUnboundedPointCounts() {
        for (int count : new int[]{-1, 1025, Integer.MAX_VALUE}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeUtf("PLAYER");
                buffer.writeUtf("Unknown");
                buffer.writeInt(count);
                assertThrows(RuntimeException.class, () -> MarketNetwork.SyncPortfolioPacket.decode(buffer));
            } finally { buffer.release(); }
        }
    }

    @Test void decodeRejectsHoldingCountsAboveSenderLimit() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUtf("PLAYER"); buffer.writeUtf("Unknown"); buffer.writeInt(0); buffer.writeInt(513);
            assertThrows(RuntimeException.class, () -> MarketNetwork.SyncPortfolioPacket.decode(buffer));
        } finally { buffer.release(); }
    }
}
