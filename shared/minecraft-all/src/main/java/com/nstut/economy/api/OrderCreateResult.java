package com.nstut.economy.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Stable public result of submitting an order to the market. */
public record OrderCreateResult(
        Status status,
        IOrder remainingOrder,
        int requestedQuantity,
        int filledQuantity,
        String errorKey,
        List<String> errorArgs
) {
    public enum Status { POSTED, PARTIALLY_FILLED, FILLED, REJECTED }

    public OrderCreateResult {
        Objects.requireNonNull(status, "status");
        errorArgs = errorArgs == null ? List.of() : List.copyOf(errorArgs);
    }

    public Optional<IOrder> order() {
        return Optional.ofNullable(remainingOrder);
    }

    public boolean accepted() {
        return status != Status.REJECTED;
    }
}
