package com.example.spatialsurvivor.ui.upgrade

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.spatialsurvivor.ui.SpatialOverlayVisibility
import com.example.spatialsurvivor.ui.upgrade.components.UpgradeSelectionPanel
import com.example.spatialsurvivor.upgrade.UpgradePresentationState

@Composable
fun UpgradeScreen(
    snapshot: UpgradePresentationState,
    modifier: Modifier = Modifier,
) {
    if (!SpatialOverlayVisibility.upgrade(snapshot)) return

    val viewModel: UpgradeViewModel = viewModel(factory = UpgradeViewModel.Factory)
    LaunchedEffect(snapshot) {
        viewModel.onEvent(
            UpgradeEvent.Synchronize(
                UpgradeUiState(snapshot.visible, snapshot.options, snapshot.rerollAvailable),
            ),
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    key(state.visible, state.options.map { it.id }) {
        UpgradeSelectionPanel(
            state = state,
            onSelect = { viewModel.onEvent(UpgradeEvent.Select(it)) },
            onReroll = { viewModel.onEvent(UpgradeEvent.Reroll) },
            modifier = modifier,
        )
    }
}
