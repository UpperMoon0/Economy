package com.nstut.economy.ui.framework;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class EditBoxWrapper extends UIComponent {

    private final EditBox editBox;
    private String placeholder = "";

    public EditBoxWrapper(int maxLength, int textColor, int bgColor, Font font) {
        this.editBox = new EditBox(font, 0, 0, 100, 16, Component.empty());
        this.editBox.setMaxLength(maxLength);
        this.editBox.setBordered(false);
        this.editBox.setTextColor(textColor);
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
        com.nstut.Economy.LOGGER.info("[EditBoxWrapper] setVisible visible={}, editBox={}", visible, editBox != null ? editBox.getValue() : "null");
    }

    public EditBox getEditBox() { return editBox; }
    public String getValue() { return editBox.getValue(); }
    public void setValue(String v) {
        if (this.editBox != null) {
            String oldVal = this.editBox.getValue();
            this.editBox.setValue(v != null ? v : "");
            this.editBox.setCursorPosition(0);
            this.editBox.setHighlightPos(0);
            com.nstut.Economy.LOGGER.info("[EditBoxWrapper] setValue oldVal='{}' -> newVal='{}', actualEditBoxVal='{}', cursor={}",
                oldVal, v, this.editBox.getValue(), this.editBox.getCursorPosition());
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
        int bg = 0xFF141422;
        int border = editBox.isFocused() ? 0xFF00D4AA : 0xFF35354A;
        g.fill(x, y, x + width, y + height, bg);
        g.fill(x, y, x + width, y + 1, border);
        g.fill(x, y + height - 1, x + width, y + height, border);
        g.fill(x, y, x + 1, y + height, border);
        g.fill(x + width - 1, y, x + width, y + height, border);

        // keep editbox inner bounds aligned with padding
        editBox.setX(x + 4);
        editBox.setY(y + (height - 10) / 2);
        editBox.setWidth(Math.max(10, width - 8));
        editBox.setHeight(10);

        if (editBox.getValue().isEmpty() && !placeholder.isEmpty()) {
            int placeholderColor = editBox.isFocused() ? 0xFF45455A : 0xFF65657A;
            g.drawString(font, placeholder, x + 4, y + (height - font.lineHeight) / 2, placeholderColor);
        }
    }
}

