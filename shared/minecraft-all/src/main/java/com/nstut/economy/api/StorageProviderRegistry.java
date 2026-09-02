package com.nstut.economy.api;

import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Thread-safe registry and dispatcher for addon market storage providers. */
public final class StorageProviderRegistry {
    private final Map<EconomyId, IStorageProvider> providers = new ConcurrentHashMap<>();
    private final Supplier<IOrderManager> recoveryOrders;

    public StorageProviderRegistry() {
        this(EconomyApi::orders);
    }

    StorageProviderRegistry(Supplier<IOrderManager> recoveryOrders) {
        this.recoveryOrders = recoveryOrders;
    }

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

    /**
     * Maximum amount that can be reserved atomically from one provider. This intentionally
     * mirrors {@link #reserve}; reservations are provider-owned and are not split across providers.
     */
    public int available(ServerLevel level, UUID owner, ICommodity commodity) {
        int max = 0;
        for (IStorageProvider provider : providers()) {
            if (provider.supports(commodity)) {
                max = Math.max(max, Math.max(0, provider.available(level, owner, commodity)));
            }
        }
        return max;
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
            Optional<StorageReservation> candidate = provider.reserve(level, owner, commodity, amount);
            if (candidate.isEmpty()) continue;

            StorageReservation reservation = candidate.get();
            String violation = reservationViolation(provider, commodity, amount, reservation);
            if (violation != null) {
                IllegalStateException invalid = new IllegalStateException(
                        "Storage provider " + provider.id() + " returned invalid reservation: " + violation);
                boolean released = false;
                try {
                    released = provider.release(level, reservation);
                } catch (RuntimeException releaseFailure) {
                    invalid.addSuppressed(releaseFailure);
                }
                if (!released) {
                    String reason = "invalid provider reservation could not be released: " + provider.id() + " (" + violation + ")";
                    if (!preserveUnreleasedReservation(owner, commodity, reservation, reason, invalid)) {
                        invalid.addSuppressed(new IllegalStateException(
                                "Provider escrow could not be durably quarantined: " + reservation.token()));
                    }
                }
                throw invalid;
            }
            return candidate;
        }
        return Optional.empty();
    }

    private boolean preserveUnreleasedReservation(UUID owner, ICommodity commodity,
                                                  StorageReservation reservation, String reason,
                                                  IllegalStateException parent) {
        try {
            IOrderManager orders = recoveryOrders.get();
            if (orders == null) return false;
            orders.preserveProviderReservation(owner, commodity, reservation, null, reason);
            return true;
        } catch (RuntimeException preservationFailure) {
            parent.addSuppressed(preservationFailure);
            return false;
        }
    }

    private static String reservationViolation(IStorageProvider provider, ICommodity commodity, int requested,
                                               StorageReservation reservation) {
        if (!provider.id().equals(reservation.providerId())) {
            return "providerId=" + reservation.providerId() + " expected=" + provider.id();
        }
        if (!commodity.getId().equals(reservation.commodityId())) {
            return "commodityId=" + reservation.commodityId() + " expected=" + commodity.getId();
        }
        if (reservation.amount() != requested) {
            return "amount=" + reservation.amount() + " expected=" + requested;
        }
        if (reservation.token().isBlank()) {
            return "blank reservation token";
        }
        return null;
    }

    public StorageDeliveryResult deliver(ServerLevel level, StorageReservation reservation, UUID receiver, int amount) {
        IStorageProvider provider = providers.get(reservation.providerId());
        if (provider == null || amount <= 0) return StorageDeliveryResult.unchanged(reservation);
        return provider.deliverReserved(level, reservation, receiver, amount)
                .validateAgainst(reservation, Math.min(amount, reservation.amount()));
    }

    public boolean release(ServerLevel level, StorageReservation reservation) {
        IStorageProvider provider = providers.get(reservation.providerId());
        return provider != null && provider.release(level, reservation);
    }

    public record StorageProviderRegistered(EconomyId providerId) implements EconomyEvents.Event { }
    public record StorageProviderUnregistered(EconomyId providerId) implements EconomyEvents.Event { }
}
