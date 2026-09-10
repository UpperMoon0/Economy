package com.nstut.economy.trading;

import com.nstut.Economy;
import com.nstut.economy.api.CommodityPayload;
import com.nstut.economy.api.EconomyId;
import com.nstut.economy.api.ICommodity;
import com.nstut.economy.test.MinecraftTestBase;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommodityStorageTest extends MinecraftTestBase {
    @Test
    @DisplayName("Item commodities count, extract, and stack matching items")
    void itemCommodityStorageOperations() {
        ItemCommodity iron = new ItemCommodity(
                new ResourceLocation("minecraft", "iron_ingot"), Items.IRON_INGOT, BigDecimal.ONE);
        SimpleContainer container = new SimpleContainer(3);
        container.setItem(0, new ItemStack(Items.IRON_INGOT, 32));
        container.setItem(1, new ItemStack(Items.DIAMOND, 1));

        NonNullList<ItemStack> extracted = NonNullList.create();
        assertFalse(iron.extractFrom(container, 33, extracted));
        assertTrue(extracted.isEmpty());
        assertTrue(iron.extractFrom(container, 20, extracted));
        assertEquals(20, extracted.stream().mapToInt(ItemStack::getCount).sum());
        assertEquals(12, container.getItem(0).getCount());

        NonNullList<ItemStack> incoming = NonNullList.create();
        incoming.add(new ItemStack(Items.IRON_INGOT, 40));
        assertTrue(iron.insertInto(container, incoming));
        assertEquals(52, container.getItem(0).getCount());
    }

    @Test
    @DisplayName("Item and fluid commodities with the same path remain distinct types")
    void commodityIdentityIncludesType() {
        ResourceLocation water = new ResourceLocation("minecraft", "water");
        FluidCommodity fluid = new FluidCommodity(water, Fluids.WATER, BigDecimal.ZERO);
        ItemCommodity item = new ItemCommodity(water, Items.WATER_BUCKET, BigDecimal.ZERO);

        assertNotEquals(fluid, item);
        assertEquals(1000, fluid.createFluidStack(1000).getAmount());
        assertSame(Fluids.WATER, fluid.createFluidStack(1000).getFluid());
    }

    @Test
    void schemaV1MatchNbtDataMigratesSafelyToLegacyItemOnlyIdentity() {
        Economy.ensureApiRegistrations();
        EconomyId id = EconomyId.of("minecraft", "diamond_sword");
        CommodityPayload legacy = new CommodityPayload(1, Map.of(
                "basePrice", "12.5",
                "dynamic", "true",
                "matchNbt", "true"));

        ICommodity decoded = com.nstut.economy.api.EconomyApi.commodityTypes()
                .decode(ICommodity.ITEM_TYPE, id, legacy.version(), legacy.values());

        assertInstanceOf(ItemCommodity.class, decoded);
        ItemCommodity item = (ItemCommodity) decoded;
        assertEquals(id, item.getId());
        assertEquals(ItemMatchPolicy.ITEM_ONLY, item.getMatchPolicy());
        assertEquals(new BigDecimal("12.5"), item.getBasePrice());

        CommodityPayload migrated = com.nstut.economy.api.EconomyApi.commodityTypes().encode(item);
        assertEquals(2, migrated.version());
        assertEquals("ITEM_ONLY", migrated.values().get("matchPolicy"));
        assertEquals("minecraft:diamond_sword", migrated.values().get("baseItem"));
    }
}
