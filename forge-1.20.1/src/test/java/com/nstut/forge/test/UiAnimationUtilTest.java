package com.nstut.forge.test;

import com.nstut.economy.util.UiAnimationUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiAnimationUtilTest {
    @Test
    @DisplayName("Short row text stays fixed")
    void fittingTextDoesNotScroll() {
        assertEquals(0, UiAnimationUtil.pingPongOffset(40, 80, 10_000));
    }

    @Test
    @DisplayName("Overflowing row text pauses, scrolls to the end, and reverses")
    void longTextUsesPingPongMarquee() {
        assertEquals(0, UiAnimationUtil.pingPongOffset(180, 80, 0));
        assertEquals(0, UiAnimationUtil.pingPongOffset(180, 80, 650));
        assertEquals(50, UiAnimationUtil.pingPongOffset(180, 80, 2_650));
        assertEquals(100, UiAnimationUtil.pingPongOffset(180, 80, 4_650));
        assertEquals(100, UiAnimationUtil.pingPongOffset(180, 80, 5_300));
        assertEquals(50, UiAnimationUtil.pingPongOffset(180, 80, 7_300));
        assertEquals(0, UiAnimationUtil.pingPongOffset(180, 80, 9_300));
    }
}
