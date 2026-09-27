package com.nstut.economy.api.internal;

import com.nstut.economy.api.TeamRef;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Internal bridge between the stable team API and Economy-owned server lifecycle state. */
public final class TeamEconomyLifecycleBridge {
    private static volatile Consumer<TeamRef> observer = team -> {};
    private static volatile Predicate<UUID> closing = team -> false;

    private TeamEconomyLifecycleBridge() {}

    public static void bind(Consumer<TeamRef> nextObserver, Predicate<UUID> nextClosing) {
        observer = Objects.requireNonNull(nextObserver, "observer");
        closing = Objects.requireNonNull(nextClosing, "closing");
    }

    public static void clear() {
        observer = team -> {};
        closing = team -> false;
    }

    public static void observe(TeamRef team) {
        observer.accept(team);
    }

    public static boolean isClosing(UUID teamId) {
        return teamId != null && closing.test(teamId);
    }
}
