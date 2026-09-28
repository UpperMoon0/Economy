package com.nstut.economy.data;

import com.nstut.economy.test.MinecraftTestBase;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PortfolioVariantSnapshot1201Test extends MinecraftTestBase {
    private final RegistryAccess registries = RegistryAccess.EMPTY;

    @Test
    void exactVariantUsesItsOwnTradePriceInsteadOfBaseItemPrice() {
        ItemStack exactStack = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantedBookItem.addEnchantment(exactStack, new EnchantmentInstance(Enchantments.SHARPNESS, 5));
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
}
