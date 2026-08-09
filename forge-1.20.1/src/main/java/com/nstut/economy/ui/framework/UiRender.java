package com.nstut.economy.ui.framework;

import net.minecraft.client.gui.GuiGraphics;

/** Lightweight, texture-free drawing primitives shared by every Economy UI. */
public final class UiRender {
    private UiRender() {}

    public static void roundedRect(GuiGraphics g, int x, int y, int width, int height,
                                   int radius, int color) {
        if (width <= 0 || height <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r <= 1) {
            g.fill(x, y, x + width, y + height, color);
            return;
        }

        g.fill(x + r, y, x + width - r, y + height, color);
        g.fill(x, y + r, x + width, y + height - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5D;
            int inset = Math.max(0, (int) Math.ceil(r - Math.sqrt(r * r - dy * dy)));
            g.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            g.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, color);
        }
    }

    public static void roundedOutline(GuiGraphics g, int x, int y, int width, int height,
                                      int radius, int fillColor, int borderColor) {
        if (width <= 0 || height <= 0) return;
        if (borderColor == 0 || width < 3 || height < 3) {
            roundedRect(g, x, y, width, height, radius, fillColor);
            return;
        }
        roundedRect(g, x, y, width, height, radius, borderColor);
        roundedRect(g, x + 1, y + 1, width - 2, height - 2,
                Math.max(0, radius - 1), fillColor);
    }

    public static void shadow(GuiGraphics g, int x, int y, int width, int height, int radius) {
        roundedRect(g, x - 2, y + 2, width + 4, height + 3, radius + 2, UiTheme.SHADOW_SOFT);
        roundedRect(g, x - 1, y + 1, width + 2, height + 2, radius + 1, UiTheme.SHADOW);
    }

    public static void surface(GuiGraphics g, int x, int y, int width, int height,
                               int radius, int fillColor, int borderColor, boolean elevated) {
        if (elevated) shadow(g, x, y, width, height, radius);
        roundedOutline(g, x, y, width, height, radius, fillColor, borderColor);
        if (width > radius * 2 + 2 && height > 3) {
            g.fill(x + radius, y + 1, x + width - radius, y + 2, UiTheme.HIGHLIGHT);
        }
    }

    public static void pill(GuiGraphics g, int x, int y, int width, int height,
                            int fillColor, int borderColor) {
        roundedOutline(g, x, y, width, height, Math.max(1, height / 2), fillColor, borderColor);
    }

    public static void slot(GuiGraphics g, int x, int y, int width, int height) {
        roundedOutline(g, x, y, width, height, UiTheme.RADIUS_SM, UiTheme.SLOT, UiTheme.BORDER);
        if (width > 4) g.fill(x + 3, y + 1, x + width - 3, y + 2, 0x12FFFFFF);
    }

    public static void progressTrack(GuiGraphics g, int x, int y, int width, int height,
                                     float progress, int fillColor) {
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        roundedRect(g, x, y, width, height, Math.max(1, height / 2), UiTheme.INPUT);
        int fillWidth = Math.round(width * clamped);
        if (fillWidth > 0) {
            roundedRect(g, x, y, fillWidth, height, Math.max(1, height / 2), fillColor);
        }
    }

    public static int mix(int from, int to, float amount) {
        float t = Math.max(0.0F, Math.min(1.0F, amount));
        int a = channel(from, 24) + Math.round((channel(to, 24) - channel(from, 24)) * t);
        int r = channel(from, 16) + Math.round((channel(to, 16) - channel(from, 16)) * t);
        int green = channel(from, 8) + Math.round((channel(to, 8) - channel(from, 8)) * t);
        int b = channel(from, 0) + Math.round((channel(to, 0) - channel(from, 0)) * t);
        return a << 24 | r << 16 | green << 8 | b;
    }

    public static int alpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static int channel(int color, int shift) {
        return color >> shift & 0xFF;
    }
}
