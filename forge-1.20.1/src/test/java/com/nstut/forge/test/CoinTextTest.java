package com.nstut.forge.test;

import com.nstut.economy.util.CoinText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CoinTextTest extends MinecraftTestBase {
    @Test
    void coinGlyphUsesTheDedicatedBitmapFont() {
        MutableComponent icon = CoinText.icon();

        assertEquals(CoinText.GLYPH, icon.getString());
        assertEquals(CoinText.FONT, icon.getStyle().getFont());
        assertEquals(ChatFormatting.WHITE.getColor(), icon.getStyle().getColor().getValue());
    }

    @Test
    void amountKeepsTheIconAndReadableNumber() {
        MutableComponent amountComp = CoinText.amount("12.5k");
        assertEquals(CoinText.GLYPH + " 12.5k", amountComp.getString());
        assertEquals(2, amountComp.getSiblings().size());
        assertEquals(CoinText.FONT, amountComp.getSiblings().get(0).getStyle().getFont());
        assertEquals(Style.DEFAULT_FONT, amountComp.getSiblings().get(1).getStyle().getFont());
        assertEquals(ChatFormatting.GOLD.getColor(), amountComp.getSiblings().get(1).getStyle().getColor().getValue());
    }

    @Test
    void amountFormatsMoneyWithoutDecimals() {
        assertEquals(CoinText.GLYPH + " 1000", CoinText.amount(new java.math.BigDecimal("1000.00")).getString());
        assertEquals(CoinText.GLYPH + " 500", CoinText.amount("500.0").getString());
    }

    @Test
    void coinBitmapFontResourceIsPackaged() {
        assertNotNull(getClass().getClassLoader()
                .getResource("assets/economy/font/coin.json"));
    }
}

