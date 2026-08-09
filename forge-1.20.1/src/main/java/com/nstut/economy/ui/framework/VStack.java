package com.nstut.economy.ui.framework;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public class VStack extends UIComponent {

    private int gap = 0;

    public VStack gap(int gap) { this.gap = gap; return this; }
    public VStack child(UIComponent c) { addChild(c); return this; }

    private List<UIComponent> visibleChildren() {
        List<UIComponent> vc = new ArrayList<>();
        for (UIComponent c : children) if (c.isVisible()) vc.add(c);
        return vc;
    }

    @Override
    public int preferredWidth(Font font) {
        int max = 0;
        for (UIComponent c : visibleChildren()) max = Math.max(max, c.preferredWidth(font));
        return max;
    }

    @Override
    public int preferredHeight(Font font) {
        int total = 0;
        List<UIComponent> vc = visibleChildren();
        for (UIComponent c : vc) total += c.preferredHeight(font);
        if (!vc.isEmpty()) total += gap * (vc.size() - 1);
        return total;
    }

    @Override
    public void layout(int x, int y, int availableWidth, int availableHeight) {
        setBounds(x, y, availableWidth, availableHeight);
        List<UIComponent> vc = visibleChildren();
        float flexTotal = 0.0F;
        int fixedTotal = 0;
        for (UIComponent c : vc) {
            if (c instanceof Spacer || c.isFlex()) flexTotal += c.getFlexGrow() > 0.0F ? c.getFlexGrow() : 1.0F;
            else fixedTotal += c.preferredHeight(measureFont());
        }
        int flexSpace = Math.max(0, availableHeight - fixedTotal - gap * Math.max(0, vc.size() - 1));

        int cy = y;
        int distributed = 0;
        float usedWeight = 0.0F;
        for (UIComponent c : vc) {
            boolean flexible = c instanceof Spacer || c.isFlex();
            int ch;
            if (flexible && flexTotal > 0.0F) {
                usedWeight += c.getFlexGrow() > 0.0F ? c.getFlexGrow() : 1.0F;
                int target = Math.round(flexSpace * usedWeight / flexTotal);
                ch = target - distributed;
                distributed = target;
            } else {
                ch = c.preferredHeight(measureFont());
            }
            c.layout(x, cy, availableWidth, ch);
            cy += ch + gap;
        }
    }

    @Override
    public void render(GuiGraphics g, Font font, int mx, int my, float pt) {
        for (UIComponent c : children) if (c.isVisible()) c.render(g, font, mx, my, pt);
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

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        for (UIComponent c : children) if (c.isVisible() && c.mouseDragged(mx, my, button, dragX, dragY)) return true;
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        for (UIComponent c : children) if (c.isVisible() && c.mouseReleased(mx, my, button)) return true;
        return false;
    }
}
