package com.example.spatialsurvivor.data.repository

import com.example.spatialsurvivor.upgrade.EvolutionOptionProvider
import com.example.spatialsurvivor.upgrade.UpgradeCatalog
import com.example.spatialsurvivor.upgrade.UpgradeOption
import com.example.spatialsurvivor.upgrade.UpgradeProfile
import kotlin.random.Random

interface UpgradeOptionRepository {
    fun drawThree(
        random: Random,
        profile: UpgradeProfile,
        evolutionProvider: EvolutionOptionProvider,
    ): List<UpgradeOption>
}

class CatalogUpgradeOptionRepository : UpgradeOptionRepository {
    override fun drawThree(
        random: Random,
        profile: UpgradeProfile,
        evolutionProvider: EvolutionOptionProvider,
    ): List<UpgradeOption> =
        UpgradeCatalog.drawThree(random, profile, evolutionProvider)
}
