package com.nstut.economy.api;

import com.nstut.Economy;
import com.nstut.economy.data.EconomyOrderData;
import com.nstut.economy.test.MinecraftTestBase;
import com.nstut.economy.trading.OrderManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AddonApiInvariantRegressionTest extends MinecraftTestBase {
    private static final EconomyId TYPE = EconomyId.of("identitytest", "commodity");
    private static final EconomyId COMMODITY = EconomyId.of("identitytest", "token");
    private static final EconomyId PROVIDER = EconomyId.of("identitytest", "storage");
    private static final EconomyId WRONG_PROVIDER = EconomyId.of("identitytest", "wrong_storage");

    @BeforeEach
    void setUp() {
        Economy.ensureApiRegistrations();
    }

    @Test
    @DisplayName("Equivalent custom commodity instances share one order-book identity")
    void equivalentCommodityInstancesShareOrderBookIdentity() {
        OrderManager manager = new OrderManager();
        UUID owner = UUID.randomUUID();
        ICommodity createdWith = new FixtureCommodity();
        ICommodity queriedWith = new FixtureCommodity();

        assertNotSame(createdWith, queriedWith);
        manager.createBuyOrder(owner, createdWith, 3, new BigDecimal("2.5"), null);

        assertEquals(1, manager.getBuyOrders(queriedWith).size());
        assertEquals(1, manager.getOrders(queriedWith).size());
        assertEquals(CommodityKey.of(createdWith), CommodityKey.of(queriedWith));
    }

    @Test
    @DisplayName("Malformed provider reservations are rejected and released")
    void malformedReservationIsRejectedAndReleased() {
        StorageProviderRegistry registry = new StorageProviderRegistry();
        BrokenProvider provider = new BrokenProvider(true);
        registry.register(provider);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> registry.reserve(null, UUID.randomUUID(), new FixtureCommodity(), 4));

        assertTrue(failure.getMessage().contains("invalid reservation"));
        assertTrue(failure.getMessage().contains("providerId"));
        assertTrue(provider.releaseCalled, "registry must best-effort release malformed reservations");
    }

    @Test
    @DisplayName("Malformed provider escrow is durably quarantined when release fails")
    void malformedReservationReleaseFailureIsPersisted() {
        EconomyOrderData data = new EconomyOrderData();
        OrderManager manager = new OrderManager();
        manager.loadFrom(data);
        StorageProviderRegistry registry = new StorageProviderRegistry(() -> manager);
        BrokenProvider provider = new BrokenProvider(false);
        registry.register(provider);
        UUID owner = UUID.randomUUID();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> registry.reserve(null, owner, new FixtureCommodity(), 4));

        assertTrue(provider.releaseCalled);
        assertTrue(failure.getMessage().contains("invalid reservation"));
        manager.saveAll();

        assertEquals(1, data.getOrders().size(), "unreleased malformed escrow must have a durable recovery snapshot");
        EconomyOrderData.OrderSnapshot snapshot = data.getOrders().values().iterator().next();
        assertEquals(owner, snapshot.owner);
        assertNotNull(snapshot.externalReservation);
        assertEquals("malformed-reservation", snapshot.externalReservation.token());
        assertEquals(WRONG_PROVIDER, snapshot.externalReservation.providerId());
        assertTrue(snapshot.addonMetadata.containsKey("economy:quarantine_reason"));

        OrderManager reloaded = new OrderManager();
        reloaded.loadFrom(data);
        reloaded.saveAll();
        assertEquals("malformed-reservation",
                data.getOrders().values().iterator().next().externalReservation.token(),
                "recovery token must survive repeated save/reload cycles");
    }

    private static final class FixtureCommodity implements ICommodity {
        @Override public EconomyId getId() { return COMMODITY; }
        @Override public CommodityType getType() { return CommodityType.CUSTOM; }
        @Override public EconomyId getTypeId() { return TYPE; }
        @Override public Component getDisplayName() { return Component.literal("Identity Test Commodity"); }
        @Override public BigDecimal getBasePrice() { return BigDecimal.ZERO; }
        @Override public boolean hasDynamicPricing() { return false; }
        @Override public boolean canExtractFrom(IStorage storage, int amount) { return false; }
        @Override public boolean canInsertInto(IStorage storage, int amount) { return false; }
        @Override public boolean extractFrom(IStorage storage, int amount) { return false; }
        @Override public boolean insertInto(IStorage storage, int amount) { return false; }
    }

    private static final class BrokenProvider implements IStorageProvider {
        private final boolean releaseResult;
        private boolean releaseCalled;

        private BrokenProvider(boolean releaseResult) {
            this.releaseResult = releaseResult;
        }

        @Override public EconomyId id() { return PROVIDER; }
        @Override public boolean supports(ICommodity commodity) { return commodity instanceof FixtureCommodity; }
        @Override public int available(ServerLevel level, UUID owner, ICommodity commodity) { return 4; }
        @Override public int receivable(ServerLevel level, UUID owner, ICommodity commodity, int requestedAmount) {
            return requestedAmount;
        }
        @Override
        public Optional<StorageReservation> reserve(ServerLevel level, UUID owner, ICommodity commodity, int amount) {
            return Optional.of(new StorageReservation(WRONG_PROVIDER, commodity.getId(), amount,
                    "malformed-reservation", Map.of()));
        }
        @Override
        public StorageDeliveryResult deliverReserved(ServerLevel level, StorageReservation reservation,
                                                     UUID receiver, int amount) {
            return StorageDeliveryResult.unchanged(reservation);
        }
        @Override
        public boolean release(ServerLevel level, StorageReservation reservation) {
            releaseCalled = true;
            return releaseResult;
        }
    }
}
