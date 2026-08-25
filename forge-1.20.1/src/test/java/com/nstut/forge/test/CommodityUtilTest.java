package com.nstut.economy.test;

import com.nstut.economy.util.CommodityUtil;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommodityUtilTest extends MinecraftTestBase {
    @Test
    @DisplayName("Only source fluid registry entries are canonical commodities")
    void filtersFlowingFluidVariants() {
        assertTrue(CommodityUtil.isCanonicalFluid(Fluids.WATER));
        assertTrue(CommodityUtil.isCanonicalFluid(Fluids.LAVA));
        assertFalse(CommodityUtil.isCanonicalFluid(Fluids.FLOWING_WATER));
        assertFalse(CommodityUtil.isCanonicalFluid(Fluids.FLOWING_LAVA));
        assertFalse(CommodityUtil.isCanonicalFluid(Fluids.EMPTY));
        assertSame(Fluids.WATER, CommodityUtil.getCanonicalFluid(Fluids.FLOWING_WATER));
        assertSame(Fluids.LAVA, CommodityUtil.getCanonicalFluid(Fluids.FLOWING_LAVA));
    }

    @Test
    @DisplayName("Commodity type filter modes include all, items only, or fluids only")
    void filtersCommodityTypes() {
        assertTrue(CommodityUtil.matchesTypeFilter(false, 0));
        assertTrue(CommodityUtil.matchesTypeFilter(true, 0));
        assertTrue(CommodityUtil.matchesTypeFilter(false, 1));
        assertFalse(CommodityUtil.matchesTypeFilter(true, 1));
        assertFalse(CommodityUtil.matchesTypeFilter(false, 2));
        assertTrue(CommodityUtil.matchesTypeFilter(true, 2));
        assertTrue(CommodityUtil.matchesTypeFilter(true, 99));
    }
}
