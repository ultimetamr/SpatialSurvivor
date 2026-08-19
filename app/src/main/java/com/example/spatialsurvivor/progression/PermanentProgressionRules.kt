package com.example.spatialsurvivor.progression

import com.example.spatialsurvivor.player.PlayerStats
import kotlin.math.roundToInt

data class PermanentCombatBonuses(
    val maxHealth: Int,
    val projectileDamage: Int,
    val attackIntervalSeconds: Float,
    val attackRangeMeters: Float,
    val pickupRangeMeters: Float,
    val experienceGainMultiplier: Float,
)

data class PermanentUpgradePreview(
    val type: PermanentUpgradeType,
    val level: Int,
    val maxLevel: Int,
    val currentEffectText: String,
    val nextEffectText: String?,
    val nextCost: Int?,
    val isMaxed: Boolean,
)

/** Pure permanent-upgrade math shared by runtime, UI and unit tests. */
object PermanentProgressionRules {
    const val HEALTH_PER_LEVEL = 10
    const val DAMAGE_PER_LEVEL = 2
    const val ATTACK_SPEED_FACTOR_PER_LEVEL = 0.05f
    const val ATTACK_RANGE_PER_LEVEL_METERS = 0.1f
    const val PICKUP_RANGE_PER_LEVEL_METERS = 0.1f
    const val EXPERIENCE_GAIN_PER_LEVEL = 0.1f
    const val CRYSTAL_MULTIPLIER_PER_LEVEL = 0.1f
    const val WAVE_REWARD_PER_LEVEL = 0.15f

    fun upgradeCost(definition: PermanentUpgradeDefinition, level: Int): Int =
        definition.baseCost + level.coerceAtLeast(0) * definition.costStep

    fun isMaxed(definition: PermanentUpgradeDefinition, level: Int): Boolean =
        level.coerceAtLeast(0) >= definition.maxLevel

    fun crystalIncomeMultiplier(level: Int): Float =
        1f + level.coerceAtLeast(0) * CRYSTAL_MULTIPLIER_PER_LEVEL

    fun waveRewardMultiplier(level: Int): Float =
        1f + level.coerceAtLeast(0) * WAVE_REWARD_PER_LEVEL

    fun combatBonuses(state: PermanentProgressionState): PermanentCombatBonuses {
        val healthLevel = state.startingMaxHealthLevel.coerceAtLeast(0)
        val damageLevel = state.startingAttackDamageLevel.coerceAtLeast(0)
        val speedLevel = state.startingAttackSpeedLevel.coerceAtLeast(0)
        val rangeLevel = state.startingAttackRangeLevel.coerceAtLeast(0)
        val pickupLevel = state.startingPickupRangeLevel.coerceAtLeast(0)
        val experienceLevel = state.startingExperienceGainLevel.coerceAtLeast(0)
        val attackInterval =
            (
                PlayerStats.DEFAULT_ATTACK_INTERVAL_SECONDS *
                    (1f - ATTACK_SPEED_FACTOR_PER_LEVEL * speedLevel)
            ).coerceAtLeast(MINIMUM_ATTACK_INTERVAL_SECONDS)
        return PermanentCombatBonuses(
            maxHealth = PlayerStats.DEFAULT_MAX_HEALTH + HEALTH_PER_LEVEL * healthLevel,
            projectileDamage = PlayerStats.DEFAULT_PROJECTILE_DAMAGE + DAMAGE_PER_LEVEL * damageLevel,
            attackIntervalSeconds = attackInterval,
            attackRangeMeters =
                PlayerStats.DEFAULT_ATTACK_RANGE_METERS + ATTACK_RANGE_PER_LEVEL_METERS * rangeLevel,
            pickupRangeMeters =
                PlayerStats.DEFAULT_PICKUP_RANGE_METERS + PICKUP_RANGE_PER_LEVEL_METERS * pickupLevel,
            experienceGainMultiplier = 1f + EXPERIENCE_GAIN_PER_LEVEL * experienceLevel,
        )
    }

