package com.nstut.economy.data;

import com.nstut.economy.api.AccountRef;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TypedAccountPersistence1201Test {
    @Test
    void legacyPlayersAndTypedTeamsRoundTripIndependently() {
        UUID id = UUID.randomUUID();
        UUID serverId = UUID.randomUUID();
        UUID taxId = UUID.randomUUID();
        EconomyAccountData data = new EconomyAccountData();
        data.setBalance(id, new BigDecimal("12.5"));
        data.setAccountBalance(AccountRef.team(id), new BigDecimal("77.25"));
        data.setAccountBalance(AccountRef.server(serverId), new BigDecimal("1000"));
        data.setAccountBalance(AccountRef.tax(taxId), new BigDecimal("4.5"));

        CompoundTag encoded = data.save(new CompoundTag());
        EconomyAccountData decoded = EconomyAccountData.load(encoded);

        assertEquals(new BigDecimal("12.5"), decoded.getAccountBalances().get(AccountRef.player(id)));
        assertEquals(new BigDecimal("77.25"), decoded.getAccountBalances().get(AccountRef.team(id)));
        assertEquals(new BigDecimal("1000"), decoded.getAccountBalances().get(AccountRef.server(serverId)));
        assertEquals(new BigDecimal("4.5"), decoded.getAccountBalances().get(AccountRef.tax(taxId)));
        assertFalse(decoded.getBalances().containsKey(UUID.randomUUID()));
    }
    @Test
    void playerAndTeamPortfolioHistoryRoundTripIndependently() {
        UUID id = UUID.randomUUID();
        EconomyAccountData data = new EconomyAccountData();
        data.addPortfolioPoint(AccountRef.player(id), new BigDecimal("12.5"), new BigDecimal("3.5"));
        data.addPortfolioPoint(AccountRef.team(id), new BigDecimal("77.25"), new BigDecimal("20.75"));

        CompoundTag encoded = data.save(new CompoundTag());
        EconomyAccountData decoded = EconomyAccountData.load(encoded);

        var player = decoded.getPortfolioHistory(AccountRef.player(id));
        var team = decoded.getPortfolioHistory(AccountRef.team(id));
        assertEquals(1, player.size());
        assertEquals(1, team.size());
        assertEquals(new BigDecimal("16.0"), player.get(0).netWorth);
        assertEquals(new BigDecimal("98.00"), team.get(0).netWorth);
        assertEquals(new BigDecimal("12.5"), player.get(0).balance);
        assertEquals(new BigDecimal("77.25"), team.get(0).balance);
    }

    @Test
    void teamSettlementSnapshotAndProgressRoundTrip() {
        UUID teamId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        EconomyAccountData data = new EconomyAccountData();
        var providerId = com.nstut.economy.api.EconomyId.of("test", "durable_team_provider");
        data.putTeamWallet(new com.nstut.economy.api.internal.TeamWalletState(
                teamId, owner, providerId, true, false, java.util.List.of(owner, member), java.util.Set.of(owner)));

        CompoundTag encoded = data.save(new CompoundTag());
        EconomyAccountData decoded = EconomyAccountData.load(encoded);
        var state = decoded.getTeamWallets().get(teamId);
        assertEquals(providerId, state.providerId());
        assertEquals(java.util.Set.of(owner, member), java.util.Set.copyOf(state.settlementMembers()));
        assertEquals(java.util.Set.of(owner), state.settledMembers());
        assertEquals(true, state.closing());
        assertEquals(false, state.storageSettled());

        CompoundTag legacy = encoded.copy();
        legacy.getList("TeamWallets", 10).getCompound(0).remove("Provider");
        EconomyAccountData legacyDecoded = EconomyAccountData.load(legacy);
        assertEquals(com.nstut.economy.api.internal.TeamWalletState.UNKNOWN_PROVIDER_ID,
                legacyDecoded.getTeamWallets().get(teamId).providerId());
    }

}
