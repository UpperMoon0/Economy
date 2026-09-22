package com.nstut.economy.trading;

/** Controls how an item commodity distinguishes Minecraft ItemStack variants. */
public enum ItemMatchPolicy {
    /** Preserve legacy behavior: only the base registered item matters. */
    ITEM_ONLY,
    /** The complete normalized stack metadata/components must match exactly. */
    EXACT;

    public static ItemMatchPolicy parse(String value) {
        if (value == null || value.isBlank()) return ITEM_ONLY;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("Unknown item match policy: " + value);
        }
    }
}
