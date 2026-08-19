package com.example.spatialsurvivor.domain.usecase

import com.example.spatialsurvivor.upgrade.EvolutionOptionProvider
import com.example.spatialsurvivor.upgrade.UpgradeId
import com.example.spatialsurvivor.upgrade.UpgradeOption
import com.example.spatialsurvivor.upgrade.UpgradeProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DrawUpgradeOptionsUseCaseTest {
    private val useCase = DrawUpgradeOptionsUseCase()

    @Test
    fun returnsThreeUniqueBaseOptions() {
        val result = useCase(Random(31), UpgradeProfile())

        assertEquals(3, result.size)
        assertEquals(3, result.map { it.id }.toSet().size)
    }

    @Test
    fun replacesOneCardWhenEvolutionIsEligible() {
        val evolution =
            UpgradeOption(UpgradeId.NETHER_POISON_DOMAIN)
        val result =
            useCase(
                random = Random(32),
                profile = UpgradeProfile(stacks = mapOf(UpgradeId.POISON_AURA to 5)),
                evolutionProvider = EvolutionOptionProvider { listOf(evolution) },
            )

        assertEquals(3, result.size)
        assertTrue(result.contains(evolution))
    }
}
