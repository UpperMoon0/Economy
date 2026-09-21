package com.nstut.economy.client;
import com.nstut.economy.platform.Services;

import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.UiRender;
import com.nstut.openui.theme.ColorScheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders an item or fluid commodity icon using the domain texture/tint, framed
 * by a theme-aware border. Fluid tint comes from Forge's fluid extensions and is
 * intentionally not recoloured by the UI theme.
 */
public class CommodityIconComponent extends UIComponent {
    private String commodityId;
    private static final Map<String, ItemStack> ITEM_CACHE = new HashMap<>();
    private static final Map<String, String> VARIANT_DATA = new HashMap<>();

    public static void replaceVariantData(Map<String, String> variants) {
        applyVariantData(variants, true);
    }

    public static void applyVariantData(Map<String, String> variants, boolean reset) {
        if (reset) VARIANT_DATA.clear();
        if (variants != null) VARIANT_DATA.putAll(variants);
        ITEM_CACHE.clear();
    }

    public CommodityIconComponent(String commodityId) {
        this.commodityId = commodityId;
    }

    public void setCommodityId(String id) {
        if (!java.util.Objects.equals(this.commodityId, id)) {
            this.commodityId = id;
            invalidatePaint();
        }
    }

    @Override
    public int preferredWidth(Font f) {
        return 16;
    }

    @Override
    public int preferredHeight(Font f) {
        return 16;
    }

    @Override
    public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
        ColorScheme c = theme().colors();
        UiRender.roundedOutline(g, x, y, width, height, 2, c.surface(), c.borderSubtle());
        drawIcon(g, commodityId, x + 1, y + 1, width - 2, height - 2);
    }

    public static ItemStack stackForCommodity(String commodityId) {
        if (commodityId == null || commodityId.isBlank()) return ItemStack.EMPTY;
        return ITEM_CACHE.computeIfAbsent(commodityId, id -> {
                String canonical = VARIANT_DATA.get(id);
                if (canonical != null && !canonical.isBlank() && Minecraft.getInstance().level != null) {
                    try {
                        ItemStack exact = com.nstut.economy.compat.Compat.deserializeCanonicalItemStack(
                                Minecraft.getInstance().level.registryAccess(), canonical);
                        if (exact != null && !exact.isEmpty()) return exact;
                    } catch (RuntimeException ignored) {
                        // Fall through to the registered base item. Server remains authoritative.
                    }
                }
                String baseId = com.nstut.economy.trading.ItemVariant.baseItemId(
                        com.nstut.economy.api.EconomyId.parse(id)).toString();
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(baseId));
                     ItemStack icon = stackForCommodity(commodityId);
            if (icon.isEmpty()) return;
            float scale = Math.min(w, h) / 16.0F;y, 0, w, h, sprite);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        } else {
            ItemStack icon = ITEM_CACHE.computeIfAbsent(commodityId, id -> {
                String canonical = VARIANT_DATA.get(id);
                if (canonical != null && !canonical.isBlank() && Minecraft.getInstance().level != null) {
                    try {
                        ItemStack exact = com.nstut.economy.compat.Compat.deserializeCanonicalItemStack(
                                Minecraft.getInstance().level.registryAccess(), canonical);
                        if (exact != null && !exact.isEmpty()) return exact;
                    } catch (RuntimeException ignored) {
                        // Fall through to the registered base item. Server remains authoritative.
                    }
                }
                String baseId = com.nstut.economy.trading.ItemVariant.baseItemId(
                        com.nstut.economy.api.EconomyId.parse(id)).toString();
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(baseId));
                return new ItemStack(item);
            });
            float scale = Math.min(w, h) / 16.0F;
            if (scale <= 0.0F) return;
            float drawWidth = 16.0F * scale;
            float drawHeight = 16.0F * scale;
            g.pose().pushPose();
            g.pose().translate(x + (w - drawWidth) / 2.0F, y + (h - drawHeight) / 2.0F, 0.0F);
            g.pose().scale(scale, scale, 1.0F);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
        }
    }
}
