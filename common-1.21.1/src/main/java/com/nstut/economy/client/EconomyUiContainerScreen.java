package com.nstut.economy.client;

import com.nstut.openui.api.ButtonWidget;
import com.nstut.openui.api.Ui;
import com.nstut.openui.api.UiRender;
import com.nstut.openui.minecraft.UiContainerScreen;
import com.nstut.openui.state.Signal;
import com.nstut.openui.state.Signals;
import com.nstut.openui.theme.ColorScheme;
import com.nstut.openui.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.function.Supplier;

/** Shared OpenUI base for Economy container screens. Container geometry is never resized here. */
public abstract class EconomyUiContainerScreen<M extends AbstractContainerMenu> extends UiContainerScreen<M> {
    protected final Signal<EconomyUiThemeMode> themeMode = Signals.of(MarketClientPreferences.getThemeMode());
    private ButtonWidget themeToggle;

    protected EconomyUiContainerScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        if (uiRuntime() != null) uiRuntime().theme(themeMode.get().toOpenUiTheme());
    }

    public Theme currentUiTheme() {
        return uiRuntime() != null ? uiRuntime().theme() : themeMode.get().toOpenUiTheme();
    }

    public ColorScheme colors() { return currentUiTheme().colors(); }
    public Theme.Radii radii() { return currentUiTheme().radii(); }

    protected void renderBaseShell(GuiGraphics g) {
        renderBackground(g, 0, 0, 1.0f);
        ColorScheme c = colors();
        int radius = currentUiTheme().cardTheme().radius();
        UiRender.surface(g, leftPos, topPos, imageWidth, imageHeight,
                radius, c.surface(), c.borderSubtle(), true, c);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (showInventoryLabel()) {
            UiRender.text(g, font, playerInventoryTitle.getString(),
                    economyInventoryLabelX(), inventoryLabelY, colors().onSurfaceMuted());
        }
    }

    protected boolean showInventoryLabel() { return false; }

    protected int economyInventoryLabelX() { return 8; }

    protected ButtonWidget buildThemeToggle() {
        themeToggle = Ui.button((Supplier<Component>) () -> themeToggleLabel(themeMode.get()), this::toggleTheme).ghost();
        return themeToggle;
    }

    protected ButtonWidget buildCompactThemeToggle() {
        themeToggle = Ui.button((Supplier<Component>) () -> compactThemeLabel(themeMode.get()), this::toggleTheme).ghost().small();
        themeToggle.width(18);
        return themeToggle;
    }

    private static Component themeToggleLabel(EconomyUiThemeMode mode) {
        return Component.translatable(mode == EconomyUiThemeMode.DARK
                ? "ui.economy.theme.light_label" : "ui.economy.theme.dark_label");
    }

    private static Component compactThemeLabel(EconomyUiThemeMode mode) {
        return Component.translatable(mode == EconomyUiThemeMode.DARK
                ? "ui.economy.theme.light_compact" : "ui.economy.theme.dark_compact");
    }

    private void toggleTheme() {
        EconomyUiThemeMode next = themeMode.get().next();
        themeMode.set(next);
        MarketClientPreferences.setThemeMode(next);
        if (uiRuntime() != null) uiRuntime().theme(next.toOpenUiTheme());
        if (themeToggle != null) {
            Component label = themeToggle.getWidth() <= 20 ? compactThemeLabel(next) : themeToggleLabel(next);
            themeToggle.setLabel(label);
        }
    }

    public EconomyUiThemeMode currentThemeMode() { return themeMode.get(); }
}

