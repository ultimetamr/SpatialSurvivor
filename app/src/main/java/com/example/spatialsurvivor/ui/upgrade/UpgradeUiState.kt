package com.example.spatialsurvivor.ui.upgrade

import com.example.spatialsurvivor.upgrade.UpgradeOption

data class UpgradeUiState(
    val visible: Boolean = false,
    val options: List<UpgradeOption> = emptyList(),
    val rerollAvailable: Boolean = false,
)

sealed interface UpgradeEvent {
    data class Synchronize(val state: UpgradeUiState) : UpgradeEvent
    data class Select(val index: Int) : UpgradeEvent
    data object Reroll : UpgradeEvent
}
