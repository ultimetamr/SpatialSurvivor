package com.example.spatialsurvivor.player

import com.example.spatialsurvivor.exp.ExperienceProgressionRules
import com.pico.spatial.core.ecs.Component

/** Runtime state for the single physical room-scale player. */
class PlayerComponent : Component() {
    var maxHealth: Int = PlayerStats.DEFAULT_MAX_HEALTH
    var currentHealth: Int = PlayerStats.DEFAULT_MAX_HEALTH
    var attackRangeMeters: Float = PlayerStats.DEFAULT_ATTACK_RANGE_METERS
    var attackIntervalSeconds: Float = PlayerStats.DEFAULT_ATTACK_INTERVAL_SECONDS
    var pickupRangeMeters: Float = PlayerStats.DEFAULT_PICKUP_RANGE_METERS
    var level: Int = ExperienceProgressionRules.DEFAULT_LEVEL
    var currentExperience: Int = 0
    var experienceRequired: Int = ExperienceProgressionRules.DEFAULT_EXPERIENCE_REQUIRED
    var projectileDamage: Int = PlayerStats.DEFAULT_PROJECTILE_DAMAGE
    var projectileSpeedMetersPerSecond: Float =
        PlayerStats.DEFAULT_PROJECTILE_SPEED_METERS_PER_SECOND
    var experienceGainMultiplier: Float = 1f
    var movementSpeedMultiplier: Float = 1f

    var orbitingSwordStacks: Int = 0
    // Energy projectile remains the starter weapon; its scheduled level-up strengthens it.
    var energyProjectileStacks: Int = DEFAULT_ENERGY_PROJECTILE_STACKS
    var chainLightningStacks: Int = 0
    var poisonAuraStacks: Int = 0
    var piercingIceConeStacks: Int = 0
    var lavaBombStacks: Int = 0
    var gravityBlackHoleStacks: Int = 0
    var swordRainStacks: Int = 0
    var lightningDomainStacks: Int = 0
    var projectileCount: Int = 1
    var healthRegenerationPerSecond: Int = 0
    var pierceCount: Int = 0
    var criticalChance: Float = 0f
    var criticalDamageMultiplier: Float = 1.5f
    var damageReduction: Float = 0f
    var dodgeChance: Float = 0f
    var regeneratingShieldStacks: Int = 0
    var appliedUpgradeCount: Int = 0
    var experienceMagnetOwned: Boolean = false
    var killHealOwned: Boolean = false
    var magneticFieldOwned: Boolean = false
    var explosiveRemainsOwned: Boolean = false
    var freezePulseOwned: Boolean = false
    var rerollMasterOwned: Boolean = false
    var nearDeathProtectionOwned: Boolean = false
    var extraOptionOwned: Boolean = false
    var doubleCrystalOwned: Boolean = false
    var gatheringAuraOwned: Boolean = false
    val ownedEvolutions: MutableSet<com.example.spatialsurvivor.upgrade.UpgradeId> = mutableSetOf()
    var killHealAccumulator: Float = 0f
    var automaticSkillUnlockCount: Int = 0
    var automaticAttributeCursor: Int = 0
    var attackDamageUpgradeStacks: Int = 0
    var attackSpeedUpgradeStacks: Int = 0
    var attackRangeUpgradeStacks: Int = 0
    var experienceGainUpgradeStacks: Int = 0
    var movementSpeedUpgradeStacks: Int = 0
    var pickupRangeUpgradeStacks: Int = 0
    var projectileCountUpgradeStacks: Int = 0
    var maxHealthUpgradeStacks: Int = 0
    var healthRegenerationUpgradeStacks: Int = 0
    var pierceCountUpgradeStacks: Int = 0
    var criticalChanceUpgradeStacks: Int = 0
    var damageReductionUpgradeStacks: Int = 0
    var criticalDamageUpgradeStacks: Int = 0
    var dodgeChanceUpgradeStacks: Int = 0

    var trackedHeadX: Float = 0f
    var trackedHeadY: Float = DEFAULT_HEAD_HEIGHT_METERS
    var trackedHeadZ: Float = 0f
    var hasTrackingPose: Boolean = false
    var attackCooldownSeconds: Float = 0f
    var iceConeCooldownSeconds: Float = 0f
    var lavaBombCooldownSeconds: Float = 0f
    var gravityBlackHoleCooldownSeconds: Float = 0f
    var swordRainCooldownSeconds: Float = 0f
    var lightningDomainAccumulatorSeconds: Float = 0f
    var healthRegenerationAccumulatorSeconds: Float = 0f
    var isGameOver: Boolean = false
    var damageEventSequence: Long = 0L

