package com.nstut.economy.ui.framework;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

public abstract class UIComponent {

    protected int x, y, width, height;
    protected UIComponent parent;
    protected final List<UIComponent> children = new ArrayList<>();
    protected boolean hovered;
    protected boolean visible = true;
    protected boolean flex;
    protected float flexGrow;
    private Font measureFont;

    public UIComponent flex() { return flex(1.0F); }
    public UIComponent flex(float grow) {
        this.flex = grow > 0.0F;
        this.flexGrow = Math.max(0.0F, grow);
        return this;
    }
    public boolean isFlex() { return flexGrow > 0.0F || flex; }
    public float getFlexGrow() { return flexGrow > 0.0F ? flexGrow : (flex ? 1.0F : 0.0F); }

    public void setVisible(boolean v) { this.visible = v; }
    public boolean isVisible() { return visible; }

    public void addChild(UIComponent child) {
        children.add(child);
        child.parent = this;
    }

    public int childCount() { return children.size(); }
    public UIComponent child(int i) { return children.get(i); }
    public void replaceChild(int i, UIComponent child) { children.set(i, child); child.parent = this; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public abstract int preferredWidth(Font font);
    public abstract int preferredHeight(Font font);

    /**
     * Measures and lays out an entire component tree with the real screen font.
     * Existing layout implementations stay source-compatible while no longer
     * having to guess text sizes with a null font.
     */
    public final void layoutTree(Font font, int x, int y, int availableWidth, int availableHeight) {
        setMeasureFont(font);
        layout(x, y, availableWidth, availableHeight);
    }

    private void setMeasureFont(Font font) {
        this.measureFont = font;
        for (UIComponent child : children) child.setMeasureFont(font);
    }

    protected final Font measureFont() { return measureFont; }

    public void layout(int x, int y, int availableWidth, int availableHeight) {
        setBounds(x, y, availableWidth, availableHeight);
    }

    protected void setBounds(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.width = w; this.height = h;
    }

    public abstract void render(GuiGraphics g, Font font, int mx, int my, float pt);

    public boolean mouseClicked(double mx, double my, int button) { return false; }
    public boolean mouseScrolled(double mx, double my, double delta) { return false; }
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) { return false; }
    public boolean mouseReleased(double mx, double my, int button) { return false; }

    public UIComponent hitTest(int mx, int my) {
        if (!visible) return null;
        if (mx >= x && mx < x + width && my >= y && my < y + height) {
            for (int i = children.size() - 1; i >= 0; i--) {
                UIComponent hit = children.get(i).hitTest(mx, my);
                if (hit != null) return hit;
            }
            return this;
        }
        return null;
    }

    public void preRender(int mx, int my) {
        if (!visible) return;
        hovered = mx >= x && mx < x + width && my >= y && my < y + height;
        for (UIComponent c : children) c.preRender(mx, my);
    }

    public boolean isHovered() { return hovered; }

    protected final void renderChildren(GuiGraphics g, Font font, int mx, int my, float pt) {
        for (UIComponent child : children) {
            if (child.isVisible()) child.render(g, font, mx, my, pt);
        }
    }

    protected final boolean childrenMouseClicked(double mx, double my, int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            UIComponent child = children.get(i);
            if (child.isVisible() && child.mouseClicked(mx, my, button)) return true;
        }
        return false;
    }

    protected final boolean childrenMouseScrolled(double mx, double my, double delta) {
        for (UIComponent child : children) {
            if (child.isVisible() && child.mouseScrolled(mx, my, delta)) return true;
        }
        return false;
    }

    protected final boolean childrenMouseDragged(double mx, double my, int button, double dragX, double dragY) {
        for (UIComponent child : children) {
            if (child.isVisible() && child.mouseDragged(mx, my, button, dragX, dragY)) return true;
        }
        return false;
    }

    protected final boolean childrenMouseReleased(double mx, double my, int button) {
        for (UIComponent child : children) {
            if (child.isVisible() && child.mouseReleased(mx, my, button)) return true;
        }
        return false;
    }

    public void dispose() {
        for (UIComponent c : children) c.dispose();
        children.clear();
    }
}
