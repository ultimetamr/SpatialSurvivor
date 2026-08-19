package com.example.spatialsurvivor.upgrade

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
 * Safe GLB binding for skill props. Missing or unloadable assets never throw;
 * callers always receive a placeholder ModelEntity instead.
 */
object SkillVisualRules {
    private const val TAG = "SkillVisualRules"
    private const val MODEL_UNIT_CORRECTION = 0.01f

    fun assetUri(id: SkillVisualId): String? =
        when (id) {
            SkillVisualId.ORBIT_SWORD -> "asset://models/orbit_sword.glb"
            SkillVisualId.POISON_AURA -> "asset://models/poison_aura.glb"
            SkillVisualId.CHAIN_LIGHTNING -> "asset://models/chain_lightning.glb"
            SkillVisualId.ICE_SPIKE -> "asset://models/ice_spike.glb"
            SkillVisualId.LAVA_BOMB -> "asset://models/lava_bomb.glb"
            SkillVisualId.BLACK_HOLE -> "asset://models/black_hole.glb"
            SkillVisualId.SWORD_RAIN_BLADE -> "asset://models/sword_rain_blade.glb"
            SkillVisualId.LIGHTNING_FIELD -> "asset://models/lightning_field.glb"
            SkillVisualId.EVO_TEN_THOUSAND_SWORDS -> "asset://models/evo_ten_thousand_swords.glb"
            SkillVisualId.EVO_HEAVENLY_THUNDER -> "asset://models/evo_heavenly_thunder.glb"
            SkillVisualId.EVO_DARK_POISON_DOMAIN -> "asset://models/evo_dark_poison_domain.glb"
            SkillVisualId.EVO_METEOR_LAVA -> "asset://models/evo_meteor_lava.glb"
            SkillVisualId.EVO_ABSOLUTE_ZERO -> "asset://models/evo_absolute_zero.glb"
            SkillVisualId.EVO_VOID_BLACK_HOLE -> "asset://models/evo_void_black_hole.glb"
            SkillVisualId.FREEZE_PULSE -> "asset://models/freeze_pulse.glb"
            SkillVisualId.EXPLOSION -> "asset://models/explosion.glb"
            SkillVisualId.ENERGY_PROJECTILE -> "asset://models/energy_projectile.glb"
        }

    fun visualScale(id: SkillVisualId): Float =
        when (id) {
            SkillVisualId.ORBIT_SWORD -> 0.55f * MODEL_UNIT_CORRECTION
            SkillVisualId.EVO_TEN_THOUSAND_SWORDS -> 0.62f * MODEL_UNIT_CORRECTION
            SkillVisualId.POISON_AURA -> 1.0f * MODEL_UNIT_CORRECTION
            SkillVisualId.EVO_DARK_POISON_DOMAIN -> 1.35f * MODEL_UNIT_CORRECTION
            SkillVisualId.CHAIN_LIGHTNING -> 0.7f * MODEL_UNIT_CORRECTION
            SkillVisualId.EVO_HEAVENLY_THUNDER -> 0.9f * MODEL_UNIT_CORRECTION
            SkillVisualId.ICE_SPIKE -> 0.6f * MODEL_UNIT_CORRECTION
            SkillVisualId.EVO_ABSOLUTE_ZERO -> 0.7f * MODEL_UNIT_CORRECTION
            SkillVisualId.LAVA_BOMB -> 0.55f * MODEL_UNIT_CORRECTION
            SkillVisualId.EVO_METEOR_LAVA -> 0.75f * MODEL_UNIT_CORRECTION
            SkillVisualId.BLACK_HOLE -> 0.85f * MODEL_UNIT_CORRECTION
            SkillVisualId.EVO_VOID_BLACK_HOLE -> 1.1f * MODEL_UNIT_CORRECTION
            SkillVisualId.SWORD_RAIN_BLADE -> 0.65f * MODEL_UNIT_CORRECTION
            SkillVisualId.LIGHTNING_FIELD -> 1.0f * MODEL_UNIT_CORRECTION
            SkillVisualId.FREEZE_PULSE -> 1.2f * MODEL_UNIT_CORRECTION
            SkillVisualId.EXPLOSION -> 0.9f * MODEL_UNIT_CORRECTION
            SkillVisualId.ENERGY_PROJECTILE -> 0.35f * MODEL_UNIT_CORRECTION
        }

    fun scaleVector(id: SkillVisualId): Vector3 {
        val s = visualScale(id)
        return Vector3(s, s, s)
    }

    fun createPoolVisual(id: SkillVisualId): Entity = createPlaceholderVisual(id)

    fun loadGlbEntity(id: SkillVisualId): Entity? {
        val uri = assetUri(id) ?: return null
        return loadGlb(uri)
    }

    fun createGlbVisual(id: SkillVisualId): Entity? {
        val loaded = loadGlbEntity(id) ?: return null
        Log.i(TAG, "Loaded skill visual ${id.name}: ${assetUri(id)}")
        return wrapLoadedGlb(loaded, id)
    }

    /** Attach loaded GLB under an identity wrapper; unit correction stays on the loaded child. */
    fun wrapLoadedGlb(loaded: Entity, id: SkillVisualId): Entity {
        applyVisualLocalPose(loaded, id)
        return Entity().apply {
            setName("${id.name}_Visual")
            addChild(loaded)
            components[TransformComponent::class.java]?.apply {
                setPosition(Vector3.ZERO)
                setEulerAngles(EulerAngles(0f, 0f, 0f))
                setScaleVector(Vector3.ONE)
            }
        }
    }

