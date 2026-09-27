package com.nstut.economy.client;

import com.nstut.economy.api.AccountRef;
import com.nstut.economy.api.MarketIdentity;
import com.nstut.economy.network.MarketIdentityCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MarketIdentityCodecTest {
    @Test void typedAndLegacyStorageWithTheSameUuidRoundTripWithoutTrailingBytes() {
        UUID id = UUID.randomUUID();
        MarketIdentity legacy = new MarketIdentity(AccountRef.team(id), id, id);
        MarketIdentity typed = new MarketIdentity(AccountRef.team(id), id, AccountRef.team(id));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            MarketIdentityCodec.write(buffer, legacy);
            MarketIdentityCodec.write(buffer, typed);
            MarketIdentityCodec.write(buffer, null);
            assertEquals(legacy, MarketIdentityCodec.read(buffer));
            assertEquals(typed, MarketIdentityCodec.read(buffer));
            assertNull(MarketIdentityCodec.read(buffer));
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }
}
