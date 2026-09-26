package com.nstut.economy.api;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Loader-neutral synchronous account/transaction event bus. */
public final class EconomyEvents {
    private static final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Consumer<?>>> LISTENERS = new ConcurrentHashMap<>();
    private static final System.Logger LOGGER = System.getLogger(EconomyEvents.class.getName());
    private EconomyEvents() { }
    public interface Event { }
    public abstract static class CancellableEvent implements Event {
        private boolean cancelled;
        public final boolean isCancelled() { return cancelled; }
        public final void cancel() { cancelled = true; }
    }
    @FunctionalInterface public interface Subscription extends AutoCloseable { @Override void close(); }
    public static <E extends Event> Subscription listen(Class<E> eventType, Consumer<E> listener) {
        Objects.requireNonNull(eventType); Objects.requireNonNull(listener);
        CopyOnWriteArrayList<Consumer<?>> listeners = LISTENERS.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>());
        listeners.add(listener);
        return () -> { listeners.remove(listener); if (listeners.isEmpty()) LISTENERS.remove(eventType, listeners); };
    }
    @SuppressWarnings("unchecked")
    public static <E extends Event> E post(E event) {
        Objects.requireNonNull(event);
        for (Consumer<?> raw : LISTENERS.getOrDefault(event.getClass(), new CopyOnWriteArrayList<>())) {
            try {
                ((Consumer<E>) raw).accept(event);
            } catch (RuntimeException failure) {
                LOGGER.log(System.Logger.Level.ERROR,
                        "Economy event listener failed for " + event.getClass().getName(), failure);
                // Pre-events are fail-closed: a broken veto listener must never allow the mutation to commit.
                if (event instanceof CancellableEvent cancellable) cancellable.cancel();
            }
        }
        return event;
    }
    public static void clearListeners() { LISTENERS.clear(); }

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
