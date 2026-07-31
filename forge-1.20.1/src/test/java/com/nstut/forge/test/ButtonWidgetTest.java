package com.nstut.forge.test;

import com.nstut.economy.ui.framework.ButtonWidget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ButtonWidgetTest {
    @Test
    void twoLineLabelsRequestEnoughVerticalSpace() {
        ButtonWidget button = new ButtonWidget("Grid", 0, 0, 0);
        assertEquals(14, button.preferredHeight(null));

        button.setLabel("Activity\nAll");
        assertEquals(22, button.preferredHeight(null));
    }
}
