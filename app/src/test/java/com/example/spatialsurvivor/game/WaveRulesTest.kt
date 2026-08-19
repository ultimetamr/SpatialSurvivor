package com.example.spatialsurvivor.game

import com.example.spatialsurvivor.monster.MonsterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveRulesTest {
    @Test
    fun waveAdvancesEveryTwoMinutesAndStopsAtTen() {
        assertEquals(1, WaveRules.waveAt(119.99f))
        assertEquals(2, WaveRules.waveAt(120f))
        assertEquals(10, WaveRules.waveAt(1200f))
    }

    @Test
    fun cumulativeMultipliersMatchSecondWaveRequirements() {
        assertEquals(1.2f, WaveRules.healthMultiplier(2), 0.0001f)
        assertEquals(1.15f, WaveRules.quantityMultiplier(2), 0.0001f)
        assertEquals(1.1f, WaveRules.speedMultiplier(2), 0.0001f)
    }

    @Test
    fun activeCountGrowsButRespectsPoolBudget() {
        assertEquals(12, WaveRules.activeMonsterLimit(1))
        assertEquals(14, WaveRules.activeMonsterLimit(2))
        assertEquals(18, WaveRules.activeMonsterLimit(10))
    }

    @Test
    fun monsterTypesUnlockAcrossEarlyWaves() {
        assertEquals(listOf(MonsterType.NORMAL_BUG), WaveRules.unlockedTypes(1))
        assertTrue(MonsterType.RUNNER in WaveRules.unlockedTypes(2))
        assertTrue(MonsterType.ARMORED in WaveRules.unlockedTypes(3))
        assertTrue(MonsterType.CEILING_DROPPER in WaveRules.unlockedTypes(4))
        assertFalse(MonsterType.FINAL_BOSS in WaveRules.unlockedTypes(10))
    }

    @Test
    fun bossTriggersAtTenMinutes() {
        assertFalse(WaveRules.shouldSpawnFinalBoss(599.99f))
        assertTrue(WaveRules.shouldSpawnFinalBoss(600f))
    }
}
