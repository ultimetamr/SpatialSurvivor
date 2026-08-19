package com.example.spatialsurvivor.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.example.spatialsurvivor.game.GameLoopSystem
import com.example.spatialsurvivor.game.GameRuntime
import com.example.spatialsurvivor.game.GameSessionRuntime
import com.example.spatialsurvivor.game.GameplaySceneFactory
import com.example.spatialsurvivor.game.AppUiGameplay
import com.example.spatialsurvivor.game.AppUiRuntime
import com.example.spatialsurvivor.game.MainMenuPanelComponent
import com.example.spatialsurvivor.game.PausePanelComponent
import com.example.spatialsurvivor.game.PermanentProgressionPanelComponent
import com.example.spatialsurvivor.game.SettlementPanelComponent
import com.example.spatialsurvivor.game.SettlementGameplay
import com.example.spatialsurvivor.game.SpatialHudPanelComponent
import com.example.spatialsurvivor.game.SceneMeshRuntime
import com.example.spatialsurvivor.game.SpatialTrackingRuntime
import com.example.spatialsurvivor.monster.MonsterVisualAsyncLoader
import com.example.spatialsurvivor.player.PlayerComponent
import com.example.spatialsurvivor.progression.PermanentProgressionRuntime
import com.example.spatialsurvivor.upgrade.SkillVisualAsyncLoader
import com.example.spatialsurvivor.ui.SpatialOverlayVisibility
import com.example.spatialsurvivor.ui.hud.components.PlayerHudPanel
import com.example.spatialsurvivor.ui.mainmenu.MainMenuScreen
import com.example.spatialsurvivor.ui.pause.PauseScreen
import com.example.spatialsurvivor.ui.progression.PermanentProgressionScreen
import com.example.spatialsurvivor.ui.settlement.SettlementScreen
import com.example.spatialsurvivor.ui.upgrade.UpgradeScreen
import com.example.spatialsurvivor.upgrade.UpgradeGameplay
import com.example.spatialsurvivor.upgrade.UpgradePanelComponent
import com.example.spatialsurvivor.upgrade.UpgradeRuntime
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.SortAsUIElementComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.sense.mesh.MeshTrackingManager
import com.pico.spatial.tracking.eye.EyeTrackingProvider
import com.pico.spatial.tracking.controller.ControllerTrackingProvider
import com.pico.spatial.tracking.hand.HandTrackingProvider
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.dsl.registerSystem
import com.pico.spatial.ui.foundation.dsl.unregisterSystem

