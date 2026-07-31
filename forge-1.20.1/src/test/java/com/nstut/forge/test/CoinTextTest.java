package com.nstut.forge.test;

import com.nstut.economy.util.CoinText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
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
        assertEquals(CoinText.GLYPH + " 12.5k", CoinText.amount("12.5k").getString());
    }

    @Test
    void coinBitmapFontResourceIsPackaged() {
        assertNotNull(getClass().getClassLoader()
                .getResource("assets/economy/font/coin.json"));
    }
}
