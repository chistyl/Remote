package com.chi.tvremote.ui

/** Converts touchpad travel into repeated D-pad steps while preserving sub-threshold motion. */
class TouchpadMotion {
    private var accumulatedX = 0f
    private var accumulatedY = 0f

    /**
     * Returns one key code for every complete threshold crossed. A per-event cap prevents a
     * single large pointer event from flooding the remote socket; remaining distance is kept.
     */
    fun consume(deltaX: Float, deltaY: Float, thresholdPx: Float,
                left: Int, right: Int, up: Int, down: Int,
                maxStepsPerAxis: Int = 6): List<Int> {
        require(thresholdPx > 0f) { "thresholdPx must be positive" }
        require(maxStepsPerAxis > 0) { "maxStepsPerAxis must be positive" }
        accumulatedX += deltaX
        accumulatedY += deltaY
        val result = ArrayList<Int>()
        var xSteps = 0
        while (kotlin.math.abs(accumulatedX) >= thresholdPx && xSteps < maxStepsPerAxis) {
            if (accumulatedX > 0f) {
                result += right
                accumulatedX -= thresholdPx
            } else {
                result += left
                accumulatedX += thresholdPx
            }
            xSteps++
        }
        var ySteps = 0
        while (kotlin.math.abs(accumulatedY) >= thresholdPx && ySteps < maxStepsPerAxis) {
            if (accumulatedY > 0f) {
                result += down
                accumulatedY -= thresholdPx
            } else {
                result += up
                accumulatedY += thresholdPx
            }
            ySteps++
        }
        return result
    }

    fun reset() {
        accumulatedX = 0f
        accumulatedY = 0f
    }
}
