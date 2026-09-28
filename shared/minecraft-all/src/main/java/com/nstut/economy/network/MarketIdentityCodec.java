package com.nstut.economy.network;

import com.nstut.economy.api.AccountRef;
import com.nstut.economy.api.MarketIdentity;
import net.minecraft.network.FriendlyByteBuf;

public final class MarketIdentityCodec {
    private MarketIdentityCodec() {}
    public static void write(FriendlyByteBuf buffer, MarketIdentity identity) {
        buffer.writeBoolean(identity != null);
        if (identity != null) {
            buffer.writeUtf(identity.principal().toString());
            buffer.writeUUID(identity.actor());
            buffer.writeUUID(identity.storageOwner());
            buffer.writeUtf(identity.storageAccount().toString());
        }
    }
    public static MarketIdentity read(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? new MarketIdentity(AccountRef.parse(buffer.readUtf()), buffer.readUUID(), buffer.readUUID(), AccountRef.parse(buffer.readUtf())) : null;
    }
}
