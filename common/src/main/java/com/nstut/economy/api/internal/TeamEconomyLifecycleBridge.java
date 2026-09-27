package com.nstut.economy.api.internal;

import com.nstut.economy.api.EconomyId;
import com.nstut.economy.api.TeamRef;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** Internal bridge between the stable team API and Economy-owned server lifecycle state. */
public final class TeamEconomyLifecycleBridge {
    private static volatile BiConsumer<EconomyId, TeamRef> observer = (provider, team) -> {};
    private static volatile Predicate<UUID> closing = team -> false;
    private static volatile BiPredicate<EconomyId, UUID> allowsProvider = (provider, team) -> true;
    private static volatile BiPredicate<EconomyId, UUID> ownsProvider = (provider, team) -> false;

    private TeamEconomyLifecycleBridge() {}

    public static void bind(BiConsumer<EconomyId, TeamRef> nextObserver, Predicate<UUID> nextClosing,
                            BiPredicate<EconomyId, UUID> nextAllowsProvider,
                            BiPredicate<EconomyId, UUID> nextOwnsProvider) {
        observer = Objects.requireNonNull(nextObserver, "observer");
        closing = Objects.requireNonNull(nextClosing, "closing");
        allowsProvider = Objects.requireNonNull(nextAllowsProvider, "allowsProvider");
        ownsProvider = Objects.requireNonNull(nextOwnsProvider, "ownsProvider");
    }

    public static void clear() {
        observer = (provider, team) -> {};
        closing = team -> false;
        allowsProvider = (provider, team) -> true;
        ownsProvider = (provider, team) -> false;
    }

    public static void observe(EconomyId providerId, TeamRef team) {
        observer.accept(Objects.requireNonNull(providerId, "providerId"), team);
    }

    public static boolean isClosing(UUID teamId) {
        return teamId != null && closing.test(teamId);
    }

    /** True when this provider may positively claim/use the Team id; unknown legacy state is claimable. */
    public static boolean allowsProvider(EconomyId providerId, UUID teamId) {
        return providerId != null && teamId != null && allowsProvider.test(providerId, teamId);
    }

    /** True only when persisted lifecycle state already belongs to this provider. */
    public static boolean ownsProvider(EconomyId providerId, UUID teamId) {
        return providerId != null && teamId != null && ownsProvider.test(providerId, teamId);
    }
}
