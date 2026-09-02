package com.nstut.economy.trading;

import com.nstut.economy.api.OrderCreateResult;

import java.util.List;

/**
 * Internal concrete result retained for source compatibility. It now extends
 * the stable addon-facing result so {@link OrderManager} can implement the
 * public order service without changing existing internal callers.
 */
public final class CreateOrderResult extends OrderCreateResult {
    public CreateOrderResult(Status status, Order remainingOrder, int requestedQuantity,
                             int filledQuantity, String errorKey, List<String> errorArgs) {
        super(status, remainingOrder, requestedQuantity, filledQuantity, errorKey, errorArgs);
    }

    @Override
    public Order remainingOrder() {
        return (Order) super.remainingOrder();
    }

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
