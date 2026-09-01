package com.nstut.economy.api;

import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe registry and dispatcher for addon market storage providers. */
public final class StorageProviderRegistry {
    private final Map<EconomyId, IStorageProvider> providers = new ConcurrentHashMap<>();

    public void register(IStorageProvider provider) {
        IStorageProvider previous = providers.putIfAbsent(provider.id(), provider);
        if (previous != null && previous != provider) {
            throw new IllegalStateException("Storage provider already registered: " + provider.id());
        }
        EconomyEvents.post(new StorageProviderRegistered(provider.id()));
    }

    public boolean unregister(EconomyId id) {
        IStorageProvider removed = providers.remove(id);
        if (removed != null) EconomyEvents.post(new StorageProviderUnregistered(id));
        return removed != null;
    }

    public Optional<IStorageProvider> provider(EconomyId id) {
        return Optional.ofNullable(providers.get(id));
    }

    public List<IStorageProvider> providers() {
        ArrayList<IStorageProvider> result = new ArrayList<>(providers.values());
        result.sort(Comparator.comparingInt(IStorageProvider::priority).reversed()
                .thenComparing(p -> p.id().toString()));
        return List.copyOf(result);
    }

    public int available(ServerLevel level, UUID owner, ICommodity commodity) {
        long total = 0;
        for (IStorageProvider provider : providers()) {
            if (provider.supports(commodity)) total += Math.max(0, provider.available(level, owner, commodity));
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public int receivable(ServerLevel level, UUID owner, ICommodity commodity, int requested) {
        int remaining = Math.max(0, requested);
        int total = 0;
        for (IStorageProvider provider : providers()) {
            if (!provider.supports(commodity) || remaining == 0) continue;
            int accepted = Math.max(0, Math.min(remaining, provider.receivable(level, owner, commodity, remaining)));
            total += accepted;
            remaining -= accepted;
        }
        return total;
    }

    public Optional<StorageReservation> reserve(ServerLevel level, UUID owner, ICommodity commodity, int amount) {
        if (amount <= 0) return Optional.empty();
        for (IStorageProvider provider : providers()) {
            if (!provider.supports(commodity) || provider.available(level, owner, commodity) < amount) continue;
            Optional<StorageReservation> reservation = provider.reserve(level, owner, commodity, amount);
            if (reservation.isPresent()) return reservation;
        }
        return Optional.empty();
    }

    public int deliver(ServerLevel level, StorageReservation reservation, UUID receiver, int amount) {
        IStorageProvider provider = providers.get(reservation.providerId());
        if (provider == null || amount <= 0) return 0;
        return Math.max(0, Math.min(amount, provider.deliverReserved(level, reservation, receiver, amount)));
    }

    public boolean release(ServerLevel level, StorageReservation reservation) {
        IStorageProvider provider = providers.get(reservation.providerId());
        return provider != null && provider.release(level, reservation);
    }

    public record StorageProviderRegistered(EconomyId providerId) implements EconomyEvents.Event { }
    public record StorageProviderUnregistered(EconomyId providerId) implements EconomyEvents.Event { }
}
