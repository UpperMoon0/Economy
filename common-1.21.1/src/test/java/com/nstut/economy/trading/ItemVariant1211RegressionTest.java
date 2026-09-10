package com.nstut.economy.trading;

import com.nstut.economy.Economy;
import com.nstut.economy.api.CommodityPayload;
import com.nstut.economy.api.EconomyApi;
import com.nstut.economy.api.IOrder;
import com.nstut.economy.test.MinecraftTestBase;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Data-component-era regression coverage for issue #23. */
class ItemVariant1211RegressionTest extends MinecraftTestBase {
    private final HolderLookup.Provider registries = VanillaRegistries.createLookup();

    @BeforeEach
    void registerCommodityCodec() {
        Economy.ensureApiRegistrations();
    }

    @Test
    void sharpnessAndMendingBooksHaveDistinctDeterministicIdentities() {
        ItemStack sharpness = enchantedBook(Enchantments.SHARPNESS, 5);
        ItemStack mending = enchantedBook(Enchantments.MENDING, 1);

        ItemCommodity sharpnessCommodity = ItemCommodity.exactFromItemStack(registries, sharpness, BigDecimal.ONE);
        ItemCommodity sameSharpness = ItemCommodity.exactFromItemStack(registries, sharpness.copy(), BigDecimal.TEN);
        ItemCommodity mendingCommodity = ItemCommodity.exactFromItemStack(registries, mending, BigDecimal.ONE);

        assertEquals(sharpnessCommodity.getId(), sameSharpness.getId());
        assertNotEquals(sharpnessCommodity.getId(), mendingCommodity.getId());
        assertTrue(sharpnessCommodity.matches(registries, sharpness.copy()));
        assertFalse(sharpnessCommodity.matches(registries, mending));
        assertTrue(mendingCommodity.matches(registries, mending.copy()));
        assertFalse(mendingCommodity.matches(registries, sharpness));
    }

    @Test
    void exactExtractionCannotConsumeAnotherComponentVariant() {
        ItemStack sharpness = enchantedBook(Enchantments.SHARPNESS, 5);
        ItemStack mending = enchantedBook(Enchantments.MENDING, 1);
        ItemCommodity commodity = ItemCommodity.exactFromItemStack(registries, sharpness, BigDecimal.ONE);

        SimpleContainer container = new SimpleContainer(3);
        container.setItem(0, sharpness.copy());
        container.setItem(1, mending.copy());
        NonNullList<ItemStack> extracted = NonNullList.create();

        assertTrue(commodity.extractFrom(container, 1, extracted));
        assertEquals(1, extracted.size());
        assertTrue(commodity.matches(registries, extracted.get(0)));
        assertTrue(container.getItem(0).isEmpty());
        assertTrue(ItemStack.isSameItemSameComponents(mending, container.getItem(1)));
    }

    @Test
    void itemOnlyPreservesLegacyBaseItemBehavior() {
        ItemStack sharpness = enchantedBook(Enchantments.SHARPNESS, 5);
        ItemStack mending = enchantedBook(Enchantments.MENDING, 1);
        ItemCommodity first = ItemCommodity.fromItemStack(sharpness, BigDecimal.ONE);
        ItemCommodity second = ItemCommodity.fromItemStack(mending, BigDecimal.ONE);

        assertEquals(first.getId(), second.getId());
        assertEquals(ItemMatchPolicy.ITEM_ONLY, first.getMatchPolicy());
        assertTrue(first.matches(registries, sharpness));
        assertTrue(first.matches(registries, mending));
    }

    @Test
    void componentPayloadAndOrderSnapshotRoundTripExactly() {
        ItemStack sharpness = enchantedBook(Enchantments.SHARPNESS, 5);
        ItemStack mending = enchantedBook(Enchantments.MENDING, 1);
        ItemCommodity original = ItemCommodity.exactFromItemStack(registries, sharpness, new BigDecimal("3.75"));

        CommodityPayload encoded = EconomyApi.commodityTypes().encode(original);
        ItemCommodity decoded = (ItemCommodity) EconomyApi.commodityTypes().decode(
                original.getTypeId(), original.getId(), encoded.version(), encoded.values());
        assertEquals(original.getId(), decoded.getId());
        assertTrue(decoded.matches(registries, sharpness));
        assertFalse(decoded.matches(registries, mending));
        assertTrue(ItemStack.isSameItemSameComponents(sharpness, decoded.getRepresentativeStack(registries)));

        Order order = new Order(UUID.randomUUID(), original, 2, new BigDecimal("3.75"), IOrder.OrderType.SELL, null);
        ItemCommodity restored = (ItemCommodity) Order.fromSnapshot(order.toSnapshot()).getCommodity();
        assertEquals(original.getId(), restored.getId());
        assertTrue(restored.matches(registries, sharpness));
        assertFalse(restored.matches(registries, mending));
    }

    @Test
    void corruptComponentFingerprintFailsClosed() {
        ItemCommodity original = ItemCommodity.exactFromItemStack(
                registries, enchantedBook(Enchantments.SHARPNESS, 5), BigDecimal.ONE);
        CommodityPayload encoded = EconomyApi.commodityTypes().encode(original);
        Map<String, String> corrupted = new HashMap<>(encoded.values());
        corrupted.put("variantFingerprint", "f".repeat(64));

        assertThrows(IllegalArgumentException.class, () -> EconomyApi.commodityTypes().decode(
                original.getTypeId(), original.getId(), encoded.version(), corrupted));
    }

    private ItemStack enchantedBook(ResourceKey<Enchantment> key, int level) {
        var enchantment = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(enchantment, level));
        return stack;
    }
}
