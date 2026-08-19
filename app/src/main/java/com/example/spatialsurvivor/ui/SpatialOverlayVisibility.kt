package com.example.spatialsurvivor.ui

import com.example.spatialsurvivor.game.AppUiPresentationState
import com.example.spatialsurvivor.game.GameSessionPresentationState
import com.example.spatialsurvivor.game.PermanentPanelOrigin
import com.example.spatialsurvivor.game.SettlementPhase
import com.example.spatialsurvivor.ui.upgrade.UpgradeUiState
import com.example.spatialsurvivor.upgrade.UpgradePresentationState

/**
 * Compose hit-test must match ECS scale gating. Hidden overlay content must not
 * synthesize Buttons, or stacked AttachmentPanels steal pointer hits.
 */
object SpatialOverlayVisibility {
    fun mainMenu(app: AppUiPresentationState): Boolean =
        app.mainMenuVisible && !app.permanentPanelVisible

    fun pause(app: AppUiPresentationState): Boolean =
        app.pausePanelVisible && !app.permanentPanelVisible

    fun permanent(app: AppUiPresentationState): Boolean = app.permanentPanelVisible

    fun upgrade(upgrade: UpgradePresentationState): Boolean = upgrade.visible

    fun upgrade(upgrade: UpgradeUiState): Boolean = upgrade.visible

    fun settlement(
        app: AppUiPresentationState,
        session: GameSessionPresentationState,
    ): Boolean {
        if (session.settlement == null) return false
        if (session.settlementPhase != SettlementPhase.PANEL) return false
        if (app.pausePanelVisible) return false
        val coveredByPermanent =
            app.permanentPanelVisible &&
                app.permanentPanelOrigin == PermanentPanelOrigin.SETTLEMENT
        return !coveredByPermanent
    }

    /** Whole HUD AttachmentPanel — hide Compose + ECS while any modal overlay is up. */
    fun hudPanel(
        app: AppUiPresentationState,
        session: GameSessionPresentationState,
        upgradeVisible: Boolean,
    ): Boolean =
        !app.mainMenuVisible &&
            !app.permanentPanelVisible &&
            !app.pausePanelVisible &&
            session.settlement == null &&
            !upgradeVisible

    fun hudPauseButton(
        app: AppUiPresentationState,
        session: GameSessionPresentationState,
        upgradeVisible: Boolean,
    ): Boolean = hudPanel(app, session, upgradeVisible)
}
