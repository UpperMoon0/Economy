package com.nstut.economy.trading;

import com.nstut.economy.api.ICommodity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigDecimal;

/**
 * Implementation of ICommodity for Minecraft items.
 */
public class ItemCommodity implements ICommodity {
    
    private final ResourceLocation id;
    private final Item item;
    private final BigDecimal basePrice;
    private final boolean dynamicPricing;
    private final boolean matchNBT;
    
    public ItemCommodity(ResourceLocation id, Item item, BigDecimal basePrice, 
                         boolean dynamicPricing, boolean matchNBT) {
        this.id = id;
        this.item = item;
        this.basePrice = basePrice;
        this.dynamicPricing = dynamicPricing;
        this.matchNBT = matchNBT;
    }
    
    public ItemCommodity(ResourceLocation id, Item item, BigDecimal basePrice) {
        this(id, item, basePrice, true, false);
    }
    
    @Override
    public ResourceLocation getId() {
        return id;
    }
    
    @Override
    public CommodityType getType() {
        return CommodityType.ITEM;
    }
    
    @Override
    public Component getDisplayName() {
        return new ItemStack(item).getDisplayName();
    }
    
    @Override
    public BigDecimal getBasePrice() {
        return basePrice;
    }
    
    @Override
    public boolean hasDynamicPricing() {
        return dynamicPricing;
    }
    
    @Override
    public boolean canExtractFrom(IStorage storage, int amount) {
        // Implementation would check item inventory capability
        return true;
    }
    
    @Override
    public boolean canInsertInto(IStorage storage, int amount) {
        // Implementation would check item inventory capability
        return true;
    }
    
    @Override
    public boolean extractFrom(IStorage storage, int amount) {
        // Platform-specific implementation needed
        return false;
    }
    
    @Override
    public boolean insertInto(IStorage storage, int amount) {
        // Platform-specific implementation needed
        return false;
    }
    
    public Item getItem() {
        return item;
    }
    
    public boolean shouldMatchNBT() {
        return matchNBT;
    }
    
    /**
     * Creates a commodity from an ItemStack.
     */
    public static ItemCommodity fromItemStack(ItemStack stack, BigDecimal basePrice) {
        ResourceLocation id = new ResourceLocation("economy", 
            stack.getItem().toString().toLowerCase().replace(':', '_'));
        return new ItemCommodity(id, stack.getItem(), basePrice);
    }
}
