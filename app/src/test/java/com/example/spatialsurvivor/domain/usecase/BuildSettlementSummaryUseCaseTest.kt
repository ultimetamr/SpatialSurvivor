package com.example.spatialsurvivor.domain.usecase

import com.example.spatialsurvivor.progression.SettlementCrystalReward
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildSettlementSummaryUseCaseTest {
    private val useCase = BuildSettlementSummaryUseCase()

    @Test
    fun formatsSurvivalDurationAsMinutesAndSeconds() {
        assertEquals(
            "20:05",
            useCase(
                1205.9f,
                emptyList(),
                "第10波",
                SettlementCrystalReward(10, 2, 30, 0, 42),
                42,
            ).survivalTimeText,
        )
    }

    @Test
    fun groupsRepeatedUpgradesInAcquisitionOrder() {
        val result =
            useCase(
                0f,
                listOf("ATTACK_DAMAGE", "PICKUP_RANGE", "ATTACK_DAMAGE"),
                "第1波",
                SettlementCrystalReward(5, 2, 0, 0, 7),
                100,
            )
        assertEquals(listOf("ATTACK_DAMAGE", "PICKUP_RANGE"), result.upgradeHistory.map { it.id })
        assertEquals(listOf(2, 1), result.upgradeHistory.map { it.count })
        assertEquals(3, result.upgradeCount)
    }

    @Test
    fun emptyUpgradeListProducesNoRows() {
        val result =
            useCase(
                -4f,
                emptyList(),
                "第1波",
                SettlementCrystalReward(0, 0, 0, 0, 0),
                0,
            )
        assertTrue(result.upgradeHistory.isEmpty())
        assertEquals("00:00", result.survivalTimeText)
    }
}
