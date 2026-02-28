package com.nstut.economy.trading;

import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.IOffer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages all market offers in the economy system.
 */
public class OfferManager {
    
    private final Map<UUID, Offer> offers;
    private final Map<ICommodity, List<Offer>> commodityIndex;
    
    public OfferManager() {
        this.offers = new ConcurrentHashMap<>();
        this.commodityIndex = new ConcurrentHashMap<>();
    }
    
    /**
     * Creates a new sell offer.
     * @param owner The seller's UUID
     * @param commodity The commodity to sell
     * @param quantity The quantity to sell
     * @param pricePerUnit The price per unit
     * @return The created offer
     */
    public Offer createSellOffer(UUID owner, ICommodity commodity, int quantity, 
                                  java.math.BigDecimal pricePerUnit) {
        Offer offer = new Offer(owner, commodity, quantity, pricePerUnit, 
                                IOffer.OfferType.SELL, null);
        registerOffer(offer);
        return offer;
    }
    
    /**
     * Creates a new buy offer.
     * @param owner The buyer's UUID
     * @param commodity The commodity to buy
     * @param quantity The quantity to buy
     * @param pricePerUnit The price per unit
     * @return The created offer
     */
    public Offer createBuyOffer(UUID owner, ICommodity commodity, int quantity,
                                 java.math.BigDecimal pricePerUnit) {
        Offer offer = new Offer(owner, commodity, quantity, pricePerUnit,
                                IOffer.OfferType.BUY, null);
        registerOffer(offer);
        return offer;
    }
    
    /**
     * Registers an offer in the system.
     * @param offer The offer to register
     */
    private void registerOffer(Offer offer) {
        offers.put(offer.getOfferId(), offer);
        commodityIndex.computeIfAbsent(offer.getCommodity(), k -> new ArrayList<>()).add(offer);
    }
    
    /**
     * Gets an offer by ID.
     * @param offerId The offer UUID
     * @return Optional containing the offer if found
     */
    public Optional<Offer> getOffer(UUID offerId) {
        return Optional.ofNullable(offers.get(offerId));
    }
    
    /**
     * Cancels and removes an offer.
     * @param offerId The offer UUID
     * @param requester The UUID of the player requesting cancellation
     * @return true if successfully cancelled
     */
    public boolean cancelOffer(UUID offerId, UUID requester) {
        Offer offer = offers.get(offerId);
        if (offer == null) {
            return false;
        }
        
        // Only the owner or admin can cancel
        if (!offer.getOwner().equals(requester)) {
            return false;
        }
        
        if (offer.cancel()) {
            removeOffer(offer);
            return true;
        }
        return false;
    }
    
    /**
     * Removes an offer from the system.
     * @param offer The offer to remove
     */
    private void removeOffer(Offer offer) {
        offers.remove(offer.getOfferId());
        List<Offer> commodityOffers = commodityIndex.get(offer.getCommodity());
        if (commodityOffers != null) {
            commodityOffers.remove(offer);
        }
    }
    
    /**
     * Gets all active sell offers for a commodity, sorted by price (lowest first).
     * @param commodity The commodity
     * @return List of sell offers
     */
    public List<Offer> getSellOffers(ICommodity commodity) {
        return getOffersByType(commodity, IOffer.OfferType.SELL).stream()
            .sorted(Comparator.comparing(Offer::getPricePerUnit))
            .collect(Collectors.toList());
    }
    
    /**
     * Gets all active buy offers for a commodity, sorted by price (highest first).
     * @param commodity The commodity
     * @return List of buy offers
     */
    public List<Offer> getBuyOffers(ICommodity commodity) {
        return getOffersByType(commodity, IOffer.OfferType.BUY).stream()
            .sorted(Comparator.comparing(Offer::getPricePerUnit).reversed())
            .collect(Collectors.toList());
    }
    
    /**
     * Gets all offers for a specific commodity.
     * @param commodity The commodity
     * @return List of all offers
     */
    public List<Offer> getAllOffers(ICommodity commodity) {
        return commodityIndex.getOrDefault(commodity, Collections.emptyList()).stream()
            .filter(Offer::isValid)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets all offers of a specific type for a commodity.
     * @param commodity The commodity
     * @param type The offer type
     * @return List of matching offers
     */
    private List<Offer> getOffersByType(ICommodity commodity, IOffer.OfferType type) {
        return commodityIndex.getOrDefault(commodity, Collections.emptyList()).stream()
            .filter(offer -> offer.isValid() && offer.getType() == type)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets all offers by a specific player.
     * @param player The player's UUID
     * @return List of offers
     */
    public List<Offer> getPlayerOffers(UUID player) {
        return offers.values().stream()
            .filter(offer -> offer.getOwner().equals(player) && offer.isValid())
            .collect(Collectors.toList());
    }
    
    /**
     * Cleans up expired and invalid offers.
     * Should be called periodically.
     */
    public void cleanupOffers() {
        List<Offer> toRemove = offers.values().stream()
            .filter(offer -> !offer.isValid())
            .collect(Collectors.toList());
        
        for (Offer offer : toRemove) {
            removeOffer(offer);
        }
    }
    
    /**
     * Gets the best sell price for a commodity.
     * @param commodity The commodity
     * @return Optional containing the lowest price
     */
    public Optional<java.math.BigDecimal> getBestSellPrice(ICommodity commodity) {
        return getSellOffers(commodity).stream()
            .findFirst()
            .map(Offer::getPricePerUnit);
    }
    
    /**
     * Gets the best buy price for a commodity.
     * @param commodity The commodity
     * @return Optional containing the highest price
     */
    public Optional<java.math.BigDecimal> getBestBuyPrice(ICommodity commodity) {
        return getBuyOffers(commodity).stream()
            .findFirst()
            .map(Offer::getPricePerUnit);
    }
}
