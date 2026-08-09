package com.nstut.economy.ui.framework;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class EditBoxWrapper extends UIComponent {

    private final EditBox editBox;
    private final int bgColor;
    private String placeholder = "";
    private int radius = UiTheme.RADIUS_SM;

    public EditBoxWrapper(int maxLength, int textColor, int bgColor, Font font) {
        this.editBox = new EditBox(font, 0, 0, 100, 16, Component.empty());
        this.editBox.setMaxLength(maxLength);
        this.editBox.setBordered(false);
        this.editBox.setTextColor(textColor);
        this.editBox.setTextColorUneditable(UiTheme.TEXT_DISABLED);
        this.bgColor = bgColor;
    }

    public EditBoxWrapper setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (this.editBox != null) {
            this.editBox.visible = visible;
            if (!visible) {
                this.editBox.setFocused(false);
            }
        }
    }

    public EditBox getEditBox() { return editBox; }
    public EditBoxWrapper radius(int radius) { this.radius = Math.max(0, radius); return this; }
    public String getValue() { return editBox.getValue(); }
    public void setValue(String v) {
        if (this.editBox != null) {
            String newVal = v != null ? v : "";
            this.editBox.setValue(newVal);
            this.editBox.setCursorPosition(newVal.length());
            this.editBox.setHighlightPos(newVal.length());
        }
    }
    public boolean isFocused() { return editBox.isFocused(); }

    public boolean keyPressed(int key, int scan, int mod) {
        return editBox.keyPressed(key, scan, mod);
    }

    @Override
    public int preferredWidth(Font font) { return 80; }
    @Override
    public int preferredHeight(Font font) { return 18; }

    @Override
    public void render(GuiGraphics g, Font font, int mx, int my, float pt) {
        if (!visible) return;
        int border = editBox.isFocused() ? UiTheme.ACCENT : (hovered ? UiTheme.BORDER_STRONG : UiTheme.BORDER);
        if (editBox.isFocused()) {
            UiRender.roundedRect(g, x - 1, y - 1, width + 2, height + 2, radius + 1,
                    UiRender.alpha(UiTheme.ACCENT, 80));
        }
        UiRender.roundedOutline(g, x, y, width, height, radius, bgColor, border);

        // keep editbox inner bounds aligned with padding
        editBox.setX(x + 4);
        editBox.setY(y + (height - font.lineHeight) / 2);
        editBox.setWidth(Math.max(10, width - 8));
        editBox.setHeight(font.lineHeight);

        if (editBox.getValue().isEmpty() && !placeholder.isEmpty()) {
            int placeholderColor = editBox.isFocused() ? UiTheme.TEXT_SECONDARY : UiTheme.TEXT_MUTED;
            g.drawString(font, placeholder, x + 4, y + (height - font.lineHeight) / 2, placeholderColor);
        }
        // The native widget remains registered for input routing. Rendering it
        // here keeps its text and cursor above the component's styled surface.
        editBox.render(g, mx, my, pt);
    }
}
