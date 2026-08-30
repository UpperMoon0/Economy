package com.nstut.economy.util;

import com.nstut.economy.config.EconomyConfig;
import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Server-side validation for client-supplied order inputs. Used by the
 * network layer before packets touch domain code, so malformed values are
 * rejected without ever reaching trading logic.
 */
public final class OrderInputValidator {
    private OrderInputValidator() {}

    /**
     * Exact reason a price was rejected, paired with the client-facing
     * translation key and arguments so the UI can show actionable feedback.
     */
    public enum PriceValidationError {
        EMPTY("ui.economy.error.price_required"),
        TOO_LONG("ui.economy.error.price_invalid"),
        INVALID_NUMBER("ui.economy.error.price_number"),
        NOT_POSITIVE("ui.economy.error.price_positive"),
        TOO_MANY_DECIMALS("ui.economy.error.price_scale"),
        TOO_MANY_DIGITS("ui.economy.error.price_digits"),
        BELOW_MINIMUM("ui.economy.error.price_below_min"),
        ABOVE_MAXIMUM("ui.economy.error.price_above_max");

        public final String messageKey;

        PriceValidationError(String messageKey) {
            this.messageKey = messageKey;
        }

        /** Localizable arguments (configured limits) for this rejection. */
        public List<String> args() {
            EconomyConfig config = EconomyConfig.getInstance();
            return switch (this) {
                case TOO_MANY_DECIMALS -> List.of(String.valueOf(config.getMaxPriceScale()));
                case TOO_MANY_DIGITS -> List.of(String.valueOf(config.getMaxPriceDigits()));
                case BELOW_MINIMUM -> List.of(config.getMinPrice().stripTrailingZeros().toPlainString());
                case ABOVE_MAXIMUM -> List.of(config.getMaxPrice().stripTrailingZeros().toPlainString());
                default -> List.of();
            };
        }
    }

    public record PriceValidationResult(BigDecimal value, PriceValidationError error) {
        public boolean valid() {
            return error == null;
        }
    }

    /**
     * Parses and bounds-checks a price, preserving the rejection reason so the
     * server can tell the player exactly what to fix.
     */
    public static PriceValidationResult validatePrice(String raw) {
        if (raw == null) return new PriceValidationResult(null, PriceValidationError.EMPTY);
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return new PriceValidationResult(null, PriceValidationError.EMPTY);
        if (trimmed.length() > 32) return new PriceValidationResult(null, PriceValidationError.TOO_LONG);
        BigDecimal price;
        try {
            price = new BigDecimal(trimmed);
        } catch (NumberFormatException e) {
            return new PriceValidationResult(null, PriceValidationError.INVALID_NUMBER);
        }
        EconomyConfig config = EconomyConfig.getInstance();
        if (price.signum() <= 0) return new PriceValidationResult(null, PriceValidationError.NOT_POSITIVE);
        if (price.scale() > config.getMaxPriceScale()) return new PriceValidationResult(null, PriceValidationError.TOO_MANY_DECIMALS);
        if (price.precision() - price.scale() > config.getMaxPriceDigits()) return new PriceValidationResult(null, PriceValidationError.TOO_MANY_DIGITS);
        if (price.compareTo(config.getMinPrice()) < 0) return new PriceValidationResult(null, PriceValidationError.BELOW_MINIMUM);
        if (price.compareTo(config.getMaxPrice()) > 0) return new PriceValidationResult(null, PriceValidationError.ABOVE_MAXIMUM);
        return new PriceValidationResult(price, null);
    }

    /**
     * Legacy wrapper kept for callers that only need pass/fail.
     */
    public static BigDecimal parsePrice(String raw) {
        return validatePrice(raw).value();
    }

    /**
     * Out-of-range quantity rejection with a client-facing key, or null when
     * the quantity is valid.
     */
    public static Rejection validateQuantity(int quantity) {
        if (!isValidQuantity(quantity)) {
            return new Rejection("ui.economy.error.qty_limit",
                    List.of(String.valueOf(EconomyConfig.getInstance().getMaxOrderQuantity())));
        }
        return null;
    }

    public record Rejection(String key, List<String> args) {}

    public static ResourceLocation parseCommodityId(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > 128) return null;
        try {
            return com.nstut.economy.compat.Compat.rl(raw);
        } catch (Exception e) {
            com.nstut.Economy.LOGGER.warn("Rejected malformed commodity id from client: {}", sanitizeForLog(raw));
            return null;
        }
    }

    /**
     * Strips characters that cannot appear in a resource location and caps the
     * length so untrusted client input can neither forge log lines nor flood
     * them.
     */
    private static String sanitizeForLog(String raw) {
        String cleaned = raw.replaceAll("[^a-zA-Z0-9._:\\-/]", "?");
        return cleaned.length() > 64 ? cleaned.substring(0, 64) + "..." : cleaned;
    }

    public static boolean isValidQuantity(int quantity) {
        return quantity > 0 && quantity <= EconomyConfig.getInstance().getMaxOrderQuantity();
    }

    /**
     * Domain-level validation applied on every order creation regardless of
     * where the request came from. Returns null when the order is valid,
     * otherwise the exact client-facing rejection with configured limits.
     */
    public static Rejection validateNewOrder(int quantity, BigDecimal pricePerUnit, boolean fluid) {
        Rejection quantityError = validateQuantity(quantity);
        if (quantityError != null) return quantityError;
        if (pricePerUnit == null || pricePerUnit.signum() <= 0) {
            return new Rejection("ui.economy.error.price_positive", List.of());
        }
        EconomyConfig config = EconomyConfig.getInstance();
        BigDecimal quotedPrice = fluid
                ? com.nstut.economy.trading.FluidCommodity.pricePerBucket(pricePerUnit)
                : pricePerUnit;
        if (quotedPrice.scale() > config.getMaxPriceScale()) {
            return new Rejection("ui.economy.error.price_scale",
                    List.of(String.valueOf(config.getMaxPriceScale())));
        }
        if (quotedPrice.precision() - quotedPrice.scale() > config.getMaxPriceDigits()) {
            return new Rejection("ui.economy.error.price_digits",
                    List.of(String.valueOf(config.getMaxPriceDigits())));
        }
        if (quotedPrice.compareTo(config.getMinPrice()) < 0) {
            return new Rejection("ui.economy.error.price_below_min",
                    List.of(config.getMinPrice().stripTrailingZeros().toPlainString()));
        }
        if (quotedPrice.compareTo(config.getMaxPrice()) > 0) {
            return new Rejection("ui.economy.error.price_above_max",
                    List.of(config.getMaxPrice().stripTrailingZeros().toPlainString()));
        }
        return null;
    }

    /**
     * Domain-level validation applied on every order creation regardless of
     * where the request came from.
     */
    public static boolean isValidNewOrder(int quantity, BigDecimal pricePerUnit) {
        return isValidNewOrder(quantity, pricePerUnit, false);
    }

    public static boolean isValidNewOrder(int quantity, BigDecimal pricePerUnit, boolean fluid) {
        return validateNewOrder(quantity, pricePerUnit, fluid) == null;
    }
}
