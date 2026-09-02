package com.nstut.economy.api;

import com.nstut.Economy;
import com.nstut.economy.data.EconomyOrderData;
import com.nstut.economy.test.MinecraftTestBase;
import com.nstut.economy.trading.Order;
import com.nstut.economy.trading.OrderManager;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AddonPayloadPersistenceRegressionTest extends MinecraftTestBase {
    private static final EconomyId TYPE = EconomyId.of("payloadreview", "commodity");
    private static final EconomyId ID = EconomyId.of("payloadreview", "token");

    @BeforeEach
    void setUp() {
        EconomyApi.commodityTypes().unregister(TYPE);
        EconomyApi.commodityTypes().register(new Handler(false));
        Economy.ensureApiRegistrations();
    }

    @AfterEach
    void tearDown() {
        EconomyApi.commodityTypes().unregister(TYPE);
    }

    @Test
    @DisplayName("Active addon orders keep their last known payload while encode is unavailable")
    void activeOrderPreservesPayloadAcrossCodecOutage() {
        Order original = new Order(UUID.randomUUID(), new Commodity("blue"), 4,
                new BigDecimal("2.5"), IOrder.OrderType.BUY, null);
        EconomyOrderData data = new EconomyOrderData();
        data.putOrder(original.toSnapshot());

        OrderManager manager = new OrderManager();
        manager.loadFrom(data);
        assertEquals(Map.of("variant", "blue"), data.getOrders().get(original.getOrderId()).commodityPayload);

        EconomyApi.commodityTypes().unregister(TYPE);
        manager.saveAll();
        EconomyOrderData.OrderSnapshot preserved = data.getOrders().get(original.getOrderId());
        assertNotNull(preserved);
        assertEquals(TYPE.toString(), preserved.commodityTypeId);
        assertEquals(Map.of("variant", "blue"), preserved.commodityPayload,
                "missing handler must not rewrite a known addon payload to empty");

        EconomyApi.commodityTypes().register(new Handler(false));
        manager.loadFrom(data);
        Commodity recovered = (Commodity) manager.getOrder(original.getOrderId()).orElseThrow().getCommodity();
        assertEquals("blue", recovered.variant);
    }

    @Test
    @DisplayName("Transient encoder exceptions preserve the last known addon payload")
    void activeOrderPreservesPayloadWhenEncoderThrows() {
        Order original = new Order(UUID.randomUUID(), new Commodity("blue"), 4,
                new BigDecimal("2.5"), IOrder.OrderType.BUY, null);
        EconomyOrderData data = new EconomyOrderData();
        data.putOrder(original.toSnapshot());
        OrderManager manager = new OrderManager();
        manager.loadFrom(data);

        EconomyApi.commodityTypes().unregister(TYPE);
        EconomyApi.commodityTypes().register(new Handler(true));
        manager.saveAll();

        assertEquals(Map.of("variant", "blue"), data.getOrders().get(original.getOrderId()).commodityPayload);
    }

    private static final class Commodity implements ICommodity {
        private final String variant;
        private Commodity(String variant) { this.variant = variant; }
        @Override public EconomyId getId() { return ID; }
        @Override public CommodityType getType() { return CommodityType.CUSTOM; }
        @Override public EconomyId getTypeId() { return TYPE; }
        @Override public Component getDisplayName() { return Component.literal("Payload " + variant); }
        @Override public BigDecimal getBasePrice() { return BigDecimal.ZERO; }
        @Override public boolean hasDynamicPricing() { return false; }
        @Override public boolean canExtractFrom(IStorage storage, int amount) { return false; }
        @Override public boolean canInsertInto(IStorage storage, int amount) { return false; }
        @Override public boolean extractFrom(IStorage storage, int amount) { return false; }
        @Override public boolean insertInto(IStorage storage, int amount) { return false; }
    }

    private static final class Handler implements ICommodityTypeHandler {
        private final boolean throwOnEncode;
        private Handler(boolean throwOnEncode) { this.throwOnEncode = throwOnEncode; }
        @Override public EconomyId id() { return TYPE; }
        @Override public int currentSchemaVersion() { return 1; }
        @Override public boolean supports(ICommodity commodity) { return commodity instanceof Commodity; }
        @Override public CommodityPayload encode(ICommodity commodity) {
            if (throwOnEncode) throw new IllegalStateException("transient encoder failure");
            return new CommodityPayload(1, Map.of("variant", ((Commodity) commodity).variant));
        }
        @Override public ICommodity decode(EconomyId commodityId, CommodityPayload payload) {
            if (!ID.equals(commodityId)) throw new IllegalArgumentException("unexpected commodity " + commodityId);
            return new Commodity(payload.values().getOrDefault("variant", "missing"));
        }
    }
}
