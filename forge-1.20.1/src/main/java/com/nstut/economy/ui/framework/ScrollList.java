package com.nstut.economy.ui.framework;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.BiConsumer;
import java.util.function.IntSupplier;

public class ScrollList extends UIComponent {

    private final IntSupplier itemCount;
    private final int itemHeight;
    private final ItemClickListener onItemClick;
    private int scrollOffset;
    private final int trackColor;
    private final int thumbColor;

    public interface ItemRenderer {
        void render(GuiGraphics g, Font font, int index, int x, int y, int width, int mx, int my, boolean hovered);
    }

    public interface ItemClickListener {
        void onClick(int index, int button, int mx, int my);
    }

    public ScrollList(IntSupplier itemCount, int itemHeight, ItemRenderer renderer,
                      ItemClickListener onItemClick, int trackColor, int thumbColor) {
        this.itemCount = itemCount;
        this.itemHeight = itemHeight;
        this.itemRenderer = renderer;
        this.onItemClick = onItemClick;
        this.trackColor = trackColor;
        this.thumbColor = thumbColor;
        this.flex = true;
    }

    public ScrollList(IntSupplier itemCount, int itemHeight, ItemRenderer renderer,
                      BiConsumer<Integer, Integer> onItemClick, int trackColor, int thumbColor) {
        this(itemCount, itemHeight, renderer, (idx, btn, mx, my) -> onItemClick.accept(idx, btn), trackColor, thumbColor);
    }

    private final ItemRenderer itemRenderer;

    @Override
    public int preferredWidth(Font font) { return 10; }
    @Override
    public int preferredHeight(Font font) { return 10; }

    @Override
    public void render(GuiGraphics g, Font font, int mx, int my, float pt) {
        if (!visible) return;
        int total = itemCount.getAsInt();
        int maxVisible = Math.max(1, height / itemHeight);
        int maxScroll = Math.max(0, total - maxVisible);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        int renderCount = Math.min(maxVisible + 1, total - scrollOffset);
        for (int i = 0; i < renderCount; i++) {
            int idx = scrollOffset + i;
            if (idx >= total) break;
            int iy = y + i * itemHeight;
            if (iy + itemHeight > y + height && i >= maxVisible) break;
            boolean itemHovered = mx >= x && mx < x + width - 8 && my >= iy && my < iy + itemHeight && my < y + height;
            itemRenderer.render(g, font, idx, x, iy, width - 8, mx, my, itemHovered);
        }

        if (maxScroll > 0) {
            int trackX = x + width - 6;
            int trackH = height;
            int thumbH = Math.max(10, trackH * maxVisible / total);
            int thumbY = scrollOffset * (trackH - thumbH) / maxScroll;
            UiRender.roundedRect(g, trackX + 1, y, 3, trackH, 2, trackColor);
            UiRender.roundedRect(g, trackX, y + thumbY, 5, thumbH, 3,
                    isDraggingScrollbar ? UiTheme.ACCENT_HOVER : thumbColor);
        }
    }

    private double scrollAccumulator = 0.0;
    private boolean isDraggingScrollbar = false;

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible) return false;
        if (mx < x || mx >= x + width || my < y || my >= y + height) return false;

        int total = itemCount.getAsInt();
        int maxVisible = Math.max(1, height / itemHeight);
        int maxScroll = Math.max(0, total - maxVisible);

        // Scrollbar track or thumb click (rightmost 12 pixels hit area)
        if (maxScroll > 0 && mx >= x + width - 12 && mx <= x + width) {
            isDraggingScrollbar = true;
            updateScrollFromMouseY(my, total, maxVisible, maxScroll);
            return true;
        }

        // Item click inside item region
        if (mx >= x && mx < x + width - 12) {
            int idx = scrollOffset + ((int) my - y) / itemHeight;
            if (idx >= 0 && idx < total) {
                if (onItemClick != null) {
                    onItemClick.onClick(idx, button, (int) mx, (int) my);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (!visible || !isDraggingScrollbar) return false;
        int total = itemCount.getAsInt();
        int maxVisible = Math.max(1, height / itemHeight);
        int maxScroll = Math.max(0, total - maxVisible);
        if (maxScroll > 0) {
            updateScrollFromMouseY(my, total, maxVisible, maxScroll);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }
        return false;
    }

    private void updateScrollFromMouseY(double my, int total, int maxVisible, int maxScroll) {
        int trackH = height;
        int thumbH = Math.max(10, trackH * maxVisible / total);
        if (trackH - thumbH <= 0) return;
        double relY = Math.max(0, Math.min(trackH - thumbH, my - y - thumbH / 2.0));
        scrollOffset = (int) Math.round((relY / (double) (trackH - thumbH)) * maxScroll);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!visible) return false;
        if (mx < x || mx >= x + width || my < y || my >= y + height) return false;

        int total = itemCount.getAsInt();
        int maxVisible = Math.max(1, height / itemHeight);
        int maxScroll = Math.max(0, total - maxVisible);
        if (maxScroll <= 0) return false;

        scrollAccumulator += delta;
        if (Math.abs(scrollAccumulator) >= 0.5) {
            int steps = (int) Math.signum(scrollAccumulator);
            scrollOffset -= steps;
            scrollAccumulator -= steps;
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        }
        return true;
    }

    public void resetScroll() {
        scrollOffset = 0;
        scrollAccumulator = 0.0;
        isDraggingScrollbar = false;
    }
}
