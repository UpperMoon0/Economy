package com.nstut.economy.client;

import com.nstut.economy.network.MarketNetwork;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketClientStoreTeamWalletTest {
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
        assertFalse(team.canWithdraw());
        assertEquals("OFFICER", team.spendRole());
        assertEquals("OWNER", team.withdrawRole());
        assertEquals("PLAYER", MarketClientStore.marketPrincipal.get());
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
        assertFalse(team.canWithdraw());
        assertEquals("OWNER", team.withdrawRole());
        assertEquals("11", MarketClientStore.balance.get());
    }
}
