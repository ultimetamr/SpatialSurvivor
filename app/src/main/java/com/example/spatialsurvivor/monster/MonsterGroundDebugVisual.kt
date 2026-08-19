package com.example.spatialsurvivor.monster

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3

/** Optional red foot marker when [MonsterGroundingRules.DEBUG_GROUND_MARKERS] is enabled. */
object MonsterGroundDebugVisual {
    fun ensureMarker(poolEntity: Entity, monster: MonsterComponent) {
        if (!MonsterGroundingRules.DEBUG_GROUND_MARKERS) return
        if (monster.groundDebugMarker != null) return
        val material =
            UnlitMaterial.create(BlendingMode.OPAQUE).apply {
                setBaseColor(Color4(1f, 0.08f, 0.08f, 0.85f))
            }
        val marker =
            ModelEntity(
                MeshResource.createCylinder(radius = 0.14f, height = 0.004f),
                material,
            ).apply {
                setName("${monster.monsterType.name}_GroundMarker")
                components[TransformComponent::class.java]?.apply {
                    setPosition(
                        Vector3(0f, MonsterGroundingRules.groundMarkerLocalY(monster.monsterType), 0f),
                    )
                    setEulerAngles(EulerAngles(0f, 0f, 0f))
                    setScaleVector(Vector3.ONE)
                }
            }
        poolEntity.addChild(marker)
        monster.groundDebugMarker = marker
    }

    fun hideMarker(monster: MonsterComponent) {
        monster.groundDebugMarker?.components?.get(TransformComponent::class.java)
            ?.setScaleVector(Vector3.ZERO)
    }
}
