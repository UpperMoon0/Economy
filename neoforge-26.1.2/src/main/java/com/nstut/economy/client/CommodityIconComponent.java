package com.nstut.economy.client;
import com.nstut.economy.platform.Services;

import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.UiRender;
import com.nstut.openui.theme.ColorScheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import com.nstut.economy.trading.EconomyFluidStack;

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
    public void render(GuiGraphicsExtractor g, Font f, int mx, int my, float pt) {
        ColorScheme c = theme().colors();
        UiRender.roundedOutline(g, x, y, width, height, 2, c.surface(), c.borderSubtle());
        drawIcon(g, commodityId, x + 1, y + 1, width - 2, height - 2);
    }

    public static void drawIcon(GuiGraphicsExtractor g, String commodityId, int x, int y, int w, int h) {
        if (commodityId == null || commodityId.isEmpty()) return;
        Fluid fluid = BuiltInRegistries.FLUID.getValue(Identifier.parse(commodityId));
        if (fluid != net.minecraft.world.level.material.Fluids.EMPTY && !com.nstut.economy.platform.Services.FLUID.isAir(fluid)) {
            Identifier still = Services.FLUID.stillTexture(fluid);
            TextureAtlasSprite sprite = Minecraft.getInstance()
                    .getAtlasManager()
                    .get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, still));
            int color = Services.FLUID.tint(fluid);
            if ((color >>> 24) == 0) {
                color |= 0xFF000000;
            }
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h, color);
        } else {
            ItemStack icon = ITEM_CACHE.computeIfAbsent(commodityId, id -> {
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
                return new ItemStack(item);
            });
            float scale = Math.min(w, h) / 16.0F;
            if (scale <= 0.0F) return;
            float drawWidth = 16.0F * scale;
            float drawHeight = 16.0F * scale;
            g.pose().pushMatrix();
            g.pose().translate(x + (w - drawWidth) / 2.0F, y + (h - drawHeight) / 2.0F);
            g.pose().scale(scale, scale);
            g.item(icon, 0, 0);
            g.pose().popMatrix();
        }
    }
}


