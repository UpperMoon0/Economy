package com.nstut.economy.client;
import com.nstut.economy.platform.Services;

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
import com.nstut.economy.trading.EconomyFluidStack;

import java.util.Objects;

/** Theme-aware fixed-size vessel; screen text/layout deliberately lives outside this component. */
public class FluidTankComponent extends UIComponent {
    private EconomyFluidStack fluid;
    private int capacity;

    public FluidTankComponent(EconomyFluidStack fluid, int capacity) {
        this.fluid = fluid == null ? EconomyFluidStack.EMPTY : fluid.copy();
        this.capacity = capacity;
        width(48);
        height(48);
    }

    public void setFluid(EconomyFluidStack fluid) {
        EconomyFluidStack next = fluid == null ? EconomyFluidStack.EMPTY : fluid;
        if (!this.fluid.isFluidEqual(next)
                || this.fluid.getAmount() != next.getAmount()) {
            this.fluid = next.copy();
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

        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(com.nstut.economy.platform.Services.FLUID.stillTexture(fluid.getFluid()));
        int tint = com.nstut.economy.platform.Services.FLUID.tint(fluid.getFluid());
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


