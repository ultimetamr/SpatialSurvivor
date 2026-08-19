package com.example.spatialsurvivor.monster

import com.pico.spatial.core.ecs.Component
import com.pico.spatial.core.ecs.Entity

enum class MonsterSpawnMode {
    BOUNDARY,
    CEILING_DROP,
    BOSS,
}

enum class MonsterMovementState {
    INACTIVE,
    DROPPING,
    CHASING,
    DYING,
}

/** Tunable archetype table for all currently implemented monster types. */
enum class MonsterType(
    val displayName: String,
    val moveSpeedMetersPerSecond: Float,
    val maxHealth: Int,
    val contactDamage: Int,
    val collisionRadiusMeters: Float,
    /** Gameplay root Y above ground (body pivot / collision center). */
    val anchorOffsetYMeters: Float,
    /** Extra local visual Y offset for model-origin differences (GLB feet vs center). */
    val visualFeetLocalOffsetYMeters: Float,
    val experienceValue: Int,
    val spawnMode: MonsterSpawnMode,
) {
    NORMAL_BUG(
        displayName = "普通虫",
        moveSpeedMetersPerSecond = 0.65f,
        maxHealth = 40,
        contactDamage = 8,
        collisionRadiusMeters = 0.20f,
        anchorOffsetYMeters = 0.14f,
        visualFeetLocalOffsetYMeters = 0f,
        experienceValue = 1,
        spawnMode = MonsterSpawnMode.BOUNDARY,
    ),
    RUNNER(
        displayName = "疾行者",
        moveSpeedMetersPerSecond = 1.2f,
        maxHealth = 30,
        contactDamage = 7,
        collisionRadiusMeters = 0.20f,
        anchorOffsetYMeters = 0.26f,
        visualFeetLocalOffsetYMeters = 0f,
        experienceValue = 2,
        spawnMode = MonsterSpawnMode.BOUNDARY,
    ),
    ARMORED(
        displayName = "重甲怪",
        moveSpeedMetersPerSecond = 0.38f,
        maxHealth = 140,
        contactDamage = 18,
        collisionRadiusMeters = 0.42f,
        anchorOffsetYMeters = 0.36f,
        visualFeetLocalOffsetYMeters = 0f,
        experienceValue = 4,
        spawnMode = MonsterSpawnMode.BOUNDARY,
    ),
    CEILING_DROPPER(
        displayName = "垂降怪",
        moveSpeedMetersPerSecond = 0.78f,
        maxHealth = 55,
        contactDamage = 11,
        collisionRadiusMeters = 0.27f,
        anchorOffsetYMeters = 0.20f,
        visualFeetLocalOffsetYMeters = 0f,
        experienceValue = 3,
        spawnMode = MonsterSpawnMode.CEILING_DROP,
    ),
    FINAL_BOSS(
        displayName = "终局 Boss",
        moveSpeedMetersPerSecond = 0.48f,
        maxHealth = 6000,
        contactDamage = 30,
        collisionRadiusMeters = 0.95f,
        anchorOffsetYMeters = 0.60f,
        visualFeetLocalOffsetYMeters = 0f,
        experienceValue = 0,
        spawnMode = MonsterSpawnMode.BOSS,
    ),
    ;

    /** Back-compat alias used by navigation / collision code. */
    val bodyCenterHeightMeters: Float get() = anchorOffsetYMeters
    val anchorOffsetY: Float get() = anchorOffsetYMeters
}

