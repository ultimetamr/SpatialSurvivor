package com.example.spatialsurvivor.domain.usecase

import com.example.spatialsurvivor.data.repository.CatalogUpgradeOptionRepository
import com.example.spatialsurvivor.data.repository.UpgradeOptionRepository
import com.example.spatialsurvivor.upgrade.EmptyEvolutionOptionProvider
import com.example.spatialsurvivor.upgrade.EvolutionOptionProvider
import com.example.spatialsurvivor.upgrade.UpgradeOption
import com.example.spatialsurvivor.upgrade.UpgradeProfile
import kotlin.random.Random

class DrawUpgradeOptionsUseCase(
    private val repository: UpgradeOptionRepository = CatalogUpgradeOptionRepository(),
) {
    operator fun invoke(
        random: Random,
        profile: UpgradeProfile,
        evolutionProvider: EvolutionOptionProvider = EmptyEvolutionOptionProvider,
    ): List<UpgradeOption> = repository.drawThree(random, profile, evolutionProvider)
}
