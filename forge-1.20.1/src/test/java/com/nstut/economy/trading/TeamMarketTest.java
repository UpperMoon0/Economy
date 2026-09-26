package com.nstut.economy.trading;

import com.nstut.Economy;
import com.nstut.economy.api.*;
import com.nstut.economy.core.AccountManager;
import com.nstut.economy.data.*;
import com.nstut.economy.server.MarketWalletSelection;
import com.nstut.economy.server.TeamWalletLifecycle;
import com.nstut.economy.test.MinecraftTestBase;
import net.minecraft.core.NonNullList;
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
        public Optional<TeamRef> resolveTeam(UUID player) { return roles.containsKey(player) && !deleted ? getTeam(team) : Optional.empty(); }
        public Optional<TeamRef> getTeam(UUID id) { return team.equals(id) && !deleted ? Optional.of(new TeamRef(team, "Builders", currentOwner)) : Optional.empty(); }
        public TeamRole getRole(UUID player, UUID id) { return roles.getOrDefault(player, TeamRole.NONE); }
        public boolean isAvailable() { return !unavailable; }
        public boolean isTeamDeleted(UUID id) { return !unavailable && deleted && team.equals(id); }
    };
    ItemCommodity commodity() { return new ItemCommodity(new ResourceLocation("minecraft", "iron_ingot"), Items.IRON_INGOT, BigDecimal.ONE); }
    MarketIdentity teamIdentity(UUID actor) { return new MarketIdentity(AccountRef.team(team), actor, actor); }
    @BeforeEach void setup() {
        EconomyApi.unbindRuntime();
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
        TeamWalletLifecycle.clear(); MarketWalletSelection.clear(); TradeLedger.clearTradeData(); EconomyEvents.clearListeners();
        EconomyApi.teamEconomy().unregisterProvider(provider); EconomyApi.teamEconomy().setMode(TeamEconomyMode.PERSONAL_ONLY);
    }
    Order sell(UUID actor, MarketIdentity identity) {
        var escrow = NonNullList.<ItemStack>create(); escrow.add(new ItemStack(Items.IRON_INGOT, 5));
        Order order = new Order(actor, commodity(), 5, new BigDecimal("2"), IOrder.OrderType.SELL, null, escrow);
        order.setIdentity(identity); return order;
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
    @Test void disbandPaysLastOwnerOnceAndPersistsTombstoneAcrossRestart() {
        currentOwner = officer; TeamWalletLifecycle.observe(new TeamRef(team, "Renamed", officer));
        deleted = true;
        TeamWalletLifecycle.reconcile(accounts, orders, null);
        assertEquals(new BigDecimal("100"), accounts.getOrCreatePlayerAccount(officer).getBalance());
        assertEquals(BigDecimal.ZERO, accounts.getOrCreateTeamAccount(team).getBalance());
        assertTrue(data.getTeamWallets().get(team).closing());
        data = EconomyAccountData.load(data.save(new CompoundTag()));
        accounts = new AccountManager(); accounts.loadFrom(data); TeamWalletLifecycle.bind(data);
        TeamWalletLifecycle.deleted(new TeamRef(team, "Replay with old owner", owner));
        TeamWalletLifecycle.reconcile(accounts, orders, null);
        assertEquals(new BigDecimal("100"), accounts.getOrCreatePlayerAccount(officer).getBalance());
        assertEquals(BigDecimal.ZERO, accounts.getOrCreatePlayerAccount(owner).getBalance());
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
        orders.saveAll();
        assertEquals(5, Order.fromSnapshot(saved.getOrders().get(sell.getOrderId())).getEscrowedItemCount());
    }
    @Test void vetoedSettlementRetriesWithoutDuplicatingFunds() {
        deleted = true;
        var veto = EconomyEvents.listen(EconomyEvents.TransferPre.class, event -> event.cancel());
        TeamWalletLifecycle.reconcile(accounts, orders, null);
        assertEquals(new BigDecimal("100"), accounts.getOrCreateTeamAccount(team).getBalance());
        veto.close();
        TeamWalletLifecycle.reconcile(accounts, orders, null);
        TeamWalletLifecycle.reconcile(accounts, orders, null);
        assertEquals(new BigDecimal("100"), accounts.getOrCreatePlayerAccount(owner).getBalance());
    }
}
