package com.nstut.economy.ui.framework;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.IntSupplier;

/**
 * A vertically scrolling, fixed-column collection. Scrolling is measured in
 * rows while render and click callbacks continue to receive item indices.
 */
public class ScrollGrid extends UIComponent {
    private final IntSupplier itemCount;
    private final int columns;
    private final int rowHeight;
    private final int columnGap;
    private final ScrollList.ItemRenderer itemRenderer;
    private final ScrollList.ItemClickListener onItemClick;
    private final int trackColor;
    private final int thumbColor;
    private int scrollRow;
    private double scrollAccumulator;
    private boolean draggingScrollbar;

    public ScrollGrid(IntSupplier itemCount, int columns, int rowHeight, int columnGap,
                      ScrollList.ItemRenderer itemRenderer, ScrollList.ItemClickListener onItemClick,
                      int trackColor, int thumbColor) {
        this.itemCount = itemCount;
        this.columns = Math.max(1, columns);
        this.rowHeight = Math.max(1, rowHeight);
        this.columnGap = Math.max(0, columnGap);
        this.itemRenderer = itemRenderer;
        this.onItemClick = onItemClick;
        this.trackColor = trackColor;
        this.thumbColor = thumbColor;
        this.flex = true;
    }

    public static int rowCount(int itemCount, int columns) {
        if (itemCount <= 0) return 0;
        int safeColumns = Math.max(1, columns);
        return (itemCount + safeColumns - 1) / safeColumns;
    }

    public static int indexForCell(int row, int column, int columns) {
        return row * Math.max(1, columns) + column;
    }

    @Override
    public int preferredWidth(Font font) {
        return 10;
    }

    @Override
    public int preferredHeight(Font font) {
        return 10;
    }

    @Override
    public void render(GuiGraphics g, Font font, int mx, int my, float pt) {
        if (!visible) return;

        int totalItems = itemCount.getAsInt();
        int totalRows = rowCount(totalItems, columns);
        int visibleRows = Math.max(1, height / rowHeight);
        int maxScroll = Math.max(0, totalRows - visibleRows);
        scrollRow = Math.max(0, Math.min(scrollRow, maxScroll));

        int contentWidth = Math.max(1, width - 8);
        int cellWidth = Math.max(1, (contentWidth - columnGap * (columns - 1)) / columns);
        int rowsToRender = Math.min(visibleRows + 1, totalRows - scrollRow);

        for (int visibleRow = 0; visibleRow < rowsToRender; visibleRow++) {
            int dataRow = scrollRow + visibleRow;
            int cellY = y + visibleRow * rowHeight;
            if (cellY + rowHeight > y + height && visibleRow >= visibleRows) break;

            for (int column = 0; column < columns; column++) {
                int index = indexForCell(dataRow, column, columns);
                if (index >= totalItems) break;
                int cellX = x + column * (cellWidth + columnGap);
                boolean hovered = mx >= cellX && mx < cellX + cellWidth
                        && my >= cellY && my < cellY + rowHeight && my < y + height;
                itemRenderer.render(g, font, index, cellX, cellY, cellWidth, mx, my, hovered);
            }
        }

        if (maxScroll > 0) {
            int trackX = x + width - 6;
            int thumbHeight = Math.max(10, height * visibleRows / totalRows);
            int thumbY = scrollRow * (height - thumbHeight) / maxScroll;
            g.fill(trackX, y, trackX + 5, y + height, trackColor);
            g.fill(trackX, y + thumbY, trackX + 5, y + thumbY + thumbHeight, thumbColor);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible || mx < x || mx >= x + width || my < y || my >= y + height) return false;

        int totalItems = itemCount.getAsInt();
        int totalRows = rowCount(totalItems, columns);
        int visibleRows = Math.max(1, height / rowHeight);
        int maxScroll = Math.max(0, totalRows - visibleRows);

        if (maxScroll > 0 && mx >= x + width - 12) {
            draggingScrollbar = true;
            updateScrollFromMouseY(my, totalRows, visibleRows, maxScroll);
            return true;
        }

        int contentWidth = Math.max(1, width - 8);
        int cellWidth = Math.max(1, (contentWidth - columnGap * (columns - 1)) / columns);
        int relativeX = (int) mx - x;
        int column = relativeX / (cellWidth + columnGap);
        if (column < 0 || column >= columns
                || relativeX - column * (cellWidth + columnGap) >= cellWidth) {
            return false;
        }

        int row = scrollRow + ((int) my - y) / rowHeight;
        int index = indexForCell(row, column, columns);
        if (index >= 0 && index < totalItems && onItemClick != null) {
            onItemClick.onClick(index, button, (int) mx, (int) my);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (!visible || !draggingScrollbar) return false;
        int totalRows = rowCount(itemCount.getAsInt(), columns);
        int visibleRows = Math.max(1, height / rowHeight);
        int maxScroll = Math.max(0, totalRows - visibleRows);
        if (maxScroll > 0) updateScrollFromMouseY(my, totalRows, visibleRows, maxScroll);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!draggingScrollbar) return false;
        draggingScrollbar = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!visible || mx < x || mx >= x + width || my < y || my >= y + height) return false;

        int totalRows = rowCount(itemCount.getAsInt(), columns);
        int visibleRows = Math.max(1, height / rowHeight);
        int maxScroll = Math.max(0, totalRows - visibleRows);
        if (maxScroll <= 0) return false;

        scrollAccumulator += delta;
        if (Math.abs(scrollAccumulator) >= 0.5) {
            int steps = (int) Math.signum(scrollAccumulator);
            scrollRow -= steps;
            scrollAccumulator -= steps;
            scrollRow = Math.max(0, Math.min(scrollRow, maxScroll));
        }
        return true;
    }

    private void updateScrollFromMouseY(double mouseY, int totalRows, int visibleRows, int maxScroll) {
        int thumbHeight = Math.max(10, height * visibleRows / totalRows);
        if (height - thumbHeight <= 0) return;
        double relativeY = Math.max(0, Math.min(height - thumbHeight, mouseY - y - thumbHeight / 2.0));
        scrollRow = (int) Math.round(relativeY / (height - thumbHeight) * maxScroll);
        scrollRow = Math.max(0, Math.min(scrollRow, maxScroll));
    }

    public void resetScroll() {
        scrollRow = 0;
        scrollAccumulator = 0;
        draggingScrollbar = false;
    }
}
