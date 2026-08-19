package com.example.spatialsurvivor.game

import android.util.Log

enum class TrackingContinuityStatus {
    PAUSED,
    RECOVERING,
    RUNNING,
}

data class TrackingContinuityState(
    val status: TrackingContinuityStatus = TrackingContinuityStatus.PAUSED,
    val recoverySeconds: Float = 0f,
) {
    val gameplayPaused: Boolean
        get() = status != TrackingContinuityStatus.RUNNING
}

object TrackingContinuityRules {
    fun isFreshHmdSample(hmdPresent: Boolean, sampleAgeSeconds: Float): Boolean =
        hmdPresent && sampleAgeSeconds in 0f..HMD_LOSS_TIMEOUT_SECONDS

    fun next(
        current: TrackingContinuityState,
        freshHmdSample: Boolean,
        deltaSeconds: Float,
    ): TrackingContinuityState {
        if (!freshHmdSample) return TrackingContinuityState(TrackingContinuityStatus.PAUSED)
        if (current.status == TrackingContinuityStatus.RUNNING) return current
        val recoveredFor = current.recoverySeconds + deltaSeconds.coerceAtLeast(0f)
        return if (recoveredFor >= RECOVERY_STABILITY_SECONDS) {
            TrackingContinuityState(TrackingContinuityStatus.RUNNING)
        } else {
            TrackingContinuityState(TrackingContinuityStatus.RECOVERING, recoveredFor)
        }
    }

    const val HMD_LOSS_TIMEOUT_SECONDS = 0.35f
    const val RECOVERY_STABILITY_SECONDS = 0.15f
}

/** Session state machine that pauses gameplay time until HMD tracking is stable again. */
object TrackingPauseRuntime {
    private var state = TrackingContinuityState()

    val gameplayPaused: Boolean
        get() = state.gameplayPaused

    fun reset() {
        state = TrackingContinuityState()
    }

    fun update(snapshot: SpatialTrackingSnapshot, deltaSeconds: Float): Boolean {
        val previous = state
        state =
            TrackingContinuityRules.next(
                current = state,
                freshHmdSample = snapshot.hasFreshHmdTracking,
                deltaSeconds = deltaSeconds,
            )
        if (previous.status != state.status) {
            when (state.status) {
                TrackingContinuityStatus.PAUSED ->
                    Log.w(TAG, "Spatial tracking lost; gameplay time and motion paused")
                TrackingContinuityStatus.RECOVERING ->
                    Log.i(TAG, "Spatial tracking sample restored; validating stability")
                TrackingContinuityStatus.RUNNING ->
                    Log.i(TAG, "Spatial tracking stable; gameplay resumed")
            }
        }
        return state.gameplayPaused
    }

    private const val TAG = "TrackingPauseRuntime"
}
