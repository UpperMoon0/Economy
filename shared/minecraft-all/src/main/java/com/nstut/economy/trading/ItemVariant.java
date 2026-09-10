package com.nstut.economy.trading;

import com.nstut.economy.api.EconomyId;
import com.nstut.economy.compat.Compat;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Version-neutral identity for a concrete item variant.
 *
 * The canonical stack representation itself is produced by the version-specific
 * Compat layer so 1.20.1 NBT and 1.21+ data components never leak into the
 * shared market code. Counts are normalized before capture.
 */
public final class ItemVariant {
    private static final String VARIANT_SEGMENT = "/variant/";
    private static final int SHA256_HEX_LENGTH = 64;
    private static final ItemVariant ITEM_ONLY = new ItemVariant(ItemMatchPolicy.ITEM_ONLY, "", "", ItemStack.EMPTY);

    private final ItemMatchPolicy policy;
    private final String canonicalData;
    private final String fingerprint;
    private final ItemStack capturedRepresentative;

    private ItemVariant(ItemMatchPolicy policy, String canonicalData, String fingerprint, ItemStack representative) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.canonicalData = Objects.requireNonNull(canonicalData, "canonicalData");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.capturedRepresentative = representative == null ? ItemStack.EMPTY : normalizedCopy(representative);
    }

    public static ItemVariant itemOnly() {
        return ITEM_ONLY;
    }

    public static ItemVariant capture(HolderLookup.Provider registries, ItemStack stack, ItemMatchPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (policy == ItemMatchPolicy.ITEM_ONLY) return itemOnly();
        Objects.requireNonNull(registries, "registries");
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) throw new IllegalArgumentException("Cannot capture an empty item variant");
        ItemStack normalized = normalizedCopy(stack);
        String canonical = Compat.canonicalItemStack(registries, normalized);
        if (canonical == null || canonical.isBlank()) {
            throw new IllegalArgumentException("Could not canonicalize item variant");
        }
        return new ItemVariant(policy, canonical, fingerprintOf(canonical), normalized);
    }

    public static ItemVariant persisted(ItemMatchPolicy policy, String canonicalData, String expectedFingerprint) {
        Objects.requireNonNull(policy, "policy");
        if (policy == ItemMatchPolicy.ITEM_ONLY) return itemOnly();
        if (canonicalData == null || canonicalData.isBlank()) {
            throw new IllegalArgumentException("Exact item variant is missing canonical data");
        }
        String actual = fingerprintOf(canonicalData);
        if (expectedFingerprint != null && !expectedFingerprint.isBlank() && !actual.equals(expectedFingerprint)) {
            throw new IllegalArgumentException("Item variant fingerprint does not match canonical data");
        }
        return new ItemVariant(policy, canonicalData, actual, ItemStack.EMPTY);
    }

    public ItemMatchPolicy policy() {
        return policy;
    }

    public String canonicalData() {
        return canonicalData;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public EconomyId commodityId(EconomyId baseItemId) {
        Objects.requireNonNull(baseItemId, "baseItemId");
        if (policy == ItemMatchPolicy.ITEM_ONLY) return baseItemId;
        return EconomyId.of(baseItemId.namespace(), baseItemId.path() + VARIANT_SEGMENT + fingerprint);
    }

    /**
     * Returns the registered base item id embedded in an Economy canonical item
     * commodity id. A normal item id is returned unchanged. Only the exact
     * Economy suffix shape (/variant/ + 64 lowercase hex chars) is stripped,
     * so ordinary mod item paths containing the word "variant" remain intact.
     */
    public static EconomyId baseItemId(EconomyId commodityId) {
        Objects.requireNonNull(commodityId, "commodityId");
        String path = commodityId.path();
        int marker = path.lastIndexOf(VARIANT_SEGMENT);
        if (marker < 0) return commodityId;
        String digest = path.substring(marker + VARIANT_SEGMENT.length());
        if (digest.length() != SHA256_HEX_LENGTH) return commodityId;
        for (int i = 0; i < digest.length(); i++) {
            char c = digest.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return commodityId;
        }
        if (marker == 0) return commodityId;
        return EconomyId.of(commodityId.namespace(), path.substring(0, marker));
    }

    public boolean matches(HolderLookup.Provider registries, ItemStack candidate) {
        if (candidate == null || candidate.isEmpty()) return false;
        if (policy == ItemMatchPolicy.ITEM_ONLY) return true;
        if (registries == null) return false;
        String candidateCanonical = Compat.canonicalItemStack(registries, normalizedCopy(candidate));
        return canonicalData.equals(candidateCanonical);
    }

    /**
     * Fast in-memory exact comparison used only while the originally captured
     * representative is still available. Persisted exact commodities fail
     * closed here; production storage paths must use the registry-aware overload.
     */
    public boolean matchesCaptured(ItemStack candidate) {
        if (candidate == null || candidate.isEmpty()) return false;
        if (policy == ItemMatchPolicy.ITEM_ONLY) return true;
        return !capturedRepresentative.isEmpty() && Compat.stacksEqual(capturedRepresentative, normalizedCopy(candidate));
    }

    public ItemStack representative(Item baseItem, HolderLookup.Provider registries) {
        Objects.requireNonNull(baseItem, "baseItem");
        if (policy == ItemMatchPolicy.ITEM_ONLY) return new ItemStack(baseItem);
        ItemStack result = capturedRepresentative.isEmpty()
                ? Compat.deserializeCanonicalItemStack(registries, canonicalData)
                : capturedRepresentative.copy();
        if (result == null || result.isEmpty() || !result.is(baseItem)) {
            throw new IllegalStateException("Persisted item variant does not reconstruct to its base item");
        }
        result.setCount(1);
        return result;
    }

    public ItemStack representativeOrBase(Item baseItem) {
        Objects.requireNonNull(baseItem, "baseItem");
        if (!capturedRepresentative.isEmpty()) return capturedRepresentative.copy();
        return new ItemStack(baseItem);
    }

    public static String fingerprintOf(String canonicalData) {
        Objects.requireNonNull(canonicalData, "canonicalData");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalData.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static ItemStack normalizedCopy(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }
}
