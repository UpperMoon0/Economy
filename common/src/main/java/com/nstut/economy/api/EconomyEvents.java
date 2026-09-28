package com.nstut.economy.api;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Loader-neutral synchronous account/transaction event bus. */
public final class EconomyEvents {
    private EconomyEvents() { }
    public interface Event { }
    public abstract static class CancellableEvent implements Event {
        private boolean cancelled;
        public final boolean isCancelled() { return cancelled; }
        public final void cancel() { cancelled = true; }
    }
    @FunctionalInterface public interface Subscription extends AutoCloseable { @Override void close(); }
    public static <E extends Event> Subscription listen(Class<E> eventType, Consumer<E> listener) {
        return com.nstut.economy.api.internal.EconomyEventBridge.listen(eventType, listener);
    }
    public static <E extends Event> E post(E event) {
        return com.nstut.economy.api.internal.EconomyEventBridge.post(event);
    }

    /**
     * @deprecated Compatibility shim retained for addons compiled against Economy 0.0.13.
     * This clears every listener globally, including listeners owned by other addons. New code must
     * retain the {@link Subscription} returned by {@link #listen(Class, Consumer)} and close only
     * its own subscription. Global reset remains internal lifecycle/test machinery.
     */
    @Deprecated
    public static void clearListeners() {
        com.nstut.economy.api.internal.EconomyEventBridge.clearListeners();
    }

    public static final class BalanceChangePre extends CancellableEvent {
        private final AccountRef accountRef; private final BigDecimal previousBalance; private final BigDecimal delta; private final ITransactionContext context;
        public BalanceChangePre(UUID owner, BigDecimal previousBalance, BigDecimal delta, ITransactionContext context) {
            this(AccountRef.player(owner), previousBalance, delta, context);
        }
        public BalanceChangePre(AccountRef accountRef, BigDecimal previousBalance, BigDecimal delta, ITransactionContext context) {
            this.accountRef = Objects.requireNonNull(accountRef); this.previousBalance = Objects.requireNonNull(previousBalance);
            this.delta = Objects.requireNonNull(delta); this.context = Objects.requireNonNull(context);
        }
        /** Legacy UUID projection; use accountRef() for authorization and auditing. */
        public UUID owner() { return accountRef.id(); }
        public AccountRef accountRef() { return accountRef; }
        public BigDecimal previousBalance() { return previousBalance; }
        public BigDecimal delta() { return delta; }
        public BigDecimal resultingBalance() { return previousBalance.add(delta); }
        public ITransactionContext context() { return context; }
    }
    public record BalanceChanged(AccountRef accountRef, BigDecimal previousBalance, BigDecimal balance, BigDecimal delta, ITransactionContext context) implements Event {
        public BalanceChanged { Objects.requireNonNull(accountRef); }
        public BalanceChanged(UUID owner, BigDecimal previousBalance, BigDecimal balance, BigDecimal delta, ITransactionContext context) {
            this(AccountRef.player(owner), previousBalance, balance, delta, context);
        }
        /** Legacy UUID projection; use accountRef() for authorization and auditing. */
        public UUID owner() { return accountRef.id(); }
    }

    public static final class TransferPre extends CancellableEvent {
        private final AccountRef sourceRef; private final AccountRef targetRef; private final BigDecimal amount; private final ITransactionContext context;
        public TransferPre(UUID source, UUID target, BigDecimal amount, ITransactionContext context) {
            this(AccountRef.player(source), AccountRef.player(target), amount, context);
        }
        public TransferPre(AccountRef sourceRef, AccountRef targetRef, BigDecimal amount, ITransactionContext context) {
            this.sourceRef = Objects.requireNonNull(sourceRef); this.targetRef = Objects.requireNonNull(targetRef);
            this.amount = Objects.requireNonNull(amount); this.context = Objects.requireNonNull(context);
        }
        public UUID source() { return sourceRef.id(); }
        public AccountRef sourceRef() { return sourceRef; }
        public UUID target() { return targetRef.id(); }
        public AccountRef targetRef() { return targetRef; }
        public BigDecimal amount() { return amount; }
        public ITransactionContext context() { return context; }
    }
    public record TransferCompleted(AccountRef sourceRef, AccountRef targetRef, BigDecimal amount, ITransactionContext context) implements Event {
        public TransferCompleted { Objects.requireNonNull(sourceRef); Objects.requireNonNull(targetRef); }
        public TransferCompleted(UUID source, UUID target, BigDecimal amount, ITransactionContext context) {
            this(AccountRef.player(source), AccountRef.player(target), amount, context);
        }
        public UUID source() { return sourceRef.id(); }
        public UUID target() { return targetRef.id(); }
    }
}
