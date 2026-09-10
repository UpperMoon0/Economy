package com.nstut.economy.trading;

import com.nstut.economy.compat.Compat;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemVariantComponentTest {
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void exactVariantsUseAndRestore2612DataComponents() {
        HolderLookup.Provider registries = VanillaRegistries.createLookup();
        ItemStack damageOne = new ItemStack(Items.DIAMOND_SWORD);
        damageOne.setDamageValue(1);
        ItemStack damageTwo = new ItemStack(Items.DIAMOND_SWORD);
        damageTwo.setDamageValue(2);

        ItemVariant first = ItemVariant.capture(registries, damageOne, ItemMatchPolicy.EXACT);
        ItemVariant second = ItemVariant.capture(registries, damageTwo, ItemMatchPolicy.EXACT);

        assertNotEquals(first.fingerprint(), second.fingerprint());
        assertTrue(first.matches(registries, damageOne));
        assertTrue(!first.matches(registries, damageTwo));

        ItemVariant restored = ItemVariant.persisted(
                ItemMatchPolicy.EXACT, first.canonicalData(), first.fingerprint());
        ItemStack representative = restored.representative(Items.DIAMOND_SWORD, registries);

        assertTrue(Compat.stacksEqual(damageOne, representative));
        assertTrue(restored.matches(registries, damageOne));
        assertTrue(!restored.matches(registries, damageTwo));
    }
}
