package com.nstut.economy.network;

import net.minecraft.network.FriendlyByteBuf;

public class HistoryEntry {
    public final com.nstut.economy.api.MarketIdentity buyerIdentity;
    public final com.nstut.economy.api.MarketIdentity sellerIdentity;
    public final String itemId;
    public final String displayName;
    public final String price;
    public final int quantity;
    /** true = player was the seller, false = buyer */
    public final boolean wasSell;
    /** epoch-millis */
    public final long timestamp;
    public final String counterparty;

    public HistoryEntry(String itemId, String displayName, String price, int quantity,
                        boolean wasSell, long timestamp, String counterparty) {
        this(itemId, displayName, price, quantity, wasSell, timestamp, counterparty, null, null);
    }
    public HistoryEntry(String itemId, String displayName, String price, int quantity,
                        boolean wasSell, long timestamp, String counterparty,
                        com.nstut.economy.api.MarketIdentity buyerIdentity, com.nstut.economy.api.MarketIdentity sellerIdentity) {
        this.buyerIdentity = buyerIdentity;
        this.sellerIdentity = sellerIdentity;
        this.itemId = itemId;
        this.displayName = displayName;
        this.price = price;
        this.quantity = quantity;
        this.wasSell = wasSell;
        this.timestamp = timestamp;
        this.counterparty = counterparty;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(itemId);
        buf.writeUtf(displayName);
        buf.writeUtf(price);
        buf.writeInt(quantity);
        buf.writeBoolean(wasSell);
        buf.writeLong(timestamp);
        buf.writeUtf(counterparty);
        MarketIdentityCodec.write(buf, buyerIdentity);
        MarketIdentityCodec.write(buf, sellerIdentity);
    }

    public static HistoryEntry read(FriendlyByteBuf buf) {
        return new HistoryEntry(buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readInt(), buf.readBoolean(), buf.readLong(), buf.readUtf(), MarketIdentityCodec.read(buf), MarketIdentityCodec.read(buf));
    }
}


