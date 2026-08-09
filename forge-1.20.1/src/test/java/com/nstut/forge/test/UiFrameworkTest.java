package com.nstut.forge.test;

import com.nstut.economy.ui.framework.HStack;
import com.nstut.economy.ui.framework.UIComponent;
import com.nstut.economy.ui.framework.UiRender;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiFrameworkTest {
    @Test
    void colorInterpolationPreservesEndpointsAndAlpha() {
        assertEquals(0xFF102030, UiRender.mix(0xFF102030, 0xFF90A0B0, 0.0F));
        assertEquals(0xFF90A0B0, UiRender.mix(0xFF102030, 0xFF90A0B0, 1.0F));
        assertEquals(0xFF506070, UiRender.mix(0xFF102030, 0xFF90A0B0, 0.5F));
        assertEquals(0x40123456, UiRender.alpha(0xFF123456, 0x40));
    }

    @Test
    void horizontalFlexDistributesRemainingSpaceByWeight() {
        HStack row = new HStack().gap(2);
        UIComponent fixed = new TestComponent(10, 10);
        UIComponent onePart = new TestComponent(0, 10);
        UIComponent twoParts = new TestComponent(0, 10);
        onePart.flex(1.0F);
        twoParts.flex(2.0F);
        row.child(fixed).child(onePart).child(twoParts);

        row.layoutTree(null, 0, 0, 100, 10);

        assertEquals(10, fixed.getWidth());
        assertEquals(29, onePart.getWidth());
        assertEquals(57, twoParts.getWidth());
        assertEquals(43, twoParts.getX());
    }

    private static final class TestComponent extends UIComponent {
        private final int preferredWidth;
        private final int preferredHeight;

        private TestComponent(int preferredWidth, int preferredHeight) {
            this.preferredWidth = preferredWidth;
            this.preferredHeight = preferredHeight;
        }

        @Override public int preferredWidth(Font font) { return preferredWidth; }
        @Override public int preferredHeight(Font font) { return preferredHeight; }
        @Override public void render(GuiGraphics g, Font font, int mx, int my, float pt) {}
    }
}
