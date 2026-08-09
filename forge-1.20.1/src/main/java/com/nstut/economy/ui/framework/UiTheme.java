package com.nstut.economy.ui.framework;

/**
 * Shared design tokens for Economy's client UI.
 *
 * <p>Keeping color, radius and spacing decisions here gives screens the same
 * role that a theme object provides in a web component system: individual
 * views describe hierarchy while this class owns the visual language.</p>
 */
public final class UiTheme {
    public static final int BACKDROP = 0xCC060609;
    public static final int SHELL = 0xFF0F0F13;
    public static final int SIDEBAR = 0xFF121216;
    public static final int SURFACE = 0xFF19191F;
    public static final int SURFACE_RAISED = 0xFF202027;
    public static final int SURFACE_HOVER = 0xFF292931;
    public static final int INPUT = 0xFF15151B;
    public static final int SLOT = 0xFF111116;

    public static final int BORDER_SUBTLE = 0xFF292930;
    public static final int BORDER = 0xFF35353E;
    public static final int BORDER_STRONG = 0xFF4A4A55;

    public static final int TEXT_PRIMARY = 0xFFF4F1EE;
    public static final int TEXT_SECONDARY = 0xFFB9B5B7;
    public static final int TEXT_MUTED = 0xFF79767B;
    public static final int TEXT_DISABLED = 0xFF55535A;

    public static final int ACCENT = 0xFF62E6C4;
    public static final int ACCENT_HOVER = 0xFF82F1D3;
    public static final int ACCENT_DIM = 0xFF277E69;
    public static final int ACCENT_DEEP = 0xFF143C34;
    public static final int AMBIENT_WARM = 0xFF6B342F;
    public static final int SUCCESS = 0xFF5DDA9D;
    public static final int SUCCESS_DEEP = 0xFF183B2C;
    public static final int DANGER = 0xFFFF6B73;
    public static final int DANGER_DEEP = 0xFF471F25;
    public static final int WARNING = 0xFFFFC56B;

    public static final int SHADOW = 0x70000000;
    public static final int SHADOW_SOFT = 0x36000000;
    public static final int HIGHLIGHT = 0x18FFFFFF;

    public static final int RADIUS_SM = 3;
    public static final int RADIUS_MD = 5;
    public static final int RADIUS_LG = 7;
    public static final int SPACE_1 = 2;
    public static final int SPACE_2 = 4;
    public static final int SPACE_3 = 6;
    public static final int SPACE_4 = 8;
    public static final int SPACE_5 = 12;

    private UiTheme() {}
}
