package com.thanawat.flakesince.detection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FailureRateSignalTest {

    FlakyDetector detector = new FlakyDetector(null, null);

    @Test
    void flakyWhenSomeRunsFail() {
        assertTrue(detector.failureRateSignal(new FlakyDetector.FailureWindow(30, 6)));
    }

    @Test
    void notFlakyWhenNeverFails() {
        assertFalse(detector.failureRateSignal(new FlakyDetector.FailureWindow(30, 0)));
    }

    @Test
    void notFlakyWhenAlwaysFails() {
        assertFalse(detector.failureRateSignal(new FlakyDetector.FailureWindow(30, 30)));
    }

    @Test
    void notJudgedWithTooFewRuns() {
        assertFalse(detector.failureRateSignal(new FlakyDetector.FailureWindow(5, 3)));
    }

    @Test
    void scoreIsTheShareOfSignals() {
        assertEquals(0.0, FlakyDetector.score(false, false));
        assertEquals(0.5, FlakyDetector.score(true, false));
        assertEquals(0.5, FlakyDetector.score(false, true));
        assertEquals(1.0, FlakyDetector.score(true, true));
    }
}