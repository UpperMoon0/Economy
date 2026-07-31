package com.nstut.economy.ui.framework;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class ButtonWidget extends UIComponent {

    private String label;
    private int normalColor;
    private int hoverColor;
    private final int textColor;
    private boolean active;
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
    public int preferredHeight(Font font) { return label.contains("\n") ? 22 : 14; }

    @Override
    public void render(GuiGraphics g, Font font, int mx, int my, float pt) {
        int fillColor = (active || isHovered()) ? hoverColor : normalColor;
        g.fill(x, y, x + width, y + height, fillColor);
        if (active) {
            // Draw subtle accent top/bottom highlight border for active state
            g.fill(x, y, x + width, y + 1, 0xFF00D4AA);
            g.fill(x, y + height - 1, x + width, y + height, 0xFF00D4AA);
        }
        String[] lines = label.split("\n", -1);
        if (lines.length >= 2) {
            int totalTextHeight = font.lineHeight * 2;
            int firstY = y + (height - totalTextHeight) / 2;
            int firstWidth = font.width(lines[0]);
            int secondWidth = font.width(lines[1]);
            g.drawString(font, lines[0], x + (width - firstWidth) / 2, firstY, 0xFF9E9EAE);
            g.drawString(font, lines[1], x + (width - secondWidth) / 2,
                    firstY + font.lineHeight, textColor);
        } else {
            int tw = font.width(label);
            int ty = y + (height - font.lineHeight) / 2;
            g.drawString(font, label, x + (width - tw) / 2, ty, textColor);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        boolean inBounds = mx >= x && mx < x + width && my >= y && my < y + height;
        com.nstut.Economy.LOGGER.info("[ButtonWidget '{}'] mouseClicked mx={}, my={}, btn={}, bounds=[x={}, y={}, w={}, h={}], visible={}, inBounds={}",
            label, mx, my, btn, x, y, width, height, isVisible(), inBounds);
        if (btn == 0 && inBounds && onClick != null) {
            com.nstut.Economy.LOGGER.info("[ButtonWidget '{}'] Executing onClick handler!", label);
            onClick.run();
            return true;
        }
        return false;
    }
}
