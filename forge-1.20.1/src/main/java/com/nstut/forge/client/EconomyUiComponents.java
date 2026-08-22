package com.nstut.forge.client;

import com.nstut.Economy;
import com.nstut.economy.util.EconomyFormatUtil;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.UiRender;
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
                    bal = EconomyFormatUtil.formatCompact(new BigDecimal(balance.get()));
                } catch (Exception ignored) {
                    bal = balance.get();
                }
                UiRender.text(g, f, bal, x + 16, y + 5, c.onSurface());
            }
        };
    }
}
