package com.nstut.economy.api;

import com.nstut.Economy;
import com.nstut.economy.compat.Compat;
import com.nstut.economy.data.EconomyOrderData;
import com.nstut.economy.test.MinecraftTestBase;
import com.nstut.economy.trading.EconomyFluidStack;
import com.nstut.economy.trading.FluidCommodity;
import com.nstut.economy.trading.ItemCommodity;
import com.nstut.economy.trading.Order;
import com.nstut.economy.trading.OrderManager;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AddonApiReviewRegressionTest extends MinecraftTestBase {
    private static final EconomyId ADDON_TYPE = EconomyId.of("reviewfixture", "commodity");
    private static final EconomyId ADDON_COMMODITY = EconomyId.of("reviewfixture", "token");
    private static final EconomyId ADDON_PROVIDER = EconomyId.of("reviewfixture", "storage");

    @BeforeEach
    void setUp() {
        EconomyEvents.clearListeners();
        EconomyApi.commodityTypes().unregister(ADDON_TYPE);
        EconomyApi.storage().unregister(ADDON_PROVIDER);
        Economy.ensureApiRegistrations();
    }

    @AfterEach
    void tearDown() {
        EconomyEvents.clearListeners();
        EconomyApi.commodityTypes().unregister(ADDON_TYPE);
        EconomyApi.storage().unregister(ADDON_PROVIDER);
    }

    @Test
    @DisplayName("Built-in item and fluid codecs survive an order-data save/reload round trip")
    void builtInOrderCodecsRoundTripAcrossSavedData() {
        ItemCommodity item = new ItemCommodity(new ResourceLocation("minecraft", "diamond"), Items.DIAMOND,
                new BigDecimal("12.5"));
        FluidCommodity fluid = new FluidCommodity(new ResourceLocation("minecraft", "water"), Fluids.WATER,
                new BigDecimal("0.25"));

        Order itemOrder = new Order(UUID.randomUUID(), item, 9, new BigDecimal("3"), IOrder.OrderType.BUY, null);
        Order fluidOrder = new Order(UUID.randomUUID(), fluid, 1_000, new BigDecimal("0.01"), IOrder.OrderType.BUY, null);
        EconomyOrderData data = new EconomyOrderData();
        data.putOrder(itemOrder.toSnapshot());
        data.putOrder(fluidOrder.toSnapshot());

        CompoundTag saved = data.save(new CompoundTag());
        EconomyOrderData restoredData = EconomyOrderData.load(saved);
        assertEquals(2, restoredData.getOrders().size());

        Order restoredItem = Order.fromSnapshot(restoredData.getOrders().get(itemOrder.getOrderId()));
        Order restoredFluid = Order.fromSnapshot(restoredData.getOrders().get(fluidOrder.getOrderId()));
        assertInstanceOf(ItemCommodity.class, restoredItem.getCommodity());
        assertInstanceOf(FluidCommodity.class, restoredFluid.getCommodity());
        assertEquals(item.getId(), restoredItem.getCommodity().getId());
        assertEquals(fluid.getId(), restoredFluid.getCommodity().getId());
        assertEquals(9, restoredItem.getQuantity());
        assertEquals(1_000, restoredFluid.getQuantity());
    }

    @Test
    @DisplayName("Provider-only escrow remains persisted while addon codec is unavailable and recovers later")
    void providerOnlyEscrowSurvivesMissingAddonAndRecovers() {
        UUID orderId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        StorageReservation reservation = new StorageReservation(ADDON_PROVIDER, ADDON_COMMODITY, 7,
                "opaque-reservation-v1", Map.of("providerState", "keep-me"));
        EconomyOrderData.OrderSnapshot snapshot = new EconomyOrderData.OrderSnapshot(
                orderId, owner, ADDON_COMMODITY.toString(), 7, 7, "2.5", "SELL",
                System.currentTimeMillis(), 0L, false, NonNullList.create(), new ArrayList<>(),
                false, false, "CUSTOM", ADDON_TYPE.toString(), 1, Map.of("schema", "fixture"),
                reservation, Map.of("addon", "reviewfixture"));
        EconomyOrderData data = new EconomyOrderData();
        data.putOrder(snapshot);

        OrderManager manager = new OrderManager();
        manager.loadFrom(data);
        assertTrue(manager.getOrder(orderId).isEmpty(), "unknown addon codec must not create an active order");
        manager.saveAll();

        EconomyOrderData.OrderSnapshot preserved = data.getOrders().get(orderId);
        assertNotNull(preserved, "provider-only escrow must remain in SavedData quarantine");
        assertNotNull(preserved.externalReservation);
        assertEquals("opaque-reservation-v1", preserved.externalReservation.token());
        assertEquals("keep-me", preserved.externalReservation.metadata().get("providerState"));

        EconomyApi.commodityTypes().register(new FixtureCommodityHandler());
        manager.loadFrom(data);
        Order recovered = manager.getOrder(orderId).orElseThrow();
        assertInstanceOf(FixtureCommodity.class, recovered.getCommodity());
        assertNotNull(recovered.getExternalReservation());
        assertEquals("opaque-reservation-v1", recovered.getExternalReservation().token());
    }

    @Test
    @DisplayName("Partial delivery remainder is supplied by the provider, not fabricated by core")
    void partialReservationRemainderIsProviderOwned() {
        FixtureCommodity commodity = new FixtureCommodity(ADDON_COMMODITY);
        FixtureProvider provider = new FixtureProvider();
        EconomyApi.storage().register(provider);
        StorageReservation original = new StorageReservation(ADDON_PROVIDER, ADDON_COMMODITY, 10,
                "provider-token-v1", Map.of("cursor", "0"));

        StorageReservation remainder = provider.remainingAfterDelivery(null, original, 4).orElseThrow();
        assertEquals(6, remainder.amount());
        assertEquals("provider-token-v2", remainder.token());
        assertEquals("4", remainder.metadata().get("cursor"));
        assertNotEquals(original.token(), remainder.token());
        assertTrue(provider.supports(commodity));

        IStorageProvider noSplitProvider = new FixtureProvider() {
            @Override
            public Optional<StorageReservation> remainingAfterDelivery(ServerLevel level,
                                                                      StorageReservation reservation,
                                                                      int deliveredAmount) {
                return IStorageProvider.super.remainingAfterDelivery(level, reservation, deliveredAmount);
            }
        };
        assertThrows(IllegalStateException.class,
                () -> noSplitProvider.remainingAfterDelivery(null, original, 4));
    }

    @Test
    @DisplayName("Order create/edit/cancel events fire and pre-create cancellation is authoritative")
    void marketLifecycleEventsArePublishedByManager() {
        ItemCommodity iron = new ItemCommodity(new ResourceLocation("minecraft", "iron_ingot"), Items.IRON_INGOT,
                BigDecimal.ZERO);
        OrderManager manager = new OrderManager();

        try (EconomyEvents.Subscription ignored = EconomyEvents.listen(MarketEvents.OrderCreatePre.class,
                event -> event.cancel())) {
            assertNull(manager.createServerBuyOrder(iron, 3, BigDecimal.ONE));
            assertTrue(manager.getAllOrders().isEmpty());
        }

        AtomicInteger created = new AtomicInteger();
        AtomicInteger edited = new AtomicInteger();
        AtomicInteger cancelled = new AtomicInteger();
        try (EconomyEvents.Subscription createdSub = EconomyEvents.listen(MarketEvents.OrderCreated.class,
                     event -> created.incrementAndGet());
             EconomyEvents.Subscription editedSub = EconomyEvents.listen(MarketEvents.OrderEdited.class,
                     event -> edited.incrementAndGet());
             EconomyEvents.Subscription cancelledSub = EconomyEvents.listen(MarketEvents.OrderCancelled.class,
                     event -> cancelled.incrementAndGet())) {
            Order order = manager.createServerBuyOrder(iron, 5, BigDecimal.ONE);
            assertNotNull(order);
            assertTrue(manager.editOrder(order.getOrderId(), OrderManager.SERVER_ID, 4,
                    new BigDecimal("2"), false));
            assertTrue(manager.cancelOrder(order.getOrderId(), OrderManager.SERVER_ID));
        }
        assertEquals(1, created.get());
        assertEquals(1, edited.get());
        assertEquals(1, cancelled.get());
    }

    @Test
    @DisplayName("Exact ItemStack escrow serialization preserves mutable stack state")
    void exactItemStackEscrowSerializationIsLossless() {
        ItemStack original = new ItemStack(Items.DIAMOND_SWORD);
        original.setDamageValue(17);
        original.setHoverName(Component.literal("Escrow Sword"));
        original.getOrCreateTag().putString("review-marker", "preserve-me");

        String serialized = Compat.serializeItemStack(null, original);
        ItemStack restored = Compat.deserializeItemStack(null, serialized);

        assertEquals(original.getItem(), restored.getItem());
        assertEquals(original.getCount(), restored.getCount());
        assertEquals(17, restored.getDamageValue());
        assertEquals("Escrow Sword", restored.getHoverName().getString());
        assertEquals(original.getTag(), restored.getTag());
        assertTrue(Compat.stacksEqual(original, restored));
    }

    private static final class FixtureCommodity implements ICommodity {
        private final EconomyId id;
        private FixtureCommodity(EconomyId id) { this.id = id; }
        @Override public EconomyId getId() { return id; }
        @Override public CommodityType getType() { return CommodityType.CUSTOM; }
        @Override public EconomyId getTypeId() { return ADDON_TYPE; }
        @Override public Component getDisplayName() { return Component.literal("Fixture Commodity"); }
        @Override public BigDecimal getBasePrice() { return BigDecimal.ZERO; }
        @Override public boolean hasDynamicPricing() { return false; }
        @Override public boolean canExtractFrom(IStorage storage, int amount) { return false; }
        @Override public boolean canInsertInto(IStorage storage, int amount) { return false; }
        @Override public boolean extractFrom(IStorage storage, int amount) { return false; }
        @Override public boolean insertInto(IStorage storage, int amount) { return false; }
    }

    private static final class FixtureCommodityHandler implements ICommodityTypeHandler {
        @Override public EconomyId id() { return ADDON_TYPE; }
        @Override public int currentSchemaVersion() { return 1; }
        @Override public boolean supports(ICommodity commodity) { return commodity instanceof FixtureCommodity; }
        @Override public CommodityPayload encode(ICommodity commodity) {
            return new CommodityPayload(1, Map.of("fixture", "true"));
        }
        @Override public ICommodity decode(EconomyId commodityId, CommodityPayload payload) {
            return new FixtureCommodity(commodityId);
        }
    }

    private static class FixtureProvider implements IStorageProvider {
        @Override public EconomyId id() { return ADDON_PROVIDER; }
        @Override public boolean supports(ICommodity commodity) { return commodity instanceof FixtureCommodity; }
        @Override public int available(ServerLevel level, UUID owner, ICommodity commodity) { return 10; }
        @Override public int receivable(ServerLevel level, UUID owner, ICommodity commodity, int requestedAmount) {
            return requestedAmount;
        }
        @Override public Optional<StorageReservation> reserve(ServerLevel level, UUID owner, ICommodity commodity, int amount) {
            return Optional.of(new StorageReservation(ADDON_PROVIDER, commodity.getId(), amount,
                    "provider-token-v1", Map.of("cursor", "0")));
        }
        @Override public int deliverReserved(ServerLevel level, StorageReservation reservation, UUID receiver, int amount) {
            return Math.min(amount, reservation.amount());
        }
        @Override public Optional<StorageReservation> remainingAfterDelivery(ServerLevel level,
                                                                             StorageReservation reservation,
                                                                             int deliveredAmount) {
            int remaining = reservation.amount() - deliveredAmount;
            if (remaining == 0) return Optional.empty();
            return Optional.of(new StorageReservation(ADDON_PROVIDER, reservation.commodityId(), remaining,
                    "provider-token-v2", Map.of("cursor", Integer.toString(deliveredAmount))));
        }
        @Override public boolean release(ServerLevel level, StorageReservation reservation) { return true; }
    }
}
