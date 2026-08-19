package com.example.spatialsurvivor.progression

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.example.spatialsurvivor.player.PlayerComponent

data class PermanentProgressionState(
    val totalCrystals: Int = 0,
    val startingMaxHealthLevel: Int = 0,
    val startingAttackDamageLevel: Int = 0,
    val startingAttackSpeedLevel: Int = 0,
    val startingAttackRangeLevel: Int = 0,
    val startingPickupRangeLevel: Int = 0,
    val startingExperienceGainLevel: Int = 0,
    val crystalMultiplierLevel: Int = 0,
    val waveRewardLevel: Int = 0,
) {
    fun levelOf(type: PermanentUpgradeType): Int =
        when (type) {
            PermanentUpgradeType.STARTING_MAX_HEALTH -> startingMaxHealthLevel
            PermanentUpgradeType.STARTING_ATTACK_DAMAGE -> startingAttackDamageLevel
            PermanentUpgradeType.STARTING_ATTACK_SPEED -> startingAttackSpeedLevel
            PermanentUpgradeType.STARTING_ATTACK_RANGE -> startingAttackRangeLevel
            PermanentUpgradeType.STARTING_PICKUP_RANGE -> startingPickupRangeLevel
            PermanentUpgradeType.STARTING_EXPERIENCE_GAIN -> startingExperienceGainLevel
            PermanentUpgradeType.CRYSTAL_MULTIPLIER -> crystalMultiplierLevel
            PermanentUpgradeType.WAVE_REWARD -> waveRewardLevel
        }

    fun withLevel(type: PermanentUpgradeType, level: Int): PermanentProgressionState =
        when (type) {
            PermanentUpgradeType.STARTING_MAX_HEALTH -> copy(startingMaxHealthLevel = level)
            PermanentUpgradeType.STARTING_ATTACK_DAMAGE -> copy(startingAttackDamageLevel = level)
            PermanentUpgradeType.STARTING_ATTACK_SPEED -> copy(startingAttackSpeedLevel = level)
            PermanentUpgradeType.STARTING_ATTACK_RANGE -> copy(startingAttackRangeLevel = level)
            PermanentUpgradeType.STARTING_PICKUP_RANGE -> copy(startingPickupRangeLevel = level)
            PermanentUpgradeType.STARTING_EXPERIENCE_GAIN -> copy(startingExperienceGainLevel = level)
            PermanentUpgradeType.CRYSTAL_MULTIPLIER -> copy(crystalMultiplierLevel = level)
            PermanentUpgradeType.WAVE_REWARD -> copy(waveRewardLevel = level)
        }
}

data class SettlementCrystalReward(
    val baseTimeReward: Int,
    val baseKillReward: Int,
    val baseWaveReward: Int,
    val victoryBonus: Int,
    val totalReward: Int,
)

/**
 * Persistent meta progression used by the settlement screen and the permanent-upgrade panel.
 * Combat upgrades apply at run start; economy upgrades scale settlement crystal payouts.
 */
object PermanentProgressionRuntime {
    private const val PREFS_NAME = "spatial_survivor_progression"
    private const val KEY_TOTAL_CRYSTALS = "total_crystals"
    private const val KEY_STARTING_MAX_HEALTH = "starting_max_health_level"
    private const val KEY_STARTING_ATTACK_DAMAGE = "starting_attack_damage_level"
    private const val KEY_STARTING_ATTACK_SPEED = "starting_attack_speed_level"
    private const val KEY_STARTING_ATTACK_RANGE = "starting_attack_range_level"
    private const val KEY_STARTING_PICKUP_RANGE = "starting_pickup_range_level"
    private const val KEY_STARTING_EXPERIENCE_GAIN = "starting_experience_gain_level"
    private const val KEY_CRYSTAL_MULTIPLIER_LEVEL = "crystal_multiplier_level"
    private const val KEY_WAVE_REWARD_LEVEL = "wave_reward_level"

