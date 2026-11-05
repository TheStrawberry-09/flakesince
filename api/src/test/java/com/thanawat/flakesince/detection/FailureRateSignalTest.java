package com.thanawat.flakesince.detection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FailureRateSignalTest {

    FlakyDetector detector = new FlakyDetector(null);

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
}