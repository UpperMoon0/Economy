package com.nstut.economy.trading;

import com.nstut.economy.Economy;
import com.nstut.economy.api.CommodityPayload;
import com.nstut.economy.api.EconomyApi;
import com.nstut.economy.api.ICommodity;
import com.nstut.economy.test.MinecraftTestBase;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** NBT-era regression coverage for issue #23. */
class ItemVariant1201RegressionTest extends MinecraftTestBase {
    private final RegistryAccess registries = RegistryAccess.EMPTY;

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

        assertEquals(sharpnessCommodity.getId(), sameSharpness.getId(),
                "price/count must not participate in the canonical variant identity");
        assertNotEquals(sharpnessCommodity.getId(), mendingCommodity.getId(),
                "different stored enchantments must produce different commodity ids");
        assertTrue(sharpnessCommodity.matches(registries, sharpness.copy()));
        assertFalse(sharpnessCommodity.matches(registries, mending));
        assertTrue(mendingCommodity.matches(registries, mending.copy()));
        assertFalse(mendingCommodity.matches(registries, sharpness));
    }

    @Test
    void exactExtractionCannotConsumeAnotherEnchantedBookVariant() {
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
        assertTrue(ItemStack.isSameItemSameTags(mending, container.getItem(1)),
                "Mending stock must remain untouched by a Sharpness V commodity");
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
    void payloadAndOrderSnapshotRoundTripPreserveExactVariantIdentity() {
        ItemStack sharpness = enchantedBook(Enchantments.SHARPNESS, 5);
        ItemStack mending = enchantedBook(Enchantments.MENDING, 1);
        ItemCommodity original = ItemCommodity.exactFromItemStack(registries, sharpness, new BigDecimal("12.5"));

        CommodityPayload encoded = EconomyApi.commodityTypes().encode(original);
        assertEquals(2, encoded.version());
        ItemCommodity decoded = (ItemCommodity) EconomyApi.commodityTypes().decode(
                original.getTypeId(), original.getId(), encoded.version(), encoded.values());
        assertEquals(original.getId(), decoded.getId());
        assertEquals(ItemMatchPolicy.EXACT, decoded.getMatchPolicy());
        assertTrue(decoded.matches(registries, sharpness));
        assertFalse(decoded.matches(registries, mending));
        assertTrue(ItemStack.isSameItemSameTags(sharpness, decoded.getRepresentativeStack(registries)));

        Order order = new Order(UUID.randomUUID(), original, 3, new BigDecimal("12.5"), ICommodity.OrderType.SELL, null);
        Order restored = Order.fromSnapshot(order.toSnapshot());
        ItemCommodity restoredCommodity = (ItemCommodity) restored.getCommodity();
        assertEquals(original.getId(), restoredCommodity.getId());
        assertTrue(restoredCommodity.matches(registries, sharpness));
        assertFalse(restoredCommodity.matches(registries, mending));
    }

    @Test
    void corruptVariantFingerprintFailsClosed() {
        ItemCommodity original = ItemCommodity.exactFromItemStack(
                registries, enchantedBook(Enchantments.SHARPNESS, 5), BigDecimal.ONE);
        CommodityPayload encoded = EconomyApi.commodityTypes().encode(original);
        Map<String, String> corrupted = new HashMap<>(encoded.values());
        corrupted.put("variantFingerprint", "0".repeat(64));

        assertThrows(IllegalArgumentException.class, () -> EconomyApi.commodityTypes().decode(
                original.getTypeId(), original.getId(), encoded.version(), corrupted));
    }

    private static ItemStack enchantedBook(net.minecraft.world.item.enchantment.Enchantment enchantment, int level) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantedBookItem.addEnchantment(stack, new EnchantmentInstance(enchantment, level));
        return stack;
    }
}
