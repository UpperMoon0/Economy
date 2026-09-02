package com.nstut.economy.api;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Market-specific event payloads published through {@link EconomyEvents}. */
public final class MarketEvents {
    private MarketEvents() { }

    public static final class OrderCreatePre extends EconomyEvents.CancellableEvent {
        private final UUID owner; private final ICommodity commodity; private final IOrder.OrderType type;
        private final int quantity; private final BigDecimal pricePerUnit;
        public OrderCreatePre(UUID owner, ICommodity commodity, IOrder.OrderType type, int quantity, BigDecimal pricePerUnit) {
            this.owner = Objects.requireNonNull(owner); this.commodity = Objects.requireNonNull(commodity);
            this.type = Objects.requireNonNull(type); this.quantity = quantity; this.pricePerUnit = Objects.requireNonNull(pricePerUnit);
        }
        public UUID owner() { return owner; }
        public ICommodity commodity() { return commodity; }
        public IOrder.OrderType type() { return type; }
        public int quantity() { return quantity; }
        public BigDecimal pricePerUnit() { return pricePerUnit; }
    }

    public record OrderCreated(IOrder order, int requestedQuantity, int filledQuantity) implements EconomyEvents.Event { }
    public record OrderEdited(IOrder order) implements EconomyEvents.Event { }
    public record OrderCancelled(UUID orderId, UUID owner) implements EconomyEvents.Event { }
    public record TradeCompleted(TradeView trade) implements EconomyEvents.Event { }
}
