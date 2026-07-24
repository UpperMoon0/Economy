package com.nstut.economy.ui.framework;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class TextWidget extends UIComponent {

    private String text;
    private int color;
    private boolean centered;

    public TextWidget(String text, int color, boolean centered) {
        this.text = text;
        this.color = color;
        this.centered = centered;
    }

    public void setText(String text) { this.text = text; }

    public static TextWidget label(String text, int color) { return new TextWidget(text, color, false); }
    public static TextWidget centered(String text, int color) { return new TextWidget(text, color, true); }

    @Override
    public int preferredWidth(Font font) { return font != null ? font.width(text) : 0; }

    @Override
    public int preferredHeight(Font font) { return font != null ? font.lineHeight : 10; }

    @Override
    public void render(GuiGraphics g, Font font, int mx, int my, float pt) {
        if (centered) {
            int tw = font.width(text);
            g.drawString(font, text, x + (width - tw) / 2, y, color);
        } else {
            g.drawString(font, text, x, y, color);
        }
    }
}
