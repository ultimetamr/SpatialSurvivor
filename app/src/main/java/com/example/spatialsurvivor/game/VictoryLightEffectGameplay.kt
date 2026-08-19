package com.example.spatialsurvivor.game

import com.pico.spatial.core.ecs.Component
import com.pico.spatial.core.ecs.EntityQueryCondition
import com.pico.spatial.core.ecs.Scene
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3

class VictoryLightEffectComponent : Component() {
    var active = false
    var elapsedSeconds = 0f
}

object VictoryLightEffectGameplay {
    private val query = EntityQueryCondition.hasComponent(VictoryLightEffectComponent::class.java)

    fun trigger(scene: Scene) {
        scene.queryEntity(query).forEach { entity ->
            entity.components[VictoryLightEffectComponent::class.java]?.apply {
                active = true
                elapsedSeconds = 0f
            }
            entity.components[TransformComponent::class.java]?.setScaleVector(INITIAL_SCALE)
        }
    }

    fun fixedUpdate(scene: Scene, deltaSeconds: Float) {
        scene.queryEntity(query).forEach { entity ->
            val effect = entity.components[VictoryLightEffectComponent::class.java] ?: return@forEach
            if (!effect.active) return@forEach
            effect.elapsedSeconds += deltaSeconds
            val progress = (effect.elapsedSeconds / DURATION_SECONDS).coerceIn(0f, 1f)
            val scale = 1f + progress * MAX_SCALE_DELTA
            entity.components[TransformComponent::class.java]?.setScaleVector(Vector3(scale, scale, scale))
            if (progress >= 1f) {
                effect.active = false
                entity.components[TransformComponent::class.java]?.setScaleVector(Vector3.ZERO)
            }
        }
    }

    fun resetScene(scene: Scene) {
        scene.queryEntity(query).forEach { entity ->
            entity.components[VictoryLightEffectComponent::class.java]?.apply {
                active = false
                elapsedSeconds = 0f
            }
            entity.components[TransformComponent::class.java]?.setScaleVector(Vector3.ZERO)
        }
    }

    private val INITIAL_SCALE = Vector3.ONE
    private const val MAX_SCALE_DELTA = 5f
    private const val DURATION_SECONDS = 1f
}
