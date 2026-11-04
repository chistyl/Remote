package com.chi.tvremote.protocol

import com.chi.tvremote.ui.TouchpadMotion
import org.junit.Assert.assertEquals
import org.junit.Test

class TouchpadMotionTest {
    private fun motion() = TouchpadMotion()

    @Test
    fun accumulatesSmallMovesUntilOneStepIsReached() {
        val pad = motion()
        assertEquals(emptyList<Int>(), pad.consume(4f, 0f, 10f, 21, 22, 19, 20))
        assertEquals(listOf(22), pad.consume(7f, 0f, 10f, 21, 22, 19, 20))
    }

    @Test
    fun longSwipeProducesMultipleStepsAndKeepsRemainder() {
        val pad = motion()
        assertEquals(listOf(22, 22, 22), pad.consume(35f, 0f, 10f, 21, 22, 19, 20))
        assertEquals(listOf(22), pad.consume(5f, 0f, 10f, 21, 22, 19, 20))
    }

    @Test
    fun diagonalSwipeMovesBothAxes() {
        val pad = motion()
        assertEquals(listOf(22, 20), pad.consume(12f, 11f, 10f, 21, 22, 19, 20))
    }

    @Test
    fun largePointerDeltaIsCappedPerEventWithoutDroppingDistance() {
        val pad = motion()
        assertEquals(List(6) { 22 }, pad.consume(100f, 0f, 10f, 21, 22, 19, 20))
        assertEquals(List(4) { 22 }, pad.consume(0f, 0f, 10f, 21, 22, 19, 20))
    }

    @Test
    fun resetClearsAccumulatedDistance() {
        val pad = motion()
        pad.consume(9f, 0f, 10f, 21, 22, 19, 20)
        pad.reset()
        assertEquals(emptyList<Int>(), pad.consume(2f, 0f, 10f, 21, 22, 19, 20))
    }
}
