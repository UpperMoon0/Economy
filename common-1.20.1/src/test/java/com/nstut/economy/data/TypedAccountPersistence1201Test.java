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
        EconomyAccountData data = new EconomyAccountData();
        data.setBalance(id, new BigDecimal("12.5"));
        data.setAccountBalance(AccountRef.team(id), new BigDecimal("77.25"));

        CompoundTag encoded = data.save(new CompoundTag());
        EconomyAccountData decoded = EconomyAccountData.load(encoded);

        assertEquals(new BigDecimal("12.5"), decoded.getAccountBalances().get(AccountRef.player(id)));
        assertEquals(new BigDecimal("77.25"), decoded.getAccountBalances().get(AccountRef.team(id)));
        assertFalse(decoded.getBalances().containsKey(UUID.randomUUID()));
    }
}
