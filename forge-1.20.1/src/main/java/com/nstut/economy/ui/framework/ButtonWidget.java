package com.nstut.economy.ui.framework;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class ButtonWidget extends UIComponent {

    private String label;
    private int normalColor;
    private int hoverColor;
    private int textColor;
    private boolean active;
    private boolean enabled = true;
    private boolean alignLeft;
    private boolean activeIndicator;
    private int radius = UiTheme.RADIUS_SM;
    private int requestedHeight;
    private float hoverProgress;
    private Runnable onClick;

    public ButtonWidget(String label, int normalColor, int hoverColor, int textColor) {
        this.label = label;
        this.normalColor = normalColor;
        this.hoverColor = hoverColor;
        this.textColor = textColor;
    }

    public ButtonWidget onPress(Runnable r) { this.onClick = r; return this; }
    public void setActive(boolean a) { this.active = a; }
    public void setLabel(String label) { this.label = label; }
    public ButtonWidget alignLeft() { this.alignLeft = true; return this; }
    public ButtonWidget activeIndicator() { this.activeIndicator = true; return this; }
    public ButtonWidget radius(int radius) { this.radius = Math.max(0, radius); return this; }
    public ButtonWidget height(int height) { this.requestedHeight = Math.max(0, height); return this; }
    public ButtonWidget textColor(int color) { this.textColor = color; return this; }
    public ButtonWidget enabled(boolean enabled) { this.enabled = enabled; return this; }
    public boolean isEnabled() { return enabled; }
    public void setColors(int normalColor, int hoverColor) {
        this.normalColor = normalColor;
        this.hoverColor = hoverColor;
    }

    @Override
    public int preferredWidth(Font font) {
        if (font == null) return 40;
        String[] lines = label.split("\n", -1);
        int maxWidth = 0;
        for (String line : lines) maxWidth = Math.max(maxWidth, font.width(line));
        return maxWidth + 8;
    }
    @Override
    public int preferredHeight(Font font) {
        return requestedHeight > 0 ? requestedHeight : (label.contains("\n") ? 22 : 14);
    }

    @Override
    public void render(GuiGraphics g, Font font, int mx, int my, float pt) {
        if (!visible) return;
        float target = enabled && isHovered() ? 1.0F : 0.0F;
        hoverProgress += (target - hoverProgress) * 0.32F;
        if (Math.abs(target - hoverProgress) < 0.01F) hoverProgress = target;
        int fillColor = active ? hoverColor : UiRender.mix(normalColor, hoverColor, hoverProgress);
        if (!enabled) fillColor = UiRender.mix(fillColor, UiTheme.SHELL, 0.58F);
        int outline = active ? UiTheme.ACCENT_DIM
                : UiRender.mix(UiTheme.BORDER_SUBTLE, UiTheme.BORDER_STRONG, hoverProgress);
        UiRender.roundedOutline(g, x, y, width, height, radius, fillColor, outline);
        if (active && activeIndicator) {
            UiRender.roundedRect(g, x + 2, y + 3, 2, Math.max(2, height - 6), 1, UiTheme.ACCENT);
        }
        String[] lines = label.split("\n", -1);
        if (lines.length >= 2) {
            int totalTextHeight = font.lineHeight * 2;
            int firstY = y + (height - totalTextHeight) / 2;
            int firstWidth = font.width(lines[0]);
            int secondWidth = font.width(lines[1]);
            int firstX = alignLeft ? x + 8 : x + (width - firstWidth) / 2;
            int secondX = alignLeft ? x + 8 : x + (width - secondWidth) / 2;
            g.drawString(font, lines[0], firstX, firstY, UiTheme.TEXT_MUTED);
            g.drawString(font, lines[1], secondX,
                    firstY + font.lineHeight, enabled ? textColor : UiTheme.TEXT_DISABLED);
        } else {
            int tw = font.width(label);
            int ty = y + (height - font.lineHeight) / 2;
            int tx = alignLeft ? x + (activeIndicator ? 9 : 7) : x + (width - tw) / 2;
            g.drawString(font, label, tx, ty, enabled ? textColor : UiTheme.TEXT_DISABLED);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        boolean inBounds = visible && mx >= x && mx < x + width && my >= y && my < y + height;
        if (enabled && btn == 0 && inBounds && onClick != null) {
            onClick.run();
            return true;
        }
        return false;
    }
}
