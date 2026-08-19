package com.example.spatialsurvivor.ui.settlement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.spatialsurvivor.game.AppUiPresentationState
import com.example.spatialsurvivor.game.GameSessionPresentationState
import com.example.spatialsurvivor.ui.SpatialOverlayVisibility
import com.example.spatialsurvivor.ui.settlement.components.SettlementResultPanel

@Composable
fun SettlementScreen(
    appState: AppUiPresentationState,
    sessionState: GameSessionPresentationState,
    modifier: Modifier = Modifier,
) {
    val interactive = SpatialOverlayVisibility.settlement(appState, sessionState)
    if (!interactive) return

    val snapshot = sessionState.settlement ?: return
    val viewModel: SettlementViewModel = viewModel(factory = SettlementViewModel.Factory)
    LaunchedEffect(snapshot) {
        viewModel.onEvent(SettlementEvent.Synchronize(snapshot))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    key(snapshot.outcome, snapshot.survivalSeconds.toInt()) {
        SettlementResultPanel(
            state = state,
            onRestart = { viewModel.onEvent(SettlementEvent.Restart) },
            onOpenPermanentProgression = { viewModel.onEvent(SettlementEvent.OpenPermanentProgression) },
            onReturnToMainMenu = { viewModel.onEvent(SettlementEvent.ReturnToMainMenu) },
            modifier = modifier,
        )
    }
}
