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
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

/**
 * Theme-aware vessel shell with the real Forge fluid sprite/tint drawn inside a
 * clipped inner region. The fill height is derived from amount / capacity.
 * Knows nothing about screen coordinates outside its own bounds.
 */
public class FluidTankComponent extends UIComponent {
    private FluidStack fluid;
    private int capacity;

    public FluidTankComponent(FluidStack fluid, int capacity) {
        this.fluid = fluid;
        this.capacity = capacity;
    }

    public void setFluid(FluidStack fluid) {
        this.fluid = fluid;
        invalidatePaint();
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
        invalidatePaint();
    }

    @Override
    public int preferredWidth(Font f) {
        return 48;
    }

    @Override
    public int preferredHeight(Font f) {
        return 48;
    }

    @Override
    public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
        ColorScheme c = theme().colors();
        UiRender.roundedOutline(g, x, y, width, height, 3, c.input(), c.borderStrong());

        if (fluid != null && !fluid.isEmpty() && fluid.getAmount() > 0) {
            int cap = Math.max(1, capacity);
            float fill = Math.min(1f, fluid.getAmount() / (float) cap);
            int innerX = x + 3;
            int innerY = y + 3;
            int innerW = width - 6;
            int innerH = height - 6;
            int fluidH = Math.max(1, Math.round(innerH * fill));
            int fluidY = innerY + innerH - fluidH;

            Fluid flowFluid = fluid.getFluid();
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                    .apply(IClientFluidTypeExtensions.of(flowFluid).getStillTexture());
            int tint = IClientFluidTypeExtensions.of(flowFluid).getTintColor();
            float r = ((tint >> 16) & 0xFF) / 255f;
            float gr = ((tint >> 8) & 0xFF) / 255f;
            float b = (tint & 0xFF) / 255f;
            float a = ((tint >> 24) & 0xFF) / 255f;
            if (a == 0) a = 1f;

            g.enableScissor(innerX, fluidY, innerX + innerW, fluidY + fluidH);
            RenderSystem.setShaderColor(r, gr, b, a);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
            int right = innerX + innerW;
            for (int tileX = innerX; tileX < right; tileX += 16) {
                for (int tileBottom = fluidY + fluidH; tileBottom > fluidY; tileBottom -= 16) {
                    g.blit(tileX, tileBottom - 16, 0, 16, 16, sprite);
                }
            }
            g.disableScissor();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            g.fill(innerX + 2, fluidY + 1, innerX + innerW - 2, fluidY + 2, 0x55FFFFFF);
        }
    }
}
