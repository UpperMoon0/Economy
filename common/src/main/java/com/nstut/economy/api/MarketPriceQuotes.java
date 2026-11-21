package com.nstut.economy.api;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Stable, read-only server-side item price quotation boundary for addons.
 *
 * A quote may be absent when the Market has no live ask/bid or recent trades.
 * Callers must not treat an untrusted player-supplied ask as a guaranteed
 * liquidation price: apply their own payout multipliers and hard limits.
 */
public final class MarketPriceQuotes {
    @FunctionalInterface
    public interface Provider {
        Optional<BigDecimal> quote(String namespacedItemId);
    }

    private static final Provider UNAVAILABLE = ignored -> Optional.empty();
    private static volatile Provider provider = UNAVAILABLE;

    private MarketPriceQuotes() { }

    /** Installed by the Market runtime, not by a user transaction. */
    public static void install(Provider quoteProvider) {
        provider = Objects.requireNonNull(quoteProvider, "quoteProvider");
    }

    public static Optional<BigDecimal> quoteItem(String itemId) {
        if (itemId == null || itemId.length() > 128
                || !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return Optional.empty();
        try {
            Optional<BigDecimal> quote = provider.quote(itemId);
            return quote == null ? Optional.empty() : quote
                    .filter(price -> price.signum() > 0 && price.scale() <= 8
                            && price.compareTo(new BigDecimal("1000000000")) <= 0);
        } catch (RuntimeException error) {
            // Loading/teardown and unavailable Market orders are not fatal to addons.
            return Optional.empty();
        }
    }
}
