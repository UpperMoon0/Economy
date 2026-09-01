package com.nstut.economy.api;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Loader-neutral domain event bus. Economy publishes domain events here from
 * the same code paths used by commands, UI/network handlers, and addon calls.
 * Listeners execute synchronously on the thread performing the economy action.
 */
public final class EconomyEvents {
    private static final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Consumer<?>>> LISTENERS =
            new ConcurrentHashMap<>();

    private EconomyEvents() { }

    public interface Event { }

    public abstract static class CancellableEvent implements Event {
        private boolean cancelled;

        public final boolean isCancelled() {
            return cancelled;
        }

        public final void cancel() {
            this.cancelled = true;
        }
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    public static <E extends Event> Subscription listen(Class<E> eventType, Consumer<E> listener) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(listener, "listener");
        CopyOnWriteArrayList<Consumer<?>> listeners =
                LISTENERS.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>());
        listeners.add(listener);
        return () -> {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                LISTENERS.remove(eventType, listeners);
            }
        };
    }

    /** Publishes synchronously and returns the same event for cancellation checks. */
    @SuppressWarnings("unchecked")
    public static <E extends Event> E post(E event) {
        Objects.requireNonNull(event, "event");
        for (Consumer<?> raw : LISTENERS.getOrDefault(event.getClass(), new CopyOnWriteArrayList<>())) {
            ((Consumer<E>) raw).accept(event);
        }
        return event;
    }

    /** Internal/test lifecycle cleanup; addon subscriptions should normally close themselves. */
    public static void clearListeners() {
        LISTENERS.clear();
    }

    public static final class BalanceChangePre extends CancellableEvent {
        private final UUID owner;
        private final BigDecimal previousBalance;
        private final BigDecimal delta;
        private final ITransactionContext context;

        public BalanceChangePre(UUID owner, BigDecimal previousBalance, BigDecimal delta,
                                ITransactionContext context) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.previousBalance = Objects.requireNonNull(previousBalance, "previousBalance");
            this.delta = Objects.requireNonNull(delta, "delta");
            this.context = Objects.requireNonNull(context, "context");
        }

        public UUID owner() { return owner; }
        public BigDecimal previousBalance() { return previousBalance; }
        public BigDecimal delta() { return delta; }
        public BigDecimal resultingBalance() { return previousBalance.add(delta); }
        public ITransactionContext context() { return context; }
    }

    public record BalanceChanged(UUID owner, BigDecimal previousBalance, BigDecimal balance,
                                 BigDecimal delta, ITransactionContext context) implements Event {
        public BalanceChanged {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(previousBalance, "previousBalance");
            Objects.requireNonNull(balance, "balance");
            Objects.requireNonNull(delta, "delta");
            Objects.requireNonNull(context, "context");
        }
    }

    public static final class TransferPre extends CancellableEvent {
        private final UUID source;
        private final UUID target;
        private final BigDecimal amount;
        private final ITransactionContext context;

        public TransferPre(UUID source, UUID target, BigDecimal amount, ITransactionContext context) {
            this.source = Objects.requireNonNull(source, "source");
            this.target = Objects.requireNonNull(target, "target");
            this.amount = Objects.requireNonNull(amount, "amount");
            this.context = Objects.requireNonNull(context, "context");
        }

        public UUID source() { return source; }
        public UUID target() { return target; }
        public BigDecimal amount() { return amount; }
        public ITransactionContext context() { return context; }
    }

    public record TransferCompleted(UUID source, UUID target, BigDecimal amount,
                                    ITransactionContext context) implements Event {
        public TransferCompleted {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(context, "context");
        }
    }
}
