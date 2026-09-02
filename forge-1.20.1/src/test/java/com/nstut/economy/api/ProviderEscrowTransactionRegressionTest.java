package com.nstut.economy.api;

import com.nstut.Economy;
import com.nstut.economy.data.EconomyOrderData;
import com.nstut.economy.test.MinecraftTestBase;
import com.nstut.economy.trading.Order;
import com.nstut.economy.trading.OrderManager;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProviderEscrowTransactionRegressionTest extends MinecraftTestBase {
    private static final EconomyId TYPE = EconomyId.of("providerreview", "commodity");
    private static final EconomyId COMMODITY = EconomyId.of("providerreview", "token");
    private static final EconomyId PROVIDER_A = EconomyId.of("providerreview", "storage_a");
    private static final EconomyId PROVIDER_B = EconomyId.of("providerreview", "storage_b");

    @BeforeEach
    void setUp() {
        EconomyApi.commodityTypes().unregister(TYPE);
        EconomyApi.commodityTypes().register(new FixtureHandler());
        Economy.ensureApiRegistrations();
    }

    @AfterEach
    void tearDown() {
        EconomyApi.commodityTypes().unregister(TYPE);
    }

    @Test
    @DisplayName("available reports the largest atomic provider capacity rather than an unreservable sum")
    void availableMatchesAtomicReserveSemantics() {
        StorageProviderRegistry registry = new StorageProviderRegistry();
        FixtureCommodity commodity = new FixtureCommodity();
        UUID owner = UUID.randomUUID();
        registry.register(new CapacityProvider(PROVIDER_A, 4));
        registry.register(new CapacityProvider(PROVIDER_B, 7));

        assertEquals(7, registry.available(null, owner, commodity));
        assertTrue(registry.reserve(null, owner, commodity, 8).isEmpty());
        StorageReservation reservation = registry.reserve(null, owner, commodity, 7).orElseThrow();
        assertEquals(7, reservation.amount());
        assertEquals(PROVIDER_B, reservation.providerId());
    }

    @Test
    @DisplayName("Provider-backed SELL orders reject quantity changes while allowing price-only edits")
    void providerBackedSellRejectsQuantityResize() {
        FixtureCommodity commodity = new FixtureCommodity();
        UUID owner = UUID.randomUUID();
        StorageReservation reservation = reservation(PROVIDER_A, 10, "sell-reservation");
        Order order = new Order(owner, commodity, 10, 10, BigDecimal.ONE,
                IOrder.OrderType.SELL, null, NonNullList.create(), new ArrayList<>(), false);
        order.setExternalReservation(reservation);

        EconomyOrderData data = new EconomyOrderData();
        data.putOrder(order.toSnapshot());
        OrderManager manager = new OrderManager();
        manager.loadFrom(data);

        assertFalse(manager.editOrder(order.getOrderId(), owner, 12, new BigDecimal("2"), false, null));
        assertFalse(manager.editOrder(order.getOrderId(), owner, 6, new BigDecimal("2"), false, null));
        assertEquals(10, manager.getOrder(order.getOrderId()).orElseThrow().getQuantity());

        assertTrue(manager.editOrder(order.getOrderId(), owner, 10, new BigDecimal("2"), false, null));
        Order edited = manager.getOrder(order.getOrderId()).orElseThrow();
        assertEquals(10, edited.getQuantity());
        assertEquals(new BigDecimal("2"), edited.getPricePerUnit());
        assertEquals("sell-reservation", edited.getExternalReservation().token());
    }

    @Test
    @DisplayName("Quarantined provider escrow cannot silently reactivate after a save and reload")
    void quarantinedProviderEscrowStaysQuarantinedAcrossReload() {
        FixtureCommodity commodity = new FixtureCommodity();
        UUID owner = UUID.randomUUID();
        StorageReservation reservation = reservation(PROVIDER_A, 5, "quarantine-reservation");
        Order order = new Order(owner, commodity, 5, 5, BigDecimal.ONE,
                IOrder.OrderType.SELL, null, NonNullList.create(), new ArrayList<>(), false);
        order.setExternalReservation(reservation);
        order.markQuarantined("provider release failed");

        EconomyOrderData data = new EconomyOrderData();
        data.putOrder(order.toSnapshot());

        OrderManager first = new OrderManager();
        first.loadFrom(data);
        assertTrue(first.getOrder(order.getOrderId()).isEmpty());
        first.saveAll();
        assertNotNull(data.getOrders().get(order.getOrderId()));
        assertEquals("quarantine-reservation",
                data.getOrders().get(order.getOrderId()).externalReservation.token());

        OrderManager second = new OrderManager();
        second.loadFrom(data);
        assertTrue(second.getOrder(order.getOrderId()).isEmpty());
        second.saveAll();
        assertEquals("quarantine-reservation",
                data.getOrders().get(order.getOrderId()).externalReservation.token());
    }

    @Test
    @DisplayName("Failed temporary provider releases can be durably preserved for recovery")
    void failedTemporaryReleaseCanBePersistedForRecovery() {
        FixtureCommodity commodity = new FixtureCommodity();
        UUID seller = UUID.randomUUID();
        StorageReservation reservation = reservation(PROVIDER_A, 9, "temporary-buy-reservation");
        EconomyOrderData data = new EconomyOrderData();
        OrderManager manager = new OrderManager();
        manager.loadFrom(data);

        manager.preserveProviderReservation(seller, commodity, reservation, new BigDecimal("4.5"),
                "release(false) after payment failure");
        manager.saveAll();

        assertEquals(1, data.getOrders().size());
        EconomyOrderData.OrderSnapshot snapshot = data.getOrders().values().iterator().next();
        assertNotNull(snapshot.externalReservation);
        assertEquals("temporary-buy-reservation", snapshot.externalReservation.token());
        assertEquals(9, snapshot.externalReservation.amount());
        assertEquals("release(false) after payment failure",
                snapshot.addonMetadata.get("economy:quarantine_reason"));

        OrderManager reloaded = new OrderManager();
        reloaded.loadFrom(data);
        assertTrue(reloaded.getAllOrders().isEmpty());
        reloaded.saveAll();
        assertEquals("temporary-buy-reservation",
                data.getOrders().values().iterator().next().externalReservation.token());
    }

    private static StorageReservation reservation(EconomyId providerId, int amount, String token) {
        return new StorageReservation(providerId, COMMODITY, amount, token, Map.of("fixture", "true"));
    }

    private static final class FixtureCommodity implements ICommodity {
        @Override public EconomyId getId() { return COMMODITY; }
        @Override public CommodityType getType() { return CommodityType.CUSTOM; }
        @Override public EconomyId getTypeId() { return TYPE; }
        @Override public Component getDisplayName() { return Component.literal("Provider Review Commodity"); }
        @Override public BigDecimal getBasePrice() { return BigDecimal.ZERO; }
        @Override public boolean hasDynamicPricing() { return false; }
        @Override public boolean canExtractFrom(IStorage storage, int amount) { return false; }
        @Override public boolean canInsertInto(IStorage storage, int amount) { return false; }
        @Override public boolean extractFrom(IStorage storage, int amount) { return false; }
        @Override public boolean insertInto(IStorage storage, int amount) { return false; }
    }

    private static final class FixtureHandler implements ICommodityTypeHandler {
        @Override public EconomyId id() { return TYPE; }
        @Override public int currentSchemaVersion() { return 1; }
        @Override public boolean supports(ICommodity commodity) { return commodity instanceof FixtureCommodity; }
        @Override public CommodityPayload encode(ICommodity commodity) {
            return new CommodityPayload(1, Map.of("fixture", "true"));
        }
        @Override public ICommodity decode(EconomyId commodityId, CommodityPayload payload) {
            if (!COMMODITY.equals(commodityId)) throw new IllegalArgumentException("unexpected fixture commodity " + commodityId);
            return new FixtureCommodity();
        }
    }

    private static final class CapacityProvider implements IStorageProvider {
        private final EconomyId id;
        private final int capacity;

        private CapacityProvider(EconomyId id, int capacity) {
            this.id = id;
            this.capacity = capacity;
        }

        @Override public EconomyId id() { return id; }
        @Override public boolean supports(ICommodity commodity) { return commodity instanceof FixtureCommodity; }
        @Override public int available(ServerLevel level, UUID owner, ICommodity commodity) { return capacity; }
        @Override public int receivable(ServerLevel level, UUID owner, ICommodity commodity, int requestedAmount) {
            return Math.min(capacity, requestedAmount);
        }
        @Override public Optional<StorageReservation> reserve(ServerLevel level, UUID owner, ICommodity commodity, int amount) {
            if (amount > capacity) return Optional.empty();
            return Optional.of(new StorageReservation(id, commodity.getId(), amount,
                    id + "-reservation", Map.of()));
        }
        @Override public StorageDeliveryResult deliverReserved(ServerLevel level, StorageReservation reservation,
                                                               UUID receiver, int amount) {
            return StorageDeliveryResult.unchanged(reservation);
        }
        @Override public boolean release(ServerLevel level, StorageReservation reservation) { return true; }
    }
}
