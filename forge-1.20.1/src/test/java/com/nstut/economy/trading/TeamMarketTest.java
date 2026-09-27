package com.nstut.economy.trading;

import com.nstut.Economy;
import com.nstut.economy.api.*;
import com.nstut.economy.api.internal.TeamWalletState;
import com.nstut.economy.core.AccountManager;
import com.nstut.economy.data.*;
import com.nstut.economy.server.MarketWalletSelection;
import com.nstut.economy.server.TeamWalletLifecycle;
import com.nstut.economy.test.MinecraftTestBase;
import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TeamMarketTest extends MinecraftTestBase {
    final UUID owner = UUID.randomUUID(), officer = UUID.randomUUID(), outsider = UUID.randomUUID(), team = UUID.randomUUID();
    final Map<UUID, TeamRole> roles = new HashMap<>();
    UUID currentOwner = owner;
    boolean deleted, unavailable;
    AccountManager accounts;
    OrderManager orders;
    EconomyAccountData data;
    final TeamEconomyProvider provider = new TeamEconomyProvider() {
        public EconomyId providerId() { return EconomyId.of("test", "team_market"); }
        public Optional<TeamRef> resolveTeam(UUID player) { return roles.containsKey(player) && !deleted ? getTeam(team) : Optional.empty(); }
        public Optional<TeamRef> getTeam(UUID id) { return team.equals(id) && !deleted ? Optional.of(new TeamRef(team, "Builders", currentOwner)) : Optional.empty(); }
        public TeamRole getRole(UUID player, UUID id) { return roles.getOrDefault(player, TeamRole.NONE); }
        public Collection<UUID> getMembers(UUID id) { return team.equals(id) && !deleted ? List.copyOf(roles.keySet()) : List.of(); }
        public boolean isAvailable() { return !unavailable; }
        public boolean isTeamDeleted(UUID id) { return !unavailable && deleted && team.equals(id); }
    };
    ItemCommodity commodity() { return new ItemCommodity(new ResourceLocation("minecraft", "iron_ingot"), Items.IRON_INGOT, BigDecimal.ONE); }
    MarketIdentity teamIdentity(UUID actor) { return new MarketIdentity(AccountRef.team(team), actor, AccountRef.team(team)); }
    TeamEconomyProvider deletingFallbackProvider() {
        return new com.nstut.economy.api.internal.FallbackTeamEconomyProvider() {
            public EconomyId providerId() { return EconomyId.of("test", "ftb_like_fallback"); }
            public Optional<TeamRef> resolveTeam(UUID player) { return Optional.empty(); }
            public Optional<TeamRef> getTeam(UUID id) { return Optional.empty(); }
            public TeamRole getRole(UUID player, UUID id) { return TeamRole.NONE; }
            public boolean isTeamDeleted(UUID id) { return true; }
        };
    }
    TeamEconomyProvider collidingFallbackProvider() {
        return new com.nstut.economy.api.internal.FallbackTeamEconomyProvider() {
            public EconomyId providerId() { return EconomyId.of("test", "colliding_ftb_like_fallback"); }
            public Optional<TeamRef> resolveTeam(UUID player) {
                return outsider.equals(player) ? Optional.of(new TeamRef(team, "Collision", outsider)) : Optional.empty();
            }
            public Optional<TeamRef> getTeam(UUID id) {
                return team.equals(id) ? Optional.of(new TeamRef(team, "Collision", outsider)) : Optional.empty();
            }
            public TeamRole getRole(UUID player, UUID id) {
                return outsider.equals(player) && team.equals(id) ? TeamRole.OWNER : TeamRole.NONE;
            }
            public Collection<UUID> getMembers(UUID id) { return team.equals(id) ? List.of(outsider) : List.of(); }
        };
    }
    @BeforeEach void setup() {
        com.nstut.economy.api.internal.EconomyRuntimeBridge.unbind();
        EconomyApi.teamEconomy().provider().ifPresent(EconomyApi.teamEconomy()::unregisterProvider);
        EconomyApi.teamEconomy().registerProvider(provider);
        EconomyApi.teamEconomy().setMode(TeamEconomyMode.HYBRID);
        roles.put(owner, TeamRole.OWNER); roles.put(officer, TeamRole.OFFICER);
        Economy.ensureApiRegistrations();
        data = new EconomyAccountData(); accounts = new AccountManager(); accounts.loadFrom(data);
        orders = new OrderManager(); orders.loadFrom(new EconomyOrderData());
        TeamWalletLifecycle.bind(data);
        TeamWalletLifecycle.observe(new TeamRef(team, "Builders", owner));
        accounts.getOrCreateTeamAccount(team).credit(new BigDecimal("100"), null);
        accounts.getOrCreatePlayerAccount(outsider).credit(new BigDecimal("100"), null);
    }
    @AfterEach void cleanup() {
        TeamWalletLifecycle.clear(); MarketWalletSelection.clear(); TradeLedger.clearTradeData(); com.nstut.economy.api.internal.EconomyEventBridge.clearListeners();
        EconomyApi.teamEconomy().unregisterProvider(provider); EconomyApi.teamEconomy().setMode(TeamEconomyMode.PERSONAL_ONLY);
    }
    Order sell(UUID actor, MarketIdentity identity) {
        var escrow = NonNullList.<ItemStack>create(); escrow.add(new ItemStack(Items.IRON_INGOT, 5));
        Order order = new Order(actor, commodity(), 5, new BigDecimal("2"), IOrder.OrderType.SELL, null, escrow);
        order.setIdentity(identity); return order;
    }
    @Test void completedTradesSnapshotEconomicAndStoragePrincipalsInsteadOfActors() {
        MarketIdentity teamStorage = teamIdentity(officer);
        MarketIdentity personal = MarketIdentity.personal(outsider);
        assertEquals(Set.of(AccountRef.team(team), AccountRef.player(outsider)),
                Order.affectedPortfolioAccounts(teamStorage, personal));

        MarketIdentity legacyTeamWithPersonalStorage = new MarketIdentity(AccountRef.team(team), officer, officer);
        assertEquals(Set.of(AccountRef.team(team), AccountRef.player(officer), AccountRef.player(outsider)),
                Order.affectedPortfolioAccounts(legacyTeamWithPersonalStorage, personal));
    }

    @Test void selectedTeamWalletBindsMoneyAndPhysicalStorageToTheTeam() {
        assertTrue(MarketWalletSelection.selectTeam(officer));
        MarketIdentity identity = MarketWalletSelection.identity(officer);
        assertEquals(AccountRef.team(team), identity.principal());
        assertEquals(AccountRef.team(team), identity.storageAccount());
        assertEquals(officer, identity.actor());
        assertEquals(team, identity.storageOwner());
        assertTrue(identity.authorized(EconomyApi.teamEconomy()));
    }
    @Test void legacyTeamOrdersKeepPersonalStorageAndFreshAuthorizationAfterReload() {
        MarketIdentity legacy = new MarketIdentity(AccountRef.team(team), officer, officer);
        Order order = sell(officer, legacy);
        EconomyOrderData saved = new EconomyOrderData(); saved.putOrder(order.toSnapshot());
        Order restored = Order.fromSnapshot(EconomyOrderData.load(saved.save(new CompoundTag())).getOrders().get(order.getOrderId()));
        assertEquals(AccountRef.player(officer), restored.getStorageAccount());
        assertTrue(restored.isAuthorized());
        assertTrue(restored.executePartial(MarketIdentity.personal(outsider), 1, null).success);
        roles.remove(officer);
        assertFalse(restored.isAuthorized());
    }
    @Test void collidingPlayerAndTeamStorageRemainDistinctThroughMigration() {
        MarketIdentity legacy = new MarketIdentity(AccountRef.team(officer), officer, officer);
        MarketIdentity typed = new MarketIdentity(AccountRef.team(officer), officer, AccountRef.team(officer));
        assertNotEquals(legacy.storageAccount(), typed.storageAccount());
        for (MarketIdentity identity : List.of(legacy, typed)) {
            Order order = sell(officer, identity);
            EconomyOrderData saved = new EconomyOrderData(); saved.putOrder(order.toSnapshot());
            CompoundTag tag = saved.save(new CompoundTag());
            assertEquals(identity, EconomyOrderData.load(tag).getOrders().get(order.getOrderId()).identity);
            tag.getList("Orders", 10).getCompound(0).remove("StorageAccount");
            assertEquals(AccountRef.player(officer), EconomyOrderData.load(tag).getOrders().get(order.getOrderId()).identity.storageAccount());
        }
    }
    @Test void providerRecoveryRetainsTeamAttributionAcrossSave() {
        EconomyOrderData saved = new EconomyOrderData(); orders.loadFrom(saved);
        StorageReservation reservation = new StorageReservation(EconomyId.parse("test:provider"),
                EconomyId.parse("minecraft:iron_ingot"), 3, "durable-token", Map.of());
        orders.preserveProviderReservation(teamIdentity(officer), commodity(), reservation, BigDecimal.ONE, "failed release");
        orders.saveAll();
        OrderManager restored = new OrderManager(); restored.loadFrom(EconomyOrderData.load(saved.save(new CompoundTag())));
        assertTrue(restored.hasTeamRecoveryReferences(team));
    }
    @Test void cancelledTeamCreationRetainsTypedRecoveryIdentity() {
        var escrow = NonNullList.<ItemStack>create(); escrow.add(new ItemStack(Items.IRON_INGOT, 5));
        var veto = EconomyEvents.listen(MarketEvents.OrderCreatePre.class, event -> event.cancel());
        try {
            assertTrue(orders.createSellOrder(teamIdentity(officer), commodity(), 5, BigDecimal.ONE,
                    escrow, List.of(), null).order().isEmpty());
            assertTrue(orders.hasTeamRecoveryReferences(team));
        } finally { veto.close(); }
    }
    @Test void teamBuyPaysTeamAndPersistsHumanAndStorageAttribution() {
        EconomyTradeData trades = new EconomyTradeData(); TradeLedger.setTradeData(trades);
        Order sell = sell(outsider, MarketIdentity.personal(outsider));
        var result = sell.executePartial(teamIdentity(officer), 3, null);
        assertTrue(result.success);
        assertEquals(new BigDecimal("94"), accounts.getOrCreateTeamAccount(team).getBalance());
        assertEquals(new BigDecimal("106"), accounts.getOrCreatePlayerAccount(outsider).getBalance());
        assertEquals(BigDecimal.ZERO, accounts.getOrCreatePlayerAccount(officer).getBalance());
        var roundTrip = EconomyTradeData.load(trades.save(new CompoundTag())).getTrades().get(0);
        assertEquals(teamIdentity(officer), roundTrip.buyerIdentity);
        assertEquals(AccountRef.team(team), roundTrip.buyerIdentity.storageAccount());
        assertEquals(MarketIdentity.personal(outsider), roundTrip.sellerIdentity);
        assertEquals(officer, roundTrip.buyer);
    }
    @Test void teamSellCreditsOnlyTeamWallet() {
        Order sell = sell(officer, teamIdentity(officer));
        assertTrue(sell.executePartial(MarketIdentity.personal(outsider), 2, null).success);
        assertEquals(new BigDecimal("104"), accounts.getOrCreateTeamAccount(team).getBalance());
        assertEquals(new BigDecimal("96"), accounts.getOrCreatePlayerAccount(outsider).getBalance());
        assertEquals(BigDecimal.ZERO, accounts.getOrCreatePlayerAccount(officer).getBalance());
        assertEquals(3, sell.getEscrowedItemCount());
    }
    @Test void stableOrderApiRecognizesTypedTeamPrincipalsButRequiresAWorldToExecute() {
        IOrder sell = sell(outsider, MarketIdentity.personal(outsider));
        assertTrue(sell.canExecute(teamIdentity(officer)));
        assertFalse(sell.execute(teamIdentity(officer), null).success);
        assertEquals(new BigDecimal("100"), accounts.getOrCreateTeamAccount(team).getBalance());
        assertEquals(new BigDecimal("100"), accounts.getOrCreatePlayerAccount(outsider).getBalance());
    }
    @Test void sameTeamCannotSelfTradeAcrossDifferentActorsButPersonalTeammatesCan() {
        assertFalse(sell(owner, teamIdentity(owner)).executePartial(teamIdentity(officer), 1, null).success);
        accounts.getOrCreatePlayerAccount(officer).credit(BigDecimal.TEN, null);
        assertTrue(sell(owner, MarketIdentity.personal(owner)).executePartial(MarketIdentity.personal(officer), 1, null).success);
        Order sameUuidTeam = sell(outsider, new MarketIdentity(AccountRef.team(outsider), outsider, outsider));
        assertNotEquals(sameUuidTeam.getPrincipal(), AccountRef.player(outsider));
    }
    @Test void matchingUsesTeamPrincipalAndRevalidatesMembership() {
        var buy = orders.createBuyOrder(teamIdentity(officer), commodity(), 4, BigDecimal.ONE, false, null);
        assertTrue(buy.order().isPresent());
        roles.remove(officer);
        Order sell = sell(outsider, MarketIdentity.personal(outsider));
        assertFalse(sell.executePartial(teamIdentity(officer), 1, null).success);
        assertFalse(orders.cancelOrder(buy.order().orElseThrow().getOrderId(), officer, null));
        assertFalse(orders.editOrder(buy.order().orElseThrow().getOrderId(), officer, 2, BigDecimal.ONE, false, null));
        orders.revalidateTeamOrders(null);
        assertTrue(orders.getOrder(buy.order().orElseThrow().getOrderId()).isEmpty());
        assertEquals(new BigDecimal("100"), accounts.getOrCreateTeamAccount(team).getBalance());
    }
    @Test void demotionBlocksExecutionAndAnotherOfficerCanCancel() {
        var buy = orders.createBuyOrder(teamIdentity(officer), commodity(), 4, BigDecimal.ONE, false, null).order().orElseThrow();
        roles.put(officer, TeamRole.MEMBER);
        assertFalse(sell(outsider, MarketIdentity.personal(outsider)).executePartial(buy.getIdentity(), 1, null).success);
        assertFalse(orders.cancelOrder(buy.getOrderId(), officer, null));
        assertTrue(orders.cancelOrder(buy.getOrderId(), owner, null));
    }
    @Test void orderAndLegacyMigrationsRoundTripIdempotently() {
        Order order = sell(officer, teamIdentity(officer));
        EconomyOrderData saved = new EconomyOrderData(); saved.putOrder(order.toSnapshot());
        for (int i=0; i<3; i++) saved = EconomyOrderData.load(saved.save(new CompoundTag()));
        assertEquals(teamIdentity(officer), Order.fromSnapshot(saved.getOrders().get(order.getOrderId())).getIdentity());
        CompoundTag legacy = saved.save(new CompoundTag());
        CompoundTag row = legacy.getList("Orders", 10).getCompound(0);
        row.remove("Principal"); row.remove("Actor"); row.remove("StorageOwner");
        assertEquals(MarketIdentity.personal(officer), EconomyOrderData.load(legacy).getOrders().get(order.getOrderId()).identity);
        row.putString("Principal", "BROKEN:identity");
        assertEquals(1, EconomyOrderData.load(legacy).getQuarantinedOrders().size());
    }
    @Test void typedTeamStorageIndexesRoundTripWithoutReinterpretingLegacyPlayerStorage() {
        BlockPos teamVault = new BlockPos(11, 64, 12);
        BlockPos teamTank = new BlockPos(13, 64, 14);
        BlockPos personalVault = new BlockPos(15, 64, 16);
        data.addVault(AccountRef.team(team), teamVault, "minecraft:overworld");
        data.addTank(AccountRef.team(team), teamTank, "minecraft:the_nether");
        data.addVault(owner, personalVault, "minecraft:overworld");

        EconomyAccountData restored = EconomyAccountData.load(data.save(new CompoundTag()));
        assertEquals(teamVault, restored.getStorageVaults().get(AccountRef.team(team)).get(0).pos);
        assertEquals(teamTank, restored.getStorageTanks().get(AccountRef.team(team)).get(0).pos);
        assertEquals(personalVault, restored.getStorageVaults().get(AccountRef.player(owner)).get(0).pos);
        assertFalse(restored.getTypedVaults().containsKey(AccountRef.player(owner)),
                "legacy PLAYER storage must remain in the UUID-compatible map");
    }

    @Test void disbandSplitsCashAcrossFinalMembersOnceAndPersistsTombstoneAcrossRestart() {
        currentOwner = officer; TeamWalletLifecycle.observe(new TeamRef(team, "Renamed", officer));
        deleted = true;
        TeamWalletLifecycle.reconcile(accounts, orders, null);
        assertEquals(new BigDecimal("50.00000000"), accounts.getOrCreatePlayerAccount(officer).getBalance());
        assertEquals(new BigDecimal("50.00000000"), accounts.getOrCreatePlayerAccount(owner).getBalance());
        assertEquals(0, accounts.getOrCreateTeamAccount(team).getBalance().compareTo(BigDecimal.ZERO));
        assertTrue(data.getTeamWallets().get(team).closing());
        assertTrue(data.getTeamWallets().get(team).storageSettled());
        assertEquals(Set.of(owner, officer), data.getTeamWallets().get(team).settledMembers());
        data = EconomyAccountData.load(data.save(new CompoundTag()));
        assertEquals(provider.providerId(), data.getTeamWallets().get(team).providerId());
        accounts = new AccountManager(); accounts.loadFrom(data); TeamWalletLifecycle.bind(data);
        TeamWalletLifecycle.deleted(new TeamRef(team, "Replay with old owner", owner));
        TeamWalletLifecycle.reconcile(accounts, orders, null);
        assertEquals(new BigDecimal("50.00000000"), accounts.getOrCreatePlayerAccount(officer).getBalance());
        assertEquals(new BigDecimal("50.00000000"), accounts.getOrCreatePlayerAccount(owner).getBalance());
    }

    @Test void providerWithoutMemberEnumerationNeverFallsBackToOwnerOnlySettlement() {
        TeamWalletLifecycle.clear();
        assertTrue(EconomyApi.teamEconomy().unregisterProvider(provider));
        TeamEconomyProvider incomplete = new TeamEconomyProvider() {
            public EconomyId providerId() { return EconomyId.of("test", "incomplete_team_provider"); }
            public Optional<TeamRef> resolveTeam(UUID player) {
                return roles.containsKey(player) ? Optional.of(new TeamRef(team, "Incomplete", owner)) : Optional.empty();
            }
            public Optional<TeamRef> getTeam(UUID id) {
                return team.equals(id) ? Optional.of(new TeamRef(team, "Incomplete", owner)) : Optional.empty();
            }
            public TeamRole getRole(UUID player, UUID id) { return roles.getOrDefault(player, TeamRole.NONE); }
        };
        EconomyApi.teamEconomy().registerProvider(incomplete);
        EconomyAccountData isolated = new EconomyAccountData();
        AccountManager isolatedAccounts = new AccountManager();
        isolatedAccounts.loadFrom(isolated);
        TeamWalletLifecycle.bind(isolated);
        isolatedAccounts.getOrCreateTeamAccount(team).credit(new BigDecimal("100"), null);
        try {
            TeamRef ref = new TeamRef(team, "Incomplete", owner);
            TeamWalletLifecycle.observe(ref);
            TeamWalletState tracked = isolated.getTeamWallets().get(team);
            assertNotNull(tracked, "provider provenance must persist even without a member snapshot");
            assertEquals(incomplete.providerId(), tracked.providerId());
            assertTrue(tracked.settlementMembers().isEmpty(),
                    "missing membership enumeration must not synthesize an owner-only settlement snapshot");
            TeamWalletLifecycle.deleted(ref);
            assertFalse(isolated.getTeamWallets().get(team).closing(),
                    "closure must remain blocked rather than guessing the owner as the only recipient");
            assertEquals(0, isolatedAccounts.getOrCreateTeamAccount(team).getBalance().compareTo(new BigDecimal("100")));
        } finally {
            TeamWalletLifecycle.clear();
            EconomyApi.teamEconomy().unregisterProvider(incomplete);
            EconomyApi.teamEconomy().registerProvider(provider);
            TeamWalletLifecycle.bind(data);
        }
    }

    @Test void emptyLiveEnumerationDoesNotEraseLastGoodMemberSnapshot() {
        TeamWalletLifecycle.observe(new TeamRef(team, "Builders", owner));
        assertEquals(Set.of(owner, officer), Set.copyOf(data.getTeamWallets().get(team).settlementMembers()));
        TeamWalletLifecycle.observe(new TeamRef(team, "Builders", owner), List.of());
        assertEquals(Set.of(owner, officer), Set.copyOf(data.getTeamWallets().get(team).settlementMembers()));
    }

    @Test void closureFreezesMemberSnapshotBeforeSettlement() {
        UUID third = UUID.randomUUID();
        roles.put(third, TeamRole.MEMBER);
        TeamWalletLifecycle.observe(new TeamRef(team, "Builders", owner));
        TeamWalletLifecycle.deleted(new TeamRef(team, "Builders", owner));
        roles.remove(third);
        deleted = true;
        TeamWalletLifecycle.reconcile(accounts, orders, null);
        TeamWalletState state = data.getTeamWallets().get(team);
        assertEquals(Set.of(owner, officer, third), Set.copyOf(state.settlementMembers()));
        assertEquals(0, accounts.getOrCreateTeamAccount(team).getBalance().compareTo(BigDecimal.ZERO));
        BigDecimal total = accounts.getOrCreatePlayerAccount(owner).getBalance()
                .add(accounts.getOrCreatePlayerAccount(officer).getBalance())
                .add(accounts.getOrCreatePlayerAccount(third).getBalance());
        assertEquals(0, total.compareTo(new BigDecimal("100")));
    }
    @Test void customWalletIsNotDeletedWhenFtbLikeFallbackBecomesActive() {
        BlockPos vault = new BlockPos(31, 70, 31);
        BlockPos tank = new BlockPos(32, 70, 32);
        data.addVault(AccountRef.team(team), vault, "minecraft:overworld");
        data.addTank(AccountRef.team(team), tank, "minecraft:the_nether");
        var teamOrder = orders.createBuyOrder(teamIdentity(officer), commodity(), 2, BigDecimal.ONE, false, null)
                .order().orElseThrow();
        TeamEconomyProvider fallback = deletingFallbackProvider();
        EconomyApi.teamEconomy().registerProvider(fallback);
        assertEquals(provider.providerId(), data.getTeamWallets().get(team).providerId());
        assertTrue(EconomyApi.teamEconomy().unregisterProvider(provider));
        try {
            assertSame(fallback, EconomyApi.teamEconomy().provider().orElseThrow());
            TeamWalletLifecycle.reconcile(accounts, orders, null);
            TeamWalletState state = data.getTeamWallets().get(team);
            assertEquals(provider.providerId(), state.providerId());
            assertFalse(state.closing(), "fallback provider must not delete a custom-provider wallet");
            assertEquals(0, accounts.getOrCreateTeamAccount(team).getBalance().compareTo(new BigDecimal("100")));
            assertEquals(0, accounts.getOrCreatePlayerAccount(owner).getBalance().compareTo(BigDecimal.ZERO));
            assertEquals(0, accounts.getOrCreatePlayerAccount(officer).getBalance().compareTo(BigDecimal.ZERO));
            assertEquals(vault, data.getStorageVaults().get(AccountRef.team(team)).get(0).pos);
            assertEquals(tank, data.getStorageTanks().get(AccountRef.team(team)).get(0).pos);
            assertFalse(data.getStorageVaults().containsKey(AccountRef.player(owner)));
            assertFalse(data.getStorageTanks().containsKey(AccountRef.player(owner)));
            assertTrue(orders.getOrder(teamOrder.getOrderId()).isPresent(),
                    "foreign fallback provider must not cancel the custom provider's Team order");
        } finally {
            EconomyApi.teamEconomy().unregisterProvider(fallback);
            EconomyApi.teamEconomy().registerProvider(provider);
        }
    }

    @Test void fallbackUuidCollisionCannotAccessOrCancelCustomProviderState() {
        var buy = orders.createBuyOrder(teamIdentity(officer), commodity(), 2, BigDecimal.ONE, false, null)
                .order().orElseThrow();
        TeamEconomyProvider fallback = collidingFallbackProvider();
        EconomyApi.teamEconomy().registerProvider(fallback);
        assertTrue(EconomyApi.teamEconomy().unregisterProvider(provider));
        try {
            assertSame(fallback, EconomyApi.teamEconomy().provider().orElseThrow());
            assertTrue(EconomyApi.teamEconomy().resolveTeam(outsider).isEmpty(),
                    "fallback must not expose a colliding UUID owned by another provider");
            assertFalse(EconomyApi.teamEconomy().canSpend(outsider, team));
            TeamWalletLifecycle.reconcile(accounts, orders, null);
            assertTrue(orders.getOrder(buy.getOrderId()).isPresent(),
                    "provider switch must not cancel durable orders owned by the missing provider");
            assertEquals(provider.providerId(), data.getTeamWallets().get(team).providerId());
            assertFalse(data.getTeamWallets().get(team).closing());
            assertEquals(0, accounts.getOrCreateTeamAccount(team).getBalance().compareTo(new BigDecimal("100")));
        } finally {
            EconomyApi.teamEconomy().unregisterProvider(fallback);
            EconomyApi.teamEconomy().registerProvider(provider);
        }
    }

    @Test void missingCustomProviderAfterRestartPreservesPersistedWalletProvenance() {
        CompoundTag saved = data.save(new CompoundTag());
        EconomyAccountData restored = EconomyAccountData.load(saved);
        assertEquals(provider.providerId(), restored.getTeamWallets().get(team).providerId());

        TeamEconomyProvider fallback = deletingFallbackProvider();
        EconomyApi.teamEconomy().registerProvider(fallback);
        assertTrue(EconomyApi.teamEconomy().unregisterProvider(provider));
        TeamWalletLifecycle.clear();
        AccountManager restoredAccounts = new AccountManager();
        restoredAccounts.loadFrom(restored);
        TeamWalletLifecycle.bind(restored);
        try {
            assertSame(fallback, EconomyApi.teamEconomy().provider().orElseThrow());
            TeamWalletLifecycle.reconcile(restoredAccounts, orders, null);
            TeamWalletState state = restored.getTeamWallets().get(team);
            assertEquals(provider.providerId(), state.providerId());
            assertFalse(state.closing(), "missing owning provider must preserve the wallet fail-closed");
            assertEquals(0, restoredAccounts.getOrCreateTeamAccount(team).getBalance().compareTo(new BigDecimal("100")));
            assertTrue(TeamWalletLifecycle.replacementOwner(AccountRef.team(team)).isEmpty());
        } finally {
            TeamWalletLifecycle.clear();
            EconomyApi.teamEconomy().unregisterProvider(fallback);
            EconomyApi.teamEconomy().registerProvider(provider);
            TeamWalletLifecycle.bind(data);
        }
    }

    @Test void closingCustomWalletDoesNotContinueSettlementUnderFallbackAfterRestart() {
        TeamWalletLifecycle.deleted(new TeamRef(team, "Builders", owner));
        TeamWalletState beforeRestart = data.getTeamWallets().get(team);
        assertTrue(beforeRestart.closing());
        assertEquals(provider.providerId(), beforeRestart.providerId());
        assertFalse(beforeRestart.storageSettled());

        EconomyAccountData restored = EconomyAccountData.load(data.save(new CompoundTag()));
        AccountManager restoredAccounts = new AccountManager();
        restoredAccounts.loadFrom(restored);
        TeamEconomyProvider fallback = deletingFallbackProvider();
        EconomyApi.teamEconomy().registerProvider(fallback);
        assertTrue(EconomyApi.teamEconomy().unregisterProvider(provider));
        TeamWalletLifecycle.clear();
        TeamWalletLifecycle.bind(restored);
        try {
            TeamWalletLifecycle.reconcile(restoredAccounts, orders, null);
            TeamWalletState state = restored.getTeamWallets().get(team);
            assertEquals(provider.providerId(), state.providerId());
            assertTrue(state.closing());
            assertFalse(state.storageSettled(),
                    "foreign fallback provider must not finish cash/storage settlement");
            assertTrue(state.settledMembers().isEmpty());
            assertEquals(0, restoredAccounts.getOrCreateTeamAccount(team).getBalance().compareTo(new BigDecimal("100")));
            assertEquals(0, restoredAccounts.getOrCreatePlayerAccount(owner).getBalance().compareTo(BigDecimal.ZERO));
            assertEquals(0, restoredAccounts.getOrCreatePlayerAccount(officer).getBalance().compareTo(BigDecimal.ZERO));
        } finally {
            TeamWalletLifecycle.clear();
            EconomyApi.teamEconomy().unregisterProvider(fallback);
            EconomyApi.teamEconomy().registerProvider(provider);
            TeamWalletLifecycle.bind(data);
        }
    }

    @Test void preProvenanceWalletNeedsPositiveResolutionBeforeProviderCanClaimIt() {
        CompoundTag legacyTag = data.save(new CompoundTag());
        legacyTag.getList("TeamWallets", 10).getCompound(0).remove("Provider");
        EconomyAccountData legacy = EconomyAccountData.load(legacyTag);
        assertEquals(TeamWalletState.UNKNOWN_PROVIDER_ID, legacy.getTeamWallets().get(team).providerId());

        TeamEconomyProvider fallback = deletingFallbackProvider();
        EconomyApi.teamEconomy().registerProvider(fallback);
        assertTrue(EconomyApi.teamEconomy().unregisterProvider(provider));
        TeamWalletLifecycle.clear();
        AccountManager legacyAccounts = new AccountManager();
        legacyAccounts.loadFrom(legacy);
        TeamWalletLifecycle.bind(legacy);
        try {
            TeamWalletLifecycle.reconcile(legacyAccounts, orders, null);
            assertEquals(TeamWalletState.UNKNOWN_PROVIDER_ID, legacy.getTeamWallets().get(team).providerId());
            assertFalse(legacy.getTeamWallets().get(team).closing(),
                    "negative fallback lookup must not claim/delete pre-provenance state");
            assertEquals(0, legacyAccounts.getOrCreateTeamAccount(team).getBalance().compareTo(new BigDecimal("100")));

            EconomyApi.teamEconomy().unregisterProvider(fallback);
            EconomyApi.teamEconomy().registerProvider(provider);
            TeamWalletLifecycle.reconcile(legacyAccounts, orders, null);
            assertEquals(provider.providerId(), legacy.getTeamWallets().get(team).providerId(),
                    "positive resolution by the original provider should migrate legacy provenance");
            assertFalse(legacy.getTeamWallets().get(team).closing());
        } finally {
            TeamWalletLifecycle.clear();
            EconomyApi.teamEconomy().unregisterProvider(fallback);
            EconomyApi.teamEconomy().unregisterProvider(provider);
            EconomyApi.teamEconomy().registerProvider(provider);
            TeamWalletLifecycle.bind(data);
        }
    }

    @Test void unavailableProviderDoesNotMeanDeletedAndStaleSelectionNeverChargesPersonal() {
        assertTrue(MarketWalletSelection.selectTeam(officer));
        unavailable = true;
        TeamWalletLifecycle.reconcile(accounts, orders, null);
        assertFalse(data.getTeamWallets().get(team).closing());
        assertThrows(IllegalStateException.class, () -> MarketWalletSelection.identity(officer));
        assertEquals(AccountRef.team(team), MarketWalletSelection.selected(officer));
    }
    @Test void teamPrimaryDefaultTracksMembershipUntilExplicitlyOverridden() {
        EconomyApi.teamEconomy().setMode(TeamEconomyMode.TEAM_PRIMARY);
        MarketWalletSelection.reset(officer);
        assertEquals(AccountRef.team(team), MarketWalletSelection.selected(officer));
        roles.remove(officer);
        assertEquals(AccountRef.player(officer), MarketWalletSelection.selected(officer));
        roles.put(officer, TeamRole.OFFICER);
        assertEquals(AccountRef.team(team), MarketWalletSelection.selected(officer));
        MarketWalletSelection.selectPersonal(officer);
        assertEquals(AccountRef.player(officer), MarketWalletSelection.selected(officer));
        MarketWalletSelection.reset(officer);
        assertEquals(AccountRef.team(team), MarketWalletSelection.selected(officer));
    }
    @Test void disbandKeepsCashWhenEscrowOrCompensationStillNeedsRecovery() {
        Order sell = sell(officer, teamIdentity(officer));
        EconomyOrderData saved = new EconomyOrderData(); saved.putOrder(sell.toSnapshot()); orders.loadFrom(saved);
        deleted = true;
        TeamWalletLifecycle.reconcile(accounts, orders, null); // no world for restoring escrow
        assertEquals(new BigDecimal("100"), accounts.getOrCreateTeamAccount(team).getBalance());
        assertEquals(BigDecimal.ZERO, accounts.getOrCreatePlayerAccount(owner).getBalance());
        assertEquals(BigDecimal.ZERO, accounts.getOrCreatePlayerAccount(officer).getBalance());
        assertTrue(TeamWalletLifecycle.replacementOwner(AccountRef.team(team)).isEmpty());
        data = EconomyAccountData.load(data.save(new CompoundTag()));
        TeamWalletLifecycle.bind(data);
        assertTrue(TeamWalletLifecycle.replacementOwner(AccountRef.team(team)).isEmpty());
        orders.saveAll();
        assertEquals(5, Order.fromSnapshot(saved.getOrders().get(sell.getOrderId())).getEscrowedItemCount());
    }
    @Test void vetoedSettlementRetriesWithoutDuplicatingFunds() {
        deleted = true;
        var veto = EconomyEvents.listen(EconomyEvents.TransferPre.class, event -> event.cancel());
        TeamWalletLifecycle.reconcile(accounts, orders, null);
        assertEquals(new BigDecimal("100"), accounts.getOrCreateTeamAccount(team).getBalance());
        assertTrue(TeamWalletLifecycle.replacementOwner(AccountRef.team(team)).isEmpty());
        veto.close();
        TeamWalletLifecycle.reconcile(accounts, orders, null);
        TeamWalletLifecycle.reconcile(accounts, orders, null);
        assertEquals(new BigDecimal("50.00000000"), accounts.getOrCreatePlayerAccount(owner).getBalance());
        assertEquals(new BigDecimal("50.00000000"), accounts.getOrCreatePlayerAccount(officer).getBalance());
        assertEquals(0, accounts.getOrCreateTeamAccount(team).getBalance().compareTo(BigDecimal.ZERO));
    }
}
