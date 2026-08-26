package com.nstut.economy.util;

import com.nstut.Economy;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Inline chat representation of the Economy coin item texture.
 */
public final class CoinText {
    public static final String GLYPH = "\ue000";
    public static final Identifier FONT = com.nstut.economy.compat.Compat.rl(Economy.MOD_ID, "coin");

    private CoinText() {}

    public static MutableComponent icon() {
        return Component.literal(GLYPH)
                .withStyle(style -> style.withFont(new FontDescription.Resource(FONT)).withColor(ChatFormatting.WHITE));
    }

    public static MutableComponent amount(BigDecimal amount) {
        return amount(formatMoney(amount));
    }

    public static MutableComponent amount(String amount) {
        String formatted = formatMoney(amount);
        return Component.empty()
                .append(icon())
                .append(Component.literal(" " + formatted).withStyle(style -> style.withColor(ChatFormatting.GOLD)));
    }

    public static String formatMoney(BigDecimal amount) {
        if (amount == null) return "0";
        return amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    public static String formatMoney(String amount) {
        if (amount == null || amount.isEmpty()) return "0";
        try {
            return formatMoney(new BigDecimal(amount));
        } catch (Exception e) {
            return amount;
        }
    }
}


