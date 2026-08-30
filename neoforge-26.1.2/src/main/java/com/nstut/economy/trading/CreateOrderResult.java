package com.nstut.economy.trading;

import java.util.List;

/**
 * Explicit outcome of an order creation request. Order existence on the book
 * only tells whether a remainder remains; it cannot express "fully executed"
 * or "rejected", so callers must branch on the status instead of null checks.
 */
public record CreateOrderResult(
        Status status,
        Order remainingOrder,
        int requestedQuantity,
        int filledQuantity,
        String errorKey,
        List<String> errorArgs
) {

    public enum Status { POSTED, PARTIALLY_FILLED, FILLED, REJECTED }

    public static CreateOrderResult rejected(String errorKey, List<String> errorArgs) {
        return new CreateOrderResult(Status.REJECTED, null, 0, 0, errorKey, List.copyOf(errorArgs));
    }

    public static CreateOrderResult rejected(int requestedQuantity, String errorKey, List<String> errorArgs) {
        return new CreateOrderResult(Status.REJECTED, null, requestedQuantity, 0, errorKey, List.copyOf(errorArgs));
    }

    public static CreateOrderResult filled(int requestedQuantity, int filledQuantity) {
        return new CreateOrderResult(Status.FILLED, null, requestedQuantity, filledQuantity, null, List.of());
    }

    public static CreateOrderResult posted(Order order, int requestedQuantity, int filledQuantity) {
        Status status = filledQuantity > 0 ? Status.PARTIALLY_FILLED : Status.POSTED;
        return new CreateOrderResult(status, order, requestedQuantity, filledQuantity, null, List.of());
    }
}
