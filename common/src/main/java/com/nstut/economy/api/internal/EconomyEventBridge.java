package com.nstut.economy.api.internal;

import com.nstut.economy.api.EconomyEvents;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Internal implementation/reset bridge for Economy's loader-neutral event bus. */
public final class EconomyEventBridge {
    private static final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Consumer<?>>> LISTENERS = new ConcurrentHashMap<>();
    private static final System.Logger LOGGER = System.getLogger(EconomyEvents.class.getName());

    private EconomyEventBridge() {}

    public static <E extends EconomyEvents.Event> EconomyEvents.Subscription listen(
            Class<E> eventType, Consumer<E> listener) {
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(listener);
        CopyOnWriteArrayList<Consumer<?>> listeners =
                LISTENERS.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>());
        listeners.add(listener);
        return () -> {
            listeners.remove(listener);
            if (listeners.isEmpty()) LISTENERS.remove(eventType, listeners);
        };
    }

    @SuppressWarnings("unchecked")
    public static <E extends EconomyEvents.Event> E post(E event) {
        Objects.requireNonNull(event);
        for (Consumer<?> raw : LISTENERS.getOrDefault(event.getClass(), new CopyOnWriteArrayList<>())) {
            try {
                ((Consumer<E>) raw).accept(event);
            } catch (RuntimeException failure) {
                LOGGER.log(System.Logger.Level.ERROR,
                        "Economy event listener failed for " + event.getClass().getName(), failure);
                if (event instanceof EconomyEvents.CancellableEvent cancellable) cancellable.cancel();
            }
        }
        return event;
    }

    public static void clearListeners() {
        LISTENERS.clear();
    }
}