    private val mutableState = mutableStateOf(PermanentProgressionState())
    val state: State<PermanentProgressionState> = mutableState

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        mutableState.value = readState()
    }

    fun reload() {
        if (appContext != null) {
            mutableState.value = readState()
        }
    }

    fun crystalIncomeMultiplier(level: Int = mutableState.value.crystalMultiplierLevel): Float =
        PermanentProgressionRules.crystalIncomeMultiplier(level)

    fun waveRewardMultiplier(level: Int = mutableState.value.waveRewardLevel): Float =
        PermanentProgressionRules.waveRewardMultiplier(level)

    fun upgradeCost(type: PermanentUpgradeType, level: Int = mutableState.value.levelOf(type)): Int =
        PermanentProgressionRules.upgradeCost(PermanentUpgradeCatalog.definition(type), level)

    fun canPurchase(type: PermanentUpgradeType): Boolean {
        val definition = PermanentUpgradeCatalog.definition(type)
        val level = mutableState.value.levelOf(type)
        if (PermanentProgressionRules.isMaxed(definition, level)) return false
        return mutableState.value.totalCrystals >= PermanentProgressionRules.upgradeCost(definition, level)
    }

    fun purchase(type: PermanentUpgradeType): Boolean {
        val definition = PermanentUpgradeCatalog.definition(type)
        val current = mutableState.value
        val level = current.levelOf(type)
        if (PermanentProgressionRules.isMaxed(definition, level)) return false
        val cost = PermanentProgressionRules.upgradeCost(definition, level)
        if (current.totalCrystals < cost) return false
        val next =
            current
                .copy(totalCrystals = current.totalCrystals - cost)
                .withLevel(type, level + 1)
        mutableState.value = next
        persist(next)
        return true
    }

    fun preview(type: PermanentUpgradeType): PermanentUpgradePreview =
        PermanentProgressionRules.preview(
            definition = PermanentUpgradeCatalog.definition(type),
            level = mutableState.value.levelOf(type),
        )

    fun applyToPlayer(player: PlayerComponent) {
        val bonuses = PermanentProgressionRules.combatBonuses(mutableState.value)
        player.maxHealth = bonuses.maxHealth
        player.currentHealth = bonuses.maxHealth
        player.projectileDamage = bonuses.projectileDamage
        player.attackIntervalSeconds = bonuses.attackIntervalSeconds
        player.attackRangeMeters = bonuses.attackRangeMeters
        player.pickupRangeMeters = bonuses.pickupRangeMeters
        player.experienceGainMultiplier = bonuses.experienceGainMultiplier
    }

    fun awardSettlementCrystals(
        survivalSeconds: Float,
        kills: Int,
        clearedWaves: Int,
        victory: Boolean,
    ): SettlementCrystalReward {
        val state = mutableState.value
        val reward =
            PermanentProgressionRules.calculateSettlementReward(
                survivalSeconds = survivalSeconds,
                kills = kills,
                clearedWaves = clearedWaves,
                victory = victory,
                crystalMultiplierLevel = state.crystalMultiplierLevel,
                waveRewardLevel = state.waveRewardLevel,
            )
        val next = state.copy(totalCrystals = state.totalCrystals + reward.totalReward)
        mutableState.value = next
        persist(next)
        return reward
    }

    private fun readState(): PermanentProgressionState {
        val preferences = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (preferences == null) return mutableState.value
        return PermanentProgressionState(
            totalCrystals = preferences.getInt(KEY_TOTAL_CRYSTALS, 0).coerceAtLeast(0),
            startingMaxHealthLevel = preferences.getInt(KEY_STARTING_MAX_HEALTH, 0).coerceAtLeast(0),
            startingAttackDamageLevel = preferences.getInt(KEY_STARTING_ATTACK_DAMAGE, 0).coerceAtLeast(0),
            startingAttackSpeedLevel = preferences.getInt(KEY_STARTING_ATTACK_SPEED, 0).coerceAtLeast(0),
            startingAttackRangeLevel = preferences.getInt(KEY_STARTING_ATTACK_RANGE, 0).coerceAtLeast(0),
            startingPickupRangeLevel = preferences.getInt(KEY_STARTING_PICKUP_RANGE, 0).coerceAtLeast(0),
            startingExperienceGainLevel =
                preferences.getInt(KEY_STARTING_EXPERIENCE_GAIN, 0).coerceAtLeast(0),
            crystalMultiplierLevel = preferences.getInt(KEY_CRYSTAL_MULTIPLIER_LEVEL, 0).coerceAtLeast(0),
            waveRewardLevel = preferences.getInt(KEY_WAVE_REWARD_LEVEL, 0).coerceAtLeast(0),
        )
    }

    private fun persist(state: PermanentProgressionState) {
        val preferences = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        preferences
            .edit()
            .putInt(KEY_TOTAL_CRYSTALS, state.totalCrystals)
            .putInt(KEY_STARTING_MAX_HEALTH, state.startingMaxHealthLevel)
            .putInt(KEY_STARTING_ATTACK_DAMAGE, state.startingAttackDamageLevel)
            .putInt(KEY_STARTING_ATTACK_SPEED, state.startingAttackSpeedLevel)
            .putInt(KEY_STARTING_ATTACK_RANGE, state.startingAttackRangeLevel)
            .putInt(KEY_STARTING_PICKUP_RANGE, state.startingPickupRangeLevel)
            .putInt(KEY_STARTING_EXPERIENCE_GAIN, state.startingExperienceGainLevel)
            .putInt(KEY_CRYSTAL_MULTIPLIER_LEVEL, state.crystalMultiplierLevel)
            .putInt(KEY_WAVE_REWARD_LEVEL, state.waveRewardLevel)
            .apply()
    }
}
