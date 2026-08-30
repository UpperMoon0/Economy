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
            new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Economy.MOD_ID, "coin")));

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
}

