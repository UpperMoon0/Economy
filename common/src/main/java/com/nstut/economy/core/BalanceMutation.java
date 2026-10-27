package com.nstut.economy.core;

import java.math.BigDecimal;
import java.util.UUID;

/** A committed operation, independently identified from reusable caller transaction contexts. */
public record BalanceMutation(UUID operationId, long timestamp, String source, String reason,
                              BigDecimal before, BigDecimal after, BigDecimal externalFlow, BigDecimal cashDelta,
                              boolean groupedFinalMark) {
    public BalanceMutation {
        source = bounded(source, 256);
        reason = bounded(reason, 1024);
    }

    private static String bounded(String text, int limit) {
        if (text == null) return "";
        return text.length() <= limit ? text : text.substring(0, limit - 3) + "...";
    }

    public BalanceMutation(UUID operationId, long timestamp, String source, String reason,
                           BigDecimal before, BigDecimal after, BigDecimal externalFlow, BigDecimal cashDelta) {
        this(operationId, timestamp, source, reason, before, after, externalFlow, cashDelta, false);
    }

    public BalanceMutation(UUID operationId, long timestamp, String source, String reason,
                           BigDecimal before, BigDecimal after, BigDecimal externalFlow) {
        this(operationId, timestamp, source, reason, before, after, externalFlow, after.subtract(before));
    }
}
