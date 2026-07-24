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

        for (int i = 0; i < maxVisible; i++) {
            int idx = scrollOffset + i;
            if (idx >= total) break;
            int iy = y + i * itemHeight;
            boolean itemHovered = mx >= x && mx < x + width - 4 && my >= iy && my < iy + itemHeight;
            itemRenderer.render(g, font, idx, x, iy, width - 4, mx, my, itemHovered);
        }

        if (maxScroll > 0) {
            int trackX = x + width - 4;
            int trackH = maxVisible * itemHeight;
            int thumbH = Math.max(6, trackH * maxVisible / total);
            int thumbY = scrollOffset * (trackH - thumbH) / maxScroll;
            g.fill(trackX, y, trackX + 3, y + trackH, trackColor);
            g.fill(trackX, y + thumbY, trackX + 3, y + thumbY + thumbH, thumbColor);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible) return false;
        int total = itemCount.getAsInt();
        boolean inside = mx >= x && mx < x + width - 4 && my >= y && my < y + height;
        com.nstut.Economy.LOGGER.info("[ScrollList] mouseClicked mx={}, my={}, btn={}, bounds=[x={}, y={}, w={}, h={}], total={}, inside={}",
            mx, my, button, x, y, width, height, total, inside);
        if (inside) {
            int idx = scrollOffset + ((int) my - y) / itemHeight;
            com.nstut.Economy.LOGGER.info("[ScrollList] Calculated item index={}", idx);
            if (idx >= 0 && idx < total) {
                if (onItemClick != null) {
                    com.nstut.Economy.LOGGER.info("[ScrollList] Invoking onItemClick for index={}", idx);
                    onItemClick.onClick(idx, button, (int) mx, (int) my);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!visible) return false;
        scrollOffset -= (int) delta;
        return true;
    }

    public void resetScroll() { scrollOffset = 0; }
}

