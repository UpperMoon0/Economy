package com.nstut.economy.ui.framework;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** A reusable inset wrapper, equivalent to padding in a declarative UI tree. */
public class Padding extends UIComponent {
    private final int top;
    private final int right;
    private final int bottom;
    private final int left;

    public Padding(int all, UIComponent child) {
        this(all, all, all, all, child);
    }

    public Padding(int vertical, int horizontal, UIComponent child) {
        this(vertical, horizontal, vertical, horizontal, child);
    }

    public Padding(int top, int right, int bottom, int left, UIComponent child) {
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.left = left;
        if (child != null) addChild(child);
    }

    @Override
    public int preferredWidth(Font font) {
        int max = 0;
        for (UIComponent child : children) max = Math.max(max, child.preferredWidth(font));
        return max + left + right;
    }

    @Override
    public int preferredHeight(Font font) {
        int max = 0;
        for (UIComponent child : children) max = Math.max(max, child.preferredHeight(font));
        return max + top + bottom;
    }

    @Override
    public void layout(int x, int y, int availableWidth, int availableHeight) {
        setBounds(x, y, availableWidth, availableHeight);
        int childWidth = Math.max(0, availableWidth - left - right);
        int childHeight = Math.max(0, availableHeight - top - bottom);
        for (UIComponent child : children) child.layout(x + left, y + top, childWidth, childHeight);
    }

    @Override
    public void render(GuiGraphics g, Font font, int mx, int my, float pt) {
        renderChildren(g, font, mx, my, pt);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) { return childrenMouseClicked(mx, my, button); }
    @Override public boolean mouseScrolled(double mx, double my, double delta) { return childrenMouseScrolled(mx, my, delta); }
    @Override public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) { return childrenMouseDragged(mx, my, button, dragX, dragY); }
    @Override public boolean mouseReleased(double mx, double my, int button) { return childrenMouseReleased(mx, my, button); }
}
