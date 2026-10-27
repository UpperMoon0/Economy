package com.nstut.economy.core;

import com.nstut.economy.api.AccountRef;
import com.nstut.economy.api.ITransactionContext;
import com.nstut.economy.api.TransactionCauses;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Groups payment and compensation into their final committed trade outcome. No balance rollback. */
public final class PortfolioOperation implements AutoCloseable {
    private static final ThreadLocal<PortfolioOperation> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<ExternalCreditPermit> EXTERNAL_CREDIT = new ThreadLocal<>();

    /** One exact delegated credit can be a transfer leg even behind a transparent account wrapper. */
    static final class ExternalCreditPermit implements AutoCloseable {
        private final ExternalCreditPermit previous;
        private final PortfolioOperation operation;
        private final AccountRef account;
        private final ITransactionContext context;
        private final BigDecimal amount;
        private boolean claimed;
        private ExternalCreditPermit(AccountRef account, ITransactionContext context, BigDecimal amount) {
            this.previous = EXTERNAL_CREDIT.get();
            this.operation = CURRENT.get();
            this.account = account; this.context = context; this.amount = amount;
            EXTERNAL_CREDIT.set(this);
        }
        @Override public void close() { EXTERNAL_CREDIT.set(previous); }
    }

    static ExternalCreditPermit externalCreditLeg(AccountRef account, ITransactionContext context, BigDecimal amount) {
        return new ExternalCreditPermit(account, context, amount);
    }

    static boolean claimExternalCreditLeg(AccountRef account, ITransactionContext context, BigDecimal amount) {
        ExternalCreditPermit permit = EXTERNAL_CREDIT.get();
        if (permit == null || permit.claimed || permit.operation != CURRENT.get()
                || !permit.account.equals(account) || permit.context != context || permit.amount.compareTo(amount) != 0) return false;
        permit.claimed = true;
        return true;
    }

    private final PortfolioOperation parent;
    private final UUID id = UUID.randomUUID();
    private final long timestamp = System.currentTimeMillis();
    private final String source, reason;
    private final boolean external;
    private UUID contextId;
    private final Map<AccountRef, Pending> pending = new LinkedHashMap<>();
    // Root-scoped observations exclude provisional payments from independently committed callbacks.
    private final Map<AccountRef, BigDecimal> openingBalances = new LinkedHashMap<>();
    private final List<Reported> completed = new ArrayList<>();
    private final long sequence;
    private long nextSequence = 1;
    private boolean closed;
    private record Reported(long sequence, AccountRef account, BalanceMutation mutation, Consumer<BalanceMutation> sink) { }
    private record Pending(BigDecimal before, BigDecimal after, BigDecimal delta, Consumer<BalanceMutation> sink) { }

    private PortfolioOperation(String source, String reason, boolean external) {
        this.source = source; this.reason = reason; this.external = external;
        parent = CURRENT.get();
        sequence = parent == null ? 0 : parent.root().nextSequence++;
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
        committed(account, before, after, context, sink, false);
    }

    static void committed(AccountRef account, BigDecimal before, BigDecimal after,
                          ITransactionContext context, Consumer<BalanceMutation> sink, boolean transferLeg) {
        if (before.compareTo(after) == 0) return;
        PortfolioOperation operation = CURRENT.get();
        if (operation != null) operation.root().openingBalances.putIfAbsent(account, before);
        if (transferLeg && operation != null && operation.accepts(context)) {
            Pending previous = operation.pending.get(account);
            operation.pending.put(account, new Pending(previous == null ? before : previous.before, after,
                    (previous == null ? BigDecimal.ZERO : previous.delta).add(after.subtract(before)), sink));
        } else {
            BigDecimal delta = after.subtract(before);
            boolean internal = transferLeg && TransactionCauses.TRADE.equals(context.getCauseId());
            BalanceMutation mutation = new BalanceMutation(UUID.randomUUID(), System.currentTimeMillis(),
                    context.getCauseId().toString(), context.getDescription(), before, after,
                    internal ? BigDecimal.ZERO : delta);
            if (operation == null) deliver(sink, mutation);
            else operation.root().completed.add(new Reported(operation.root().nextSequence++, account, mutation, sink));
        }
    }

    private PortfolioOperation root() {
        return parent == null ? this : parent.root();
    }

    private boolean accepts(ITransactionContext context) {
        return contextId != null && contextId.equals(context.getTransactionId());
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
            // Transfer/refund children belong to their enclosing order. A different nested
            // order has its own trade scope and must retain its own operation identity.
            if (parent != null && contextId != null && parent.contextId == null && parent.source.equals(source)) {
                Pending earlier = parent.pending.get(entry.getKey());
                parent.pending.put(entry.getKey(), new Pending(earlier == null ? value.before : earlier.before,
                        value.after, (earlier == null ? BigDecimal.ZERO : earlier.delta).add(value.delta), value.sink));
            } else if (value.delta.signum() != 0) {
                root().completed.add(new Reported(sequence, entry.getKey(),
                        new BalanceMutation(id, timestamp, source, reason, value.before, value.after,
                                external ? value.delta : BigDecimal.ZERO, value.delta), value.sink));
            }
        }
        if (parent == null) {
            // Scope-start order matches operation timestamps and preserves funding order.
            // Rebuild the cash path from committed deltas, excluding fully refunded attempts.
            completed.sort(java.util.Comparator.comparingLong(Reported::sequence));
            boolean groupedFinalMark = completed.stream().map(report -> report.mutation.operationId()).distinct().count() > 1;
            for (Reported report : completed) {
                BalanceMutation mutation = report.mutation;
                BigDecimal before = openingBalances.get(report.account);
                BigDecimal after = before.add(mutation.cashDelta());
                openingBalances.put(report.account, after);
                String reason = groupedFinalMark
                        ? "Grouped settlement final valuation; per-operation cash delta. " + mutation.reason()
                        : mutation.reason();
                deliver(report.sink, new BalanceMutation(mutation.operationId(), mutation.timestamp(),
                        mutation.source(), reason, before, after, mutation.externalFlow(), mutation.cashDelta(), groupedFinalMark));
            }
        }
    }
}
