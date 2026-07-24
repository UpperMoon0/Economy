package com.nstut.economy.trading;

import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.IOffer;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class OfferManager {

    private final Map<UUID, Offer> offers;
    private final Map<ICommodity, List<Offer>> commodityIndex;

    public OfferManager() {
        this.offers = new ConcurrentHashMap<>();
        this.commodityIndex = new ConcurrentHashMap<>();
    }

    public Offer createSellOffer(UUID owner, ICommodity commodity, int quantity,
                                  java.math.BigDecimal pricePerUnit) {
        Offer offer = new Offer(owner, commodity, quantity, pricePerUnit,
                                IOffer.OfferType.SELL, null);
        registerOffer(offer);
        return offer;
    }

    public Offer createSellOffer(UUID owner, ICommodity commodity, int quantity,
                                  java.math.BigDecimal pricePerUnit, NonNullList<ItemStack> reservedItems) {
        Offer offer = new Offer(owner, commodity, quantity, pricePerUnit,
                                IOffer.OfferType.SELL, null, reservedItems);
        registerOffer(offer);
        return offer;
    }

    public Offer createBuyOffer(UUID owner, ICommodity commodity, int quantity,
                                 java.math.BigDecimal pricePerUnit) {
        Offer offer = new Offer(owner, commodity, quantity, pricePerUnit,
                                IOffer.OfferType.BUY, null);
        registerOffer(offer);
        return offer;
    }

    private void registerOffer(Offer offer) {
        offers.put(offer.getOfferId(), offer);
        commodityIndex.computeIfAbsent(offer.getCommodity(), k -> new ArrayList<>()).add(offer);
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

    public Map<ICommodity, List<Offer>> getCommodityIndex() {
        return Collections.unmodifiableMap(commodityIndex);
    }
}
