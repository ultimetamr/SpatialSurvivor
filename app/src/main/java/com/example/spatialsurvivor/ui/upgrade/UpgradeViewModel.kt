package com.example.spatialsurvivor.ui.upgrade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.spatialsurvivor.upgrade.UpgradeRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UpgradeViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(UpgradeUiState())
    val state: StateFlow<UpgradeUiState> = mutableState.asStateFlow()

    fun onEvent(event: UpgradeEvent) {
        when (event) {
            is UpgradeEvent.Synchronize -> mutableState.value = event.state
            is UpgradeEvent.Select -> UpgradeRuntime.requestSelection(event.index)
            UpgradeEvent.Reroll -> UpgradeRuntime.requestReroll()
        }
    }

    object Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = UpgradeViewModel() as T
    }
}
