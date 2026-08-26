package com.nstut.economy.trading;

import com.nstut.economy.api.ICommodity;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;

public class ItemCommodity implements ICommodity {
    private final Identifier id;
    private final Item item;
    private final BigDecimal basePrice;
    private final boolean dynamicPricing;
    private final boolean matchNBT;

    public ItemCommodity(Identifier id, Item item, BigDecimal basePrice,
                         boolean dynamicPricing, boolean matchNBT) {
        this.id = id;
        this.item = item;
        this.basePrice = basePrice;
        this.dynamicPricing = dynamicPricing;
        this.matchNBT = matchNBT;
    }

    public ItemCommodity(Identifier id, Item item, BigDecimal basePrice) {
        this(id, item, basePrice, true, false);
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public CommodityType getType() {
        return CommodityType.ITEM;
    }

    @Override
    public Component getDisplayName() {
        return new ItemStack(item).getHoverName();
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
        if (storage instanceof Container container) {
            return countInContainer(container) >= amount;
        }
        return false;
    }

    @Override
    public boolean canInsertInto(IStorage storage, int amount) {
        return storage instanceof Container;
    }

    @Override
    public boolean extractFrom(IStorage storage, int amount) {
        return false;
    }

    @Override
    public boolean insertInto(IStorage storage, int amount) {
        return false;
    }

    public boolean extractFrom(Container container, int amount, NonNullList<ItemStack> destination) {
        if (countInContainer(container) < amount) return false;
        int remaining = amount;
        for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                ItemStack taken = container.removeItem(i, take);
                destination.add(taken);
                remaining -= take;
            }
        }
        return remaining == 0;
    }

    public boolean insertInto(Container container, NonNullList<ItemStack> stacks) {
        for (ItemStack incoming : stacks) {
            ItemStack remainder = incoming.copy();
            for (int i = 0; i < container.getContainerSize() && !remainder.isEmpty(); i++) {
                ItemStack slot = container.getItem(i);
                if (slot.isEmpty()) {
                    container.setItem(i, remainder.copy());
                    remainder.setCount(0);
                } else if (com.nstut.economy.compat.Compat.stacksEqual(slot, remainder)) {
                    int space = slot.getMaxStackSize() - slot.getCount();
                    int add = Math.min(space, remainder.getCount());
                    slot.grow(add);
                    remainder.shrink(add);
                }
            }
            if (!remainder.isEmpty()) return false;
        }
        return true;
    }

    private int countInContainer(Container container) {
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    public Item getItem() {
        return item;
    }

    public boolean shouldMatchNBT() {
        return matchNBT;
    }

    public static ItemCommodity fromItemStack(ItemStack stack, BigDecimal basePrice) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            id = com.nstut.economy.compat.Compat.rl("minecraft", stack.getItem().toString().toLowerCase().replace(':' ,'_'));
        }
        return new ItemCommodity(id, stack.getItem(), basePrice);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemCommodity that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return 31 * ItemCommodity.class.hashCode() + (id != null ? id.hashCode() : 0);
    }
}
