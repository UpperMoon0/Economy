package com.nstut.economy.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortfolioRefreshGateTest {
    @Test void boundsRequestsWithoutScanningOrSharingSessions() {
        PortfolioRefreshGate first = new PortfolioRefreshGate();
        PortfolioRefreshGate second = new PortfolioRefreshGate();
        assertTrue(first.tryAcquire(0));
        for (int millis = 1; millis < 1000; millis++) assertFalse(first.tryAcquire(millis));
        assertTrue(second.tryAcquire(5));
        assertTrue(first.tryAcquire(1000));
        assertFalse(first.tryAcquire(1001));
        assertTrue(first.tryAcquire(2000));
    }
}
