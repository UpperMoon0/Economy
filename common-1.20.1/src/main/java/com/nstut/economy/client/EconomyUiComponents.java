package com.nstut.economy.client;

import com.nstut.Economy;
import com.nstut.economy.util.EconomyFormatUtil;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.UiRender;
import com.nstut.openui.controls.Badge;
import com.nstut.openui.api.ButtonWidget;
import com.nstut.openui.state.ReadableSignal;
import com.nstut.openui.theme.ColorScheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;

/**
 * Suite-level UI helpers shared across the Economy screens. Kept deliberately
 * small: only constructs that more than one screen needs.
 */
public final class EconomyUiComponents {
    public static final int BADGE_HEIGHT = 12;
    public static final ItemStack COIN_ICON =
            new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(Economy.MOD_ID, "coin")));

    private EconomyUiComponents() {}

    /** Button that uses the same font-independent two-arrow adjustment glyph as the wallet badge. */
    public static ButtonWidget configStateButton(Component label, Runnable action) {
        return new ConfigStateButton(label, action);
    }

    public static void drawConfigSwitchIcon(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y + 1, x + 8, y + 2, color);
        g.fill(x + 6, y, x + 7, y + 3, color);
        g.fill(x + 7, y + 1, x + 9, y + 2, color);
        g.fill(x + 1, y + 5, x + 9, y + 6, color);
        g.fill(x + 2, y + 4, x + 3, y + 7, color);
        g.fill(x, y + 5, x + 2, y + 6, color);
    }

    private static final class ConfigStateButton extends ButtonWidget {
        ConfigStateButton(Component label, Runnable action) {
            super(label);
            onPress(action);
            outline();
            small();
            alignLeft();
        }

        @Override
        public int preferredWidth(Font font) {
            int labelWidth = font != null ? font.width(getLabel()) : getLabel().getString().length() * 6;
            // 7px label inset + 2px gap + 9px adjustment glyph + 2px right inset.
            return Math.max(36, labelWidth + 20);
        }

        @Override
        public void render(GuiGraphics g, Font font, int mx, int my, float pt) {
            super.render(g, font, mx, my, pt);
            int sx = x + width - 11;
            int sy = y + Math.max(1, (height - 7) / 2);
            boolean hovered = mx >= x && mx < x + width && my >= y && my < y + height;
            int color = !isEnabled() ? theme().colors().onSurfaceDisabled()
                    : hovered ? theme().colors().primary() : theme().colors().onSurfaceMuted();
            drawConfigSwitchIcon(g, sx, sy, color);
        }
    }

    public static void drawCoin(GuiGraphics g, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.5f, 0.5f, 0.5f);
        g.renderItem(COIN_ICON, 0, 0);
        g.pose().popPose();
    }

    /** Draws an absolute-positioned badge with the same semantic contrast contract as OpenUI Badge. */
    public static int badgeWidth(Font font, String text) {
        return font.width(text) + 8;
    }

    public static int drawBadge(GuiGraphics g, Font font, String text, int rightX, int y,
                                Badge.Variant variant, boolean hovered, ColorScheme colors) {
        int background = switch (variant) {
            case PRIMARY -> hovered ? colors.primaryHover() : colors.primaryDim();
            case SUCCESS -> hovered ? colors.successHover() : colors.successDeep();
            case WARNING -> hovered ? colors.warningHover() : colors.warning();
            case DANGER -> hovered ? colors.dangerHover() : colors.dangerDeep();
            case NEUTRAL -> hovered ? colors.surfaceRaised() : colors.surfaceVariant();
        };
        int border = switch (variant) {
            case PRIMARY -> colors.primary();
            case SUCCESS -> colors.success();
            case WARNING -> colors.warningHover();
            case DANGER -> colors.danger();
            case NEUTRAL -> colors.border();
        };
        int width = badgeWidth(font, text);
        int x = rightX - width;
        UiRender.pill(g, x, y, width, BADGE_HEIGHT, background, border);
        int textColor = variant == Badge.Variant.NEUTRAL ? colors.onSurface() : colors.onPrimary();
        UiRender.text(g, font, text, x + 4, y + 2, textColor);
        return x;
    }

    public static int coinBadgeWidth(Font font, String text) {
        return font.width(text) + 18;
    }

    /** Draws a semantic badge whose value is explicitly marked as Coin currency. */
    public static int drawCoinBadge(GuiGraphics g, Font font, String text, int rightX, int y,
                                    Badge.Variant variant, boolean hovered, ColorScheme colors) {
        int background = switch (variant) {
            case PRIMARY -> hovered ? colors.primaryHover() : colors.primaryDim();
            case SUCCESS -> hovered ? colors.successHover() : colors.successDeep();
            case WARNING -> hovered ? colors.warningHover() : colors.warning();
            case DANGER -> hovered ? colors.dangerHover() : colors.dangerDeep();
            case NEUTRAL -> hovered ? colors.surfaceRaised() : colors.surfaceVariant();
        };
        int border = switch (variant) {
            case PRIMARY -> colors.primary();
            case SUCCESS -> colors.success();
            case WARNING -> colors.warningHover();
            case DANGER -> colors.danger();
            case NEUTRAL -> colors.border();
        };
        int width = coinBadgeWidth(font, text);
        int x = rightX - width;
        UiRender.pill(g, x, y, width, BADGE_HEIGHT, background, border);
        drawCoin(g, x + 4, y + 2);
        int textColor = variant == Badge.Variant.NEUTRAL ? colors.onSurface() : colors.onPrimary();
        UiRender.text(g, font, text, x + 14, y + 2, textColor);
        return x;
    }

    /** Coin + compacted balance pill that fills the width of its parent. */
    public static UIComponent balancePill(ReadableSignal<String> balance) {
        return new UIComponent() {
            {
                fillWidth();
            }

            @Override
            public int preferredWidth(Font f) {
                return 10;
            }

            @Override
            public int preferredHeight(Font f) {
                return 19;
            }

            @Override
            public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme c = theme().colors();
                UiRender.pill(g, x, y, width, height, c.input(), c.borderSubtle());
                drawCoin(g, x + 6, y + 4);
                String bal;
                try {
                    bal = EconomyFormatUtil.formatMoneyCompact(new BigDecimal(balance.get()));
                } catch (Exception ignored) {
                    bal = balance.get();
                }
                UiRender.text(g, f, bal, x + 16, y + 5, c.onSurface());
            }
        };
    }

    /**
     * Compact server-authoritative wallet strip. Team details are rendered only
     * when the latest sync says a viewable party wallet exists.
     */
    public static UIComponent walletBar(ReadableSignal<String> personalBalance,
                                        ReadableSignal<MarketClientStore.TeamWalletState> teamWallet,
                                        ReadableSignal<String> marketPrincipal,
                                        Runnable onSwitchWallet) {
        return new UIComponent() {
            {
                fillWidth();
                height(32);
            }

            @Override public int preferredWidth(Font f) { return 100; }
            @Override public int preferredHeight(Font f) { return 32; }

            @Override
            public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme c = theme().colors();
                MarketClientStore.TeamWalletState team = teamWallet.get();
                boolean selectedTeam = "TEAM".equals(marketPrincipal.get());
                boolean teamAvailable = team != null && team.visible();

                String label = selectedTeam
                        ? (teamAvailable ? Component.translatable("ui.economy.wallet.team", team.teamName()).getString()
                        : Component.translatable("ui.economy.wallet.team_unavailable").getString())
                        : Component.translatable("ui.economy.wallet.personal").getString();
                String amount = selectedTeam ? (teamAvailable ? team.teamBalance() : "0") : personalBalance.get();
                drawWalletCell(g, f, x, y, width, label + (teamAvailable || selectedTeam ? "  >" : ""), amount, c);

                String status;
                if (selectedTeam && !teamAvailable) {
                    status = Component.translatable("ui.economy.wallet.switch_personal_hint").getString();
                } else if (!teamAvailable) {
                    status = Component.translatable("ui.economy.wallet.personal_only").getString();
                } else if (selectedTeam) {
                    status = Component.translatable("ui.economy.wallet.team_active", humanizeEnum(team.role())).getString();
                } else if (team.canSpend()) {
                    status = Component.translatable("ui.economy.wallet.switch_team_hint", team.teamName()).getString();
                } else {
                    status = Component.translatable("ui.economy.wallet.team_spend_requires",
                            humanizeEnum(team.spendRole())).getString();
                }
                UiRender.text(g, f, fitWalletText(f, status, width), x, y + 21, c.onSurfaceMuted());
            }

            @Override
            public boolean mouseClicked(double mx, double my, int button) {
                MarketClientStore.TeamWalletState team = teamWallet.get();
                if (button == 0 && ("TEAM".equals(marketPrincipal.get()) || (team != null && team.visible()))
                        && mx >= x && mx < x + width && my >= y && my < y + height) {
                    if (onSwitchWallet != null) onSwitchWallet.run();
                    return true;
                }
                return false;
            }
        };
    }

    /** Compact Personal/Team selector with the currently selected account balance. */
    public static UIComponent walletBadge(ReadableSignal<String> personalBalance,
                                          ReadableSignal<MarketClientStore.TeamWalletState> teamWallet,
                                          ReadableSignal<String> marketPrincipal,
                                          Runnable onSwitchWallet) {
        return new UIComponent() {
            { width(104); height(32); }
            @Override public int preferredWidth(Font f) { return 104; }
            @Override public int preferredHeight(Font f) { return 32; }

            @Override
            public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme c = theme().colors();
                MarketClientStore.TeamWalletState team = teamWallet.get();
                boolean selectedTeam = "TEAM".equals(marketPrincipal.get());
                boolean teamAvailable = team != null && team.visible();
                boolean clickable = selectedTeam || teamAvailable;
                boolean hovered = clickable && mx >= x && mx < x + width && my >= y && my < y + height;
                UiRender.pill(g, x, y, width, height,
                        hovered ? c.surfaceRaised() : c.input(),
                        hovered ? c.primary() : c.borderSubtle());

                String label = selectedTeam
                        ? Component.translatable("ui.economy.principal.team").getString()
                        : Component.translatable("ui.economy.principal.personal").getString();
                String amount = formatWalletBalance(selectedTeam
                        ? (teamAvailable ? team.teamBalance() : "0") : personalBalance.get());
                // A tall pill curves inward near the top/bottom; keep both rows inside its safe inset.
                int inset = 12;
                int iconSpace = clickable ? 12 : 0;
                UiRender.text(g, f, fitWalletText(f, label, Math.max(0, width - inset * 2 - iconSpace)),
                        x + inset, y + 6, c.onSurfaceMuted());
                if (clickable) {
                    // Two opposing pixel arrows avoid font-dependent Unicode glyphs.
                    int sx = x + width - inset - 9;
                    int sy = y + 7;
                    int color = hovered ? c.primary() : c.onSurfaceMuted();
                    drawConfigSwitchIcon(g, sx, sy, color);
                }
                drawCoin(g, x + inset, y + 17);
                UiRender.text(g, f, fitWalletText(f, amount, Math.max(0, width - inset * 2 - 10)),
                        x + inset + 10, y + 19, c.onSurface());
            }

            @Override
            public boolean mouseClicked(double mx, double my, int button) {
                MarketClientStore.TeamWalletState team = teamWallet.get();
                if (button == 0
                        && ("TEAM".equals(marketPrincipal.get()) || (team != null && team.visible()))
                        && mx >= x && mx < x + width && my >= y && my < y + height) {
                    if (onSwitchWallet != null) onSwitchWallet.run();
                    return true;
                }
                return false;
            }
        };
    }

    private static void drawWalletCell(GuiGraphics g, Font f, int cellX, int cellY, int cellWidth,
                                       String label, String rawBalance, ColorScheme colors) {
        UiRender.pill(g, cellX, cellY, cellWidth, 18, colors.input(), colors.borderSubtle());
        String balance = formatWalletBalance(rawBalance);
        int balanceWidth = f.width(balance);
        int coinX = cellX + Math.max(3, cellWidth - balanceWidth - 13);
        drawCoin(g, coinX, cellY + 3);
        UiRender.text(g, f, balance, coinX + 10, cellY + 5, colors.onSurface());

        int labelWidth = Math.max(0, coinX - cellX - 6);
        if (labelWidth > 0) {
            UiRender.text(g, f, fitWalletText(f, label, labelWidth),
                    cellX + 4, cellY + 5, colors.onSurfaceMuted());
        }
    }

    private static String formatWalletBalance(String raw) {
        try {
            return EconomyFormatUtil.formatMoneyCompact(new BigDecimal(raw));
        } catch (Exception ignored) {
            return raw == null ? "0" : raw;
        }
    }

    private static String fitWalletText(Font font, String text, int maxWidth) {
        if (text == null || maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ew = font.width(ellipsis);
        if (maxWidth <= ew) return font.plainSubstrByWidth(ellipsis, maxWidth);
        return font.plainSubstrByWidth(text, maxWidth - ew) + ellipsis;
    }

    private static String humanizeEnum(String value) {
        if (value == null || value.isBlank()) return "";
        String lower = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

}

