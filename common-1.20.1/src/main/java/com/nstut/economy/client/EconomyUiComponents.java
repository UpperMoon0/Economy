package com.nstut.economy.client;

import com.nstut.Economy;
import com.nstut.economy.util.EconomyFormatUtil;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.UiRender;
import com.nstut.openui.controls.Badge;
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
                                        ReadableSignal<String> marketPrincipal) {
        return new UIComponent() {
            {
                fillWidth();
                height(32);
            }

            @Override
            public int preferredWidth(Font f) {
                return 100;
            }

            @Override
            public int preferredHeight(Font f) {
                return 32;
            }

            @Override
            public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme c = theme().colors();
                MarketClientStore.TeamWalletState team = teamWallet.get();
                int gap = 4;
                boolean showTeam = team != null && team.visible();
                int personalWidth = showTeam ? Math.max(1, (width - gap) / 2) : width;

                drawWalletCell(g, f, x, y, personalWidth,
                        Component.translatable("ui.economy.wallet.personal").getString(),
                        personalBalance.get(), c);

                if (showTeam) {
                    int teamX = x + personalWidth + gap;
                    int teamWidth = Math.max(1, width - personalWidth - gap);
                    String teamLabel = Component.translatable(
                            "ui.economy.wallet.team", team.teamName()).getString();
                    drawWalletCell(g, f, teamX, y, teamWidth, teamLabel, team.teamBalance(), c);
                }

                String principalKey = "TEAM".equalsIgnoreCase(marketPrincipal.get())
                        ? "ui.economy.principal.team"
                        : "ui.economy.principal.personal";
                String market = Component.translatable(
                        "ui.economy.wallet.market_principal",
                        Component.translatable(principalKey)).getString();
                UiRender.text(g, f, fitWalletText(f, market, personalWidth), x, y + 21, c.onSurfaceMuted());

                if (showTeam) {
                    String role = humanizeEnum(team.role());
                    String permission = team.canSpend()
                            ? Component.translatable("ui.economy.wallet.team_spend_allowed").getString()
                            : Component.translatable("ui.economy.wallet.team_spend_requires",
                                    humanizeEnum(team.spendRole())).getString();
                    String status = role + " · " + permission;
                    int teamX = x + personalWidth + gap;
                    int teamWidth = Math.max(1, width - personalWidth - gap);
                    UiRender.text(g, f, fitWalletText(f, status, teamWidth),
                            teamX, y + 21, c.onSurfaceMuted());
                }
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

