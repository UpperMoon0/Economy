package com.nstut.economy.data;

import com.nstut.economy.test.MinecraftTestBase;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PortfolioVariantSnapshot1211Test extends MinecraftTestBase {
    private final HolderLookup.Provider registries = VanillaRegistries.createLookup();

    @Test
    void exactVariantUsesItsOwnTradePriceInsteadOfBaseItemPrice() {
        ItemStack exactStack = enchantedBook(Enchantments.SHARPNESS, 5);
        String exactId = EconomyAccountData.portfolioCommodityId(registries, exactStack);
        String baseId = EconomyAccountData.portfolioCommodityId(registries, new ItemStack(Items.ENCHANTED_BOOK));
        assertNotEquals(baseId, exactId);

        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        var trades = List.of(
                new EconomyTradeData.TradeSnapshot(baseId, "3", 1, buyer, seller, 1L),
                new EconomyTradeData.TradeSnapshot(exactId, "25", 1, buyer, seller, 2L));

        assertEquals(0, EconomyAccountData.valueHoldings(
                Map.of(exactId, 2), trades).compareTo(new BigDecimal("50")));
    }

    private ItemStack enchantedBook(ResourceKey<Enchantment> key, int level) {
        var enchantment = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(enchantment, level));
        return stack;
    }
}
