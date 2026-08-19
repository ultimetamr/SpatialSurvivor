package com.example.spatialsurvivor.ui.mainmenu

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.spatialsurvivor.game.AppUiPresentationState
import com.example.spatialsurvivor.game.AppUiRuntime
import com.example.spatialsurvivor.game.PermanentPanelOrigin
import com.example.spatialsurvivor.progression.PermanentProgressionRules
import com.example.spatialsurvivor.progression.PermanentProgressionState
import com.example.spatialsurvivor.ui.PANEL_BUTTON_LABEL
import com.example.spatialsurvivor.ui.PANEL_TEXT_ACCENT
import com.example.spatialsurvivor.ui.PANEL_TEXT_PRIMARY
import com.example.spatialsurvivor.ui.PANEL_TEXT_SECONDARY
import com.example.spatialsurvivor.ui.SpatialOverlayVisibility
import com.example.spatialsurvivor.ui.SpatialPanelScaffold
import com.example.spatialsurvivor.ui.blackPanelButtonColors
import com.example.spatialsurvivor.ui.spatialBlackButtonLayer
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect

@Composable
fun MainMenuScreen(
    appState: AppUiPresentationState,
    progressionState: PermanentProgressionState,
    modifier: Modifier = Modifier,
) {
    val visible = SpatialOverlayVisibility.mainMenu(appState)
    if (!visible) return
    key(visible, progressionState.totalCrystals) {
        MainMenuContent(progressionState = progressionState, modifier = modifier)
    }
}

@Composable
private fun MainMenuContent(
    progressionState: PermanentProgressionState,
    modifier: Modifier = Modifier,
) {
    val bonuses = PermanentProgressionRules.combatBonuses(progressionState)
    SpatialPanelScaffold(
        width = 960.dp,
        height = 720.dp,
        modifier = modifier,
    ) {
        Text(
            text = "Spatial Survivor",
            color = PANEL_TEXT_PRIMARY,
            style = PicoTheme.typography.headlineLarge,
        )
        Text(
            text = "MR幸存者 · 真实空间移动 · Spatial Stage战斗",
            color = PANEL_TEXT_SECONDARY,
            style = PicoTheme.typography.titleMedium,
        )
        Text(
            text = "当前晶核 ${progressionState.totalCrystals}",
            color = PANEL_TEXT_ACCENT,
            style = PicoTheme.typography.titleLarge,
        )
        Text(
            text =
                "开局：生命 ${bonuses.maxHealth} · 伤害 ${bonuses.projectileDamage} · " +
                    "范围 ${"%.1f".format(bonuses.attackRangeMeters)}米",
            color = PANEL_TEXT_SECONDARY,
            style = PicoTheme.typography.bodyMedium,
        )
        Button(
            onClick = { AppUiRuntime.requestStartRun() },
            colors = blackPanelButtonColors(),
            modifier = Modifier.fillMaxWidth().spatialBlackButtonLayer().spatialHoverEffect(),
        ) {
            Text("开始游戏", color = PANEL_BUTTON_LABEL, style = PicoTheme.typography.titleMedium)
        }
        Button(
            onClick = { AppUiRuntime.openPermanentPanel(PermanentPanelOrigin.MAIN_MENU) },
            colors = blackPanelButtonColors(),
            modifier = Modifier.fillMaxWidth().spatialBlackButtonLayer().spatialHoverEffect(),
        ) {
            Text("永久养成", color = PANEL_BUTTON_LABEL, style = PicoTheme.typography.titleMedium)
        }
    }
}
