package com.example.spatialsurvivor.game

import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.controller.ControllerActionData
import com.pico.spatial.tracking.controller.ControllerTrackingData
import com.pico.spatial.tracking.controller.ControllerTrackingProvider
import com.pico.spatial.tracking.eye.EyeTrackingData
import com.pico.spatial.tracking.eye.EyeTrackingProvider
import com.pico.spatial.tracking.hand.HandTrackingData
import com.pico.spatial.tracking.hand.HandTrackingProvider
import com.pico.spatial.tracking.hmd.HMDTrackingData
import com.pico.spatial.tracking.hmd.HMDTrackingProvider

/** Full Space tracking providers sampled by the deterministic game loop. */
object SpatialTrackingRuntime {
    private var hmdProvider: HMDTrackingProvider? = null
    private var handProvider: HandTrackingProvider? = null
    private var eyeProvider: EyeTrackingProvider? = null
    private var controllerProvider: ControllerTrackingProvider? = null

    @Volatile
    var latestHmd: HMDTrackingData? = null
        private set
    @Volatile
    var latestHands: HandTrackingData? = null
        private set
    @Volatile
    var latestEyes: EyeTrackingData? = null
        private set
    @Volatile
    var latestControllers: ControllerTrackingData? = null
        private set
    @Volatile
    var latestControllerActions: ControllerActionData? = null
        private set

    @Volatile
    private var latestHmdSampleNanos: Long = 0L

    private val hmdListener =
        DataProvider.DataListener<HMDTrackingData> {
            latestHmd = it
            latestHmdSampleNanos = System.nanoTime()
        }
    private val handListener = DataProvider.DataListener<HandTrackingData> { latestHands = it }
    private val eyeListener = DataProvider.DataListener<EyeTrackingData> { latestEyes = it }
    private val controllerListener =
        DataProvider.DataListener<ControllerTrackingData> { latestControllers = it }
    private val controllerActionListener =
        object : ControllerTrackingProvider.ControllerActionListener {
            override fun onControllerAction(data: ControllerActionData) {
                latestControllerActions = data
            }
        }

    fun bind(
        hmd: HMDTrackingProvider,
        hands: HandTrackingProvider,
        eyes: EyeTrackingProvider,
        controllers: ControllerTrackingProvider,
    ) {
        hmdProvider = hmd
        handProvider = hands
        eyeProvider = eyes
        controllerProvider = controllers
    }

    fun start(): TrackingStartReport {
        hmdProvider?.addListener(hmdListener)
        handProvider?.addListener(handListener)
        eyeProvider?.addListener(eyeListener)
        controllerProvider?.addListener(controllerListener)
        controllerProvider?.addControllerActionListener(controllerActionListener)
        return TrackingStartReport(
            hmd = hmdProvider?.start(),
            hands = handProvider?.start(),
            eyes = eyeProvider?.start(),
            controllers = controllerProvider?.start(),
        )
    }

    fun snapshot(): SpatialTrackingSnapshot {
        val sampleNanos = latestHmdSampleNanos
        val sampleAgeSeconds =
            if (sampleNanos == 0L) {
                Float.POSITIVE_INFINITY
            } else {
                ((System.nanoTime() - sampleNanos).coerceAtLeast(0L) / NANOS_PER_SECOND)
                    .toFloat()
            }
        return SpatialTrackingSnapshot(
            hmd = latestHmd,
            hands = latestHands,
            eyes = latestEyes,
            controllers = latestControllers,
            controllerActions = latestControllerActions,
            hmdSampleAgeSeconds = sampleAgeSeconds,
        )
    }

    fun stop() {
        hmdProvider?.removeListener(hmdListener)
        handProvider?.removeListener(handListener)
        eyeProvider?.removeListener(eyeListener)
        controllerProvider?.removeListener(controllerListener)
        controllerProvider?.removeControllerActionListener(controllerActionListener)
        hmdProvider?.stop()
        handProvider?.stop()
        eyeProvider?.stop()
        controllerProvider?.stop()
        hmdProvider = null
        handProvider = null
        eyeProvider = null
        controllerProvider = null
        latestHmd = null
        latestHands = null
        latestEyes = null
        latestControllers = null
        latestControllerActions = null
        latestHmdSampleNanos = 0L
    }

    private const val NANOS_PER_SECOND = 1_000_000_000.0
}

data class TrackingStartReport(
    val hmd: DataProvider.StartResult?,
    val hands: DataProvider.StartResult?,
    val eyes: DataProvider.StartResult?,
    val controllers: DataProvider.StartResult?,
)

data class SpatialTrackingSnapshot(
    val hmd: HMDTrackingData? = null,
    val hands: HandTrackingData? = null,
    val eyes: EyeTrackingData? = null,
    val controllers: ControllerTrackingData? = null,
    val controllerActions: ControllerActionData? = null,
    val hmdSampleAgeSeconds: Float = Float.POSITIVE_INFINITY,
) {
    val hasFreshHmdTracking: Boolean
        get() =
            TrackingContinuityRules.isFreshHmdSample(
                hmdPresent = hmd != null,
                sampleAgeSeconds = hmdSampleAgeSeconds,
            )
}
