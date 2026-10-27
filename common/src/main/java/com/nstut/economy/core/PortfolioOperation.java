package com.nstut.economy.core;

import com.nstut.economy.api.AccountRef;
import com.nstut.economy.api.ITransactionContext;
import com.nstut.economy.api.TransactionCauses;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Groups payment and compensation into their final committed trade outcome. No balance rollback. */
public final class PortfolioOperation implements AutoCloseable {
    private static final ThreadLocal<PortfolioOperation> CURRENT = new ThreadLocal<>();
    private final PortfolioOperation parent;
    private final UUID id = UUID.randomUUID();
    private final long timestamp = System.currentTimeMillis();
    private final String source, reason;
    private final boolean external;
    private UUID contextId;
    private final Map<AccountRef, Pending> pending = new LinkedHashMap<>();
    private boolean closed;
    private record Pending(BigDecimal before, BigDecimal after, BigDecimal delta, Consumer<BalanceMutation> sink) { }

    private PortfolioOperation(String source, String reason, boolean external) {
        this.source = source; this.reason = reason; this.external = external;
        parent = CURRENT.get();
        CURRENT.set(this);
    }

    public static PortfolioOperation trade(String reason) { return new PortfolioOperation("economy:trade", reason, false); }

    public static boolean isActive() { return CURRENT.get() != null; }

    static PortfolioOperation transfer(ITransactionContext context) {
        PortfolioOperation operation = new PortfolioOperation(context.getCauseId().toString(), context.getDescription(),
                !TransactionCauses.TRADE.equals(context.getCauseId()));
        operation.contextId = context.getTransactionId();
        return operation;
    }

    static void committed(AccountRef account, BigDecimal before, BigDecimal after,
                          ITransactionContext context, Consumer<BalanceMutation> sink) {
        if (before.compareTo(after) == 0) return;
        PortfolioOperation operation = CURRENT.get();
        if (operation != null && operation.accepts(context)) {
            Pending previous = operation.pending.get(account);
            operation.pending.put(account, new Pending(previous == null ? before : previous.before, after,
                    (previous == null ? BigDecimal.ZERO : previous.delta).add(after.subtract(before)), sink));
        } else {
            BigDecimal delta = after.subtract(before);
            boolean internal = TransactionCauses.TRADE.equals(context.getCauseId());
            deliver(sink, new BalanceMutation(UUID.randomUUID(), System.currentTimeMillis(),
                    context.getCauseId().toString(), context.getDescription(), before, after,
                    internal ? BigDecimal.ZERO : delta));
        }
    }

    private boolean accepts(ITransactionContext context) {
        return contextId != null ? contextId.equals(context.getTransactionId())
                : source.equals(context.getCauseId().toString());
    }

    private static void deliver(Consumer<BalanceMutation> sink, BalanceMutation mutation) {
        try { sink.accept(mutation); }
        catch (RuntimeException failure) {
            System.getLogger(PortfolioOperation.class.getName()).log(System.Logger.Level.ERROR,
                    "Committed portfolio operation could not be recorded: " + mutation.operationId(), failure);
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        CURRENT.set(parent);
        for (var entry : pending.entrySet()) {
            Pending value = entry.getValue();
            if (parent != null && ((parent.contextId == null && parent.source.equals(source))
                    || (contextId != null && contextId.equals(parent.contextId)))) {
                Pending earlier = parent.pending.get(entry.getKey());
                parent.pending.put(entry.getKey(), new Pending(earlier == null ? value.before : earlier.before,
                        value.after, (earlier == null ? BigDecimal.ZERO : earlier.delta).add(value.delta), value.sink));
            } else if (value.delta.signum() != 0) {
                deliver(value.sink, new BalanceMutation(id, timestamp, source, reason,
                        value.before, value.after, external ? value.delta : BigDecimal.ZERO, value.delta));
            }
        }
    }
}