@Composable
fun SurvivorStage() {
    val hmdTrackingProvider = remember { HMDTrackingProvider() }
    val handTrackingProvider = remember { HandTrackingProvider() }
    val eyeTrackingProvider = remember { EyeTrackingProvider() }
    val controllerTrackingProvider = remember { ControllerTrackingProvider() }
    val gameplayScene = remember { GameplaySceneFactory.create() }
    val gameplayRoot = gameplayScene.root
    val hudState = GameRuntime.hudState.value
    val sessionState = GameSessionRuntime.state.value
    val upgradeState = UpgradeRuntime.state.value
    val appUiState = AppUiRuntime.state.value
    val progressionState = PermanentProgressionRuntime.state.value

    LaunchedEffect(gameplayScene.monsters, gameplayScene.skillHydrateJobs) {
        MonsterVisualAsyncLoader.hydratePool(gameplayScene.monsters)
        SkillVisualAsyncLoader.hydrate(gameplayScene.skillHydrateJobs)
    }

    DisposableEffect(Unit) {
        AppUiRuntime.resetForColdStart()
        GameRuntime.reset()
        registerSystem<GameLoopSystem>()
        SpatialTrackingRuntime.bind(
            hmd = hmdTrackingProvider,
            hands = handTrackingProvider,
            eyes = eyeTrackingProvider,
            controllers = controllerTrackingProvider,
        )
        val startReport = SpatialTrackingRuntime.start()

        val meshSubscription =
            MeshTrackingManager.subscribeAnchorUpdate(SceneMeshRuntime::onAnchorUpdate)
        MeshTrackingManager.start()

        Log.i(TAG, "Tracking started: $startReport")
        Log.i(TAG, "Scene Mesh state: ${MeshTrackingManager.state}")

        onDispose {
            MeshTrackingManager.stop()
            meshSubscription.cancel()
            SceneMeshRuntime.clear()
            SpatialTrackingRuntime.stop()
            AppUiGameplay.unbindPanels()
            SettlementGameplay.unbindPanel()
            UpgradeGameplay.unbindPanel()
            unregisterSystem<GameLoopSystem>()
            gameplayRoot.destroy()
        }
    }

    SpatialView(
        initial = { content, attachments ->
            content.addEntity(gameplayRoot)

            val viewHud =
                checkNotNull(attachments.entity(id = VIEW_HUD_PANEL_ID)) {
                    "HUD AttachmentPanel was not created for id=$VIEW_HUD_PANEL_ID"
                }
            viewHud.components.set(SpatialHudPanelComponent())
            viewHud.components.set(
                SortAsUIElementComponent(distanceBias = HUD_UI_DISTANCE_BIAS_METERS),
            )
            // Stay visible before the first HMD sample arrives; the fixed-step follower takes over next.
            checkNotNull(viewHud.components[TransformComponent::class.java]) {
                "HUD AttachmentPanel has no TransformComponent"
            }.apply {
                setPosition(Vector3(0f, INITIAL_HUD_Y_METERS, -INITIAL_HUD_FORWARD_METERS))
                setEulerAngles(EulerAngles(0f, 0f, 0f))
                // Cold start shows the main menu; keep HUD out of the ray path until gameplay.
                setScaleVector(Vector3.ZERO)
            }
            content.addEntity(viewHud)
            Log.i(TAG, "HUD panel mounted and visible before tracking synchronization")

            attachments.entity(id = SETTLEMENT_PANEL_ID)?.let { settlementPanel ->
                settlementPanel.components.set(SettlementPanelComponent())
                settlementPanel.components.set(
                    SortAsUIElementComponent(distanceBias = BACK_LAYER_UI_DISTANCE_BIAS_METERS),
                )
                settlementPanel.components[TransformComponent::class.java]?.apply {
                    setPosition(Vector3(0f, 1.6f, -INITIAL_OVERLAY_FORWARD_METERS))
                    setScaleVector(Vector3.ZERO)
                }
                content.addEntity(settlementPanel)
                SettlementGameplay.bindPanel(settlementPanel)
            }

            val mainMenuPanel =
                checkNotNull(attachments.entity(id = MAIN_MENU_PANEL_ID)) {
                    "Main menu AttachmentPanel was not created for id=$MAIN_MENU_PANEL_ID"
                }
            mainMenuPanel.components.set(MainMenuPanelComponent())
            mainMenuPanel.components.set(
                SortAsUIElementComponent(distanceBias = BACK_LAYER_UI_DISTANCE_BIAS_METERS),
            )
            mainMenuPanel.components[TransformComponent::class.java]?.apply {
                setPosition(Vector3(0f, PlayerComponent.DEFAULT_HEAD_HEIGHT_METERS, -INITIAL_OVERLAY_FORWARD_METERS))
                setScaleVector(Vector3.ONE)
            }
            content.addEntity(mainMenuPanel)
            AppUiGameplay.bindMainMenuPanel(mainMenuPanel)

            val permanentPanel =
                checkNotNull(attachments.entity(id = PERMANENT_PANEL_ID)) {
                    "Permanent progression AttachmentPanel was not created for id=$PERMANENT_PANEL_ID"
                }
            permanentPanel.components.set(PermanentProgressionPanelComponent())
            permanentPanel.components.set(
                SortAsUIElementComponent(distanceBias = FRONT_LAYER_UI_DISTANCE_BIAS_METERS),
            )
            permanentPanel.components[TransformComponent::class.java]?.apply {
                setPosition(Vector3(0f, PlayerComponent.DEFAULT_HEAD_HEIGHT_METERS, -INITIAL_OVERLAY_FORWARD_METERS))
                setScaleVector(Vector3.ZERO)
            }
            content.addEntity(permanentPanel)
            AppUiGameplay.bindPermanentPanel(permanentPanel)

            val upgradePanel =
                checkNotNull(attachments.entity(id = UPGRADE_PANEL_ID)) {
                    "Upgrade AttachmentPanel was not created for id=$UPGRADE_PANEL_ID"
                }
            upgradePanel.components.set(UpgradePanelComponent())
            upgradePanel.components.set(
                SortAsUIElementComponent(distanceBias = UPGRADE_UI_DISTANCE_BIAS_METERS),
            )
            checkNotNull(upgradePanel.components[TransformComponent::class.java]) {
                "Upgrade AttachmentPanel has no TransformComponent"
            }.apply {
                setPosition(Vector3(0f, PlayerComponent.DEFAULT_HEAD_HEIGHT_METERS, -INITIAL_OVERLAY_FORWARD_METERS))
                setScaleVector(Vector3.ZERO)
            }
            content.addEntity(upgradePanel)
            UpgradeGameplay.bindPanel(upgradePanel)
            Log.i(TAG, "Upgrade panel mounted on SpatialScene root with highest UI sort bias")

            val pausePanel =
                checkNotNull(attachments.entity(id = PAUSE_PANEL_ID)) {
                    "Pause AttachmentPanel was not created for id=$PAUSE_PANEL_ID"
                }
            pausePanel.components.set(PausePanelComponent())
            pausePanel.components.set(
                SortAsUIElementComponent(distanceBias = BACK_LAYER_UI_DISTANCE_BIAS_METERS),
            )
            pausePanel.components[TransformComponent::class.java]?.apply {
                setPosition(Vector3(0f, PlayerComponent.DEFAULT_HEAD_HEIGHT_METERS, -INITIAL_OVERLAY_FORWARD_METERS))
                setScaleVector(Vector3.ZERO)
            }
            content.addEntity(pausePanel)
            AppUiGameplay.bindPausePanel(pausePanel)

        },
        attachments = {
            AttachmentPanel(id = VIEW_HUD_PANEL_ID) {
                val hudInteractive =
                    SpatialOverlayVisibility.hudPanel(
                        app = appUiState,
                        session = sessionState,
                        upgradeVisible = upgradeState.visible,
                    )
                if (!hudInteractive) return@AttachmentPanel
                PlayerHudPanel(
                    state = hudState,
                    pauseInteractionEnabled = true,
                    onPauseRequested = { AppUiRuntime.openPausePanel() },
                )
            }
            AttachmentPanel(id = SETTLEMENT_PANEL_ID) {
                SettlementScreen(appState = appUiState, sessionState = sessionState)
            }
            AttachmentPanel(id = MAIN_MENU_PANEL_ID) {
                MainMenuScreen(appState = appUiState, progressionState = progressionState)
            }
            AttachmentPanel(id = PERMANENT_PANEL_ID) {
                PermanentProgressionScreen(appState = appUiState, progressionState = progressionState)
            }
            AttachmentPanel(id = PAUSE_PANEL_ID) {
                PauseScreen(appState = appUiState)
            }
            AttachmentPanel(id = UPGRADE_PANEL_ID) {
                UpgradeScreen(snapshot = upgradeState)
            }
        },
    )
}

private const val TAG = "SpatialSurvivor"
private const val VIEW_HUD_PANEL_ID = "survivor-view-hud"
private const val SETTLEMENT_PANEL_ID = "survivor-settlement"
private const val MAIN_MENU_PANEL_ID = "survivor-main-menu"
private const val PERMANENT_PANEL_ID = "survivor-permanent-panel"
private const val PAUSE_PANEL_ID = "survivor-pause-panel"
private const val UPGRADE_PANEL_ID = "survivor-upgrade-modal"
private const val OVERLAY_UI_DISTANCE_BIAS_METERS = 0.8f
private const val BACK_LAYER_UI_DISTANCE_BIAS_METERS = 0.8f
private const val FRONT_LAYER_UI_DISTANCE_BIAS_METERS = 1.1f
private const val UPGRADE_UI_DISTANCE_BIAS_METERS = 1.25f
private const val HUD_UI_DISTANCE_BIAS_METERS = 0.18f
private const val INITIAL_HUD_Y_METERS = 1.29f
private const val INITIAL_HUD_FORWARD_METERS = 1.16f
private const val INITIAL_OVERLAY_FORWARD_METERS = 1.0f
