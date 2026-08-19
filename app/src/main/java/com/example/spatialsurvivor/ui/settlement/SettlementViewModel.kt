package com.example.spatialsurvivor.ui.settlement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.spatialsurvivor.domain.usecase.BuildSettlementSummaryUseCase
import com.example.spatialsurvivor.game.AppUiRuntime
import com.example.spatialsurvivor.game.PermanentPanelOrigin
import com.example.spatialsurvivor.game.GameSessionRuntime
import com.example.spatialsurvivor.progression.SettlementCrystalReward
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettlementViewModel(
    private val buildSummary: BuildSettlementSummaryUseCase = BuildSettlementSummaryUseCase(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettlementUiState())
    val state: StateFlow<SettlementUiState> = mutableState.asStateFlow()

    fun onEvent(event: SettlementEvent) {
        when (event) {
            is SettlementEvent.Synchronize -> {
                val snapshot = event.snapshot
                mutableState.update {
                    it.copy(
                        snapshot = snapshot,
                        summary =
                            if (snapshot == null) {
                                buildSummary(
                                    survivalSeconds = 0f,
                                    upgradeIds = emptyList(),
                                    waveText = "第1波",
                                    crystalReward = SettlementCrystalReward(0, 0, 0, 0, 0),
                                    totalCrystals = 0,
                                )
                            } else {
                                buildSummary(
                                    survivalSeconds = snapshot.survivalSeconds,
                                    upgradeIds = snapshot.acquiredUpgrades.map { id -> id.name },
                                    waveText = snapshot.waveText,
                                    crystalReward = snapshot.crystalReward,
                                    totalCrystals = snapshot.totalCrystalsAfterReward,
                                )
                            },
                    )
                }
            }
            SettlementEvent.Restart -> GameSessionRuntime.requestRestart()
            SettlementEvent.OpenPermanentProgression ->
                AppUiRuntime.openPermanentPanel(PermanentPanelOrigin.SETTLEMENT)
            SettlementEvent.ReturnToMainMenu -> AppUiRuntime.requestReturnToMainMenu()
        }
    }

    object Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettlementViewModel() as T
    }
}
