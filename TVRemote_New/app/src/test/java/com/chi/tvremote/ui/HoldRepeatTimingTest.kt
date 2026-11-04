package com.chi.tvremote.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class HoldRepeatTimingTest {
    @Test fun initialDelayIsLongerThanRepeatInterval() {
        assertTrue(HoldRepeatTiming.INITIAL_DELAY_MS > HoldRepeatTiming.INTERVAL_MS)
    }

    @Test fun repeatRateIsBoundedForRemoteInput() {
        assertTrue(HoldRepeatTiming.INTERVAL_MS in 80L..180L)
        assertTrue(HoldRepeatTiming.INITIAL_DELAY_MS in 300L..700L)
    }
}
