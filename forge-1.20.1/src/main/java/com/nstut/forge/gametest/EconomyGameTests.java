package com.nstut.forge.gametest;

import com.nstut.Economy;
import com.nstut.economy.api.EconomyApi;
import com.nstut.economy.blocks.BlockRegistries;
import com.nstut.economy.blocks.TankBlockEntity;
import com.nstut.economy.blocks.VaultBlockEntity;
import com.nstut.economy.trading.EconomyFluidStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Real-server world coverage for behavior that plain JVM tests cannot prove:
 * registry-backed block placement, block-entity creation, world attachment and
 * Economy's runtime lifecycle. Loader-specific adapters remain covered by their
 * fast JVM tests and every supported target still has a real client join smoke.
 */
@GameTestHolder(Economy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EconomyGameTests {
    private EconomyGameTests() {
    }

    @GameTest(template = "economy_gametest_empty", timeoutTicks = 40)
    public static void vaultAndTankOperateInRealServerWorld(GameTestHelper helper) {
        helper.assertTrue(EconomyApi.isReady(), "Economy API runtime must be bound before GameTests execute");

        BlockPos vaultPos = new BlockPos(0, 1, 0);
        BlockPos tankPos = new BlockPos(1, 1, 0);
        helper.setBlock(vaultPos, BlockRegistries.VAULT.get());
        helper.setBlock(tankPos, BlockRegistries.TANK.get());

        var vaultEntity = helper.getLevel().getBlockEntity(helper.absolutePos(vaultPos));
        helper.assertTrue(vaultEntity instanceof VaultBlockEntity,
                "Placing an Economy vault must create its registered block entity");
        VaultBlockEntity vault = (VaultBlockEntity) vaultEntity;
        vault.setItem(0, new ItemStack(Items.IRON_INGOT, 8));
        helper.assertTrue(vault.countItem(Items.IRON_INGOT) == 8,
                "Vault inventory mutation must work in an actual ServerLevel");
        ItemStack extracted = vault.removeItem(0, 3);
        helper.assertTrue(extracted.is(Items.IRON_INGOT) && extracted.getCount() == 3,
                "Vault extraction must return the requested real stack");
        helper.assertTrue(vault.countItem(Items.IRON_INGOT) == 5,
                "Vault contents must reflect extraction");

        var tankEntity = helper.getLevel().getBlockEntity(helper.absolutePos(tankPos));
        helper.assertTrue(tankEntity instanceof TankBlockEntity,
                "Placing an Economy tank must create its registered block entity");
        TankBlockEntity tank = (TankBlockEntity) tankEntity;
        int filled = tank.fill(new EconomyFluidStack(Fluids.WATER, 1000));
        helper.assertTrue(filled == 1000 && tank.getFluidAmount() == 1000,
                "Tank fill must mutate real block-entity state");
        EconomyFluidStack drained = tank.drain(250);
        helper.assertTrue(drained.getFluid() == Fluids.WATER && drained.getAmount() == 250,
                "Tank drain must return the real stored fluid");
        helper.assertTrue(tank.getFluidAmount() == 750,
                "Tank state must retain the undrained amount");

        helper.succeed();
    }
}
