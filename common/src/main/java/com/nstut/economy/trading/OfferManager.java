package com.nstut.economy.trading;

import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.IOffer;
import com.nstut.economy.data.EconomyOfferData;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class OfferManager {

    private final Map<UUID, Offer> offers;
    private final Map<ICommodity, List<Offer>> commodityIndex;
    private EconomyOfferData backingData;

    public OfferManager() {
        this.offers = new ConcurrentHashMap<>();
        this.commodityIndex = new ConcurrentHashMap<>();
    }

    public void setOfferData(EconomyOfferData data) {
        this.backingData = data;
    }

    public void loadFrom(EconomyOfferData data) {
        offers.clear();
        commodityIndex.clear();
        this.backingData = data;
        for (EconomyOfferData.OfferSnapshot snap : data.getOffers().values()) {
            try {
                Offer offer = Offer.fromSnapshot(snap);
                if (offer.isValid()) {
                    offers.put(offer.getOfferId(), offer);
                    commodityIndex.computeIfAbsent(offer.getCommodity(), k -> new ArrayList<>()).add(offer);
                }
            } catch (Exception e) {
            }
        }
    }

    public void saveAll() {
        if (backingData == null) return;
        backingData.clearAll();
        for (Offer offer : offers.values()) {
            if (offer.isValid()) {
                backingData.putOffer(offer.toSnapshot());
            }
        }
    }

    public Offer createSellOffer(UUID owner, ICommodity commodity, int quantity,
                                  java.math.BigDecimal pricePerUnit) {
        return createSellOffer(owner, commodity, quantity, pricePerUnit, NonNullList.create(), null);
    }

    public Offer createSellOffer(UUID owner, ICommodity commodity, int quantity,
                                  java.math.BigDecimal pricePerUnit, NonNullList<ItemStack> reservedItems) {
        return createSellOffer(owner, commodity, quantity, pricePerUnit, reservedItems, null);
    }

    public Offer createSellOffer(UUID owner, ICommodity commodity, int quantity,
                                  java.math.BigDecimal pricePerUnit, NonNullList<ItemStack> reservedItems,
                                  net.minecraft.server.level.ServerLevel level) {
        Offer offer = new Offer(owner, commodity, quantity, pricePerUnit, IOffer.OfferType.SELL, null, copyStacks(reservedItems));

        List<Offer> matchingBuyOrders = getBuyOffers(commodity).stream()
                .filter(b -> b.getPricePerUnit().compareTo(pricePerUnit) >= 0 && !b.getOwner().equals(owner))
                .sorted(Comparator.comparing(Offer::getPricePerUnit).reversed().thenComparing(Offer::getCreatedAt))
                .collect(Collectors.toList());

        for (Offer buyOrder : matchingBuyOrders) {
            if (offer.getQuantity() <= 0) break;
            int matchQty = Math.min(offer.getQuantity(), buyOrder.getQuantity());
            IOffer.TransactionResult result = offer.executePartial(buyOrder.getOwner(), matchQty, level);
            if (result.success) {
                buyOrder.reduceQuantity(matchQty);
                if (backingData != null) {
                    if (buyOrder.getQuantity() == 0) backingData.removeOffer(buyOrder.getOfferId());
                    else backingData.putOffer(buyOrder.toSnapshot());
                }
            }
        }
        cleanupOffers();

        if (offer.getQuantity() > 0) {
            registerOffer(offer);
            return offer;
        } else if (backingData != null) {
            backingData.removeOffer(offer.getOfferId());
        }
        return null;
    }

    public Offer createBuyOffer(UUID owner, ICommodity commodity, int quantity,
                                 java.math.BigDecimal pricePerUnit) {
        return createBuyOffer(owner, commodity, quantity, pricePerUnit, null);
    }

    public Offer createBuyOffer(UUID owner, ICommodity commodity, int quantity,
                                 java.math.BigDecimal pricePerUnit, net.minecraft.server.level.ServerLevel level) {
        Offer offer = new Offer(owner, commodity, quantity, pricePerUnit, IOffer.OfferType.BUY, null);

        List<Offer> matchingSellOrders = getSellOffers(commodity).stream()
                .filter(s -> s.getPricePerUnit().compareTo(pricePerUnit) <= 0 && !s.getOwner().equals(owner))
                .sorted(Comparator.comparing(Offer::getPricePerUnit).thenComparing(Offer::getCreatedAt))
                .collect(Collectors.toList());

        for (Offer sellOrder : matchingSellOrders) {
            if (offer.getQuantity() <= 0) break;
            int matchQty = Math.min(offer.getQuantity(), sellOrder.getQuantity());
            IOffer.TransactionResult result = sellOrder.executePartial(owner, matchQty, level);
            if (result.success) {
                offer.reduceQuantity(matchQty);
                if (backingData != null) {
                    if (sellOrder.getQuantity() == 0) backingData.removeOffer(sellOrder.getOfferId());
                    else backingData.putOffer(sellOrder.toSnapshot());
                }
            }
        }
        cleanupOffers();

        if (offer.getQuantity() > 0) {
            registerOffer(offer);
            return offer;
        } else if (backingData != null) {
            backingData.removeOffer(offer.getOfferId());
        }
        return null;
    }

    private static NonNullList<ItemStack> copyStacks(NonNullList<ItemStack> original) {
        NonNullList<ItemStack> copy = NonNullList.create();
        for (ItemStack stack : original) {
            copy.add(stack.copy());
        }
        return copy;
    }

    public static final UUID SERVER_ID = new UUID(0, 0);

    public Offer createServerBuyOrder(ICommodity commodity, int quantity,
                                       java.math.BigDecimal pricePerUnit) {
        Offer offer = new Offer(SERVER_ID, commodity, quantity, pricePerUnit,
                                IOffer.OfferType.BUY, null);
        offer.setServerOrder(true);
        registerOffer(offer);
        return offer;
    }

    public Offer createServerSellOrder(ICommodity commodity, int quantity,
                                        java.math.BigDecimal pricePerUnit) {
        Offer offer = new Offer(SERVER_ID, commodity, quantity, pricePerUnit,
                                IOffer.OfferType.SELL, null);
        offer.setServerOrder(true);
        registerOffer(offer);
        return offer;
    }

    private void registerOffer(Offer offer) {
        offers.put(offer.getOfferId(), offer);
        commodityIndex.computeIfAbsent(offer.getCommodity(), k -> new ArrayList<>()).add(offer);
        if (backingData != null) {
            backingData.putOffer(offer.toSnapshot());
        }
    }

    public Optional<Offer> getOffer(UUID offerId) {
        return Optional.ofNullable(offers.get(offerId));
    }

    public boolean cancelOffer(UUID offerId, UUID requester) {
        Offer offer = offers.get(offerId);
        if (offer == null) {
            return false;
        }
        if (!offer.getOwner().equals(requester)) {
            return false;
        }
        if (offer.cancel()) {
            removeOffer(offer);
            return true;
        }
        return false;
    }

    private void removeOffer(Offer offer) {
        offers.remove(offer.getOfferId());
        List<Offer> commodityOffers = commodityIndex.get(offer.getCommodity());
        if (commodityOffers != null) {
            commodityOffers.remove(offer);
        }
        if (backingData != null) {
            backingData.removeOffer(offer.getOfferId());
        }
    }

    public List<Offer> getSellOffers(ICommodity commodity) {
        return getOffersByType(commodity, IOffer.OfferType.SELL).stream()
            .sorted(Comparator.comparing(Offer::getPricePerUnit))
            .collect(Collectors.toList());
    }

    public List<Offer> getBuyOffers(ICommodity commodity) {
        return getOffersByType(commodity, IOffer.OfferType.BUY).stream()
            .sorted(Comparator.comparing(Offer::getPricePerUnit).reversed())
            .collect(Collectors.toList());
    }

    public List<Offer> getAllOffers(ICommodity commodity) {
        return commodityIndex.getOrDefault(commodity, Collections.emptyList()).stream()
            .filter(Offer::isValid)
            .collect(Collectors.toList());
    }

    public List<Offer> getAllOffers() {
        return offers.values().stream()
            .filter(Offer::isValid)
            .sorted(Comparator.comparing(Offer::getCreatedAt).reversed())
            .collect(Collectors.toList());
    }

    private List<Offer> getOffersByType(ICommodity commodity, IOffer.OfferType type) {
        return commodityIndex.getOrDefault(commodity, Collections.emptyList()).stream()
            .filter(offer -> offer.isValid() && offer.getType() == type)
            .collect(Collectors.toList());
    }

    public List<Offer> getPlayerOffers(UUID player) {
        return offers.values().stream()
            .filter(offer -> offer.getOwner().equals(player) && offer.isValid())
            .sorted(Comparator.comparing(Offer::getCreatedAt).reversed())
            .collect(Collectors.toList());
    }

    public Optional<Offer> getPlayerOfferByIndex(UUID player, int index) {
        List<Offer> playerOffers = getPlayerOffers(player);
        if (index < 0 || index >= playerOffers.size()) return Optional.empty();
        return Optional.of(playerOffers.get(index));
    }

    public Optional<Offer> getGlobalOfferByIndex(int index) {
        List<Offer> all = getAllOffers();
        if (index < 0 || index >= all.size()) return Optional.empty();
        return Optional.of(all.get(index));
    }

    public Optional<Offer> getGlobalOfferByIndex(ICommodity commodity, int index) {
        List<Offer> all = getAllOffers(commodity).stream()
            .sorted(Comparator.comparing(Offer::getCreatedAt).reversed())
            .collect(Collectors.toList());
        if (index < 0 || index >= all.size()) return Optional.empty();
        return Optional.of(all.get(index));
    }

    public void cleanupOffers() {
        List<Offer> toRemove = offers.values().stream()
            .filter(offer -> !offer.isValid())
            .collect(Collectors.toList());
        for (Offer offer : toRemove) {
            removeOffer(offer);
        }
    }

    public Optional<java.math.BigDecimal> getBestSellPrice(ICommodity commodity) {
        return getSellOffers(commodity).stream()
            .findFirst()
            .map(Offer::getPricePerUnit);
    }

    public Optional<java.math.BigDecimal> getBestBuyPrice(ICommodity commodity) {
        return getBuyOffers(commodity).stream()
            .findFirst()
            .map(Offer::getPricePerUnit);
    }

    public void matchAllPendingOrders(net.minecraft.server.level.ServerLevel level) {
        if (offers.isEmpty()) return;
        List<Offer> allSell = offers.values().stream()
                .filter(o -> o.isValid() && o.getType() == IOffer.OfferType.SELL)
                .sorted(Comparator.comparing(Offer::getPricePerUnit).thenComparing(Offer::getCreatedAt))
                .collect(Collectors.toList());

        for (Offer sellOrder : allSell) {
            if (!sellOrder.isValid()) continue;
            List<Offer> matchingBuyOrders = getBuyOffers(sellOrder.getCommodity()).stream()
                    .filter(b -> b.isValid() && b.getPricePerUnit().compareTo(sellOrder.getPricePerUnit()) >= 0 && !b.getOwner().equals(sellOrder.getOwner()))
                    .sorted(Comparator.comparing(Offer::getPricePerUnit).reversed().thenComparing(Offer::getCreatedAt))
                    .collect(Collectors.toList());

            for (Offer buyOrder : matchingBuyOrders) {
                if (!sellOrder.isValid()) break;
                int matchQty = Math.min(sellOrder.getQuantity(), buyOrder.getQuantity());
                if (matchQty <= 0) continue;

                IOffer.TransactionResult result = sellOrder.executePartial(buyOrder.getOwner(), matchQty, level);
                if (result.success) {
                    buyOrder.reduceQuantity(matchQty);
                    if (backingData != null) {
                        if (buyOrder.getQuantity() == 0) backingData.removeOffer(buyOrder.getOfferId());
                        else backingData.putOffer(buyOrder.toSnapshot());
                        if (sellOrder.getQuantity() == 0) backingData.removeOffer(sellOrder.getOfferId());
                        else backingData.putOffer(sellOrder.toSnapshot());
                    }
                }
            }
        }
        cleanupOffers();
    }

    public Map<ICommodity, List<Offer>> getCommodityIndex() {
        return Collections.unmodifiableMap(commodityIndex);
    }
}
