package com.example.spatialsurvivor.monster

import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3

/**
 * Per-archetype GLB assets and visual tuning.
 * Gameplay root stays at scale 1; all model scaling lives on the visual child so Y locking
 * is not distorted by tiny GLB correction scales.
 */
object MonsterVisualRules {
    private const val TAG = "MonsterVisualRules"
    private const val MODEL_UNIT_CORRECTION = 0.01f

    fun assetUri(type: MonsterType): String? =
        when (type) {
            MonsterType.NORMAL_BUG -> "asset://models/monster_bug.glb"
            MonsterType.RUNNER -> "asset://models/monster_runner.glb"
            MonsterType.ARMORED -> "asset://models/monster_tank.glb"
            MonsterType.FINAL_BOSS -> "asset://models/boss_final.glb"
            MonsterType.CEILING_DROPPER -> null
        }

    fun visualScale(type: MonsterType): Float =
        when (type) {
            MonsterType.NORMAL_BUG -> 0.38f * MODEL_UNIT_CORRECTION
            MonsterType.RUNNER -> 0.48f * MODEL_UNIT_CORRECTION
            MonsterType.ARMORED -> 0.72f * MODEL_UNIT_CORRECTION
            MonsterType.CEILING_DROPPER -> 1.0f * MODEL_UNIT_CORRECTION
            MonsterType.FINAL_BOSS -> 1.35f * MODEL_UNIT_CORRECTION
        }

    fun scaleVector(type: MonsterType): Vector3 {
        val s = visualScale(type)
        return Vector3(s, s, s)
    }

    fun scaleVector(monster: MonsterComponent): Vector3 {
        val s = monster.visualScale
        return Vector3(s, s, s)
    }

    /** Gameplay root always stays unit scale; visuals carry model scale. */
    fun gameplayRootScale(): Vector3 = Vector3.ONE

    /**
     * Visual-child local offset in meters (parent space).
     * Root sits at ground + [MonsterType.anchorOffsetYMeters]; feet-origin GLBs need
     * `-anchorOffsetYMeters` so feet land on the floor. Do NOT divide by visual scale —
     * scale only affects the mesh, not this parent-space offset.
     */
    fun visualLocalOffset(type: MonsterType): Vector3 =
        Vector3(0f, type.visualFeetLocalOffsetYMeters - type.anchorOffsetYMeters, 0f)

    fun visualYawOffsetDegrees(type: MonsterType): Float =
        when (type) {
            MonsterType.NORMAL_BUG,
            MonsterType.RUNNER,
            MonsterType.ARMORED,
            MonsterType.FINAL_BOSS,
            -> 180f
            MonsterType.CEILING_DROPPER -> 0f
        }

    fun createPoolVisual(type: MonsterType): Entity = createPlaceholderVisual(type)

    fun loadGlbEntity(type: MonsterType): Entity? {
        val uri = assetUri(type) ?: return null
        return loadGlb(uri)
    }

    fun createGlbVisual(type: MonsterType): Entity? {
        val loaded = loadGlbEntity(type) ?: return null
        Log.i(TAG, "Loaded monster visual for ${type.name}: ${assetUri(type)}")
        applyVisualLocalPose(loaded, type)
        return loaded
    }

    fun createVisualOrPlaceholder(type: MonsterType): Entity {
        return createGlbVisual(type) ?: run {
            Log.e(
                TAG,
                "Falling back to placeholder mesh for ${type.name} after load failure: ${assetUri(type)}",
            )
            createPlaceholderVisual(type)
        }
    }

    private fun loadGlb(uri: String): Entity? =
        try {
            Entity.load(uri)
        } catch (error: Throwable) {
            Log.e(TAG, "Entity.load failed for $uri: ${error.message}", error)
            null
        }

    fun applyVisualLocalPose(visual: Entity, type: MonsterType) {
        visual.components[TransformComponent::class.java]?.apply {
            setPosition(visualLocalOffset(type))
            setEulerAngles(EulerAngles(0f, visualYawOffsetDegrees(type), 0f))
            setScaleVector(scaleVector(type))
        }
    }

    fun applyGameplayRootPose(root: TransformComponent) {
        root.setScaleVector(gameplayRootScale())
    }

    fun applyVisualPulse(visual: Entity?, baseScale: Float, pulseMultiplier: Float) {
        val scale = baseScale * pulseMultiplier
        visual?.components?.get(TransformComponent::class.java)
            ?.setScaleVector(Vector3(scale, scale, scale))
    }

    fun resetVisualScale(visual: Entity?, monster: MonsterComponent) {
        visual?.components?.get(TransformComponent::class.java)
            ?.setScaleVector(scaleVector(monster))
    }

    private fun createPlaceholderVisual(type: MonsterType): Entity {
        val mesh =
            when (type) {
                MonsterType.NORMAL_BUG ->
                    MeshResource.createCapsule(height = type.anchorOffsetYMeters * 2f, radius = 0.16f)
                MonsterType.RUNNER ->
                    MeshResource.createCone(radius = 0.18f, height = type.anchorOffsetYMeters * 2f)
                MonsterType.ARMORED ->
                    MeshResource.createBox(
                        size = Vector3(0.54f, type.anchorOffsetYMeters * 2f, 0.5f),
                        cornerRadius = 0.07f,
                    )
                MonsterType.CEILING_DROPPER ->
                    MeshResource.createCapsule(height = type.anchorOffsetYMeters * 2f, radius = 0.19f)
                MonsterType.FINAL_BOSS ->
                    MeshResource.createCapsule(height = type.anchorOffsetYMeters * 2f, radius = 0.52f)
            }
        val material =
            UnlitMaterial.create(BlendingMode.OPAQUE).apply {
                setBaseColor(type.placeholderColor())
            }
        return ModelEntity(mesh, material).apply {
            setName("${type.name}_Placeholder")
            components[TransformComponent::class.java]?.apply {
                setPosition(Vector3.ZERO)
                setEulerAngles(EulerAngles(0f, 0f, 0f))
                setScaleVector(Vector3.ONE)
            }
        }
    }

    private fun MonsterType.placeholderColor(): Color4 =
        when (this) {
            MonsterType.NORMAL_BUG -> Color4(0.45f, 1f, 0.28f, 1f)
            MonsterType.RUNNER -> Color4(1f, 0.55f, 0.08f, 1f)
            MonsterType.ARMORED -> Color4(0.48f, 0.55f, 0.68f, 1f)
            MonsterType.CEILING_DROPPER -> Color4(0.8f, 0.22f, 1f, 1f)
            MonsterType.FINAL_BOSS -> Color4(0.9f, 0.06f, 0.18f, 1f)
        }
}
