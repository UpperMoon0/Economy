package com.nstut.economy.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Stable public result of submitting an order to the market. */
public class OrderCreateResult {
    public enum Status { POSTED, PARTIALLY_FILLED, FILLED, REJECTED }

    private final Status status;
    private final IOrder remainingOrder;
    private final int requestedQuantity;
    private final int filledQuantity;
    private final String errorKey;
    private final List<String> errorArgs;

    public OrderCreateResult(Status status, IOrder remainingOrder, int requestedQuantity,
                             int filledQuantity, String errorKey, List<String> errorArgs) {
        this.status = Objects.requireNonNull(status, "status");
        this.remainingOrder = remainingOrder;
        this.requestedQuantity = requestedQuantity;
        this.filledQuantity = filledQuantity;
        this.errorKey = errorKey;
        this.errorArgs = errorArgs == null ? List.of() : List.copyOf(errorArgs);
    }

    public Status status() { return status; }
    public IOrder remainingOrder() { return remainingOrder; }
    public int requestedQuantity() { return requestedQuantity; }
    public int filledQuantity() { return filledQuantity; }
    public String errorKey() { return errorKey; }
    public List<String> errorArgs() { return errorArgs; }

    public Optional<IOrder> order() { return Optional.ofNullable(remainingOrder); }
    public boolean accepted() { return status != Status.REJECTED; }
}
