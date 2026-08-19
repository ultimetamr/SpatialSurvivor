package com.example.spatialsurvivor.ui.pause

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.spatialsurvivor.game.AppUiPresentationState
import com.example.spatialsurvivor.game.AppUiRuntime
import com.example.spatialsurvivor.game.PermanentPanelOrigin
import com.example.spatialsurvivor.ui.PANEL_BUTTON_LABEL
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
fun PauseScreen(
    appState: AppUiPresentationState,
    modifier: Modifier = Modifier,
) {
    val visible = SpatialOverlayVisibility.pause(appState)
    if (!visible) return
    key(visible) {
        PauseContent(modifier = modifier)
    }
}

@Composable
private fun PauseContent(modifier: Modifier = Modifier) {
    SpatialPanelScaffold(
        width = 900.dp,
        height = 640.dp,
        modifier = modifier,
    ) {
        Text(
            text = "游戏暂停",
            color = PANEL_TEXT_PRIMARY,
            style = PicoTheme.typography.headlineLarge,
        )
        Text(
            text = "战斗已冻结",
            color = PANEL_TEXT_SECONDARY,
            style = PicoTheme.typography.bodyMedium,
        )
        Button(
            onClick = { AppUiRuntime.closePausePanel() },
            colors = blackPanelButtonColors(),
            modifier = Modifier.fillMaxWidth().spatialBlackButtonLayer().spatialHoverEffect(),
        ) {
            Text("继续游戏", color = PANEL_BUTTON_LABEL, style = PicoTheme.typography.titleMedium)
        }
        Button(
            onClick = { AppUiRuntime.openPermanentPanel(PermanentPanelOrigin.PAUSE) },
            colors = blackPanelButtonColors(),
            modifier = Modifier.fillMaxWidth().spatialBlackButtonLayer().spatialHoverEffect(),
        ) {
            Text("永久养成", color = PANEL_BUTTON_LABEL, style = PicoTheme.typography.titleMedium)
        }
        Button(
            onClick = { AppUiRuntime.requestReturnToMainMenu() },
            colors = blackPanelButtonColors(),
            modifier = Modifier.fillMaxWidth().spatialBlackButtonLayer().spatialHoverEffect(),
        ) {
            Text("返回主界面", color = PANEL_BUTTON_LABEL, style = PicoTheme.typography.titleMedium)
        }
    }
}
