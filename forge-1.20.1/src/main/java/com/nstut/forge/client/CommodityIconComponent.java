package com.nstut.forge.client;

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
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

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
    private static final Map<String, TextureAtlasSprite> FLUID_CACHE = new HashMap<>();

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

    public static void drawIcon(GuiGraphics g, String commodityId, int x, int y, int w, int h) {
        if (commodityId == null || commodityId.isEmpty()) return;
        Fluid fluid = BuiltInRegistries.FLUID.get(new ResourceLocation(commodityId));
        if (fluid != net.minecraft.world.level.material.Fluids.EMPTY && !fluid.getFluidType().isAir()) {
            TextureAtlasSprite sprite = FLUID_CACHE.computeIfAbsent(commodityId, id -> {
                ResourceLocation still = IClientFluidTypeExtensions.of(fluid).getStillTexture();
                return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(still);
            });
            int tint = IClientFluidTypeExtensions.of(fluid).getTintColor();
            float r = ((tint >> 16) & 0xFF) / 255f;
            float gr = ((tint >> 8) & 0xFF) / 255f;
            float b = (tint & 0xFF) / 255f;
            float a = ((tint >> 24) & 0xFF) / 255f;
            if (a == 0) a = 1f;
            RenderSystem.setShaderColor(r, gr, b, a);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
            g.blit(x, y, 0, w, h, sprite);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        } else {
            ItemStack icon = ITEM_CACHE.computeIfAbsent(commodityId, id -> {
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
                return new ItemStack(item);
            });
            g.renderItem(icon, x, y);
        }
    }
}