    fun effectText(type: PermanentUpgradeType, level: Int): String {
        val safeLevel = level.coerceAtLeast(0)
        return when (type) {
            PermanentUpgradeType.STARTING_MAX_HEALTH ->
                "生命 ${PlayerStats.DEFAULT_MAX_HEALTH + HEALTH_PER_LEVEL * safeLevel}"
            PermanentUpgradeType.STARTING_ATTACK_DAMAGE ->
                "伤害 ${PlayerStats.DEFAULT_PROJECTILE_DAMAGE + DAMAGE_PER_LEVEL * safeLevel}"
            PermanentUpgradeType.STARTING_ATTACK_SPEED -> {
                val interval =
                    (
                        PlayerStats.DEFAULT_ATTACK_INTERVAL_SECONDS *
                            (1f - ATTACK_SPEED_FACTOR_PER_LEVEL * safeLevel)
                    ).coerceAtLeast(MINIMUM_ATTACK_INTERVAL_SECONDS)
                "间隔 ${"%.2f".format(interval)} 秒"
            }
            PermanentUpgradeType.STARTING_ATTACK_RANGE ->
                "范围 ${"%.1f".format(PlayerStats.DEFAULT_ATTACK_RANGE_METERS + ATTACK_RANGE_PER_LEVEL_METERS * safeLevel)} 米"
            PermanentUpgradeType.STARTING_PICKUP_RANGE ->
                "拾取 ${"%.1f".format(PlayerStats.DEFAULT_PICKUP_RANGE_METERS + PICKUP_RANGE_PER_LEVEL_METERS * safeLevel)} 米"
            PermanentUpgradeType.STARTING_EXPERIENCE_GAIN ->
                "经验 ×${"%.2f".format(1f + EXPERIENCE_GAIN_PER_LEVEL * safeLevel)}"
            PermanentUpgradeType.CRYSTAL_MULTIPLIER ->
                "晶核 ×${"%.2f".format(crystalIncomeMultiplier(safeLevel))}"
            PermanentUpgradeType.WAVE_REWARD ->
                "波次 ×${"%.2f".format(waveRewardMultiplier(safeLevel))}"
        }
    }

    fun preview(
        definition: PermanentUpgradeDefinition,
        level: Int,
    ): PermanentUpgradePreview {
        val safeLevel = level.coerceAtLeast(0)
        val maxed = isMaxed(definition, safeLevel)
        return PermanentUpgradePreview(
            type = definition.type,
            level = safeLevel,
            maxLevel = definition.maxLevel,
            currentEffectText = effectText(definition.type, safeLevel),
            nextEffectText = if (maxed) null else effectText(definition.type, safeLevel + 1),
            nextCost = if (maxed) null else upgradeCost(definition, safeLevel),
            isMaxed = maxed,
        )
    }

    fun calculateSettlementReward(
        survivalSeconds: Float,
        kills: Int,
        clearedWaves: Int,
        victory: Boolean,
        crystalMultiplierLevel: Int,
        waveRewardLevel: Int,
    ): SettlementCrystalReward {
        val fullMinutes = (survivalSeconds.coerceAtLeast(0f) / 60f).toInt()
        val baseTimeReward = fullMinutes * 5
        val baseKillReward = (kills.coerceAtLeast(0) / 10) * 2
        val baseWaveReward =
            (
                clearedWaves.coerceAtLeast(0) * 10 *
                    waveRewardMultiplier(waveRewardLevel)
            ).roundToInt()
        val victoryBonus = if (victory) 100 else 0
        val subtotal = baseTimeReward + baseKillReward + baseWaveReward + victoryBonus
        val totalReward =
            (subtotal * crystalIncomeMultiplier(crystalMultiplierLevel))
                .roundToInt()
                .coerceAtLeast(0)
        return SettlementCrystalReward(
            baseTimeReward = baseTimeReward,
            baseKillReward = baseKillReward,
            baseWaveReward = baseWaveReward,
            victoryBonus = victoryBonus,
            totalReward = totalReward,
        )
    }

    private const val MINIMUM_ATTACK_INTERVAL_SECONDS = 0.35f
}
