package com.nstut.economy.ui.framework;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public class HStack extends UIComponent {

    private int gap = 0;

    public HStack gap(int gap) { this.gap = gap; return this; }
    public HStack child(UIComponent c) { addChild(c); return this; }

    private List<UIComponent> visibleChildren() {
        List<UIComponent> vc = new ArrayList<>();
        for (UIComponent c : children) if (c.isVisible()) vc.add(c);
        return vc;
    }

    @Override
    public int preferredWidth(Font font) {
        int total = 0;
        List<UIComponent> vc = visibleChildren();
        for (UIComponent c : vc) total += c.preferredWidth(font);
        if (!vc.isEmpty()) total += gap * (vc.size() - 1);
        return total;
    }

    @Override
    public int preferredHeight(Font font) {
        int max = 0;
        for (UIComponent c : visibleChildren()) max = Math.max(max, c.preferredHeight(font));
        return max;
    }

    @Override
    public void layout(int x, int y, int availableWidth, int availableHeight) {
        setBounds(x, y, availableWidth, availableHeight);
        List<UIComponent> vc = visibleChildren();
        int flexCount = 0, fixedTotal = 0;
        for (UIComponent c : vc) {
            if (c instanceof Spacer || c.isFlex()) flexCount++;
            else fixedTotal += c.preferredWidth(null);
        }
        int flexSize = flexCount > 0 ? Math.max(0, (availableWidth - fixedTotal - gap * (Math.max(0, vc.size() - 1)))) / flexCount : 0;
        int flexRemainder = flexCount > 0 ? (availableWidth - fixedTotal - gap * (Math.max(0, vc.size() - 1))) % flexCount : 0;

        int cx = x;
        int flexIdx = 0;
        for (UIComponent c : vc) {
            int cw = (c instanceof Spacer || c.isFlex()) ? flexSize + (flexIdx++ < flexRemainder ? 1 : 0) : c.preferredWidth(null);
            c.layout(cx, y, cw, availableHeight);
            cx += cw + gap;
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
}
