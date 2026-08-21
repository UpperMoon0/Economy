package com.nstut.forge.client;

import com.nstut.openui.api.ButtonWidget;
import com.nstut.openui.api.Ui;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.minecraft.UiContainerScreen;
import com.nstut.openui.state.Signal;
import com.nstut.openui.state.Signals;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.function.Supplier;

/**
 * Small base class for every Economy container screen. Owns the persisted
 * theme signal and applies it through the OpenUI runtime so a live toggle
 * repaints without restarting the screen.
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
        int margin = 16;
        if (this.width > 0) {
            int maxW = this.width - margin;
            if (imageWidth > maxW) imageWidth = Math.max(280, maxW);
            int maxH = this.height - margin;
            if (imageHeight > maxH) imageHeight = Math.max(160, maxH);
        }
        super.init();
        if (uiRuntime() != null) {
            uiRuntime().theme(themeMode.get().toOpenUiTheme());
        }
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
