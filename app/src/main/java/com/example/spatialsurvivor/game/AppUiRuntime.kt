package com.example.spatialsurvivor.game

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.atomic.AtomicBoolean

enum class PermanentPanelOrigin {
    MAIN_MENU,
    SETTLEMENT,
    /** In-run pause menu entry; uses the same world-lock placement path. */
    PAUSE,
}

data class AppUiPresentationState(
    val mainMenuVisible: Boolean = true,
    val permanentPanelVisible: Boolean = false,
    val permanentPanelOrigin: PermanentPanelOrigin = PermanentPanelOrigin.MAIN_MENU,
    val pausePanelVisible: Boolean = false,
)

object AppUiRuntime {
    private val mutableState = mutableStateOf(AppUiPresentationState())
    val state: State<AppUiPresentationState> = mutableState

    private val startRunRequested = AtomicBoolean(false)
    private val returnToMainMenuRequested = AtomicBoolean(false)

    val mainMenuVisible: Boolean
        get() = mutableState.value.mainMenuVisible

    val permanentPanelVisible: Boolean
        get() = mutableState.value.permanentPanelVisible

    val permanentPanelOrigin: PermanentPanelOrigin
        get() = mutableState.value.permanentPanelOrigin

    val pausePanelVisible: Boolean
        get() = mutableState.value.pausePanelVisible

    fun resetForColdStart() {
        mutableState.value = AppUiPresentationState()
        startRunRequested.set(false)
        returnToMainMenuRequested.set(false)
    }

    fun requestStartRun() {
        startRunRequested.set(true)
    }

    fun consumeStartRunRequest(): Boolean = startRunRequested.compareAndSet(true, false)

    fun showGameplay() {
        mutableState.value =
            AppUiPresentationState(
                mainMenuVisible = false,
                permanentPanelVisible = false,
                pausePanelVisible = false,
            )
    }

    fun requestReturnToMainMenu() {
        returnToMainMenuRequested.set(true)
    }

    fun consumeReturnToMainMenuRequest(): Boolean =
        returnToMainMenuRequested.compareAndSet(true, false)

    fun showMainMenu() {
        mutableState.value = AppUiPresentationState()
    }

    fun openPausePanel() {
        val current = mutableState.value
        if (current.mainMenuVisible || current.permanentPanelVisible) return
        mutableState.value =
            current.copy(
                pausePanelVisible = true,
                mainMenuVisible = false,
            )
    }

    fun closePausePanel() {
        mutableState.value = mutableState.value.copy(pausePanelVisible = false)
    }

    fun openPermanentPanel(origin: PermanentPanelOrigin) {
        val current = mutableState.value
        mutableState.value =
            current.copy(
                permanentPanelVisible = true,
                permanentPanelOrigin = origin,
                // Only the main-menu origin keeps the lobby visible underneath when closed later.
                mainMenuVisible = origin == PermanentPanelOrigin.MAIN_MENU,
                pausePanelVisible = origin == PermanentPanelOrigin.PAUSE && current.pausePanelVisible,
            )
    }

    fun closePermanentPanel() {
        val origin = mutableState.value.permanentPanelOrigin
        mutableState.value =
            mutableState.value.copy(
                permanentPanelVisible = false,
                mainMenuVisible = origin == PermanentPanelOrigin.MAIN_MENU,
                pausePanelVisible = origin == PermanentPanelOrigin.PAUSE,
            )
    }
}