    fun expandPickupRange(additionalMeters: Float) {
        pickupRangeMeters =
            ExperienceProgressionRules.expandedPickupRange(
                currentMeters = pickupRangeMeters,
                increaseMeters = additionalMeters,
            )
    }

    fun reset() {
        maxHealth = PlayerStats.DEFAULT_MAX_HEALTH
        currentHealth = PlayerStats.DEFAULT_MAX_HEALTH
        attackRangeMeters = PlayerStats.DEFAULT_ATTACK_RANGE_METERS
        attackIntervalSeconds = PlayerStats.DEFAULT_ATTACK_INTERVAL_SECONDS
        pickupRangeMeters = PlayerStats.DEFAULT_PICKUP_RANGE_METERS
        level = ExperienceProgressionRules.DEFAULT_LEVEL
        currentExperience = 0
        experienceRequired = ExperienceProgressionRules.DEFAULT_EXPERIENCE_REQUIRED
        projectileDamage = PlayerStats.DEFAULT_PROJECTILE_DAMAGE
        projectileSpeedMetersPerSecond = PlayerStats.DEFAULT_PROJECTILE_SPEED_METERS_PER_SECOND
        experienceGainMultiplier = 1f
        movementSpeedMultiplier = 1f
        orbitingSwordStacks = 0
        energyProjectileStacks = DEFAULT_ENERGY_PROJECTILE_STACKS
        chainLightningStacks = 0
        poisonAuraStacks = 0
        piercingIceConeStacks = 0
        lavaBombStacks = 0
        gravityBlackHoleStacks = 0
        swordRainStacks = 0
        lightningDomainStacks = 0
        projectileCount = 1
        healthRegenerationPerSecond = 0
        pierceCount = 0
        criticalChance = 0f
        criticalDamageMultiplier = 1.5f
        damageReduction = 0f
        dodgeChance = 0f
        regeneratingShieldStacks = 0
        appliedUpgradeCount = 0
        experienceMagnetOwned = false
        killHealOwned = false
        magneticFieldOwned = false
        explosiveRemainsOwned = false
        freezePulseOwned = false
        rerollMasterOwned = false
        nearDeathProtectionOwned = false
        extraOptionOwned = false
        doubleCrystalOwned = false
        gatheringAuraOwned = false
        ownedEvolutions.clear()
        killHealAccumulator = 0f
        automaticSkillUnlockCount = 0
        automaticAttributeCursor = 0
        attackDamageUpgradeStacks = 0
        attackSpeedUpgradeStacks = 0
        attackRangeUpgradeStacks = 0
        experienceGainUpgradeStacks = 0
        movementSpeedUpgradeStacks = 0
        pickupRangeUpgradeStacks = 0
        projectileCountUpgradeStacks = 0
        maxHealthUpgradeStacks = 0
        healthRegenerationUpgradeStacks = 0
        pierceCountUpgradeStacks = 0
        criticalChanceUpgradeStacks = 0
        damageReductionUpgradeStacks = 0
        criticalDamageUpgradeStacks = 0
        dodgeChanceUpgradeStacks = 0
        trackedHeadX = 0f
        trackedHeadY = DEFAULT_HEAD_HEIGHT_METERS
        trackedHeadZ = 0f
        hasTrackingPose = false
        attackCooldownSeconds = 0f
        iceConeCooldownSeconds = 0f
        lavaBombCooldownSeconds = 0f
        gravityBlackHoleCooldownSeconds = 0f
        swordRainCooldownSeconds = 0f
        lightningDomainAccumulatorSeconds = 0f
        healthRegenerationAccumulatorSeconds = 0f
        isGameOver = false
        damageEventSequence = 0L
    }

    companion object {
        const val DEFAULT_ENERGY_PROJECTILE_STACKS = 1
        const val DEFAULT_HEAD_HEIGHT_METERS = 1.6f
        const val BODY_COLLISION_RADIUS_METERS = 0.3f
    }
}
