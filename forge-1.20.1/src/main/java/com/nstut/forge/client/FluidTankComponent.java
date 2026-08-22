package com.nstut.forge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nstut.openui.api.ClipStack;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.UiRender;
import com.nstut.openui.theme.ColorScheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import java.util.Objects;

/** Theme-aware fixed-size vessel; screen text/layout deliberately lives outside this component. */
public class FluidTankComponent extends UIComponent {
    private FluidStack fluid;
    private int capacity;

    public FluidTankComponent(FluidStack fluid, int capacity) {
        this.fluid = fluid;
        this.capacity = capacity;
        width(48);
        height(48);
    }

    public void setFluid(FluidStack fluid) {
        if (!Objects.equals(this.fluid, fluid)) {
            this.fluid = fluid;
            invalidatePaint();
        }
    }

    public void setCapacity(int capacity) {
        if (this.capacity != capacity) {
            this.capacity = capacity;
            invalidatePaint();
        }
    }

    @Override public int preferredWidth(Font f) { return 48; }
    @Override public int preferredHeight(Font f) { return 48; }

    @Override
    public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
        ColorScheme c = theme().colors();
        UiRender.roundedOutline(g, x, y, width, height, 3, c.input(), c.borderStrong());
        if (fluid == null || fluid.isEmpty() || fluid.getAmount() <= 0) return;

        int cap = Math.max(1, capacity);
        float fill = Math.min(1f, fluid.getAmount() / (float) cap);
        int innerX = x + 3, innerY = y + 3;
        int innerW = Math.max(1, width - 6), innerH = Math.max(1, height - 6);
        int fluidH = Math.max(1, Math.round(innerH * fill));
        int fluidY = innerY + innerH - fluidH;

        Fluid forgeFluid = fluid.getFluid();
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(forgeFluid);
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ext.getStillTexture());
        int tint = ext.getTintColor();
        float r = ((tint >> 16) & 0xFF) / 255f;
        float gr = ((tint >> 8) & 0xFF) / 255f;
        float b = (tint & 0xFF) / 255f;
        float a = ((tint >> 24) & 0xFF) / 255f;
        if (a == 0) a = 1f;

        ClipStack.push(g, innerX, fluidY, innerW, fluidH);
        try {
            RenderSystem.setShaderColor(r, gr, b, a);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
            int right = innerX + innerW;
            for (int tileX = innerX; tileX < right; tileX += 16) {
                for (int tileBottom = fluidY + fluidH; tileBottom > fluidY; tileBottom -= 16) {
                    g.blit(tileX, tileBottom - 16, 0, 16, 16, sprite);
                }
            }
            g.fill(innerX + 2, fluidY + 1, innerX + innerW - 2, fluidY + 2, 0x55FFFFFF);
        } finally {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            ClipStack.pop(g);
        }
    }
}
