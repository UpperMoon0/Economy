package com.nstut.economy.trading;

import com.nstut.economy.api.EconomyId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemVariantIdentityTest {

    @Test
    void itemOnlyIdentityRemainsTheBaseRegistryId() {
        EconomyId base = EconomyId.of("minecraft", "enchanted_book");
        assertEquals(base, ItemVariant.itemOnly().commodityId(base));
    }

    @Test
    void exactIdentityIsDeterministicAndVariantSpecific() {
        EconomyId base = EconomyId.of("minecraft", "enchanted_book");
        String sharpness = "{id:\"minecraft:enchanted_book\",tag:{StoredEnchantments:[{id:\"minecraft:sharpness\",lvl:5s}]}}";
        String mending = "{id:\"minecraft:enchanted_book\",tag:{StoredEnchantments:[{id:\"minecraft:mending\",lvl:1s}]}}";

        ItemVariant first = ItemVariant.persisted(ItemMatchPolicy.EXACT, sharpness, ItemVariant.fingerprintOf(sharpness));
        ItemVariant second = ItemVariant.persisted(ItemMatchPolicy.EXACT, sharpness, ItemVariant.fingerprintOf(sharpness));
        ItemVariant other = ItemVariant.persisted(ItemMatchPolicy.EXACT, mending, ItemVariant.fingerprintOf(mending));

        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals(first.commodityId(base), second.commodityId(base));
        assertNotEquals(first.commodityId(base), other.commodityId(base));
        assertEquals(base, ItemVariant.baseItemId(first.commodityId(base)));
    }

    @Test
    void baseItemResolverOnlyStripsTheCanonicalSha256Suffix() {
        EconomyId ordinary = EconomyId.of("example", "machine/variant/controller");
        EconomyId malformed = EconomyId.of("example", "machine/variant/deadbeef");
        EconomyId nonHex = EconomyId.of("example", "machine/variant/" + "g".repeat(64));

        assertEquals(ordinary, ItemVariant.baseItemId(ordinary));
        assertEquals(malformed, ItemVariant.baseItemId(malformed));
        assertEquals(nonHex, ItemVariant.baseItemId(nonHex));
    }

    @Test
    void persistedVariantRejectsTamperedFingerprint() {
        assertThrows(IllegalArgumentException.class,
                () -> ItemVariant.persisted(ItemMatchPolicy.EXACT, "{variant:1}", "deadbeef"));
    }
}
