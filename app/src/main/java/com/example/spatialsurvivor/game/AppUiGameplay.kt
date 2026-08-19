package com.example.spatialsurvivor.game

import android.util.Log
import com.pico.spatial.core.ecs.Component
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.EntityQueryCondition
import com.pico.spatial.core.ecs.Scene
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3

class MainMenuPanelComponent : Component()

class PermanentProgressionPanelComponent : Component()

class PausePanelComponent : Component()

/**
 * Single-instance world-locked AttachmentPanels.
 * Transform writes only on lock / recenter — idle frames never touch position/rotation.
 * Permanent is exclusive: back layers hide completely (no stacked glass z-fight).
 */
object AppUiGameplay {
    private val mainMenuQuery = EntityQueryCondition.hasComponent(MainMenuPanelComponent::class.java)
    private val permanentPanelQuery =
        EntityQueryCondition.hasComponent(PermanentProgressionPanelComponent::class.java)
    private val pausePanelQuery = EntityQueryCondition.hasComponent(PausePanelComponent::class.java)

    private var boundMainMenuPanel: Entity? = null
    private var boundPermanentPanel: Entity? = null
    private var boundPausePanel: Entity? = null

    private val mainMenuPose =
        WorldLockedPanelPose(WorldLockedPanelPlacementRules.OVERLAY_DISTANCE_METERS)
    private val permanentPose =
        WorldLockedPanelPose(WorldLockedPanelPlacementRules.OVERLAY_DISTANCE_METERS)
    private val pausePose =
        WorldLockedPanelPose(WorldLockedPanelPlacementRules.OVERLAY_DISTANCE_METERS)

    private var mainMenuScaleShown = false
    private var permanentScaleShown = false
    private var pauseScaleShown = false

    fun bindMainMenuPanel(entity: Entity) {
        boundMainMenuPanel = entity
        WorldLockedPanelSupport.detachCameraFollow(entity, "main menu", TAG)
    }

    fun bindPermanentPanel(entity: Entity) {
        boundPermanentPanel = entity
        WorldLockedPanelSupport.detachCameraFollow(entity, "permanent progression", TAG)
    }

    fun bindPausePanel(entity: Entity) {
        boundPausePanel = entity
        WorldLockedPanelSupport.detachCameraFollow(entity, "pause", TAG)
    }

    fun unbindPanels() {
        boundMainMenuPanel = null
        boundPermanentPanel = null
        boundPausePanel = null
    }

    fun fixedUpdate(scene: Scene, deltaSeconds: Float, tracking: SpatialTrackingSnapshot) {
        updateLockedPanel(
            entity = boundMainMenuPanel ?: scene.queryEntity(mainMenuQuery).firstOrNull(),
            pose = mainMenuPose,
            visible = AppUiRuntime.mainMenuVisible && !AppUiRuntime.permanentPanelVisible,
            deltaSeconds = deltaSeconds,
            tracking = tracking,
            label = "main menu",
            scaleShown = { mainMenuScaleShown },
            setScaleShown = { mainMenuScaleShown = it },
        )
        updateLockedPanel(
            entity = boundPermanentPanel ?: scene.queryEntity(permanentPanelQuery).firstOrNull(),
            pose = permanentPose,
            visible = AppUiRuntime.permanentPanelVisible,
            deltaSeconds = deltaSeconds,
            tracking = tracking,
            label = "permanent progression",
            scaleShown = { permanentScaleShown },
            setScaleShown = { permanentScaleShown = it },
        )
        updateLockedPanel(
            entity = boundPausePanel ?: scene.queryEntity(pausePanelQuery).firstOrNull(),
            pose = pausePose,
            visible = AppUiRuntime.pausePanelVisible && !AppUiRuntime.permanentPanelVisible,
            deltaSeconds = deltaSeconds,
            tracking = tracking,
            label = "pause",
            scaleShown = { pauseScaleShown },
            setScaleShown = { pauseScaleShown = it },
        )
    }

    fun resetScene(scene: Scene) {
        mainMenuPose.clear()
        permanentPose.clear()
        pausePose.clear()
        mainMenuScaleShown = false
        permanentScaleShown = false
        pauseScaleShown = false
        scene.queryEntity(mainMenuQuery).forEach(::resetAndHide)
        scene.queryEntity(permanentPanelQuery).forEach(::resetAndHide)
        scene.queryEntity(pausePanelQuery).forEach(::resetAndHide)
    }

    private fun updateLockedPanel(
        entity: Entity?,
        pose: WorldLockedPanelPose,
        visible: Boolean,
        deltaSeconds: Float,
        tracking: SpatialTrackingSnapshot,
        label: String,
        scaleShown: () -> Boolean,
        setScaleShown: (Boolean) -> Unit,
    ) {
        val transform = entity?.components?.get(TransformComponent::class.java) ?: return
        if (!visible) {
            transform.setScaleVector(Vector3.ZERO)
            pose.clear()
            setScaleShown(false)
            return
        }

        WorldLockedPanelSupport.detachCameraFollow(entity, label, TAG)
        val tick = WorldLockedPanelSupport.tickPose(pose, deltaSeconds, tracking)
        if (tick.applyTransform) {
            WorldLockedPanelSupport.applyPose(transform, pose)
        }
        if (tick.justLocked) {
            Log.i(
                TAG,
                "$label panel world-locked (idle writes disabled): " +
                    "pos=(${pose.x},${pose.y},${pose.z}), yaw=${pose.yawDegrees}",
            )
        }
        // Keep ECS hit targets aligned with visible Compose UI while pose settles.
        transform.setScaleVector(Vector3.ONE)
        setScaleShown(true)
    }

    private fun resetAndHide(entity: Entity) {
        WorldLockedPanelSupport.detachCameraFollow(entity, "reset", TAG)
        entity.components[TransformComponent::class.java]?.apply {
            setPosition(HIDDEN_POSITION)
            setScaleVector(Vector3.ZERO)
        }
    }

    private val HIDDEN_POSITION = Vector3(0f, -100f, 0f)
    private const val TAG = "AppUiGameplay"
}
