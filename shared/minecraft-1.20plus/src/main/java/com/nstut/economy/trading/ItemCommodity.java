package com.nstut.economy.trading;

import com.nstut.economy.api.CommodityPayload;
import com.nstut.economy.api.EconomyApi;
import com.nstut.economy.api.EconomyId;
import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.ICommodityTypeHandler;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class ItemCommodity implements ICommodity {
    private final ResourceLocation platformId;
    private final EconomyId id;
    private final Item item;
    private final BigDecimal basePrice;
    private final boolean dynamicPricing;
    private final ItemVariant variant;

    private ItemCommodity(ResourceLocation baseId, Item item, BigDecimal basePrice,
                          boolean dynamicPricing, ItemVariant variant) {
        this.platformId = Objects.requireNonNull(baseId, "baseId");
        this.item = Objects.requireNonNull(item, "item");
        this.basePrice = Objects.requireNonNull(basePrice, "basePrice");
        this.dynamicPricing = dynamicPricing;
        this.variant = Objects.requireNonNull(variant, "variant");
        this.id = variant.commodityId(EconomyId.parse(baseId.toString()));
    }

    /**
     * Legacy constructor retained for source compatibility. The old matchNBT
     * flag never carried a reference stack, so persisted v1 values migrate to
     * ITEM_ONLY rather than pretending to perform unsafe metadata matching.
     */
    @Deprecated
    public ItemCommodity(ResourceLocation id, Item item, BigDecimal basePrice, boolean dynamicPricing, boolean matchNBT) {
        this(id, item, basePrice, dynamicPricing, ItemVariant.itemOnly());
    }

    public ItemCommodity(ResourceLocation id, Item item, BigDecimal basePrice) {
        this(id, item, basePrice, true, ItemVariant.itemOnly());
    }

    @Override public EconomyId getId() { return id; }
    @Override public CommodityType getType() { return CommodityType.ITEM; }
    @Override public Component getDisplayName() { return variant.representativeOrBase(item).getHoverName(); }
    public Component getDisplayName(HolderLookup.Provider registries) { return getRepresentativeStack(registries).getHoverName(); }
    @Override public BigDecimal getBasePrice() { return basePrice; }
    @Override public boolean hasDynamicPricing() { return dynamicPricing; }
    @Override public boolean canExtractFrom(IStorage storage, int amount) { return storage instanceof Container c && countInContainer(c) >= amount; }
    @Override public boolean canInsertInto(IStorage storage, int amount) { return storage instanceof Container; }
    @Override public boolean extractFrom(IStorage storage, int amount) { return false; }
    @Override public boolean insertInto(IStorage storage, int amount) { return false; }

    public boolean matches(ItemStack stack) {
        if (variant.policy() != ItemMatchPolicy.ITEM_ONLY && !variant.hasCapturedRepresentative()) {
            throw new UnsupportedOperationException("Persisted variant matching requires registry access; use matches(Level, ItemStack) or matches(HolderLookup.Provider, ItemStack)");
        }
        return stack != null && !stack.isEmpty() && stack.is(item) && variant.matchesCaptured(stack);
    }

    public boolean matches(HolderLookup.Provider registries, ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(item) && variant.matches(registries, stack);
    }

    public boolean matches(Level level, ItemStack stack) {
        return level != null && matches(level.registryAccess(), stack);
    }

    public boolean extractFrom(Container container, int amount, NonNullList<ItemStack> destination) {
        if (countInContainer(container) < amount) return false;
        int remaining = amount;
        for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = container.getItem(i);
            if (matches(stack)) {
                int take = Math.min(remaining, stack.getCount());
                destination.add(container.removeItem(i, take));
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
                    int add = Math.min(slot.getMaxStackSize() - slot.getCount(), remainder.getCount());
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
            if (matches(stack)) count += stack.getCount();
        }
        return count;
    }

    public NonNullList<ItemStack> createStacks(HolderLookup.Provider registries, int amount) {
        NonNullList<ItemStack> result = NonNullList.create();
        if (amount <= 0) return result;
        ItemStack template = getRepresentativeStack(registries);
        int max = Math.max(1, template.getMaxStackSize());
        for (int remaining = amount; remaining > 0;) {
            int count = Math.min(remaining, max);
            ItemStack stack = template.copy();
            stack.setCount(count);
            result.add(stack);
            remaining -= count;
        }
        return result;
    }

    public ResourceLocation getPlatformId() { return platformId; }
    public EconomyId getBaseItemId() { return EconomyId.parse(platformId.toString()); }
    public Item getItem() { return item; }
    public ItemMatchPolicy getMatchPolicy() { return variant.policy(); }
    public String getVariantFingerprint() { return variant.fingerprint(); }
    public String getCanonicalVariantData() { return variant.canonicalData(); }
    public ItemStack getRepresentativeStack(HolderLookup.Provider registries) { return variant.representative(item, registries); }

    /** @deprecated Use {@link #getMatchPolicy()}. */
    @Deprecated public boolean shouldMatchNBT() { return variant.policy() == ItemMatchPolicy.EXACT; }

    /** Preserves the historical base-item behavior. */
    public static ItemCommodity fromItemStack(ItemStack stack, BigDecimal basePrice) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) id = com.nstut.economy.compat.Compat.rl("minecraft", stack.getItem().toString().toLowerCase().replace(':', '_'));
        return new ItemCommodity(id, stack.getItem(), basePrice);
    }

    public static ItemCommodity fromItemStack(HolderLookup.Provider registries, ItemStack stack,
                                              BigDecimal basePrice, ItemMatchPolicy policy) {
        Objects.requireNonNull(stack, "stack");
        ResourceLocation baseId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (baseId == null) baseId = com.nstut.economy.compat.Compat.rl("minecraft", stack.getItem().toString().toLowerCase().replace(':', '_'));
        return new ItemCommodity(baseId, stack.getItem(), basePrice, true, ItemVariant.capture(registries, stack, policy));
    }

    public static ItemCommodity exactFromItemStack(HolderLookup.Provider registries, ItemStack stack, BigDecimal basePrice) {
        return fromItemStack(registries, stack, basePrice, ItemMatchPolicy.EXACT);
    }

    public static void registerApiType() {
        EconomyApi.commodityTypes().register(new ICommodityTypeHandler() {
            @Override public EconomyId id() { return ICommodity.ITEM_TYPE; }
            @Override public int currentSchemaVersion() { return 2; }
            @Override public boolean supports(ICommodity commodity) { return commodity instanceof ItemCommodity; }

            @Override public CommodityPayload encode(ICommodity commodity) {
                ItemCommodity value = (ItemCommodity) commodity;
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("basePrice", value.basePrice.toPlainString());
                fields.put("dynamic", Boolean.toString(value.dynamicPricing));
                fields.put("baseItem", value.platformId.toString());
                fields.put("matchPolicy", value.variant.policy().name());
                if (value.variant.policy() != ItemMatchPolicy.ITEM_ONLY) {
                    fields.put("variantData", value.variant.canonicalData());
                    fields.put("variantFingerprint", value.variant.fingerprint());
                }
                return new CommodityPayload(2, fields);
            }

            @Override public ICommodity decode(EconomyId commodityId, CommodityPayload payload) {
                Map<String, String> fields = payload.values();
                ItemMatchPolicy policy = payload.version() <= 1
                        ? ItemMatchPolicy.ITEM_ONLY
                        : ItemMatchPolicy.parse(fields.getOrDefault("matchPolicy", ItemMatchPolicy.ITEM_ONLY.name()));
                String baseValue = fields.get("baseItem");
                if (baseValue == null || baseValue.isBlank()) {
                    if (policy != ItemMatchPolicy.ITEM_ONLY) {
                        throw new IllegalArgumentException("Variant commodity is missing its base item id");
                    }
                    baseValue = commodityId.toString();
                }
                ResourceLocation baseId = com.nstut.economy.compat.Compat.rl(baseValue);
                Item resolved = BuiltInRegistries.ITEM.get(baseId);
                BigDecimal base = new BigDecimal(fields.getOrDefault("basePrice", "0"));
                ItemVariant decodedVariant = policy == ItemMatchPolicy.ITEM_ONLY
                        ? ItemVariant.itemOnly()
                        : ItemVariant.persisted(policy, fields.get("variantData"), fields.get("variantFingerprint"));
                ItemCommodity decoded = new ItemCommodity(baseId, resolved, base,
                        Boolean.parseBoolean(fields.getOrDefault("dynamic", "true")), decodedVariant);
                if (!decoded.getId().equals(commodityId)) {
                    throw new IllegalArgumentException("Variant commodity id does not match persisted identity: " + commodityId);
                }
                return decoded;
            }
        });
    }

    @Override public boolean equals(Object o) { return this == o || (o instanceof ItemCommodity that && id.equals(that.id)); }
    @Override public int hashCode() { return 31 * ItemCommodity.class.hashCode() + id.hashCode(); }
}
