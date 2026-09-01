package com.nstut.economy.api;

import net.minecraft.server.level.ServerLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Read/operation contract for a buy or sell order. */
public interface IOrder {
    UUID getOrderId();
    UUID getOwner();
    ICommodity getCommodity();
    int getQuantity();
    BigDecimal getPricePerUnit();

    default BigDecimal getTotalPrice() {
        return getPricePerUnit().multiply(BigDecimal.valueOf(getQuantity()));
    }

    OrderType getType();
    Instant getCreatedAt();
    Instant getExpiresAt();
    boolean isValid();
    boolean canExecute(UUID trader);
    TransactionResult execute(UUID trader, ServerLevel level);
    TransactionResult execute(UUID trader);
    boolean cancel();

    enum OrderType { BUY, SELL }

    final class TransactionResult {
        public final boolean success;
        public final String message;
        public final BigDecimal amountTransferred;
        public final int quantityTransferred;

        public TransactionResult(boolean success, String message,
                                 BigDecimal amountTransferred, int quantityTransferred) {
            this.success = success;
            this.message = message;
            this.amountTransferred = amountTransferred;
            this.quantityTransferred = quantityTransferred;
        }

        public static TransactionResult success(String message, BigDecimal amount, int quantity) {
            return new TransactionResult(true, message, amount, quantity);
        }

        public static TransactionResult failure(String message) {
            return new TransactionResult(false, message, BigDecimal.ZERO, 0);
        }
    }
}
