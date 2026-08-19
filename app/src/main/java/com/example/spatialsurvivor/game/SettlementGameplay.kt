package com.example.spatialsurvivor.game

import android.util.Log
import com.pico.spatial.core.ecs.Component
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.EntityQueryCondition
import com.pico.spatial.core.ecs.Scene
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3

class SettlementPanelComponent : Component()

/**
 * Settlement panel: one-shot world lock + FOV-loss recenter only.
 * Idle frames never rewrite position/rotation.
 */
object SettlementGameplay {
    private val panelQuery =
        EntityQueryCondition.hasComponent(SettlementPanelComponent::class.java)
    private var boundPanelEntity: Entity? = null
    private val worldPose =
        WorldLockedPanelPose(WorldLockedPanelPlacementRules.OVERLAY_DISTANCE_METERS)
    private var scaleShown = false

    fun bindPanel(entity: Entity) {
        boundPanelEntity = entity
        WorldLockedPanelSupport.detachCameraFollow(entity, "settlement", TAG)
    }

    fun unbindPanel() {
        boundPanelEntity = null
    }

    fun fixedUpdate(scene: Scene, deltaSeconds: Float, tracking: SpatialTrackingSnapshot) {
        val panelEntity =
            boundPanelEntity?.takeIf {
                it.components[SettlementPanelComponent::class.java] != null &&
                    it.components[TransformComponent::class.java] != null
            } ?: scene.queryEntity(panelQuery).firstOrNull() ?: return
        val transform = panelEntity.components[TransformComponent::class.java] ?: return

        if (!GameSessionRuntime.settlementBlockingActive) {
            hideAndUnlock(transform)
            return
        }

        WorldLockedPanelSupport.detachCameraFollow(panelEntity, "settlement", TAG)

        val coveredByPermanent =
            AppUiRuntime.permanentPanelVisible &&
                AppUiRuntime.permanentPanelOrigin == PermanentPanelOrigin.SETTLEMENT
        val showPanel =
            GameSessionRuntime.settlementVisible &&
                !AppUiRuntime.pausePanelVisible &&
                !coveredByPermanent

        if (!worldPose.isLocked) {
            val tick = WorldLockedPanelSupport.tickPose(worldPose, deltaSeconds, tracking)
            if (tick.justLocked) {
                Log.i(
                    TAG,
                    "Settlement panel world-locked at trigger (idle writes disabled): " +
                        "distance=${WorldLockedPanelPlacementRules.OVERLAY_DISTANCE_METERS}m, " +
                        "pos=(${worldPose.x},${worldPose.y},${worldPose.z}), yaw=${worldPose.yawDegrees}",
                )
            }
            if (tick.applyTransform) {
                WorldLockedPanelSupport.applyPose(transform, worldPose)
            }
        } else if (showPanel || coveredByPermanent) {
            val tick = WorldLockedPanelSupport.tickPose(worldPose, deltaSeconds, tracking)
            if (tick.applyTransform) {
                WorldLockedPanelSupport.applyPose(transform, worldPose)
            }
        }

        if (!showPanel) {
            transform.setScaleVector(Vector3.ZERO)
            scaleShown = false
            return
        }

        transform.setScaleVector(Vector3.ONE)
        scaleShown = true

        if (GameSessionRuntime.consumeRestartRequest()) {
            hideAndUnlock(transform)
            GameRuntime.restart(scene)
        }
    }

    fun resetScene(scene: Scene) {
        worldPose.clear()
        scaleShown = false
        scene.queryEntity(panelQuery).forEach { entity ->
            WorldLockedPanelSupport.detachCameraFollow(entity, "settlement", TAG)
            entity.components[TransformComponent::class.java]?.apply {
                setPosition(HIDDEN_POSITION)
                setScaleVector(Vector3.ZERO)
            }
        }
    }

    private fun hideAndUnlock(transform: TransformComponent) {
        transform.setScaleVector(Vector3.ZERO)
        worldPose.clear()
        scaleShown = false
    }

    private val HIDDEN_POSITION = Vector3(0f, -100f, 0f)
    private const val TAG = "SettlementGameplay"
}
