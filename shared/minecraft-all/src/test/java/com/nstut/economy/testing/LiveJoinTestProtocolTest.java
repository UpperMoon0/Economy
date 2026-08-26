package com.nstut.economy.testing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveJoinTestProtocolTest {
    @AfterEach
    void reset() {
        System.clearProperty(LiveJoinTestProtocol.SYSTEM_PROPERTY);
        LiveJoinTestProtocol.reset();
    }

    @Test
    void modeIsStrictlyOptIn() {
        assertFalse(LiveJoinTestProtocol.isEnabled());
        System.setProperty(LiveJoinTestProtocol.SYSTEM_PROPERTY, "true");
        assertTrue(LiveJoinTestProtocol.isEnabled());
    }

    @Test
    void passMarkerCanOnlyBeReportedOnce() {
        assertTrue(LiveJoinTestProtocol.markReported());
        assertFalse(LiveJoinTestProtocol.markReported());
    }
}