/** Runtime state shared by spawning, Scene Mesh navigation, combat, and death effects. */
class MonsterComponent : Component() {
    var monsterType: MonsterType = MonsterType.NORMAL_BUG
    var movementState: MonsterMovementState = MonsterMovementState.INACTIVE
    var active: Boolean = false
    var maxHealth: Int = MonsterType.NORMAL_BUG.maxHealth
    var currentHealth: Int = MonsterType.NORMAL_BUG.maxHealth
    var moveSpeedMetersPerSecond: Float = MonsterType.NORMAL_BUG.moveSpeedMetersPerSecond
    var contactDamage: Int = MonsterType.NORMAL_BUG.contactDamage
    var contactRadiusMeters: Float = MonsterType.NORMAL_BUG.collisionRadiusMeters
    var contactCooldownSeconds: Float = 0f
    var bodyCenterHeightMeters: Float = MonsterType.NORMAL_BUG.bodyCenterHeightMeters
    var experienceValue: Int = MonsterType.NORMAL_BUG.experienceValue
    var navigationCooldownSeconds: Float = 0f
    var navigationRevision: Long = -1L
    var waypointX: Float = 0f
    var waypointZ: Float = 0f
    var landingWorldY: Float = 0f
    var deathEffectRemainingSeconds: Float = 0f
    var deathDropRequested: Boolean = false
    var swordContactCooldownSeconds: Float = 0f
    var poisonDamageAccumulatorSeconds: Float = 0f
    var hitFeedbackRemainingSeconds: Float = 0f
    var hitFeedbackScaleApplied: Boolean = false
    var aiAccumulatedSeconds: Float = 0f
    var lodUpdatePhase: Int = 0
    /** Uniform root scale for the active archetype visual (GLB or placeholder). */
    var visualScale: Float = 1f
    /** Pooled visual child; may be swapped from placeholder to GLB after async load. */
    var visualChild: Entity? = null
    var groundDebugMarker: Entity? = null
    /** Ground Y locked at spawn for drift correction when mesh refreshes. */
    var lockedGroundY: Float = Float.NaN

    fun configure(
        type: MonsterType,
        healthMultiplier: Float = 1f,
        speedMultiplier: Float = 1f,
    ) {
        monsterType = type
        maxHealth = kotlin.math.ceil(type.maxHealth * healthMultiplier).toInt().coerceAtLeast(1)
        currentHealth = maxHealth
        moveSpeedMetersPerSecond = type.moveSpeedMetersPerSecond * speedMultiplier
        contactDamage = type.contactDamage
        contactRadiusMeters = type.collisionRadiusMeters
        bodyCenterHeightMeters = type.anchorOffsetYMeters
        experienceValue = type.experienceValue
        visualScale = MonsterVisualRules.visualScale(type)
        movementState = MonsterMovementState.INACTIVE
        active = false
        contactCooldownSeconds = 0f
        navigationCooldownSeconds = 0f
        navigationRevision = -1L
        deathEffectRemainingSeconds = 0f
        deathDropRequested = false
        swordContactCooldownSeconds = 0f
        poisonDamageAccumulatorSeconds = 0f
        hitFeedbackRemainingSeconds = 0f
        hitFeedbackScaleApplied = false
        aiAccumulatedSeconds = 0f
        lockedGroundY = Float.NaN
    }

    fun activate(state: MonsterMovementState) {
        currentHealth = maxHealth
        contactCooldownSeconds = 0f
        navigationCooldownSeconds = 0f
        navigationRevision = -1L
        deathEffectRemainingSeconds = 0f
        deathDropRequested = false
        swordContactCooldownSeconds = 0f
        poisonDamageAccumulatorSeconds = 0f
        hitFeedbackRemainingSeconds = 0f
        hitFeedbackScaleApplied = false
        aiAccumulatedSeconds = 0f
        movementState = state
        active = true
    }

    fun lockGround(groundY: Float) {
        lockedGroundY = groundY
    }

    fun resolvedGroundY(liveGroundY: Float): Float =
        liveGroundY.takeIf { it.isFinite() }
            ?: lockedGroundY.takeIf { it.isFinite() }
            ?: 0f

    fun activateWithGround(state: MonsterMovementState, groundY: Float) {
        lockGround(groundY)
        activate(state)
    }

    fun beginDeath() {
        if (movementState == MonsterMovementState.DYING ||
            movementState == MonsterMovementState.INACTIVE
        ) {
            return
        }
        active = false
        movementState = MonsterMovementState.DYING
        deathEffectRemainingSeconds = DEATH_EFFECT_DURATION_SECONDS
    }

    companion object {
        const val CONTACT_DAMAGE_INTERVAL_SECONDS = 0.75f
        const val DEATH_EFFECT_DURATION_SECONDS = 0.45f
        const val HIT_FEEDBACK_DURATION_SECONDS = 0.12f
    }
}
