package com.nstut.economy.data;

import com.nstut.economy.api.AccountRef;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypedAccountPersistence2612Test {
    @Test
    void legacyPlayersAndTypedTeamsRoundTripIndependently() {
        UUID id = UUID.randomUUID();
        UUID serverId = UUID.randomUUID();
        UUID taxId = UUID.randomUUID();
        EconomyAccountData data = new EconomyAccountData();
        data.setBalance(id, new BigDecimal("4"));
        data.setAccountBalance(AccountRef.team(id), new BigDecimal("9"));
        data.setAccountBalance(AccountRef.server(serverId), new BigDecimal("1000"));
        data.setAccountBalance(AccountRef.tax(taxId), new BigDecimal("4.5"));

        CompoundTag encoded = data.save(new CompoundTag());
        EconomyAccountData decoded = EconomyAccountData.load(encoded);

        assertEquals(new BigDecimal("4"), decoded.getAccountBalances().get(AccountRef.player(id)));
        assertEquals(new BigDecimal("9"), decoded.getAccountBalances().get(AccountRef.team(id)));
        assertEquals(new BigDecimal("1000"), decoded.getAccountBalances().get(AccountRef.server(serverId)));
        assertEquals(new BigDecimal("4.5"), decoded.getAccountBalances().get(AccountRef.tax(taxId)));
    }
}
