package com.nstut.economy.client;

import com.nstut.economy.network.MarketNetwork;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketClientStoreTeamWalletTest {
    @Test
    void switchingPrincipalClearsPortfolioBeforeItsNextBoundedRefresh() {
        MarketClientStore.marketPrincipal.set("PLAYER");
        MarketClientStore.applySyncPortfolio(new MarketNetwork.SyncPortfolioPacket(
                List.of(new MarketNetwork.PortfolioPointData(1, "12", "2", "10")),
                List.of(new MarketNetwork.AssetHoldingData("minecraft:iron_ingot", "Iron", 1, "10"))));
        assertFalse(MarketClientStore.portfolioPoints.get().isEmpty());
        MarketClientStore.applySyncItemList(new MarketNetwork.SyncItemListPacket(
                "2", 1, List.of(), "HYBRID", true, "Team", "99", "OWNER",
                true, true, true, "OFFICER", "OWNER", "TEAM"));
        assertTrue(MarketClientStore.portfolioPoints.get().isEmpty());
        assertTrue(MarketClientStore.assetHoldings.get().isEmpty());
    }

    @Test
    void switchingBetweenSameNameTeamsClearsOldPortfolioByExactAccountIdentity() {
        String first = com.nstut.economy.api.AccountRef.team(java.util.UUID.randomUUID()).toString();
        String second = com.nstut.economy.api.AccountRef.team(java.util.UUID.randomUUID()).toString();
        MarketClientStore.applySyncItemList(new MarketNetwork.SyncItemListPacket(
                "2", 1, List.of(), "HYBRID", true, "Same name", "99", "OWNER",
                true, true, true, "OFFICER", "OWNER", "TEAM", first));
        MarketClientStore.applySyncPortfolio(new MarketNetwork.SyncPortfolioPacket(
                List.of(new MarketNetwork.PortfolioPointData(1, "12", "2", "10")),
                List.of(new MarketNetwork.AssetHoldingData("minecraft:iron_ingot", "Iron", 1, "10")),
                "TEAM", "fresh", first));
        assertEquals(first, MarketClientStore.marketAccount.get());
        assertFalse(MarketClientStore.portfolioPoints.get().isEmpty());
        MarketClientStore.applySyncItemList(new MarketNetwork.SyncItemListPacket(
                "2", 1, List.of(), "HYBRID", true, "Same name", "99", "OWNER",
                true, true, true, "OFFICER", "OWNER", "TEAM", second));
        assertEquals(second, MarketClientStore.marketAccount.get());
        assertEquals("TEAM", MarketClientStore.marketPrincipal.get());
        assertTrue(MarketClientStore.portfolioPoints.get().isEmpty());
        assertTrue(MarketClientStore.assetHoldings.get().isEmpty());
        assertEquals("Unknown", MarketClientStore.portfolioValuationStatus.get());
    }

    @Test
    void portfolioReplyReflectsPermissionFallbackToPersonalWallet() {
        MarketClientStore.marketPrincipal.set("TEAM");
        MarketClientStore.applySyncPortfolio(new MarketNetwork.SyncPortfolioPacket(
                List.of(new MarketNetwork.PortfolioPointData(1, "12", "2", "10")), List.of(), "PLAYER", "Fresh: 1"));
        assertEquals("PLAYER", MarketClientStore.marketPrincipal.get());
        assertEquals("Fresh: 1", MarketClientStore.portfolioValuationStatus.get());
    }

    @Test
    void syncKeepsPersonalAndTeamBalancesDistinctAndShowsMarketPrincipal() {
        MarketClientStore.applySyncItemList(new MarketNetwork.SyncItemListPacket(
                "12.50", 2, List.of(),
                "HYBRID", true, "Upper Moon", "77.25", "MEMBER",
                true, false, false, "OFFICER", "OWNER", "PLAYER"));

        assertEquals("12.50", MarketClientStore.balance.get());
        var team = MarketClientStore.teamWallet.get();
        assertTrue(team.visible());
        assertEquals("Upper Moon", team.teamName());
        assertEquals("77.25", team.teamBalance());
        assertEquals("MEMBER", team.role());
        assertTrue(team.canDeposit());
        assertFalse(team.canSpend());
        assertFalse(team.canPayout());
        assertEquals("OFFICER", team.spendRole());
        assertEquals("OWNER", team.payoutRole());
        assertEquals("PLAYER", MarketClientStore.marketPrincipal.get());
        assertTrue(MarketClientStore.isPersonalPrincipal());
        assertFalse(MarketClientStore.isTeamPrincipal());

        MarketClientStore.applySyncItemList(new MarketNetwork.SyncItemListPacket(
                "12.50", 2, List.of(),
                "HYBRID", true, "Upper Moon", "77.25", "OWNER",
                true, true, true, "OFFICER", "OWNER", "TEAM"));
        assertTrue(MarketClientStore.isTeamPrincipal());
        assertFalse(MarketClientStore.isPersonalPrincipal());
    }

    @Test
    void personalOnlyOrLeaveClearsPreviouslyVisibleTeamState() {
        MarketClientStore.applySyncItemList(new MarketNetwork.SyncItemListPacket(
                "10", 0, List.of(),
                "HYBRID", true, "Old Party", "90", "OWNER",
                true, true, true, "OFFICER", "OWNER", "PLAYER"));

        MarketClientStore.applySyncItemList(new MarketNetwork.SyncItemListPacket(
                "11", 0, List.of(),
                "PERSONAL_ONLY", false, "", "0", "NONE",
                false, false, false, "OFFICER", "OWNER", "PLAYER"));

        var team = MarketClientStore.teamWallet.get();
        assertFalse(team.visible());
        assertEquals("", team.teamName());
        assertEquals("0", team.teamBalance());
        assertEquals("NONE", team.role());
        assertFalse(team.canPayout());
        assertEquals("OWNER", team.payoutRole());
        assertEquals("11", MarketClientStore.balance.get());
    }
}
