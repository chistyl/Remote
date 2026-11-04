package com.chi.tvremote.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbStreamIdAllocatorTest {
    @Test
    fun allocatesDistinctPositiveIdsAndReusesReleasedIdsAfterWrap() {
        val ids = AdbStreamIdAllocator(Int.MAX_VALUE)
        val last = ids.acquire()
        val first = ids.acquire()
        assertEquals(Int.MAX_VALUE, last)
        assertEquals(1, first)
        assertTrue(ids.isActive(last))
        assertTrue(ids.release(last))
        assertFalse(ids.isActive(last))
        assertEquals(2, ids.acquire())
    }

    @Test
    fun releasingUnknownIdDoesNotChangeActiveStreams() {
        val ids = AdbStreamIdAllocator()
        val allocated = ids.acquire()
        assertFalse(ids.release(allocated + 100))
        assertTrue(ids.isActive(allocated))
    }
}
