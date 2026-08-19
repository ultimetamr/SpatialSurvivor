package com.example.spatialsurvivor.upgrade

import com.pico.spatial.core.ecs.Component
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3

/** Temporary world-space skill FX slot (ice spike, lava, bolts, etc.). */
class SkillFxComponent(
    val kind: SkillVisualId,
) : Component() {
    var active: Boolean = false
    var remainingSeconds: Float = 0f
    var visualChild: Entity? = null
}

class LightningDomainVisualComponent : Component() {
    var visualChild: Entity? = null
    var evoVisualChild: Entity? = null
}

class PoisonAuraVisualComponent : Component() {
    var visualChild: Entity? = null
    var evoVisualChild: Entity? = null
}

class OrbitingSwordComponent(val orbitIndex: Int) : Component() {
    var visualChild: Entity? = null
    var evoVisualChild: Entity? = null
}

class AttackRangeHaloComponent : Component()

data class SkillFxSpawn(
    val kind: SkillVisualId,
    val worldPosition: Vector3,
    val durationSeconds: Float = 0.45f,
    val eulerAngles: EulerAngles = EulerAngles(0f, 0f, 0f),
)
