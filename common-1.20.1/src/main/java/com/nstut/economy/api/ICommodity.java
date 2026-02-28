package com.nstut.economy.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;

/**
 * Represents a tradeable commodity in the economy system.
 */
public interface ICommodity {
    
    /**
     * Gets the unique identifier for this commodity.
     * @return The resource location ID
     */
    ResourceLocation getId();
    
    /**
     * Gets the type of commodity.
     * @return The commodity type
     */
    CommodityType getType();
    
    /**
     * Gets the display name for this commodity.
     * @return The component name
     */
    Component getDisplayName();
    
    /**
     * Gets the base price for this commodity.
     * @return The base price
     */
    BigDecimal getBasePrice();
    
    /**
     * Checks if this commodity uses dynamic pricing.
     * @return true if price changes based on market
     */
    boolean hasDynamicPricing();
    
    /**
     * Checks if this commodity can be extracted from storage.
     * @param storage The storage to check
     * @param amount The amount to extract
     * @return true if extraction is possible
     */
    boolean canExtractFrom(IStorage storage, int amount);
    
    /**
     * Checks if this commodity can be inserted into storage.
     * @param storage The storage to check
     * @param amount The amount to insert
     * @return true if insertion is possible
     */
    boolean canInsertInto(IStorage storage, int amount);
    
    /**
     * Extracts this commodity from storage.
     * @param storage The storage to extract from
     * @param amount The amount to extract
     * @return true if successful
     */
    boolean extractFrom(IStorage storage, int amount);
    
    /**
     * Inserts this commodity into storage.
     * @param storage The storage to insert into
     * @param amount The amount to insert
     * @return true if successful
     */
    boolean insertInto(IStorage storage, int amount);
    
    /**
     * Enum representing different types of commodities.
     */
    enum CommodityType {
        ITEM,
        FLUID,
        ENERGY,
        CUSTOM
    }
    
    /**
     * Simple storage interface for commodity operations.
     */
    interface IStorage {
        // Marker interface - actual implementation depends on storage type
    }
}
