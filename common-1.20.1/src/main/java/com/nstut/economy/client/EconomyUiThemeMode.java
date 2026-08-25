package com.nstut.economy.client;

import com.nstut.openui.theme.Theme;

/**
 * Persisted client theme selection. An enum (not a boolean) so a future
 * high-contrast option can be added without a file-format migration.
 */
public enum EconomyUiThemeMode {
    DARK,
    LIGHT;

    public Theme toOpenUiTheme() {
        return this == LIGHT ? Theme.light() : Theme.dark();
    }

    public EconomyUiThemeMode next() {
        return this == DARK ? LIGHT : DARK;
    }

    public static EconomyUiThemeMode fromString(String value) {
        if (value == null) return DARK;
        return switch (value.trim().toLowerCase()) {
            case "light" -> LIGHT;
            default -> DARK;
        };
    }

    public String storageValue() {
        return this == LIGHT ? "light" : "dark";
    }
}

