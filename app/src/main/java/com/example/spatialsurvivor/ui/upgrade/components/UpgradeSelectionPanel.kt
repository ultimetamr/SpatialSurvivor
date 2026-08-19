package com.example.spatialsurvivor.ui.upgrade.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.spatialsurvivor.R
import com.example.spatialsurvivor.ui.PANEL_BUTTON_LABEL
import com.example.spatialsurvivor.ui.PANEL_TEXT_PRIMARY
import com.example.spatialsurvivor.ui.PANEL_TEXT_SECONDARY
import com.example.spatialsurvivor.ui.SpatialPanelScaffold
import com.example.spatialsurvivor.ui.blackPanelButtonColors
import com.example.spatialsurvivor.ui.spatialBlackButtonLayer
import com.example.spatialsurvivor.ui.upgrade.UpgradeUiState
import com.example.spatialsurvivor.upgrade.UpgradeOption
import com.example.spatialsurvivor.upgrade.UpgradeRarity
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import kotlinx.coroutines.delay

@Composable
fun UpgradeSelectionPanel(
    state: UpgradeUiState,
    onSelect: (Int) -> Unit,
    onReroll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.visible) return
    SpatialPanelScaffold(
        width = 1080.dp,
        height = 440.dp,
        modifier = modifier,
        horizontalPadding = 28.dp,
        verticalPadding = 22.dp,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "升级强化 · 选择一项",
            color = PANEL_TEXT_PRIMARY,
            style = PicoTheme.typography.headlineMedium,
        )
        Text(
            text = "注视卡牌2秒高亮，捏合 / 手柄扳机 / 直接触碰确认",
            color = PANEL_TEXT_SECONDARY,
            style = PicoTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        ) {
            state.options.forEachIndexed { index, option ->
                UpgradeCard(
                    option = option,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
            }
        }
        if (state.rerollAvailable) {
            Button(
                onClick = onReroll,
                colors = blackPanelButtonColors(),
                modifier = Modifier.spatialBlackButtonLayer().spatialHoverEffect(),
            ) {
                Text("刷新本轮选项（1次）", color = PANEL_BUTTON_LABEL, style = PicoTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun UpgradeCard(option: UpgradeOption, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val definition = option.definition
    val shape = RoundedCornerShape(20.dp)
    var gazeFocused by remember(option.id) { mutableStateOf(false) }
    var gazeArmed by remember(option.id) { mutableStateOf(false) }
    LaunchedEffect(gazeFocused) {
        gazeArmed = false
        if (gazeFocused) {
            delay(GAZE_DWELL_MILLIS)
            if (gazeFocused) gazeArmed = true
        }
    }
    Box(
        modifier =
            Modifier
                .then(modifier)
                .onFocusChanged { focus -> gazeFocused = focus.isFocused }
                .border(
                    width = if (gazeArmed) 7.dp else 4.dp,
                    color = option.rarity.borderColor(),
                    shape = shape,
                )
                .padding(5.dp),
    ) {
        Button(
            onClick = onClick,
            colors = blackPanelButtonColors(),
            modifier = Modifier.fillMaxSize().spatialBlackButtonLayer().spatialHoverEffect(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = option.rarity.displayName,
                    color = option.rarity.borderColor(),
                    style = PicoTheme.typography.labelLarge,
                )
                // Card sits on solid black — use light labels, not glass dark text.
                Text(
                    text = definition.title,
                    color = PANEL_BUTTON_LABEL,
                    style = PicoTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = definition.description,
                    color = Color(0xFFDDDDDD),
                    style = PicoTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text =
                        when {
                            gazeArmed -> "已注视锁定 · 请确认"
                            definition.maxLevel > 1 -> "最高 ${definition.maxLevel} 级"
                            else -> "唯一强化"
                        },
                    color = Color(0xFFBBBBBB),
                    style = PicoTheme.typography.labelMedium,
                )
            }
        }
    }
}

private const val GAZE_DWELL_MILLIS = 2_000L

private val UpgradeRarity.displayName: String
    get() =
        when (this) {
            UpgradeRarity.COMMON -> "普通"
            UpgradeRarity.RARE -> "稀有"
            UpgradeRarity.EPIC -> "史诗"
            UpgradeRarity.LEGENDARY -> "传说进化"
        }

@Composable
private fun UpgradeRarity.borderColor(): Color =
    colorResource(
        when (this) {
            UpgradeRarity.COMMON -> R.color.rarity_common
            UpgradeRarity.RARE -> R.color.rarity_rare
            UpgradeRarity.EPIC -> R.color.rarity_epic
            UpgradeRarity.LEGENDARY -> R.color.rarity_legendary
        },
    )
