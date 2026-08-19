package com.example.spatialsurvivor.ui.settlement

import com.example.spatialsurvivor.domain.model.SettlementSummary
import com.example.spatialsurvivor.game.SettlementSnapshot

data class SettlementUiState(
    val snapshot: SettlementSnapshot? = null,
    val summary: SettlementSummary = SettlementSummary("00:00"),
)

sealed interface SettlementEvent {
    data class Synchronize(val snapshot: SettlementSnapshot?) : SettlementEvent

    data object Restart : SettlementEvent
    data object OpenPermanentProgression : SettlementEvent
    data object ReturnToMainMenu : SettlementEvent
}
