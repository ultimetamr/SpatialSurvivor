package com.example.spatialsurvivor.upgrade

import com.pico.spatial.core.ecs.Component
import com.pico.spatial.core.ecs.EntityQueryCondition
import com.pico.spatial.core.ecs.Scene
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3

class LevelUpRingComponent : Component() {
    var elapsedSeconds = 0f
    var active = false
}

/** A pooled expanding ring, attached to the player so its world position always follows tracked walking. */
object LevelUpEffectGameplay {
    private val ringQuery = EntityQueryCondition.hasComponent(LevelUpRingComponent::class.java)

    fun trigger(scene: Scene) {
        scene.queryEntity(ringQuery).firstOrNull()?.let { entity ->
            entity.components[LevelUpRingComponent::class.java]?.apply {
                elapsedSeconds = 0f
                active = true
            }
            entity.components[TransformComponent::class.java]?.setScaleVector(Vector3.ONE)
        }
    }

    fun fixedUpdate(scene: Scene, deltaSeconds: Float) {
        scene.queryEntity(ringQuery).forEach { entity ->
            val effect = entity.components[LevelUpRingComponent::class.java] ?: return@forEach
            if (!effect.active) return@forEach
            effect.elapsedSeconds += deltaSeconds
            val progress = (effect.elapsedSeconds / DURATION_SECONDS).coerceIn(0f, 1f)
            entity.components[TransformComponent::class.java]?.setScaleVector(
                Vector3(1f + progress * MAX_SCALE, 1f + progress * MAX_SCALE, 1f + progress * MAX_SCALE),
            )
            if (progress >= 1f) {
                effect.active = false
                entity.components[TransformComponent::class.java]?.setScaleVector(Vector3.ZERO)
            }
        }
    }

    private const val DURATION_SECONDS = 0.55f
    private const val MAX_SCALE = 3.5f
}
