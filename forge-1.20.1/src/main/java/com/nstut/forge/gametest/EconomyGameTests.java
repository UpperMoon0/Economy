package com.nstut.forge.gametest;

import com.nstut.Economy;
import com.nstut.economy.api.EconomyApi;
import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.IOrder;
import com.nstut.economy.api.AccountRef;
import com.nstut.economy.api.MarketIdentity;
import com.nstut.economy.api.TeamEconomyMode;
import com.nstut.economy.api.TeamEconomyProvider;
import com.nstut.economy.api.TeamRef;
import com.nstut.economy.api.TeamRole;
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
import net.minecraft.resources.ResourceLocation;
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

        UUID ledgerOnlyBuyer = UUID.randomUUID();
        ItemCommodity ledgerOnlyCommodity = com.nstut.economy.network.MarketNetwork.resolveItemCommodityForOrder(
                new OrderManager(), helper.getLevel(), ledgerOnlyBuyer, sharpnessCommodity.getId().toString());
        helper.assertTrue(ledgerOnlyCommodity != null
                        && ledgerOnlyCommodity.getId().equals(sharpnessCommodity.getId()),
                "a ledger-only exact variant must resolve without an active order or matching buyer Vault stock");
        helper.assertTrue(ledgerOnlyCommodity.matches(helper.getLevel(), sharpness)
                        && !ledgerOnlyCommodity.matches(helper.getLevel(), mending),
                "ledger reconstruction must preserve the exact variant matcher");
        helper.assertTrue(ledgerOnlyCommodity.getDisplayName(helper.getLevel().registryAccess()).getString()
                        .equals(sharpness.getHoverName().getString()),
                "ledger reconstruction must preserve the exact variant display stack");
        OrderManager ledgerOnlyBook = new OrderManager();
        var ledgerBuy = ledgerOnlyBook.createBuyOrder(
                ledgerOnlyBuyer, ledgerOnlyCommodity, 1, BigDecimal.ONE, helper.getLevel());
        helper.assertTrue(ledgerBuy.accepted(),
                "another player must be able to create a BUY order from the ledger-only exact commodity");

        helper.succeed();
    }
    @GameTest(template = "economy_gametest_empty", timeoutTicks = 100)
    public static void teamMarketPrincipalUsesPlayerOwnedVaults(GameTestHelper helper) {
        helper.assertTrue(EconomyApi.isReady(), "Economy API must be ready for team market coverage");

        UUID actor = UUID.randomUUID();
        UUID counterparty = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        TeamRef team = new TeamRef(teamId, "GameTest Team", actor);
        boolean[] activeMember = {true};
        TeamEconomyProvider fake = new TeamEconomyProvider() {
            @Override public java.util.Optional<TeamRef> resolveTeam(UUID playerId) {
                return activeMember[0] && actor.equals(playerId) ? java.util.Optional.of(team) : java.util.Optional.empty();
            }
            @Override public java.util.Optional<TeamRef> getTeam(UUID id) {
                return teamId.equals(id) ? java.util.Optional.of(team) : java.util.Optional.empty();
            }
            @Override public TeamRole getRole(UUID playerId, UUID id) {
                return activeMember[0] && actor.equals(playerId) && teamId.equals(id) ? TeamRole.OFFICER : TeamRole.NONE;
            }
        };

        var teams = EconomyApi.teamEconomy();
        TeamEconomyProvider previousProvider = teams.provider().orElse(null);
        TeamEconomyMode previousMode = teams.mode();
        if (previousProvider != null) teams.unregisterProvider(previousProvider);
        teams.registerProvider(fake);
        teams.setMode(TeamEconomyMode.HYBRID);

        try {
            BlockPos teamVaultPos = new BlockPos(0, 1, 0);
            BlockPos counterpartyVaultPos = new BlockPos(1, 1, 0);
            helper.setBlock(teamVaultPos, BlockRegistries.VAULT.get());
            helper.setBlock(counterpartyVaultPos, BlockRegistries.VAULT.get());

            VaultBlockEntity teamVault = (VaultBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(teamVaultPos));
            VaultBlockEntity counterpartyVault = (VaultBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(counterpartyVaultPos));
            helper.assertTrue(teamVault != null && counterpartyVault != null, "team market Vaults must exist");
            teamVault.setOwner(actor);
            teamVault.setMode(VaultBlockEntity.VaultMode.BOTH);
            counterpartyVault.setOwner(counterparty);
            counterpartyVault.setMode(VaultBlockEntity.VaultMode.BOTH);
            counterpartyVault.setItem(0, new ItemStack(Items.IRON_INGOT, 4));

            ItemCommodity iron = new ItemCommodity(new ResourceLocation("minecraft", "iron_ingot"), Items.IRON_INGOT, BigDecimal.ONE);
            var accounts = EconomyApi.accounts();
            var teamAccount = accounts.getOrCreateTeamAccount(teamId);
            var actorAccount = accounts.getOrCreatePlayerAccount(actor);
            var counterpartyAccount = accounts.getOrCreatePlayerAccount(counterparty);
            BigDecimal actorBefore = actorAccount.getBalance();
            BigDecimal counterpartyBefore = counterpartyAccount.getBalance();
            teamAccount.credit(new BigDecimal("100"), null);

            MarketIdentity teamIdentity = new MarketIdentity(AccountRef.team(teamId), actor, actor);
            OrderManager buyBook = new OrderManager();
            var buyCreated = buyBook.createBuyOrder(teamIdentity, iron, 2, new BigDecimal("3"), false, helper.getLevel());
            helper.assertTrue(buyCreated.accepted() && buyCreated.order().isPresent(), "team BUY order must be accepted");
            IOrder.TransactionResult bought = buyCreated.order().orElseThrow().execute(MarketIdentity.personal(counterparty), helper.getLevel());
            helper.assertTrue(bought.success && bought.quantityTransferred == 2, "team BUY must execute through typed IOrder API");
            helper.assertTrue(teamAccount.getBalance().compareTo(new BigDecimal("94")) == 0, "team BUY must debit only the team principal");
            helper.assertTrue(actorAccount.getBalance().compareTo(actorBefore) == 0, "team BUY must not debit the actor personal wallet");
            helper.assertTrue(counterpartyAccount.getBalance().compareTo(counterpartyBefore.add(new BigDecimal("6"))) == 0, "team BUY must credit the seller personal wallet");
            helper.assertTrue(VaultManager.countItemInVaults(helper.getLevel(), actor, iron) == 2, "team BUY delivery must target the actor storage owner");
            helper.assertTrue(VaultManager.countItemInVaults(helper.getLevel(), counterparty, iron) == 2, "team BUY must extract from the seller storage owner");

            NonNullList<ItemStack> reserved = NonNullList.create();
            helper.assertTrue(VaultManager.extractItemFromVaults(helper.getLevel(), actor, iron, 1, reserved), "team SELL must reserve from actor-owned Vault storage");
            OrderManager sellBook = new OrderManager();
            var sellCreated = sellBook.createSellOrder(teamIdentity, iron, 1, new BigDecimal("4"), reserved, java.util.List.of(), helper.getLevel());
            helper.assertTrue(sellCreated.accepted() && sellCreated.order().isPresent(), "team SELL order must be accepted");
            IOrder.TransactionResult sold = sellCreated.order().orElseThrow().execute(MarketIdentity.personal(counterparty), helper.getLevel());
            helper.assertTrue(sold.success && sold.quantityTransferred == 1, "team SELL must execute through typed IOrder API");
            helper.assertTrue(teamAccount.getBalance().compareTo(new BigDecimal("98")) == 0, "team SELL proceeds must credit the team principal");
            helper.assertTrue(actorAccount.getBalance().compareTo(actorBefore) == 0, "team SELL must not credit the actor personal wallet");
            helper.assertTrue(counterpartyAccount.getBalance().compareTo(counterpartyBefore.add(new BigDecimal("2"))) == 0, "team SELL must debit the buyer personal wallet");
            helper.assertTrue(VaultManager.countItemInVaults(helper.getLevel(), actor, iron) == 1, "team SELL reservation must come from actor storage");
            helper.assertTrue(VaultManager.countItemInVaults(helper.getLevel(), counterparty, iron) == 3, "team SELL delivery must target buyer storage");

            NonNullList<ItemStack> invalidatedEscrow = NonNullList.create();
            helper.assertTrue(VaultManager.extractItemFromVaults(helper.getLevel(), actor, iron, 1, invalidatedEscrow),
                    "invalidated team SELL must reserve from actor storage before membership changes");
            OrderManager invalidatedBook = new OrderManager();
            var invalidated = invalidatedBook.createSellOrder(teamIdentity, iron, 1, new BigDecimal("5"),
                    invalidatedEscrow, java.util.List.of(), helper.getLevel());
            helper.assertTrue(invalidated.accepted() && invalidated.order().isPresent(), "team SELL must exist before leave/kick");
            UUID invalidatedId = invalidated.order().orElseThrow().getOrderId();
            activeMember[0] = false;
            invalidatedBook.revalidateTeamOrders(helper.getLevel());
            helper.assertTrue(invalidatedBook.getOrder(invalidatedId).isEmpty(), "leave/kick must remove an invalidated team order");
            helper.assertTrue(VaultManager.countItemInVaults(helper.getLevel(), actor, iron) == 1,
                    "invalidated team SELL escrow must return losslessly to the recorded player storage owner");

            helper.succeed();
        } finally {
            teams.unregisterProvider(fake);
            if (previousProvider != null) teams.registerProvider(previousProvider);
            teams.setMode(previousMode);
        }
    }

}
