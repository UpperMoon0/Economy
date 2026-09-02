package example.economyaddon;

import com.nstut.economy.api.CommodityKey;
import com.nstut.economy.api.EconomyApi;
import com.nstut.economy.api.EconomyEvents;
import com.nstut.economy.api.EconomyId;
import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.IStorageProvider;
import com.nstut.economy.api.MarketEvents;
import com.nstut.economy.api.OrderCreateResult;
import com.nstut.economy.api.StorageDeliveryResult;
import com.nstut.economy.api.StorageReservation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Compile-only black-box consumer fixture. This source intentionally imports
 * Economy only through com.nstut.economy.api so CI catches accidental API
 * signature/package regressions from an addon author's point of view.
 */
public final class EconomyAddonCompileFixture {
    private static final EconomyId STORAGE_ID = EconomyId.of("fixtureaddon", "warehouse");

    private EconomyAddonCompileFixture() { }

    public static AutoCloseable register() {
        EconomyApi.storage().register(new FixtureStorageProvider());
        EconomyEvents.Subscription tradeSubscription = EconomyEvents.listen(
                MarketEvents.TradeCompleted.class,
                event -> event.trade().commodityTypeId());
        return () -> {
            tradeSubscription.close();
            EconomyApi.storage().unregister(STORAGE_ID);
        };
    }

    public static void exerciseRuntime(UUID playerId, ICommodity commodity) {
        if (!EconomyApi.isReady()) return;

        CommodityKey key = CommodityKey.of(commodity);
        EconomyApi.marketData().recentTrades(key, 20);
        EconomyApi.marketData().lastTradePrice(key);
        EconomyApi.marketData().tradedVolume(key);
        EconomyApi.marketData().activeOrderCount(key);

        OrderCreateResult result = EconomyApi.orders().createBuyOrder(
                playerId, commodity, 1, BigDecimal.ONE);
        result.order().ifPresent(order -> EconomyApi.orders().cancelOrder(order.getOrderId(), playerId));
    }

    private static final class FixtureStorageProvider implements IStorageProvider {
        @Override public EconomyId id() { return STORAGE_ID; }
        @Override public boolean supports(ICommodity commodity) { return true; }
        @Override public int available(ServerLevel level, UUID owner, ICommodity commodity) { return 64; }
        @Override public int receivable(ServerLevel level, UUID owner, ICommodity commodity, int requestedAmount) {
            return Math.max(0, requestedAmount);
        }

        @Override
        public Optional<StorageReservation> reserve(ServerLevel level, UUID owner,
                                                    ICommodity commodity, int amount) {
            if (amount <= 0) return Optional.empty();
            CompoundTag state = new CompoundTag();
            state.putString("fixture_state", "durable");
            return Optional.of(new StorageReservation(
                    STORAGE_ID,
                    commodity.getId(),
                    amount,
                    UUID.randomUUID().toString(),
                    Map.of("schema", "1"),
                    state));
        }

        @Override
        public StorageDeliveryResult deliverReserved(ServerLevel level, StorageReservation reservation,
                                                     UUID receiver, int amount) {
            int delivered = Math.min(Math.max(0, amount), reservation.amount());
            int remaining = reservation.amount() - delivered;
            if (delivered == 0) return StorageDeliveryResult.unchanged(reservation);
            if (remaining == 0) return StorageDeliveryResult.complete(delivered);
            StorageReservation rest = new StorageReservation(
                    reservation.providerId(),
                    reservation.commodityId(),
                    remaining,
                    UUID.randomUUID().toString(),
                    reservation.metadata(),
                    reservation.providerState());
            return StorageDeliveryResult.partial(delivered, rest);
        }

        @Override public boolean release(ServerLevel level, StorageReservation reservation) { return true; }
    }
}
