package com.example.spatialsurvivor.monster

import com.pico.spatial.core.ecs.EntityQueryCondition
import com.pico.spatial.core.ecs.Scene
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import kotlin.math.sin

/** Allocation-free pooled hit pulse plus a tightly capped gameplay hit-stop. */
object CombatFeedbackRuntime {
    private val monsterQuery =
        EntityQueryCondition.hasComponent(MonsterComponent::class.java)
    private var hitStopRemainingSeconds = 0f

    fun reset() {
        hitStopRemainingSeconds = 0f
    }

    fun requestHit(monster: MonsterComponent) {
        monster.hitFeedbackRemainingSeconds = MonsterComponent.HIT_FEEDBACK_DURATION_SECONDS
        hitStopRemainingSeconds = HIT_STOP_DURATION_SECONDS
    }

    fun consumeHitStop(deltaSeconds: Float): Boolean {
        if (hitStopRemainingSeconds <= 0f) return false
        hitStopRemainingSeconds =
            (hitStopRemainingSeconds - deltaSeconds.coerceAtLeast(0f)).coerceAtLeast(0f)
        return true
    }

    fun updateVisuals(scene: Scene, deltaSeconds: Float) {
        scene.queryEntity(monsterQuery).forEach { entity ->
            val monster = entity.components[MonsterComponent::class.java] ?: return@forEach
            if (!monster.active || monster.movementState == MonsterMovementState.DYING) return@forEach
            val visual = monster.visualChild
            if (monster.hitFeedbackRemainingSeconds <= 0f) {
                if (monster.hitFeedbackScaleApplied) {
                    monster.hitFeedbackScaleApplied = false
                    MonsterVisualRules.resetVisualScale(visual, monster)
                }
                return@forEach
            }
            monster.hitFeedbackRemainingSeconds =
                (monster.hitFeedbackRemainingSeconds - deltaSeconds.coerceAtLeast(0f))
                    .coerceAtLeast(0f)
            val normalized =
                1f -
                    monster.hitFeedbackRemainingSeconds /
                    MonsterComponent.HIT_FEEDBACK_DURATION_SECONDS
            val pulse = 1f + sin(normalized * Math.PI).toFloat() * HIT_SCALE_PULSE
            monster.hitFeedbackScaleApplied = true
            MonsterVisualRules.applyVisualPulse(visual, monster.visualScale, pulse)
            entity.components[TransformComponent::class.java]?.setScaleVector(Vector3.ONE)
        }
    }

    private const val HIT_STOP_DURATION_SECONDS = 0.05f
    private const val HIT_SCALE_PULSE = 0.16f
}
