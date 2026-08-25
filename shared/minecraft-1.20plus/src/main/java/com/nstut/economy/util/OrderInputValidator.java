package com.nstut.economy.util;

import com.nstut.economy.config.EconomyConfig;
import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;

/**
 * Server-side validation for client-supplied order inputs. Used by the
 * network layer before packets touch domain code, so malformed values are
 * rejected without ever reaching trading logic.
 */
public final class OrderInputValidator {
    private OrderInputValidator() {}

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

    /**
     * Parses and bounds-checks a price. Returns null when the value is
     * unparseable, non-positive, absurdly scaled, or outside the configured
     * min/max range.
     */
    public static BigDecimal parsePrice(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > 32) return null;
        BigDecimal price;
        try {
            price = new BigDecimal(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
        EconomyConfig config = EconomyConfig.getInstance();
        if (price.signum() <= 0) return null;
        if (price.scale() > config.getMaxPriceScale()) return null;
        if (price.precision() - price.scale() > config.getMaxPriceDigits()) return null;
        if (price.compareTo(config.getMinPrice()) < 0 || price.compareTo(config.getMaxPrice()) > 0) return null;
        return price;
    }

    public static boolean isValidQuantity(int quantity) {
        return quantity > 0 && quantity <= EconomyConfig.getInstance().getMaxOrderQuantity();
    }

    /**
     * Domain-level validation applied on every order creation regardless of
     * where the request came from.
     */
    public static boolean isValidNewOrder(int quantity, BigDecimal pricePerUnit) {
        EconomyConfig config = EconomyConfig.getInstance();
        if (!isValidQuantity(quantity)) {
            return false;
        }
        if (pricePerUnit == null || pricePerUnit.signum() <= 0) {
            return false;
        }
        if (pricePerUnit.scale() > config.getMaxPriceScale()
                || pricePerUnit.precision() - pricePerUnit.scale() > config.getMaxPriceDigits()) {
            return false;
        }
        return pricePerUnit.compareTo(config.getMinPrice()) >= 0
                && pricePerUnit.compareTo(config.getMaxPrice()) <= 0;
    }
}
