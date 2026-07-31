package com.nstut.economy.util;

public final class UiAnimationUtil {
    public static final long MARQUEE_PAUSE_MILLIS = 650L;
    public static final float MARQUEE_PIXELS_PER_MILLI = 0.025f;

    private UiAnimationUtil() {}

    public static int pingPongOffset(int textWidth, int viewportWidth, long elapsedMillis) {
        int overflow = Math.max(0, textWidth - viewportWidth);
        if (overflow == 0) return 0;

        long travelMillis = Math.max(1L, (long) Math.ceil(overflow / MARQUEE_PIXELS_PER_MILLI));
        long halfCycle = MARQUEE_PAUSE_MILLIS + travelMillis;
        long cycleMillis = halfCycle * 2L;
        long phase = Math.floorMod(elapsedMillis, cycleMillis);

        float offset;
        if (phase < MARQUEE_PAUSE_MILLIS) {
            offset = 0.0f;
        } else if (phase < halfCycle) {
            offset = Math.min(overflow,
                    (phase - MARQUEE_PAUSE_MILLIS) * MARQUEE_PIXELS_PER_MILLI);
        } else if (phase < halfCycle + MARQUEE_PAUSE_MILLIS) {
            offset = overflow;
        } else {
            offset = Math.max(0.0f, overflow
                    - (phase - halfCycle - MARQUEE_PAUSE_MILLIS) * MARQUEE_PIXELS_PER_MILLI);
        }
        return Math.round(offset);
    }
}
