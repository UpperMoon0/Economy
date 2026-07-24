package com.nstut.economy.ui.framework;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class SizedBox extends UIComponent {

    private final int fixedW, fixedH;

    public SizedBox(int w, int h) { this.fixedW = w; this.fixedH = h; }

    @Override public int preferredWidth(Font font) { return fixedW; }
    @Override public int preferredHeight(Font font) { return fixedH; }

    @Override
    public void layout(int x, int y, int availableWidth, int availableHeight) {
        setBounds(x, y, fixedW, fixedH);
        for (UIComponent c : children) c.layout(x, y, fixedW, fixedH);
    }

    @Override
    public void render(GuiGraphics g, Font font, int mx, int my, float pt) {
        for (UIComponent c : children) c.render(g, font, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            UIComponent c = children.get(i);
            if (c.isVisible() && c.mouseClicked(mx, my, button)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        for (UIComponent c : children) if (c.isVisible() && c.mouseScrolled(mx, my, delta)) return true;
        return false;
    }
}
