package com.nstut.forge.client;

import com.nstut.openui.api.ButtonWidget;
import com.nstut.openui.api.Ui;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.UiRender;
import com.nstut.openui.minecraft.UiContainerScreen;
import com.nstut.openui.state.Signal;
import com.nstut.openui.state.Signals;
import com.nstut.openui.theme.ColorScheme;
import com.nstut.openui.theme.Radii;
import com.nstut.openui.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.function.Supplier;

/**
 * Base class for every Economy container screen. Owns the persisted
 * theme signal, applies it through the OpenUI runtime, and provides
 * theme-aware shell rendering without mutating container dimensions.
 */
public abstract class EconomyUiContainerScreen<M extends AbstractContainerMenu> extends UiContainerScreen<M> {
    protected final Signal<EconomyUiThemeMode> themeMode =
            Signals.of(MarketClientPreferences.getThemeMode());

    private ButtonWidget themeToggle;

    protected EconomyUiContainerScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        if (uiRuntime() != null) {
            uiRuntime().theme(themeMode.get().toOpenUiTheme());
        }
    }

    public Theme currentUiTheme() {
        return uiRuntime() != null ? uiRuntime().theme() : themeMode.get().toOpenUiTheme();
    }

    public ColorScheme colors() {
        return currentUiTheme().colors();
    }

    public Radii radii() {
        return currentUiTheme().radii();
    }

    protected void renderBaseShell(GuiGraphics g) {
        renderBackground(g);
        int x = leftPos;
        int y = topPos;
        int r = radii().card();
        ColorScheme c = colors();
        UiRender.shadow(g, x, y, imageWidth, imageHeight, r, c);
        UiRender.surface(g, x, y, imageWidth, imageHeight, r, c.surface(), c.borderSubtle(), c);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Suppress default vanilla black text title/inventory rendering.
        // Headers and titles are cleanly composed in the OpenUI tree.
    }

    protected ButtonWidget buildThemeToggle() {
        themeToggle = Ui.button((Supplier<Component>) () -> themeToggleLabel(themeMode.get()),
                this::toggleTheme).ghost();
        return themeToggle;
    }

    private static Component themeToggleLabel(EconomyUiThemeMode mode) {
        return Component.translatable(mode == EconomyUiThemeMode.DARK
                ? "ui.economy.theme.light_label" : "ui.economy.theme.dark_label");
    }

    private void toggleTheme() {
        EconomyUiThemeMode next = themeMode.get().next();
        themeMode.set(next);
        MarketClientPreferences.setThemeMode(next);
        if (uiRuntime() != null) {
            uiRuntime().theme(next.toOpenUiTheme());
        }
        if (themeToggle != null) {
            themeToggle.setLabel(themeToggleLabel(next));
        }
    }

    public EconomyUiThemeMode currentThemeMode() {
        return themeMode.get();
    }
}
