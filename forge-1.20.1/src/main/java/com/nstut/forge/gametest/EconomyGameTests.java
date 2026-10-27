package com.nstut.forge.gametest;

import com.nstut.Economy;
import com.nstut.economy.api.EconomyApi;
import com.nstut.economy.api.EconomyId;
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
    public static void portfolioPrunesConfirmedMissingRegistrationsAndKeepsUnloadedMarks(GameTestHelper helper) {
        var level = helper.getLevel();
        AccountRef owner = AccountRef.player(UUID.randomUUID());
        var data = com.nstut.economy.data.EconomyAccountData.get(level);
        String dimension = level.dimension().location().toString();
        BlockPos missingVault = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos missingTank = helper.absolutePos(new BlockPos(1, 1, 0));
        // Stale durable records can survive a restart or an out-of-band block removal.
        VaultManager.register(owner, missingVault, dimension);
        com.nstut.economy.blocks.TankManager.register(owner, missingTank, dimension);
        data.portfolioStores().update(owner,
                com.nstut.economy.api.internal.BuiltinContainerStorageProvider.storeKey("vault", dimension, missingVault),
                java.util.Map.of("minecraft:iron_ingot", 9L), 1);
        data.portfolioStores().update(owner,
                com.nstut.economy.api.internal.BuiltinContainerStorageProvider.storeKey("tank", dimension, missingTank),
                java.util.Map.of("minecraft:water", 1000L), 1);
        BlockPos unloaded = new BlockPos(30000000, 100, 30000000);
        helper.assertTrue(!level.hasChunkAt(unloaded), "Remote test chunk must start unloaded");
        VaultManager.register(owner, unloaded, dimension);
        data.portfolioStores().update(owner,
                com.nstut.economy.api.internal.BuiltinContainerStorageProvider.storeKey("vault", dimension, unloaded),
                java.util.Map.of("minecraft:gold_ingot", 7L), 1);
        var snapshot = com.nstut.economy.api.internal.BuiltinContainerStorageProvider.portfolioSnapshot(level, owner);
        helper.assertTrue(snapshot.holdings().equals(java.util.Map.of("minecraft:gold_ingot", 7L)),
                "Only the last-known unloaded mark must remain");
        helper.assertTrue(VaultManager.getVaultRecords(owner).size() == 1
                        && com.nstut.economy.blocks.TankManager.getTankRecords(owner).isEmpty(),
                "Loaded missing records must be pruned from manager indexes");
        helper.assertTrue(data.getStorageVaults().get(owner).size() == 1
                        && data.getStorageTanks().getOrDefault(owner, java.util.List.of()).isEmpty(),
                "Pruning must survive save/restart through the durable registrations");
        helper.assertTrue(data.portfolioStores().keys(owner).size() == 1 && snapshot.stale() == 1,
                "Missing marks must be removed and unloaded mark explicitly stale");
        helper.assertTrue(!level.hasChunkAt(unloaded), "Portfolio must never load the remote chunk");
        VaultManager.unregister(owner, unloaded, dimension);
        helper.succeed();
    }

    @GameTest(template = "economy_gametest_empty", timeoutTicks = 40)
    public static void vaultCraftingRecipeLoadsAndProducesVault(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipe = level.getRecipeManager().byKey(new ResourceLocation("economy", "vault"));
        helper.assertTrue(recipe.isPresent(), "Vault recipe must exist in the real server recipe manager");
        var loaded = recipe.orElseThrow();
        helper.assertTrue(loaded.getResultItem(level.registryAccess()).is(BlockRegistries.VAULT.get().asItem()),
                "Vault recipe must produce the registered Vault item");
        helper.assertTrue(loaded.getIngredients().stream().anyMatch(ingredient -> ingredient.test(new ItemStack(Items.CHEST))),
                "Default Vault recipe must include the documented vanilla chest ingredient");
        helper.succeed();
    }

    @GameTest(template = "economy_gametest_empty", timeoutTicks = 40)
    public static void purchaseRoutingFiltersAndPriority(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        var level = helper.getLevel();
        var vaults = new java.util.ArrayList<VaultBlockEntity>();
        var tanks = new java.util.ArrayList<TankBlockEntity>();
        for (int i = 0; i < 3; i++) {
            BlockPos vp = new BlockPos(i, 1, 0), tp = new BlockPos(i, 1, 1);
            helper.setBlock(vp, BlockRegistries.VAULT.get());
            helper.setBlock(tp, BlockRegistries.TANK.get());
            var v = (VaultBlockEntity) level.getBlockEntity(helper.absolutePos(vp));
            var t = (TankBlockEntity) level.getBlockEntity(helper.absolutePos(tp));
            v.setOwner(owner); t.setOwner(owner);
            v.setMode(VaultBlockEntity.VaultMode.OUTPUT); t.setMode(TankBlockEntity.TankMode.OUTPUT);
            vaults.add(v); tanks.add(t);
        }
        // Registered in the opposite order to priority. Highest rejects iron/water.
        vaults.get(0).setRoutingSettings(com.nstut.economy.config.StorageRoutingSettings.parse("", -10));
        vaults.get(1).setRoutingSettings(com.nstut.economy.config.StorageRoutingSettings.parse("minecraft:iron_ingot", 10));
        vaults.get(2).setRoutingSettings(com.nstut.economy.config.StorageRoutingSettings.parse("minecraft:gold_ingot", 20));
        for (int slot = 1; slot < 54; slot++) vaults.get(1).setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        vaults.get(1).setItem(0, new ItemStack(Items.IRON_INGOT, 60));
        var payload = java.util.List.of(new ItemStack(Items.IRON_INGOT, 12));
        helper.assertTrue(VaultManager.simulateInsertItemStacksToVaults(level, owner, payload).isEmpty(), "Allowed overflow must fit");
        helper.assertTrue(vaults.get(1).countItem(Items.IRON_INGOT) == 60 && vaults.get(0).isEmpty(), "Simulation must not mutate");
        helper.assertTrue(VaultManager.insertItemStacksToVaults(level, owner, payload).isEmpty(), "Commit must match simulation");
        helper.assertTrue(vaults.get(1).countItem(Items.IRON_INGOT) == 64 && vaults.get(0).countItem(Items.IRON_INGOT) == 8
                && vaults.get(2).isEmpty(), "Priority and rejection must route exact item overflow");
        helper.assertTrue(VaultManager.insertItemStacksToVaults(level, owner, java.util.List.of(new ItemStack(Items.GOLD_INGOT, 7))).isEmpty()
                && vaults.get(2).countItem(Items.GOLD_INGOT) == 7, "Second allowed item must route to its dedicated vault");
        for (var v : vaults) v.setMode(VaultBlockEntity.VaultMode.INPUT);
        NonNullList<ItemStack> extracted = NonNullList.create();
        helper.assertTrue(VaultManager.extractItemFromVaults(level, owner, Items.IRON_INGOT, 3, extracted)
                && vaults.get(1).countItem(Items.IRON_INGOT) == 61 && vaults.get(0).countItem(Items.IRON_INGOT) == 8,
                "Sales must extract from higher priority first");
        helper.assertTrue(VaultManager.countMaxAcceptableItems(level, owner, payload) == 0, "Input-only must reject purchases");
        for (int i = 0; i < 3; i++) {
            tanks.get(i).setCapacity(1000);
            tanks.get(i).setRoutingSettings(com.nstut.economy.config.StorageRoutingSettings.parse(
                    i == 2 ? "minecraft:lava" : i == 1 ? "minecraft:water" : "", i * 10));
        }
        var water = new EconomyFluidStack(Fluids.WATER, 1500);
        helper.assertTrue(com.nstut.economy.blocks.TankManager.countAvailableFluidSpaceInTanks(level, owner, Fluids.WATER) == 2000,
                "Capacity must exclude filter-rejecting tanks");
        helper.assertTrue(com.nstut.economy.blocks.TankManager.simulateInsertFluidToTanks(level, owner, water) == 1500
                && tanks.get(1).getFluidAmount() == 0, "Fluid simulation must honor filters without mutation");
        helper.assertTrue(com.nstut.economy.blocks.TankManager.insertFluidToTanks(level, owner, water) == 1500
                && tanks.get(1).getFluidAmount() == 1000 && tanks.get(0).getFluidAmount() == 500 && tanks.get(2).getFluidAmount() == 0,
                "Water must fill higher-priority allowed tank, then overflow");
        for (var t : tanks) t.setMode(TankBlockEntity.TankMode.INPUT);
        var drained = new java.util.ArrayList<EconomyFluidStack>();
        helper.assertTrue(com.nstut.economy.blocks.TankManager.extractFluidFromTanks(level, owner, Fluids.WATER, 250, drained) == 250
                && tanks.get(1).getFluidAmount() == 750 && tanks.get(0).getFluidAmount() == 500,
                "Fluid sales must use higher priority first");
        for (var v : vaults) v.setRoutingSettings(com.nstut.economy.config.StorageRoutingSettings.parse("minecraft:gold_ingot", 0));
        for (var t : tanks) t.setRoutingSettings(com.nstut.economy.config.StorageRoutingSettings.parse("minecraft:lava", 0));
        helper.assertTrue(VaultManager.restoreItemStacksToVaults(level, AccountRef.player(owner), payload, true).isEmpty()
                && VaultManager.restoreItemStacksToVaults(level, AccountRef.player(owner), payload, false).isEmpty(),
                "Owned item escrow must return despite input-only mode and rejecting purchase filter");
        helper.assertTrue(com.nstut.economy.blocks.TankManager.simulateInsertFluidToTanks(level, AccountRef.player(owner),
                new EconomyFluidStack(Fluids.WATER, 100), false) == 100
                && com.nstut.economy.blocks.TankManager.restoreFluidToTanks(level, owner, new EconomyFluidStack(Fluids.WATER, 100)) == 100,
                "Owned fluid escrow must return despite purchase restrictions");
        // Do not leave deliberately undersized test tanks in the reusable GameTest save.
        for (var t : tanks) t.setCapacity(com.nstut.economy.config.EconomyConfig.getInstance().getTankCapacity());
        helper.succeed();
    }

    @GameTest(template = "economy_gametest_empty", timeoutTicks = 60)
    public static void rejectedBuyVariantReturnsDespiteSellerPurchaseRestrictions(GameTestHelper helper) {
        helper.assertTrue(EconomyApi.isReady(), "Economy API must be ready for BUY rollback coverage");
        var level = helper.getLevel();
        UUID seller = UUID.randomUUID(), buyer = UUID.randomUUID();
        BlockPos sellerPos = new BlockPos(0, 1, 0), buyerPos = new BlockPos(1, 1, 0);
        helper.setBlock(sellerPos, BlockRegistries.VAULT.get());
        helper.setBlock(buyerPos, BlockRegistries.VAULT.get());
        var sellerVault = (VaultBlockEntity) level.getBlockEntity(helper.absolutePos(sellerPos));
        var buyerVault = (VaultBlockEntity) level.getBlockEntity(helper.absolutePos(buyerPos));
        sellerVault.setOwner(seller); buyerVault.setOwner(buyer);
        buyerVault.setMode(VaultBlockEntity.VaultMode.OUTPUT);
        for (int slot = 1; slot < buyerVault.getContainerSize(); slot++)
            buyerVault.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        buyerVault.setItem(0, new ItemStack(Items.IRON_INGOT, 63));
        ItemStack namedIron = new ItemStack(Items.IRON_INGOT);
        namedIron.setHoverName(net.minecraft.network.chat.Component.literal("Seller's named ingot"));
        var iron = new ItemCommodity(new ResourceLocation("minecraft", "iron_ingot"), Items.IRON_INGOT, BigDecimal.ONE);
        var sellerAccount = EconomyApi.accounts().getOrCreatePlayerAccount(seller);
        var buyerAccount = EconomyApi.accounts().getOrCreatePlayerAccount(buyer);
        buyerAccount.credit(new BigDecimal("100"), null);
        BigDecimal sellerBefore = sellerAccount.getBalance(), buyerBefore = buyerAccount.getBalance();

        for (boolean blacklist : new boolean[]{false, true}) {
            for (var mode : new VaultBlockEntity.VaultMode[]{VaultBlockEntity.VaultMode.BOTH, VaultBlockEntity.VaultMode.INPUT}) {
                sellerVault.setMode(mode);
                sellerVault.setRoutingSettings(com.nstut.economy.config.StorageRoutingSettings.parse(
                        blacklist ? "minecraft:iron_ingot" : "minecraft:gold_ingot", 0, blacklist));
                sellerVault.setItem(0, namedIron.copy());
                // An ITEM_ONLY BUY can preflight a plain stack but receive a non-stacking named variant.
                helper.assertTrue(VaultManager.countMaxAcceptableItems(level, buyer,
                        java.util.List.of(new ItemStack(Items.IRON_INGOT))) == 1, "Plain representative must pass preflight");
                helper.assertTrue(VaultManager.countMaxAcceptableItems(level, buyer,
                        java.util.List.of(namedIron)) == 0, "Actual named variant must be rejected");
                Order buy = new Order(buyer, iron, 1, BigDecimal.ONE, IOrder.OrderType.BUY, null);
                var result = buy.execute(seller, level);
                helper.assertTrue(!result.success && result.quantityTransferred == 0 && buy.getQuantity() == 1,
                        "Rejected variant must not fill the order");
                helper.assertTrue(sellerVault.countItem(Items.IRON_INGOT) == 1
                                && ItemStack.isSameItemSameTags(sellerVault.getItem(0), namedIron),
                        "Rejected item must return losslessly despite seller filter and mode: " + blacklist + "/" + mode);
                helper.assertTrue(buyerVault.countItem(Items.IRON_INGOT) == 63,
                        "Rejected variant must not change buyer inventory");
                helper.assertTrue(sellerAccount.getBalance().compareTo(sellerBefore) == 0
                                && buyerAccount.getBalance().compareTo(buyerBefore) == 0,
                        "Rejected delivery must refund both wallets exactly");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "economy_gametest_empty", timeoutTicks = 40)
    public static void unreturnableItemsRetainExactSellerOwnedRecovery(GameTestHelper helper) {
        var level = helper.getLevel();
        var teamAccount = AccountRef.team(UUID.randomUUID());
        var seller = new MarketIdentity(teamAccount, UUID.randomUUID(), teamAccount);
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, BlockRegistries.VAULT.get());
        var vault = (VaultBlockEntity) level.getBlockEntity(helper.absolutePos(pos));
        vault.setOwner(teamAccount); vault.setMode(VaultBlockEntity.VaultMode.INPUT);
        vault.setRoutingSettings(com.nstut.economy.config.StorageRoutingSettings.parse("minecraft:iron_ingot", 0, true));
        for (int slot = 1; slot < vault.getContainerSize(); slot++)
            vault.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        vault.setItem(0, new ItemStack(Items.IRON_INGOT, 63));
        ItemStack namedIron = new ItemStack(Items.IRON_INGOT);
        namedIron.setHoverName(net.minecraft.network.chat.Component.literal("Retained seller variant"));
        var payload = java.util.List.of(new ItemStack(Items.IRON_INGOT, 2), namedIron);
        var iron = new ItemCommodity(new ResourceLocation("minecraft", "iron_ingot"), Items.IRON_INGOT, BigDecimal.ONE);
        var data = new com.nstut.economy.data.EconomyOrderData();
        var manager = new OrderManager(); manager.setOrderData(data);
        helper.assertTrue(!manager.restoreOrPreserveItemEscrow(seller, iron, payload, BigDecimal.ONE, level,
                        "gametest rejected BUY recovery"), "Insufficient space must retain recovery escrow");
        helper.assertTrue(vault.countItem(Items.IRON_INGOT) == 63,
                "Failed atomic restoration must not partially insert items");
        namedIron.setCount(7); // Recovery must own a defensive copy of the exact payload.
        manager.saveAll();
        helper.assertTrue(data.getOrders().size() == 1, "Recovery must be persisted as exactly one order");
        var snapshot = data.getOrders().values().iterator().next();
        helper.assertTrue(snapshot.identity.equals(seller) && snapshot.type.equals("SELL") && snapshot.quantity == 3,
                "Recovery must belong to the original seller principal, actor and storage account");
        helper.assertTrue(snapshot.reservedItems.size() == 2 && snapshot.reservedItems.get(0).getCount() == 2
                        && snapshot.reservedItems.get(1).getCount() == 1
                        && ItemStack.isSameItemSameTags(snapshot.reservedItems.get(1), namedIron),
                "Recovery must preserve exact variants and counts independently of the caller");
        helper.assertTrue(snapshot.addonMetadata.containsKey("economy:quarantine_reason"),
                "Retained items must never become an executable SELL offer");
        var reloaded = new OrderManager(); reloaded.loadFrom(data); reloaded.saveAll();
        helper.assertTrue(data.getOrders().size() == 1 && data.getOrders().containsKey(snapshot.orderId),
                "Quarantined seller escrow must survive manager reload and save");
        helper.succeed();
    }

    @GameTest(template = "economy_gametest_empty", timeoutTicks = 40)
    public static void blacklistRoutesItemsAndFluidsWithoutChangingSelections(GameTestHelper helper) {
        var level = helper.getLevel(); var owner = UUID.randomUUID();
        BlockPos vp = new BlockPos(0, 1, 0), tp = new BlockPos(1, 1, 0);
        helper.setBlock(vp, BlockRegistries.VAULT.get()); helper.setBlock(tp, BlockRegistries.TANK.get());
        var vault = (VaultBlockEntity)level.getBlockEntity(helper.absolutePos(vp));
        var tank = (TankBlockEntity)level.getBlockEntity(helper.absolutePos(tp));
        vault.setOwner(owner); tank.setOwner(owner);
        vault.setMode(VaultBlockEntity.VaultMode.OUTPUT); tank.setMode(TankBlockEntity.TankMode.OUTPUT);
        var itemSettings = com.nstut.economy.config.StorageRoutingSettings.parse("minecraft:iron_ingot", 17, true);
        var fluidSettings = com.nstut.economy.config.StorageRoutingSettings.parse("minecraft:water", 17, true);
        vault.setRoutingSettings(itemSettings); tank.setRoutingSettings(fluidSettings);
        var iron = java.util.List.of(new ItemStack(Items.IRON_INGOT, 3));
        var gold = java.util.List.of(new ItemStack(Items.GOLD_INGOT, 3));
        helper.assertTrue(VaultManager.countMaxAcceptableItems(level, owner, iron) == 0
                && !VaultManager.insertItemStacksToVaults(level, owner, iron).isEmpty(), "Blacklisted item must reject simulation and delivery");
        helper.assertTrue(VaultManager.insertItemStacksToVaults(level, owner, gold).isEmpty(), "Unlisted item must be accepted in blacklist mode");
        var water = new EconomyFluidStack(Fluids.WATER, 100);
        var lava = new EconomyFluidStack(Fluids.LAVA, 100);
        helper.assertTrue(com.nstut.economy.blocks.TankManager.simulateInsertFluidToTanks(level, owner, water) == 0
                && com.nstut.economy.blocks.TankManager.insertFluidToTanks(level, owner, water) == 0, "Blacklisted fluid must reject simulation and delivery");
        helper.assertTrue(com.nstut.economy.blocks.TankManager.insertFluidToTanks(level, owner, lava) == 100, "Unlisted fluid must be accepted in blacklist mode");
        tank.drain(100);
        vault.setRoutingSettings(itemSettings.withBlacklist(false)); tank.setRoutingSettings(fluidSettings.withBlacklist(false));
        helper.assertTrue(vault.getRoutingSettings().allowedIds().equals(itemSettings.allowedIds())
                && tank.getRoutingSettings().allowedIds().equals(fluidSettings.allowedIds()), "Switching filter modes must preserve both lists");
        helper.assertTrue(VaultManager.insertItemStacksToVaults(level, owner, iron).isEmpty()
                && com.nstut.economy.blocks.TankManager.insertFluidToTanks(level, owner, water) == 100, "Same lists must accept listed resources after switching to whitelist");
        helper.succeed();
    }

    @GameTest(template = "economy_gametest_empty", timeoutTicks = 40)
    public static void routingPersistsAndTiesAreDeterministic(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        var level = helper.getLevel();
        BlockPos a = new BlockPos(0, 1, 0), b = new BlockPos(1, 1, 0), t = new BlockPos(0, 1, 1);
        helper.setBlock(a, BlockRegistries.VAULT.get()); helper.setBlock(b, BlockRegistries.VAULT.get()); helper.setBlock(t, BlockRegistries.TANK.get());
        var first = (VaultBlockEntity) level.getBlockEntity(helper.absolutePos(a));
        var second = (VaultBlockEntity) level.getBlockEntity(helper.absolutePos(b));
        var tank = (TankBlockEntity) level.getBlockEntity(helper.absolutePos(t));
        second.setOwner(owner); first.setOwner(owner); tank.setOwner(owner);
        helper.assertTrue(VaultManager.getVaults(level, owner).get(0) == first, "Equal priorities must use coordinates, not registration order");
        var routing = com.nstut.economy.config.StorageRoutingSettings.parse("minecraft:iron_ingot,minecraft:gold_ingot", 99, true);
        first.setRoutingSettings(routing);
        var fluidRouting = com.nstut.economy.config.StorageRoutingSettings.parse("minecraft:water", -8, true);
        tank.setRoutingSettings(fluidRouting);
        for (int i = 0; i < 3; i++) { first.cycleMode(); tank.cycleMode(); }
        var vaultTag = first.saveWithoutMetadata(); var tankTag = tank.saveWithoutMetadata();
        first.setRoutingSettings(com.nstut.economy.config.StorageRoutingSettings.DEFAULT);
        tank.setRoutingSettings(com.nstut.economy.config.StorageRoutingSettings.DEFAULT);
        first.load(vaultTag); tank.load(tankTag);
        helper.assertTrue(first.getRoutingSettings().equals(routing) && tank.getRoutingSettings().equals(fluidRouting),
                "Filters and priority must survive all mode transitions and disk roundtrip");
        helper.assertTrue(first.getUpdateTag().getString("PurchaseFilter").equals(routing.filterText())
                && tank.getUpdateTag().getInt("StoragePriority") == -8
                && first.getUpdateTag().getBoolean("PurchaseFilterBlacklist")
                && tank.getUpdateTag().getBoolean("PurchaseFilterBlacklist"), "Client update tags must include routing settings");
        vaultTag.remove("PurchaseFilterBlacklist"); tankTag.remove("PurchaseFilterBlacklist");
        first.load(vaultTag); tank.load(tankTag);
        helper.assertTrue(first.getRoutingSettings().equals(routing.withBlacklist(false))
                && tank.getRoutingSettings().equals(fluidRouting.withBlacklist(false)),
                "Older filter data without a mode must retain its list as a whitelist");
        first.load(new net.minecraft.nbt.CompoundTag()); tank.load(new net.minecraft.nbt.CompoundTag());
        helper.assertTrue(first.getRoutingSettings().equals(com.nstut.economy.config.StorageRoutingSettings.DEFAULT)
                && tank.getRoutingSettings().equals(com.nstut.economy.config.StorageRoutingSettings.DEFAULT),
                "Legacy block data must keep unrestricted default routing");
        helper.succeed();
    }

    @GameTest(template = "economy_gametest_empty", timeoutTicks = 40)
    public static void pipeAndMachineCapabilitiesTransferOnEveryFace(GameTestHelper helper) {
        BlockPos vaultPos = new BlockPos(0, 1, 0);
        BlockPos tankPos = new BlockPos(1, 1, 0);
        helper.setBlock(vaultPos, BlockRegistries.VAULT.get());
        helper.setBlock(tankPos, BlockRegistries.TANK.get());
        var vault = (VaultBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(vaultPos));
        var tank = (TankBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(tankPos));
        var sides = new java.util.ArrayList<net.minecraft.core.Direction>(java.util.List.of(net.minecraft.core.Direction.values()));
        sides.add(null);
        for (var side : sides) {
            helper.assertTrue(vault.getSlotsForFace(side).length == 54
                    && vault.canPlaceItemThroughFace(53, new ItemStack(Items.IRON_INGOT), side)
                    && vault.canTakeItemThroughFace(53, new ItemStack(Items.IRON_INGOT), side),
                    "Vanilla sided inventory logistics must always expose every slot");
            for (var mode : VaultBlockEntity.VaultMode.values()) {
                vault.setMode(mode);
                var items = vault.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, side)
                        .orElseThrow(() -> new AssertionError("Missing vault item capability on " + side));
                helper.assertTrue(items.getSlots() == 54, "All vault slots must be visible to automation");
                helper.assertTrue(items.insertItem(53, new ItemStack(Items.IRON_INGOT, 8), true).isEmpty()
                        && vault.isEmpty(), "Simulated insertion must not change inventory");
                helper.assertTrue(items.insertItem(53, new ItemStack(Items.IRON_INGOT, 8), false).isEmpty(),
                        "Machine output must insert into vault");
                helper.assertTrue(items.extractItem(53, 3, true).getCount() == 3 && vault.countItem(Items.IRON_INGOT) == 8,
                        "Simulated extraction must preserve inventory");
                helper.assertTrue(items.extractItem(53, 8, false).getCount() == 8 && vault.isEmpty(),
                        "Machine input must extract from vault independently of market mode");
            }
            for (var mode : TankBlockEntity.TankMode.values()) {
                tank.setMode(mode);
                var fluids = tank.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER, side)
                        .orElseThrow(() -> new AssertionError("Missing tank fluid capability on " + side));
                var water = new net.minecraftforge.fluids.FluidStack(Fluids.WATER, 1000);
                var simulate = net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;
                var execute = net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
                helper.assertTrue(fluids.fill(water, simulate) == 1000 && tank.getFluidAmount() == 0,
                        "Simulated fill must not mutate tank");
                var taggedWater = water.copy();
                var variantTag = new net.minecraft.nbt.CompoundTag();
                variantTag.putString("variant", "special");
                taggedWater.setTag(variantTag);
                helper.assertTrue(!fluids.isFluidValid(0, taggedWater)
                        && fluids.fill(taggedWater, simulate) == 0
                        && fluids.fill(taggedWater, execute) == 0 && tank.getFluidAmount() == 0,
                        "Metadata-bearing fluids must not be accepted and silently stripped");
                helper.assertTrue(fluids.fill(water, execute) == 1000, "Machine output must fill tank");
                helper.assertTrue(fluids.drain(taggedWater, simulate).isEmpty()
                        && fluids.drain(taggedWater, execute).isEmpty() && tank.getFluidAmount() == 1000,
                        "Variant-specific extraction must not consume untagged stored fluid");
                helper.assertTrue(fluids.fill(new net.minecraftforge.fluids.FluidStack(Fluids.LAVA, 1000), execute) == 0,
                        "Incompatible fluid must be rejected");
                helper.assertTrue(fluids.drain(250, simulate).getAmount() == 250 && tank.getFluidAmount() == 1000,
                        "Simulated drain must preserve fluid");
                helper.assertTrue(fluids.drain(water, execute).getAmount() == 1000 && tank.getFluidAmount() == 0,
                        "Machine input must drain tank independently of market mode");
            }
        }
        var reloadedVault = BlockRegistries.VAULT_BE.get().create(vault.getBlockPos(), vault.getBlockState());
        helper.assertTrue(reloadedVault instanceof com.nstut.forge.ForgeVaultBlockEntity,
                "Saved vaults must reload with the Forge item capability implementation");
        var oldItems = vault.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER);
        var oldFluids = tank.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER);
        vault.invalidateCaps();
        tank.invalidateCaps();
        helper.assertTrue(!oldItems.isPresent() && !oldFluids.isPresent(), "Removed capabilities must invalidate cached references");
        vault.reviveCaps();
        tank.reviveCaps();
        helper.assertTrue(vault.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).isPresent()
                && tank.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER).isPresent(),
                "Capabilities must revive after reload");
        helper.succeed();
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

    @GameTest(template = "economy_gametest_empty", timeoutTicks = 40)
    public static void tankContainerInputAndOutputSlotsProcessStack(GameTestHelper helper) {
        BlockPos tankPos = new BlockPos(2, 1, 0);
        helper.setBlock(tankPos, BlockRegistries.TANK.get());
        var blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(tankPos));
        helper.assertTrue(blockEntity instanceof TankBlockEntity, "Tank must create its block entity");
        TankBlockEntity tank = (TankBlockEntity) blockEntity;
        helper.assertTrue(tank.getContainerSize() == 2, "Tank must expose distinct input and output inventory slots");
        helper.assertTrue(tank.getCapacity() == com.nstut.economy.config.EconomyConfig.getInstance().getTankCapacity(),
                "new Tank capacity must come from server storage config");

        tank.setItem(TankBlockEntity.INPUT_SLOT, new ItemStack(Items.WATER_BUCKET, 2));

        helper.assertTrue(tank.getItem(TankBlockEntity.INPUT_SLOT).isEmpty(),
                "stacked filled containers must be consumed from the input slot");
        ItemStack output = tank.getItem(TankBlockEntity.OUTPUT_SLOT);
        helper.assertTrue(output.is(Items.BUCKET) && output.getCount() == 2,
                "empty result containers must accumulate in the dedicated output slot");
        helper.assertTrue(tank.getFluidAmount() == 2000,
                "two water containers must transfer exactly 2,000 mB into the internal tank");
        helper.succeed();
    }

    @GameTest(template = "economy_gametest_empty", timeoutTicks = 60)
    public static void savedTanksAdoptIncreasedCapacityWithoutLosingFluid(GameTestHelper helper) {
        var config = com.nstut.economy.config.EconomyConfig.getInstance();
        int previousCapacity = config.getTankCapacity();
        try {
            config.setTankCapacity(512000);
            BlockPos tankPos = new BlockPos(3, 1, 0);
            helper.setBlock(tankPos, BlockRegistries.TANK.get());
            TankBlockEntity tank = (TankBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(tankPos));
            tank.fill(new EconomyFluidStack(Fluids.WATER, 125000));
            var saved = tank.getUpdateTag();
            saved.putInt("Capacity", 128000);
            tank.load(saved);
            helper.assertTrue(tank.getCapacity() == 512000 && tank.getFluidAmount() == 125000,
                    "Loading an existing tank must raise capacity and preserve fluid");
            helper.assertTrue(tank.simulateFill(new EconomyFluidStack(Fluids.WATER, 512000)) == 387000,
                    "Existing tanks must accept fluid up to the upgraded capacity");

            TankBlockEntity detached = new TankBlockEntity(tank.getType(), tank.getBlockPos(), tank.getBlockState());
            detached.load(saved);
            helper.assertTrue(detached.getCapacity() == 128000, "Unattached data must wait for an authoritative level");
            detached.setLevel(helper.getLevel());
            helper.assertTrue(detached.getCapacity() == 512000 && detached.getFluidAmount() == 125000,
                    "Attaching a loaded tank to the server must upgrade it");
            saved.putInt("Capacity", 768000);
            tank.load(saved);
            helper.assertTrue(tank.getCapacity() == 768000 && tank.getFluidAmount() == 125000,
                    "A larger existing capacity and its contents must survive migration");
            saved.putInt("Capacity", 128000);
            var fluidTag = new net.minecraft.nbt.CompoundTag();
            new EconomyFluidStack(Fluids.WATER, 600000).writeTo(fluidTag);
            saved.put("Fluid", fluidTag);
            tank.load(saved);
            helper.assertTrue(tank.getCapacity() == 600000 && tank.getFluidAmount() == 600000,
                    "Migration must never discard existing over-capacity fluid");
        } finally {
            config.setTankCapacity(previousCapacity);
        }
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
    public static void teamMarketPrincipalUsesTeamOwnedVaults(GameTestHelper helper) {
        helper.assertTrue(EconomyApi.isReady(), "Economy API must be ready for team market coverage");

        var actingPlayer = helper.makeMockPlayer();
        UUID actor = actingPlayer.getUUID();
        UUID counterparty = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        TeamRef team = new TeamRef(teamId, "GameTest Team", actor);
        boolean[] activeMember = {true};
        TeamRole[] memberRole = {TeamRole.OFFICER};
        TeamEconomyProvider fake = new TeamEconomyProvider() {
            @Override public EconomyId providerId() { return EconomyId.of("test", "economy_gametest"); }
            @Override public java.util.Optional<TeamRef> resolveTeam(UUID playerId) {
                return activeMember[0] && actor.equals(playerId) ? java.util.Optional.of(team) : java.util.Optional.empty();
            }
            @Override public java.util.Optional<TeamRef> getTeam(UUID id) {
                return teamId.equals(id) ? java.util.Optional.of(team) : java.util.Optional.empty();
            }
            @Override public TeamRole getRole(UUID playerId, UUID id) {
                return activeMember[0] && actor.equals(playerId) && teamId.equals(id) ? memberRole[0] : TeamRole.NONE;
            }
            @Override public java.util.Collection<UUID> getMembers(UUID id) {
                return teamId.equals(id) && activeMember[0] ? java.util.List.of(actor) : java.util.List.of();
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
            teamVault.setOwner(AccountRef.team(teamId));
            teamVault.setMode(VaultBlockEntity.VaultMode.BOTH);
            BlockPos absoluteVault = helper.absolutePos(teamVaultPos);
            actingPlayer.setPos(absoluteVault.getX() + 0.5, absoluteVault.getY() + 0.5, absoluteVault.getZ() + 0.5);
            helper.assertTrue(teamVault.stillValid(actingPlayer), "current officer can keep team storage open");
            var officerBreak = new net.minecraftforge.event.level.BlockEvent.BreakEvent(helper.getLevel(), absoluteVault,
                    teamVault.getBlockState(), actingPlayer);
            com.nstut.forge.EconomyEvents.onBlockBreak(officerBreak);
            helper.assertTrue(officerBreak.isCanceled(), "officer cannot break OWNER-controlled team storage");
            memberRole[0] = TeamRole.OWNER;
            var ownerBreak = new net.minecraftforge.event.level.BlockEvent.BreakEvent(helper.getLevel(), absoluteVault,
                    teamVault.getBlockState(), actingPlayer);
            com.nstut.forge.EconomyEvents.onBlockBreak(ownerBreak);
            helper.assertTrue(!ownerBreak.isCanceled(), "current team OWNER can break typed team storage");
            memberRole[0] = TeamRole.OFFICER;
            helper.assertTrue(AccountRef.team(teamId).equals(teamVault.getOwnerRef()),
                    "team market Vault must retain typed TEAM ownership");
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

            MarketIdentity teamIdentity = new MarketIdentity(AccountRef.team(teamId), actor, AccountRef.team(teamId));
            OrderManager buyBook = new OrderManager();
            var buyCreated = buyBook.createBuyOrder(teamIdentity, iron, 2, new BigDecimal("3"), false, helper.getLevel());
            helper.assertTrue(buyCreated.accepted() && buyCreated.order().isPresent(), "team BUY order must be accepted");
            IOrder.TransactionResult bought = buyCreated.order().orElseThrow().execute(MarketIdentity.personal(counterparty), helper.getLevel());
            helper.assertTrue(bought.success && bought.quantityTransferred == 2, "team BUY must execute through typed IOrder API");
            helper.assertTrue(teamAccount.getBalance().compareTo(new BigDecimal("94")) == 0, "team BUY must debit only the team principal");
            helper.assertTrue(actorAccount.getBalance().compareTo(actorBefore) == 0, "team BUY must not debit the actor personal wallet");
            helper.assertTrue(counterpartyAccount.getBalance().compareTo(counterpartyBefore.add(new BigDecimal("6"))) == 0, "team BUY must credit the seller personal wallet");
            helper.assertTrue(VaultManager.countItemInVaults(helper.getLevel(), AccountRef.team(teamId), iron) == 2, "team BUY delivery must target TEAM-owned storage");
            helper.assertTrue(VaultManager.countItemInVaults(helper.getLevel(), counterparty, iron) == 2, "team BUY must extract from the seller storage owner");

            NonNullList<ItemStack> reserved = NonNullList.create();
            helper.assertTrue(VaultManager.extractItemFromVaults(helper.getLevel(), AccountRef.team(teamId), iron, 1, reserved), "team SELL must reserve from TEAM-owned Vault storage");
            OrderManager sellBook = new OrderManager();
            var sellCreated = sellBook.createSellOrder(teamIdentity, iron, 1, new BigDecimal("4"), reserved, java.util.List.of(), helper.getLevel());
            helper.assertTrue(sellCreated.accepted() && sellCreated.order().isPresent(), "team SELL order must be accepted");
            IOrder.TransactionResult sold = sellCreated.order().orElseThrow().execute(MarketIdentity.personal(counterparty), helper.getLevel());
            helper.assertTrue(sold.success && sold.quantityTransferred == 1, "team SELL must execute through typed IOrder API");
            helper.assertTrue(teamAccount.getBalance().compareTo(new BigDecimal("98")) == 0, "team SELL proceeds must credit the team principal");
            helper.assertTrue(actorAccount.getBalance().compareTo(actorBefore) == 0, "team SELL must not credit the actor personal wallet");
            helper.assertTrue(counterpartyAccount.getBalance().compareTo(counterpartyBefore.add(new BigDecimal("2"))) == 0, "team SELL must debit the buyer personal wallet");
            helper.assertTrue(VaultManager.countItemInVaults(helper.getLevel(), AccountRef.team(teamId), iron) == 1, "team SELL reservation must come from TEAM storage");
            helper.assertTrue(VaultManager.countItemInVaults(helper.getLevel(), counterparty, iron) == 3, "team SELL delivery must target buyer storage");

            NonNullList<ItemStack> invalidatedEscrow = NonNullList.create();
            helper.assertTrue(VaultManager.extractItemFromVaults(helper.getLevel(), AccountRef.team(teamId), iron, 1, invalidatedEscrow),
                    "invalidated team SELL must reserve from TEAM storage before membership changes");
            OrderManager invalidatedBook = new OrderManager();
            var invalidated = invalidatedBook.createSellOrder(teamIdentity, iron, 1, new BigDecimal("5"),
                    invalidatedEscrow, java.util.List.of(), helper.getLevel());
            helper.assertTrue(invalidated.accepted() && invalidated.order().isPresent(), "team SELL must exist before leave/kick");
            UUID invalidatedId = invalidated.order().orElseThrow().getOrderId();
            activeMember[0] = false;
            helper.assertTrue(!teamVault.stillValid(actingPlayer), "leave/kick must invalidate an already-open storage menu");
            invalidatedBook.revalidateTeamOrders(helper.getLevel());
            helper.assertTrue(invalidatedBook.getOrder(invalidatedId).isEmpty(), "leave/kick must remove an invalidated team order");
            helper.assertTrue(VaultManager.countItemInVaults(helper.getLevel(), AccountRef.team(teamId), iron) == 1,
                    "invalidated team SELL escrow must return losslessly to the recorded TEAM storage owner");

            helper.succeed();
        } finally {
            teams.unregisterProvider(fake);
            if (previousProvider != null) teams.registerProvider(previousProvider);
            teams.setMode(previousMode);
        }
    }

}
