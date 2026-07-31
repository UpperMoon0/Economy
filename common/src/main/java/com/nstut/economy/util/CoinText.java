package com.nstut.economy.util;

import com.nstut.Economy;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * Inline chat representation of the Economy coin item texture.
 */
public final class CoinText {
    public static final String GLYPH = "\ue000";
    public static final ResourceLocation FONT = new ResourceLocation(Economy.MOD_ID, "coin");

    private CoinText() {}

    public static MutableComponent icon() {
        return Component.literal(GLYPH)
                .withStyle(style -> style.withFont(FONT).withColor(ChatFormatting.WHITE));
    }

    public static MutableComponent amount(String amount) {
        return icon().append(Component.literal(" " + amount).withStyle(ChatFormatting.GOLD));
    }
}
