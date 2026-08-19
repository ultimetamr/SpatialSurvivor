package com.example.spatialsurvivor.progression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermanentProgressionRulesTest {
    @Test
    fun combatBonusesScaleFromDefaults() {
        val bonuses =
            PermanentProgressionRules.combatBonuses(
                PermanentProgressionState(
                    startingMaxHealthLevel = 2,
                    startingAttackDamageLevel = 3,
                    startingAttackSpeedLevel = 2,
                    startingAttackRangeLevel = 1,
                    startingPickupRangeLevel = 2,
                    startingExperienceGainLevel = 1,
                ),
            )

        assertEquals(120, bonuses.maxHealth)
        assertEquals(26, bonuses.projectileDamage)
        assertEquals(0.9f, bonuses.attackIntervalSeconds, 0.0001f)
        assertEquals(2.1f, bonuses.attackRangeMeters, 0.0001f)
        assertEquals(0.7f, bonuses.pickupRangeMeters, 0.0001f)
        assertEquals(1.1f, bonuses.experienceGainMultiplier, 0.0001f)
    }

    @Test
    fun upgradeCostAndMaxLevelRespectCatalog() {
        val health = PermanentUpgradeCatalog.definition(PermanentUpgradeType.STARTING_MAX_HEALTH)
        assertEquals(40, PermanentProgressionRules.upgradeCost(health, 0))
        assertEquals(65, PermanentProgressionRules.upgradeCost(health, 1))
        assertFalse(PermanentProgressionRules.isMaxed(health, 9))
        assertTrue(PermanentProgressionRules.isMaxed(health, 10))
    }

    @Test
    fun settlementRewardAppliesWaveAndCrystalMultipliers() {
        val reward =
            PermanentProgressionRules.calculateSettlementReward(
                survivalSeconds = 125f,
                kills = 25,
                clearedWaves = 2,
                victory = true,
                crystalMultiplierLevel = 1,
                waveRewardLevel = 2,
            )

        // time=10, kill=4, wave=20*1.3=26, victory=100 → subtotal=140 → *1.1 = 154
        assertEquals(10, reward.baseTimeReward)
        assertEquals(4, reward.baseKillReward)
        assertEquals(26, reward.baseWaveReward)
        assertEquals(100, reward.victoryBonus)
        assertEquals(154, reward.totalReward)
    }

    @Test
    fun previewShowsNextEffectUntilMaxed() {
        val definition = PermanentUpgradeCatalog.definition(PermanentUpgradeType.CRYSTAL_MULTIPLIER)
        val mid = PermanentProgressionRules.preview(definition, 1)
        assertEquals("晶核 ×1.10", mid.currentEffectText)
        assertEquals("晶核 ×1.20", mid.nextEffectText)
        assertEquals(85, mid.nextCost)

        val maxed = PermanentProgressionRules.preview(definition, 10)
        assertTrue(maxed.isMaxed)
        assertEquals(null, maxed.nextEffectText)
        assertEquals(null, maxed.nextCost)
    }
}
