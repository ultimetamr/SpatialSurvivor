package com.example.spatialsurvivor.game

/** Converts render-frame deltas into deterministic simulation ticks. */
class FixedStepClock(
    val fixedStepSeconds: Double = DEFAULT_FIXED_STEP_SECONDS,
    private val maxFrameDeltaSeconds: Double = DEFAULT_MAX_FRAME_DELTA_SECONDS,
    private val maxStepsPerFrame: Int = DEFAULT_MAX_STEPS_PER_FRAME,
) {
    private var accumulatorSeconds = 0.0

    var tick: Long = 0L
        private set

    init {
        require(fixedStepSeconds > 0f)
        require(maxFrameDeltaSeconds >= fixedStepSeconds)
        require(maxStepsPerFrame > 0)
    }

    fun advance(frameDeltaSeconds: Float, fixedUpdate: (Float) -> Unit): Int {
        accumulatorSeconds += frameDeltaSeconds.toDouble().coerceIn(0.0, maxFrameDeltaSeconds)

        var completedSteps = 0
        while (
            accumulatorSeconds + FLOAT_COMPARISON_EPSILON >= fixedStepSeconds &&
                completedSteps < maxStepsPerFrame
        ) {
            fixedUpdate(fixedStepSeconds.toFloat())
            accumulatorSeconds = (accumulatorSeconds - fixedStepSeconds).coerceAtLeast(0.0)
            tick += 1
            completedSteps += 1
        }

        // Drop excess backlog after a long stall to avoid a catch-up spiral.
        if (completedSteps == maxStepsPerFrame && accumulatorSeconds >= fixedStepSeconds) {
            accumulatorSeconds %= fixedStepSeconds
        }
        return completedSteps
    }

    fun discardAccumulator() {
        accumulatorSeconds = 0.0
    }

    companion object {
        const val TARGET_TICKS_PER_SECOND = 90
        const val DEFAULT_FIXED_STEP_SECONDS = 1.0 / TARGET_TICKS_PER_SECOND
        const val DEFAULT_MAX_FRAME_DELTA_SECONDS = 0.25
        const val DEFAULT_MAX_STEPS_PER_FRAME = 4
        private const val FLOAT_COMPARISON_EPSILON = 0.000000001
    }
}
