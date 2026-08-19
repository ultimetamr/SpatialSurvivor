package com.example.spatialsurvivor.upgrade

import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.EntityQueryCondition
import com.pico.spatial.core.ecs.Scene
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlin.math.atan2

/** Pooled one-shot skill model FX. Never blocks combat clock; missing visuals stay placeholders. */
object SkillFxGameplay {
    private val fxQuery = EntityQueryCondition.hasComponent(SkillFxComponent::class.java)
    private val pending = ArrayDeque<SkillFxSpawn>(32)

    fun reset() {
        pending.clear()
    }

    fun resetScene(scene: Scene) {
        reset()
        scene.queryEntity(fxQuery).forEach(::hide)
    }

    fun request(spawn: SkillFxSpawn) {
        if (pending.size >= MAX_PENDING) pending.removeFirst()
        pending.addLast(spawn)
    }

    fun requestBolt(
        from: Vector3,
        to: Vector3,
        evolved: Boolean,
        durationSeconds: Float = 0.28f,
    ) {
        val mid =
            Vector3(
                (from.x + to.x) * 0.5f,
                (from.y + to.y) * 0.5f + 0.15f,
                (from.z + to.z) * 0.5f,
            )
        val yaw =
            Math.toDegrees(atan2((to.x - from.x).toDouble(), (to.z - from.z).toDouble())).toFloat()
        request(
            SkillFxSpawn(
                kind =
                    if (evolved) SkillVisualId.EVO_HEAVENLY_THUNDER
                    else SkillVisualId.CHAIN_LIGHTNING,
                worldPosition = mid,
                durationSeconds = durationSeconds,
                eulerAngles = EulerAngles(0f, yaw, 0f),
            ),
        )
    }

    fun fixedUpdate(scene: Scene, deltaSeconds: Float) {
        drainPending(scene)
        scene.queryEntity(fxQuery).forEach { entity ->
            val fx = entity.components[SkillFxComponent::class.java] ?: return@forEach
            if (!fx.active) return@forEach
            fx.remainingSeconds = (fx.remainingSeconds - deltaSeconds).coerceAtLeast(0f)
            if (fx.remainingSeconds <= 0f) hide(entity)
        }
    }

    private fun drainPending(scene: Scene) {
        var stalled = false
        while (pending.isNotEmpty() && !stalled) {
            val spawn = pending.removeFirst()
            val slot =
                scene.queryEntity(fxQuery).firstOrNull { entity ->
                    val fx = entity.components[SkillFxComponent::class.java]
                    fx != null && fx.kind == spawn.kind && !fx.active
                }
            if (slot == null) {
                pending.addFirst(spawn)
                stalled = true
                continue
            }
            val transform = slot.components[TransformComponent::class.java] ?: continue
            val fx = slot.components[SkillFxComponent::class.java] ?: continue
            transform.setPosition(spawn.worldPosition)
            transform.setEulerAngles(spawn.eulerAngles)
            transform.setScaleVector(Vector3.ONE)
            fx.active = true
            fx.remainingSeconds = spawn.durationSeconds
        }
    }

    private fun hide(entity: Entity) {
        val fx = entity.components[SkillFxComponent::class.java]
        fx?.active = false
        fx?.remainingSeconds = 0f
        entity.components[TransformComponent::class.java]?.apply {
            setPosition(HIDDEN)
            setScaleVector(Vector3.ZERO)
            setEulerAngles(EulerAngles(0f, 0f, 0f))
        }
    }

    fun createPoolSlot(kind: SkillVisualId, index: Int): Entity =
        Entity().apply {
            setName("${kind.name}_Fx${index + 1}")
            val fx = SkillFxComponent(kind)
            components.set(fx)
            val visual = SkillVisualRules.createPoolVisual(kind)
            fx.visualChild = visual
            addChild(visual)
            components[TransformComponent::class.java]?.apply {
                setPosition(HIDDEN)
                setScaleVector(Vector3.ZERO)
            }
        }

    fun logPoolReady(count: Int) {
        Log.i(TAG, "Skill FX pool ready: $count slots (placeholders until async GLB hydrate)")
    }

    private val HIDDEN = Vector3(0f, -100f, 0f)
    private const val MAX_PENDING = 24
    private const val TAG = "SkillFxGameplay"
}
