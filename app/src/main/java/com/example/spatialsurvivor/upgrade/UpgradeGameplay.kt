package com.example.spatialsurvivor.upgrade

import android.util.Log
import com.example.spatialsurvivor.game.GameRuntime
import com.example.spatialsurvivor.game.GameSessionRuntime
import com.example.spatialsurvivor.game.SpatialTrackingSnapshot
import com.example.spatialsurvivor.game.WorldLockedPanelPlacementRules
import com.example.spatialsurvivor.game.WorldLockedPanelPose
import com.example.spatialsurvivor.game.WorldLockedPanelSupport
import com.example.spatialsurvivor.player.PlayerComponent
import com.pico.spatial.core.ecs.Component
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.EntityQueryCondition
import com.pico.spatial.core.ecs.Scene
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3

class UpgradePanelComponent : Component()

/**
 * Places the upgrade modal once in Stage/world space when it opens, then keeps that pose fixed.
 * Large turn / walk drift triggers a 0.3s recenter; there is no continuous head follow.
 */
object UpgradeGameplay {
    private val playerQuery = EntityQueryCondition.hasComponent(PlayerComponent::class.java)
    private val panelQuery = EntityQueryCondition.hasComponent(UpgradePanelComponent::class.java)
    private var boundPanel: Entity? = null
    private var freezePulseRemainingSeconds = 0f
    private val worldPose =
        WorldLockedPanelPose(WorldLockedPanelPlacementRules.UPGRADE_DISTANCE_METERS)

    val monstersFrozenByPulse: Boolean get() = freezePulseRemainingSeconds > 0f

    fun bindPanel(entity: Entity) {
        boundPanel = entity
        WorldLockedPanelSupport.detachCameraFollow(entity, "upgrade", TAG)
    }

    fun unbindPanel() {
        boundPanel = null
    }

    fun reset() {
        freezePulseRemainingSeconds = 0f
        worldPose.clear()
        UpgradeRuntime.reset()
    }

    fun fixedUpdate(scene: Scene, deltaSeconds: Float, tracking: SpatialTrackingSnapshot) {
        freezePulseRemainingSeconds = (freezePulseRemainingSeconds - deltaSeconds).coerceAtLeast(0f)
        val panelEntity = boundPanel ?: scene.queryEntity(panelQuery).firstOrNull()
        val transform = panelEntity?.components?.get(TransformComponent::class.java)
        if (panelEntity != null && transform != null) {
            updatePanel(panelEntity, transform, deltaSeconds, tracking)
        }

        val player =
            scene.queryEntity(playerQuery).firstOrNull()?.components?.get(PlayerComponent::class.java)
                ?: return
        UpgradeRuntime.processRequests(player)?.let { applied ->
            GameSessionRuntime.recordUpgrade(applied.id)
            if (player.freezePulseOwned) {
                freezePulseRemainingSeconds = FREEZE_PULSE_SECONDS
                val playerEntity = scene.queryEntity(playerQuery).firstOrNull()
                val pos = playerEntity?.components?.get(TransformComponent::class.java)?.position
                if (pos != null) {
                    SkillFxGameplay.request(
                        SkillFxSpawn(
                            kind = SkillVisualId.FREEZE_PULSE,
                            worldPosition = Vector3(pos.x, pos.y + 0.3f, pos.z),
                            durationSeconds = FREEZE_PULSE_SECONDS,
                        ),
                    )
                }
            }
            GameRuntime.showLevelUpFeedback(
                "获得：${applied.id.definitionTitle()} Lv.${applied.resultingStack}",
            )
            GameRuntime.publishPlayerState(player)
            LevelUpEffectGameplay.trigger(scene)
        }
    }

    fun resetScene(scene: Scene) {
        reset()
        scene.queryEntity(panelQuery).forEach { entity ->
            WorldLockedPanelSupport.detachCameraFollow(entity, "upgrade", TAG)
            entity.components[TransformComponent::class.java]?.apply {
                setPosition(HIDDEN)
                setScaleVector(Vector3.ZERO)
            }
        }
    }

    private fun updatePanel(
        panelEntity: Entity,
        transform: TransformComponent,
        deltaSeconds: Float,
        tracking: SpatialTrackingSnapshot,
    ) {
        if (!UpgradeRuntime.isVisible) {
            transform.setScaleVector(Vector3.ZERO)
            worldPose.clear()
            return
        }

        WorldLockedPanelSupport.detachCameraFollow(panelEntity, "upgrade", TAG)
        val tick = WorldLockedPanelSupport.tickPose(worldPose, deltaSeconds, tracking)
        if (tick.applyTransform) {
            WorldLockedPanelSupport.applyPose(transform, worldPose)
        }
        if (tick.justLocked) {
            Log.i(
                TAG,
                "Upgrade panel world-locked (idle writes disabled): " +
                    "distance=${WorldLockedPanelPlacementRules.UPGRADE_DISTANCE_METERS}m, " +
                    "pos=(${worldPose.x},${worldPose.y},${worldPose.z}), yaw=${worldPose.yawDegrees}",
            )
        }
        transform.setScaleVector(Vector3.ONE)
    }

    private fun UpgradeId.definitionTitle(): String = UpgradeCatalog.definition(this).title

    private val HIDDEN = Vector3(0f, -100f, 0f)
    private const val FREEZE_PULSE_SECONDS = 2f
    private const val TAG = "UpgradeGameplay"
}
