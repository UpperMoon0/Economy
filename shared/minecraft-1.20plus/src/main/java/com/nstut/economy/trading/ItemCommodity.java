package com.nstut.economy.trading;

import com.nstut.economy.api.CommodityPayload;
import com.nstut.economy.api.EconomyApi;
import com.nstut.economy.api.EconomyId;
import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.ICommodityTypeHandler;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.util.Map;

public class ItemCommodity implements ICommodity {
    private final ResourceLocation platformId;
    private final EconomyId id;
    private final Item item;
    private final BigDecimal basePrice;
    private final boolean dynamicPricing;
    private final boolean matchNBT;

    public ItemCommodity(ResourceLocation id, Item item, BigDecimal basePrice, boolean dynamicPricing, boolean matchNBT) {
        this.platformId = id; this.id = EconomyId.parse(id.toString()); this.item = item; this.basePrice = basePrice;
        this.dynamicPricing = dynamicPricing; this.matchNBT = matchNBT;
    }
    public ItemCommodity(ResourceLocation id, Item item, BigDecimal basePrice) { this(id, item, basePrice, true, false); }

    @Override public EconomyId getId() { return id; }
    @Override public CommodityType getType() { return CommodityType.ITEM; }
    @Override public Component getDisplayName() { return new ItemStack(item).getHoverName(); }
    @Override public BigDecimal getBasePrice() { return basePrice; }
    @Override public boolean hasDynamicPricing() { return dynamicPricing; }
    @Override public boolean canExtractFrom(IStorage storage, int amount) { return storage instanceof Container c && countInContainer(c) >= amount; }
    @Override public boolean canInsertInto(IStorage storage, int amount) { return storage instanceof Container; }
    @Override public boolean extractFrom(IStorage storage, int amount) { return false; }
    @Override public boolean insertInto(IStorage storage, int amount) { return false; }

    public boolean extractFrom(Container container, int amount, NonNullList<ItemStack> destination) {
        if (countInContainer(container) < amount) return false;
        int remaining = amount;
        for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(item)) { int take = Math.min(remaining, stack.getCount()); destination.add(container.removeItem(i, take)); remaining -= take; }
        }
        return remaining == 0;
    }
    public boolean insertInto(Container container, NonNullList<ItemStack> stacks) {
        for (ItemStack incoming : stacks) {
            ItemStack remainder = incoming.copy();
            for (int i = 0; i < container.getContainerSize() && !remainder.isEmpty(); i++) {
                ItemStack slot = container.getItem(i);
                if (slot.isEmpty()) { container.setItem(i, remainder.copy()); remainder.setCount(0); }
                else if (com.nstut.economy.compat.Compat.stacksEqual(slot, remainder)) {
                    int add = Math.min(slot.getMaxStackSize() - slot.getCount(), remainder.getCount()); slot.grow(add); remainder.shrink(add);
                }
            }
            if (!remainder.isEmpty()) return false;
        }
        return true;
    }
    private int countInContainer(Container container) {
        int count = 0; for (int i = 0; i < container.getContainerSize(); i++) { ItemStack stack = container.getItem(i); if (stack.is(item)) count += stack.getCount(); } return count;
    }

    public ResourceLocation getPlatformId() { return platformId; }
    public Item getItem() { return item; }
    public boolean shouldMatchNBT() { return matchNBT; }

    public static ItemCommodity fromItemStack(ItemStack stack, BigDecimal basePrice) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) id = com.nstut.economy.compat.Compat.rl("minecraft", stack.getItem().toString().toLowerCase().replace(':', '_'));
        return new ItemCommodity(id, stack.getItem(), basePrice);
    }

    public static void registerApiType() {
        EconomyApi.commodityTypes().register(new ICommodityTypeHandler() {
            @Override public EconomyId id() { return ICommodity.ITEM_TYPE; }
            @Override public int currentSchemaVersion() { return 1; }
            @Override public boolean supports(ICommodity commodity) { return commodity instanceof ItemCommodity; }
            @Override public CommodityPayload encode(ICommodity commodity) {
                ItemCommodity item = (ItemCommodity) commodity;
                return new CommodityPayload(1, Map.of("basePrice", item.basePrice.toPlainString(),
                        "dynamic", Boolean.toString(item.dynamicPricing), "matchNbt", Boolean.toString(item.matchNBT)));
            }
            @Override public ICommodity decode(EconomyId commodityId, CommodityPayload payload) {
                ResourceLocation rl = com.nstut.economy.compat.Compat.rl(commodityId.toString());
                Item resolved = BuiltInRegistries.ITEM.get(rl);
                BigDecimal base = new BigDecimal(payload.values().getOrDefault("basePrice", "0"));
                return new ItemCommodity(rl, resolved, base,
                        Boolean.parseBoolean(payload.values().getOrDefault("dynamic", "true")),
                        Boolean.parseBoolean(payload.values().getOrDefault("matchNbt", "false")));
            }
        });
    }

    @Override public boolean equals(Object o) { return this == o || (o instanceof ItemCommodity that && id.equals(that.id)); }
    @Override public int hashCode() { return 31 * ItemCommodity.class.hashCode() + id.hashCode(); }
}