    fun createVisualOrPlaceholder(id: SkillVisualId): Entity =
        createGlbVisual(id) ?: run {
            Log.w(TAG, "Using placeholder for ${id.name}: ${assetUri(id)}")
            createPlaceholderVisual(id)
        }

    fun applyVisualLocalPose(visual: Entity, id: SkillVisualId) {
        val transform = visual.components[TransformComponent::class.java]
        if (transform == null) {
            Log.w(TAG, "No TransformComponent on ${id.name}; scale may be wrong")
            return
        }
        transform.setPosition(Vector3.ZERO)
        transform.setEulerAngles(EulerAngles(0f, yawOffsetDegrees(id), 0f))
        transform.setScaleVector(scaleVector(id))
    }

    private fun yawOffsetDegrees(id: SkillVisualId): Float =
        when (id) {
            SkillVisualId.ORBIT_SWORD,
            SkillVisualId.EVO_TEN_THOUSAND_SWORDS,
            SkillVisualId.SWORD_RAIN_BLADE,
            SkillVisualId.ICE_SPIKE,
            SkillVisualId.EVO_ABSOLUTE_ZERO,
            -> 180f
            else -> 0f
        }

    private fun loadGlb(uri: String): Entity? =
        try {
            Entity.load(uri)
        } catch (error: Throwable) {
            Log.e(TAG, "Entity.load failed for $uri: ${error.message}", error)
            null
        }

    private fun createPlaceholderVisual(id: SkillVisualId): Entity {
        val mesh =
            when (id) {
                SkillVisualId.ORBIT_SWORD,
                SkillVisualId.EVO_TEN_THOUSAND_SWORDS,
                SkillVisualId.SWORD_RAIN_BLADE,
                ->
                    MeshResource.createBox(
                        size = Vector3(0.08f, 0.5f, 0.13f),
                        cornerRadius = 0.025f,
                    )
                SkillVisualId.POISON_AURA,
                SkillVisualId.EVO_DARK_POISON_DOMAIN,
                SkillVisualId.LIGHTNING_FIELD,
                ->
                    MeshResource.createTorus(outerRingRadius = 1.35f, innerRingRadius = 1.2f)
                SkillVisualId.CHAIN_LIGHTNING,
                SkillVisualId.EVO_HEAVENLY_THUNDER,
                ->
                    MeshResource.createBox(
                        size = Vector3(0.08f, 0.08f, 1.2f),
                        cornerRadius = 0.02f,
                    )
                SkillVisualId.ICE_SPIKE,
                SkillVisualId.EVO_ABSOLUTE_ZERO,
                -> MeshResource.createCone(radius = 0.12f, height = 0.55f)
                SkillVisualId.LAVA_BOMB,
                SkillVisualId.EVO_METEOR_LAVA,
                SkillVisualId.ENERGY_PROJECTILE,
                -> MeshResource.createSphere(radius = 0.08f)
                SkillVisualId.BLACK_HOLE,
                SkillVisualId.EVO_VOID_BLACK_HOLE,
                -> MeshResource.createSphere(radius = 0.35f)
                SkillVisualId.FREEZE_PULSE -> MeshResource.createSphere(radius = 0.9f)
                SkillVisualId.EXPLOSION -> MeshResource.createSphere(radius = 0.55f)
            }
        val material =
            UnlitMaterial.create(BlendingMode.ADD).apply {
                setBaseColor(placeholderColor(id))
            }
        return ModelEntity(mesh, material).apply {
            setName("${id.name}_Placeholder")
            components[TransformComponent::class.java]?.apply {
                setPosition(Vector3.ZERO)
                setEulerAngles(EulerAngles(0f, 0f, 0f))
                setScaleVector(Vector3.ONE)
            }
        }
    }

    private fun placeholderColor(id: SkillVisualId): Color4 =
        when (id) {
            SkillVisualId.ORBIT_SWORD,
            SkillVisualId.EVO_TEN_THOUSAND_SWORDS,
            SkillVisualId.SWORD_RAIN_BLADE,
            -> Color4(0.45f, 0.9f, 1f, 1f)
            SkillVisualId.POISON_AURA,
            SkillVisualId.EVO_DARK_POISON_DOMAIN,
            -> Color4(0.18f, 1f, 0.2f, 0.55f)
            SkillVisualId.CHAIN_LIGHTNING,
            SkillVisualId.EVO_HEAVENLY_THUNDER,
            SkillVisualId.LIGHTNING_FIELD,
            -> Color4(0.55f, 0.75f, 1f, 1f)
            SkillVisualId.ICE_SPIKE,
            SkillVisualId.EVO_ABSOLUTE_ZERO,
            SkillVisualId.FREEZE_PULSE,
            -> Color4(0.55f, 0.9f, 1f, 1f)
            SkillVisualId.LAVA_BOMB,
            SkillVisualId.EVO_METEOR_LAVA,
            SkillVisualId.EXPLOSION,
            -> Color4(1f, 0.35f, 0.08f, 1f)
            SkillVisualId.BLACK_HOLE,
            SkillVisualId.EVO_VOID_BLACK_HOLE,
            -> Color4(0.35f, 0.05f, 0.55f, 1f)
            SkillVisualId.ENERGY_PROJECTILE -> Color4(0.15f, 0.85f, 1f, 1f)
        }
}
