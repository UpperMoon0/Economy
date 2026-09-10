package com.nstut.forge.gametest;

import com.nstut.Economy;
import com.nstut.economy.api.EconomyApi;
import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.IOrder;
import com.nstut.economy.blocks.BlockRegistries;
import com.nstut.economy.blocks.TankBlockEntity;
import com.nstut.economy.blocks.VaultBlockEntity;
import com.nstut.economy.blocks.VaultManager;
import com.nstut.economy.data.TradeLedger;
import com.nstut.economy.trading.EconomyFluidStack;
import com.nstut.economy.trading.ItemCommodity;
import com.nstut.economy.trading.ItemMatchPolicy;
import com.nstut.economy.trading.Order;
import com.nstut.economy.trading.OrderManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.math.BigDecimal;
import java.util.UUID;

/** Real-server coverage for Economy storage, commodity identity and order behavior. */
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

    @GameTest(template = "economy_gametest_empty", timeoutTicks = 60)
    public static void exactEnchantedBooksRemainDistinctThroughVaultCodecOrdersAndHistory(GameTestHelper helper) {
        helper.assertTrue(EconomyApi.isReady(), "Economy API must be ready for variant integration coverage");

        ItemStack sharpness = EnchantedBookItem.createForEnchantment(
                new EnchantmentInstance(Enchantments.SHARPNESS, 5));
        ItemStack mending = EnchantedBookItem.createForEnchantment(
                new EnchantmentInstance(Enchantments.MENDING, 1));

        ItemCommodity sharpnessCommodity = ItemCommodity.exactFromItemStack(
                helper.getLevel().registryAccess(), sharpness, BigDecimal.ONE);
        ItemCommodity mendingCommodity = ItemCommodity.exactFromItemStack(
                helper.getLevel().registryAccess(), mending, BigDecimal.ONE);

        helper.assertTrue(sharpnessCommodity.getMatchPolicy() == ItemMatchPolicy.EXACT,
                "captured enchanted book must use EXACT matching");
        helper.assertTrue(!sharpnessCommodity.getId().equals(mendingCommodity.getId()),
                "Sharpness V and Mending must have different commodity ids");
        helper.assertTrue(sharpnessCommodity.matches(helper.getLevel(), sharpness),
                "Sharpness commodity must match its source stack");
        helper.assertTrue(!sharpnessCommodity.matches(helper.getLevel(), mending),
                "Sharpness commodity must not match a Mending book");

        BlockPos vaultPos = new BlockPos(0, 1, 0);
        helper.setBlock(vaultPos, BlockRegistries.VAULT.get());
        var blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(vaultPos));
        helper.assertTrue(blockEntity instanceof VaultBlockEntity, "variant test vault must exist");
        VaultBlockEntity vault = (VaultBlockEntity) blockEntity;
        UUID seller = UUID.randomUUID();
        vault.setOwner(seller);
        vault.setMode(VaultBlockEntity.VaultMode.BOTH);
        vault.setItem(0, sharpness.copy());
        vault.setItem(1, sharpness.copy());
        vault.setItem(2, mending.copy());
        vault.setItem(3, mending.copy());
        vault.setItem(4, mending.copy());

        helper.assertTrue(VaultManager.countItemInVaults(helper.getLevel(), seller, sharpnessCommodity) == 2,
                "exact Vault count must include only Sharpness V books");
        helper.assertTrue(VaultManager.countItemInVaults(helper.getLevel(), seller, mendingCommodity) == 3,
                "exact Vault count must include only Mending books");

        NonNullList<ItemStack> extracted = NonNullList.create();
        helper.assertTrue(VaultManager.extractItemFromVaults(helper.getLevel(), seller, sharpnessCommodity, 1, extracted),
                "exact Sharpness extraction must succeed");
        helper.assertTrue(extracted.size() == 1 && sharpnessCommodity.matches(helper.getLevel(), extracted.get(0)),
                "exact extraction must return the requested Sharpness variant");
        helper.assertTrue(VaultManager.countItemInVaults(helper.getLevel(), seller, sharpnessCommodity) == 1,
                "Sharpness stock must decrement independently");
        helper.assertTrue(VaultManager.countItemInVaults(helper.getLevel(), seller, mendingCommodity) == 3,
                "Mending stock must remain untouched by Sharpness extraction");

        var payload = EconomyApi.commodityTypes().encode(sharpnessCommodity);
        ICommodity decoded = EconomyApi.commodityTypes().decode(
                ICommodity.ITEM_TYPE, sharpnessCommodity.getId(), payload.version(), payload.values());
        helper.assertTrue(decoded instanceof ItemCommodity, "decoded exact commodity must remain an item commodity");
        ItemCommodity restored = (ItemCommodity) decoded;
        helper.assertTrue(restored.getId().equals(sharpnessCommodity.getId()),
                "save/network payload round trip must preserve exact commodity identity");
        helper.assertTrue(restored.getVariantFingerprint().equals(sharpnessCommodity.getVariantFingerprint()),
                "save/network payload round trip must preserve the variant fingerprint");
        helper.assertTrue(restored.matches(helper.getLevel(), sharpness) && !restored.matches(helper.getLevel(), mending),
                "restored exact matcher must remain variant-specific");

        ItemCommodity bootstrapped = com.nstut.economy.network.MarketNetwork.resolveItemCommodityForOrder(
                new OrderManager(), helper.getLevel(), seller, sharpnessCommodity.getId().toString());
        helper.assertTrue(bootstrapped != null && bootstrapped.getId().equals(sharpnessCommodity.getId()),
                "first exact sell order must bootstrap its commodity from matching Vault stock without an existing order");
        boolean noContextFailedLoudly = false;
        try {
            restored.matches(sharpness);
        } catch (UnsupportedOperationException expected) {
            noContextFailedLoudly = true;
        }
        helper.assertTrue(noContextFailedLoudly,
                "persisted exact commodities must reject registry-less matching instead of silently reporting no stock");

        OrderManager orderBook = new OrderManager();
        orderBook.createBuyOrder(UUID.randomUUID(), sharpnessCommodity, 1, new BigDecimal("10"), false, null);
        orderBook.createBuyOrder(UUID.randomUUID(), mendingCommodity, 1, new BigDecimal("20"), false, null);
        helper.assertTrue(orderBook.getBuyOrders(sharpnessCommodity).size() == 1,
                "Sharpness order lookup must not include Mending orders");
        helper.assertTrue(orderBook.getBuyOrders(mendingCommodity).size() == 1,
                "Mending order lookup must not include Sharpness orders");

        Order serverBuy = new Order(UUID.randomUUID(), sharpnessCommodity, 1, BigDecimal.ONE,
                IOrder.OrderType.BUY, null);
        serverBuy.setServerOrder(true);
        IOrder.TransactionResult result = serverBuy.execute(seller, helper.getLevel());
        helper.assertTrue(result.success && result.quantityTransferred == 1,
                "server BUY must execute one exact Sharpness commodity");
        helper.assertTrue(VaultManager.countItemInVaults(helper.getLevel(), seller, sharpnessCommodity) == 0,
                "variant-aware BUY execution must consume the remaining Sharpness book");
        helper.assertTrue(VaultManager.countItemInVaults(helper.getLevel(), seller, mendingCommodity) == 3,
                "variant-aware BUY execution must never consume Mending stock");
        helper.assertTrue(!TradeLedger.getRecentTrades(sharpnessCommodity.getId().toString(), 10).isEmpty(),
                "trade history must be keyed by the exact Sharpness commodity id");
        helper.assertTrue(TradeLedger.getRecentTrades(mendingCommodity.getId().toString(), 10).isEmpty(),
                "Sharpness execution must not pollute Mending trade history");

        helper.succeed();
    }
}
