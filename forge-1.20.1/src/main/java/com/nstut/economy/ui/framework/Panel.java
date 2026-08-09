package com.nstut.economy.ui.framework;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class Panel extends UIComponent {

    private int bgColor;
    private int borderColor;
    private int radius = UiTheme.RADIUS_MD;
    private int padding = UiTheme.SPACE_2;
    private boolean elevated;

    public Panel(int bgColor, int borderColor) {
        this.bgColor = bgColor;
        this.borderColor = borderColor;
    }

    public Panel(int bgColor) { this(bgColor, 0); }

    public Panel radius(int radius) { this.radius = Math.max(0, radius); return this; }
    public Panel padding(int padding) { this.padding = Math.max(0, padding); return this; }
    public Panel elevated() { this.elevated = true; return this; }
    public Panel child(UIComponent child) { addChild(child); return this; }
    public Panel colors(int background, int border) { this.bgColor = background; this.borderColor = border; return this; }

    @Override
    public int preferredWidth(Font font) {
        int max = 0;
        for (UIComponent c : children) max = Math.max(max, c.preferredWidth(font));
        return max + padding * 2;
    }

    @Override
    public int preferredHeight(Font font) {
        int max = 0;
        for (UIComponent c : children) max = Math.max(max, c.preferredHeight(font));
        return max + padding * 2;
    }

    @Override
    public void layout(int x, int y, int availableWidth, int availableHeight) {
        setBounds(x, y, availableWidth, availableHeight);
        for (UIComponent c : children) {
            c.layout(x + padding, y + padding,
                    Math.max(0, availableWidth - padding * 2), Math.max(0, availableHeight - padding * 2));
        }
    }

    @Override
    public void render(GuiGraphics g, Font font, int mx, int my, float pt) {
        if (!visible) return;
        UiRender.surface(g, x, y, width, height, radius, bgColor, borderColor, elevated);
        renderChildren(g, font, mx, my, pt);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) { return childrenMouseClicked(mx, my, button); }
    @Override public boolean mouseScrolled(double mx, double my, double delta) { return childrenMouseScrolled(mx, my, delta); }
    @Override public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) { return childrenMouseDragged(mx, my, button, dragX, dragY); }
    @Override public boolean mouseReleased(double mx, double my, int button) { return childrenMouseReleased(mx, my, button); }
}
