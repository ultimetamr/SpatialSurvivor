package com.example.spatialsurvivor.game

import android.util.Log
import com.example.spatialsurvivor.player.PlayerComponent
import com.example.spatialsurvivor.ui.SpatialOverlayVisibility
import com.example.spatialsurvivor.upgrade.UpgradeRuntime
import com.pico.spatial.core.ecs.Component
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.EntityQueryCondition
import com.pico.spatial.core.ecs.Scene
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3

class SpatialHudPanelComponent : Component() {
    var poseInitialized: Boolean = false

    fun reset() {
        poseInitialized = false
    }
}

/** Keeps the unified HUD visible below the player's view in Stage world coordinates. */
object SpatialHudGameplay {
    private val panelQuery =
        EntityQueryCondition.hasComponent(SpatialHudPanelComponent::class.java)
    private var didLogPanelsReady = false
    private var didLogViewTracking = false

    fun fixedUpdate(scene: Scene, deltaSeconds: Float, tracking: SpatialTrackingSnapshot) {
        val panels = scene.queryEntity(panelQuery)
        if (panels.isEmpty()) return
        if (!didLogPanelsReady) {
            didLogPanelsReady = true
            Log.i(TAG, "Spatial HUD ready: unified health and experience view panel attached")
        }

        val dimmedForOverlay =
            SpatialHudRules.shouldDimForOverlay(
                settlementVisible = GameSessionRuntime.settlementVisible,
                upgradeVisible = UpgradeRuntime.isVisible,
            )
        val hudVisible =
            SpatialOverlayVisibility.hudPanel(
                app = AppUiRuntime.state.value,
                session = GameSessionRuntime.state.value,
                upgradeVisible = UpgradeRuntime.isVisible,
            )
        panels.forEach { entity ->
            val panel = entity.components[SpatialHudPanelComponent::class.java] ?: return@forEach
            val transform = entity.components[TransformComponent::class.java] ?: return@forEach
            if (!hudVisible) {
                panel.reset()
                transform.setScaleVector(Vector3.ZERO)
                GameRuntime.publishHudPresentation(dimmedForOverlay = false)
                return@forEach
            }
            updateViewPanel(transform, panel, deltaSeconds, tracking)
            GameRuntime.publishHudPresentation(dimmedForOverlay)
        }
    }

    fun resetScene(scene: Scene) {
        scene.queryEntity(panelQuery).forEach { entity ->
            entity.components[SpatialHudPanelComponent::class.java]?.reset()
            entity.components[TransformComponent::class.java]?.apply {
                setPosition(HIDDEN_POSITION)
                setScaleVector(Vector3.ZERO)
            }
        }
        GameRuntime.publishHudPresentation(dimmedForOverlay = false)
    }

    fun resetDiagnostics() {
        didLogPanelsReady = false
        didLogViewTracking = false
    }

    private fun updateViewPanel(
        transform: TransformComponent,
        panel: SpatialHudPanelComponent,
        deltaSeconds: Float,
        tracking: SpatialTrackingSnapshot,
    ) {
        val hmdPose = tracking.hmd?.hmdPose?.takeIf { tracking.hasFreshHmdTracking }
        val headPosition = hmdPose?.position ?: DEFAULT_HEAD_POSITION
        // Spatial SDK Vector3.FORWARD is +Z (toward the user in Stage space).
        // The user's visual forward is local -Z, represented by Vector3.BACK.
        val rawForward = hmdPose?.rotation?.rotateVector(Vector3.BACK) ?: Vector3.BACK
        val placement =
            SpatialHudRules.viewHudPlacement(
                headX = headPosition.x,
                headY = headPosition.y,
                headZ = headPosition.z,
                rawForwardX = rawForward.x,
                rawForwardZ = rawForward.z,
            )
        val targetPosition = Vector3(placement.centerX, placement.centerY, placement.centerZ)
        val normalLerp = SpatialHudRules.viewHudFollowLerp(deltaSeconds)
        val lerp =
            if (
                SpatialHudRules.shouldRecenterViewHud(
                    headX = headPosition.x,
                    headZ = headPosition.z,
                    currentX = transform.position.x,
                    currentZ = transform.position.z,
                    desiredForwardX = placement.forwardX,
                    desiredForwardZ = placement.forwardZ,
                )
            ) {
                maxOf(normalLerp, SpatialHudRules.VIEW_HUD_RECENTER_LERP)
            } else {
                normalLerp
            }
        val nextPosition =
            if (panel.poseInitialized) {
                Vector3.lerp(transform.position, targetPosition, lerp)
            } else {
                panel.poseInitialized = true
                targetPosition
            }
        transform.setPosition(nextPosition)
        transform.setEulerAngles(EulerAngles(0f, placement.yawDegrees, 0f))
        transform.setScaleVector(Vector3.ONE)

        if (!didLogViewTracking) {
            didLogViewTracking = true
            Log.i(
                TAG,
                "View HUD synchronized in front of HMD: distance=1.2m, downAngle=15deg, " +
                    "viewDirection=(${placement.forwardX},${placement.forwardZ})",
            )
        }
    }

    private val HIDDEN_POSITION = Vector3(0f, -100f, 0f)
    private val DEFAULT_HEAD_POSITION = Vector3(0f, PlayerComponent.DEFAULT_HEAD_HEIGHT_METERS, 0f)
    private const val TAG = "SpatialHudGameplay"
}
