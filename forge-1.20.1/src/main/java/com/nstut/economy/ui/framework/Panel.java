package com.nstut.economy.ui.framework;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class Panel extends UIComponent {

    private final int bgColor;
    private final int borderColor;

    public Panel(int bgColor, int borderColor) {
        this.bgColor = bgColor;
        this.borderColor = borderColor;
    }

    public Panel(int bgColor) { this(bgColor, 0); }

    @Override
    public int preferredWidth(Font font) {
        int max = 0;
        for (UIComponent c : children) max = Math.max(max, c.preferredWidth(font));
        return max + 4;
    }

    @Override
    public int preferredHeight(Font font) {
        int max = 0;
        for (UIComponent c : children) max = Math.max(max, c.preferredHeight(font));
        return max + 4;
    }

    @Override
    public void layout(int x, int y, int availableWidth, int availableHeight) {
        setBounds(x, y, availableWidth, availableHeight);
        for (UIComponent c : children) {
            c.layout(x + 2, y + 2, availableWidth - 4, availableHeight - 4);
        }
    }

    @Override
    public void render(GuiGraphics g, Font font, int mx, int my, float pt) {
        g.fill(x, y, x + width, y + height, bgColor);
        if (borderColor != 0) {
            g.fill(x, y, x + width, y + 1, borderColor);
            g.fill(x, y + height - 1, x + width, y + height, borderColor);
            g.fill(x, y, x + 1, y + height, borderColor);
            g.fill(x + width - 1, y, x + width, y + height, borderColor);
        }
        for (UIComponent c : children) c.render(g, font, mx, my, pt);
    }
}
