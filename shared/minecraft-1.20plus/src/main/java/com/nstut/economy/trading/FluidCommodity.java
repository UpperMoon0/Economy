package com.nstut.economy.trading;

import com.nstut.economy.api.CommodityPayload;
import com.nstut.economy.api.EconomyApi;
import com.nstut.economy.api.EconomyId;
import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.ICommodityTypeHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;

import java.math.BigDecimal;
import java.util.Map;

public class FluidCommodity implements ICommodity {
    public static final int QUOTE_AMOUNT_MB = 1_000;
    private static final BigDecimal QUOTE_AMOUNT = BigDecimal.valueOf(QUOTE_AMOUNT_MB);
    private final ResourceLocation platformId;
    private final EconomyId id;
    private final Fluid fluid;
    private final BigDecimal basePrice;
    private final boolean dynamicPricing;

    public FluidCommodity(ResourceLocation id, Fluid fluid, BigDecimal basePrice, boolean dynamicPricing) {
        this.platformId = id; this.id = EconomyId.parse(id.toString()); this.fluid = fluid; this.basePrice = basePrice; this.dynamicPricing = dynamicPricing;
    }
    public FluidCommodity(ResourceLocation id, Fluid fluid, BigDecimal basePrice) { this(id, fluid, basePrice, true); }

    @Override public EconomyId getId() { return id; }
    @Override public CommodityType getType() { return CommodityType.FLUID; }
    @Override public Component getDisplayName() { return com.nstut.economy.platform.Services.FLUID.displayName(fluid); }
    @Override public BigDecimal getBasePrice() { return basePrice; }
    @Override public boolean hasDynamicPricing() { return dynamicPricing; }
    @Override public boolean canExtractFrom(IStorage storage, int amount) { return false; }
    @Override public boolean canInsertInto(IStorage storage, int amount) { return false; }
    @Override public boolean extractFrom(IStorage storage, int amount) { return false; }
    @Override public boolean insertInto(IStorage storage, int amount) { return false; }

    public ResourceLocation getPlatformId() { return platformId; }
    public Fluid getFluid() { return fluid; }
    public EconomyFluidStack createFluidStack(int amount) { return new EconomyFluidStack(fluid, amount); }
    public static BigDecimal pricePerMb(BigDecimal pricePerBucket) { return pricePerBucket.divide(QUOTE_AMOUNT); }
    public static BigDecimal pricePerBucket(BigDecimal pricePerMb) { return pricePerMb.multiply(QUOTE_AMOUNT).stripTrailingZeros(); }
    public static BigDecimal totalFromBucketQuote(BigDecimal pricePerBucket, int amountMb) { return pricePerMb(pricePerBucket).multiply(BigDecimal.valueOf(amountMb)); }

    public static FluidCommodity fromFluid(Fluid fluid, BigDecimal basePrice) {
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid); if (id == null) id = com.nstut.economy.compat.Compat.rl("minecraft", "water");
        return new FluidCommodity(id, fluid, basePrice);
    }
    public static FluidCommodity fromFluidId(String fluidId, BigDecimal basePrice) {
        ResourceLocation rl = com.nstut.economy.compat.Compat.rl(fluidId); Fluid fluid = BuiltInRegistries.FLUID.get(rl);
        return fluid == net.minecraft.world.level.material.Fluids.EMPTY ? null : new FluidCommodity(rl, fluid, basePrice);
    }

    public static void registerApiType() {
        EconomyApi.commodityTypes().register(new ICommodityTypeHandler() {
            @Override public EconomyId id() { return ICommodity.FLUID_TYPE; }
            @Override public int currentSchemaVersion() { return 1; }
            @Override public boolean supports(ICommodity commodity) { return commodity instanceof FluidCommodity; }
            @Override public CommodityPayload encode(ICommodity commodity) {
                FluidCommodity fluid = (FluidCommodity) commodity;
                return new CommodityPayload(1, Map.of("basePrice", fluid.basePrice.toPlainString(),
                        "dynamic", Boolean.toString(fluid.dynamicPricing)));
            }
            @Override public ICommodity decode(EconomyId commodityId, CommodityPayload payload) {
                ResourceLocation rl = com.nstut.economy.compat.Compat.rl(commodityId.toString());
                Fluid resolved = BuiltInRegistries.FLUID.get(rl);
                return new FluidCommodity(rl, resolved, new BigDecimal(payload.values().getOrDefault("basePrice", "0")),
                        Boolean.parseBoolean(payload.values().getOrDefault("dynamic", "true")));
            }
            @Override public boolean fluidLike() { return true; }
        });
    }

    @Override public boolean equals(Object o) { return this == o || (o instanceof FluidCommodity that && id.equals(that.id)); }
    @Override public int hashCode() { return 31 * FluidCommodity.class.hashCode() + id.hashCode(); }
}
