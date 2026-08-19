package com.example.spatialsurvivor.ui.settlement.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.spatialsurvivor.R
import com.example.spatialsurvivor.game.GameOutcome
import com.example.spatialsurvivor.ui.PANEL_BUTTON_LABEL
import com.example.spatialsurvivor.ui.PANEL_TEXT_ACCENT
import com.example.spatialsurvivor.ui.PANEL_TEXT_ERROR
import com.example.spatialsurvivor.ui.PANEL_TEXT_PRIMARY
import com.example.spatialsurvivor.ui.PANEL_TEXT_SECONDARY
import com.example.spatialsurvivor.ui.SpatialPanelScaffold
import com.example.spatialsurvivor.ui.blackPanelButtonColors
import com.example.spatialsurvivor.ui.settlement.SettlementUiState
import com.example.spatialsurvivor.ui.spatialBlackButtonLayer
import com.example.spatialsurvivor.upgrade.UpgradeId
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material

@Composable
fun SettlementResultPanel(
    state: SettlementUiState,
    onRestart: () -> Unit,
    onOpenPermanentProgression: () -> Unit,
    onReturnToMainMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot ?: return
    val animatedKills =
        animateIntAsState(
            targetValue = snapshot.kills,
            animationSpec = tween(durationMillis = 1000),
            label = "settlementKills",
        )
    val animatedEarnedCrystals =
        animateIntAsState(
            targetValue = state.summary.earnedCrystals,
            animationSpec = tween(durationMillis = 1000),
            label = "settlementEarnedCrystals",
        )
    val animatedTotalCrystals =
        animateIntAsState(
            targetValue = state.summary.totalCrystals,
            animationSpec = tween(durationMillis = 1000),
            label = "settlementTotalCrystals",
        )
    val titleColor =
        if (snapshot.outcome == GameOutcome.VICTORY) {
            PANEL_TEXT_ACCENT
        } else {
            PANEL_TEXT_ERROR
        }
    SpatialPanelScaffold(
        width = 1080.dp,
        height = 900.dp,
        modifier = modifier,
        horizontalPadding = 42.dp,
        verticalPadding = 32.dp,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text =
                stringResource(
                    if (snapshot.outcome == GameOutcome.VICTORY) {
                        R.string.settlement_victory_title
                    } else {
                        R.string.settlement_defeat_title
                    },
                ),
            color = titleColor,
            style = PicoTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        if (snapshot.outcome == GameOutcome.DEFEAT) {
            Text(
                text = stringResource(R.string.settlement_defeat_reason),
                color = PANEL_TEXT_ERROR,
                style = PicoTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ResultMetric(stringResource(R.string.settlement_survival_time), state.summary.survivalTimeText)
            ResultMetric(stringResource(R.string.settlement_kills), animatedKills.value.toString())
            ResultMetric(stringResource(R.string.settlement_wave), state.summary.waveText)
            ResultMetric(
                stringResource(R.string.settlement_upgrade_count),
                state.summary.upgradeCount.toString(),
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .backgroundMaterial(enable = true, style = Material.Regular)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text =
                    if (snapshot.outcome == GameOutcome.VICTORY) {
                        "本局获得晶核 ${animatedEarnedCrystals.value}（含通关奖励）"
                    } else {
                        "本局获得晶核 ${animatedEarnedCrystals.value}"
                    },
                color = PANEL_TEXT_ACCENT,
                style = PicoTheme.typography.titleLarge,
            )
            Text(
                text = "当前总晶核 ${animatedTotalCrystals.value}",
                color = PANEL_TEXT_PRIMARY,
                style = PicoTheme.typography.titleMedium,
            )
            state.summary.rewardLines.forEach { rewardLine ->
                Text(
                    text = "${rewardLine.label} ${rewardLine.value}",
                    color = PANEL_TEXT_SECONDARY,
                    style = PicoTheme.typography.bodyMedium,
                )
            }
        }
        Text(
            text = stringResource(R.string.settlement_upgrades),
            color = PANEL_TEXT_PRIMARY,
            style = PicoTheme.typography.titleMedium,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.summary.upgradeHistory.isEmpty()) {
                Text(
                    text = stringResource(R.string.settlement_no_upgrades),
                    color = PANEL_TEXT_SECONDARY,
                    style = PicoTheme.typography.bodyMedium,
                )
            } else {
                state.summary.upgradeHistory.forEach { item ->
                    val id = runCatching { UpgradeId.valueOf(item.id) }.getOrNull()
                    Text(
                        text =
                            if (id == null) {
                                "${item.id} ×${item.count}"
                            } else {
                                stringResource(id.titleResId()) + " ×${item.count}"
                            },
                        color = PANEL_TEXT_SECONDARY,
                        style = PicoTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Button(
                onClick = onRestart,
                colors = blackPanelButtonColors(),
                modifier = Modifier.weight(1f).spatialBlackButtonLayer().spatialHoverEffect(),
            ) {
                Text(
                    text = stringResource(R.string.settlement_restart),
                    color = PANEL_BUTTON_LABEL,
                    style = PicoTheme.typography.labelLarge,
                )
            }
            Button(
                onClick = onOpenPermanentProgression,
                colors = blackPanelButtonColors(),
                modifier = Modifier.weight(1f).spatialBlackButtonLayer().spatialHoverEffect(),
            ) {
                Text(text = "永久养成", color = PANEL_BUTTON_LABEL, style = PicoTheme.typography.labelLarge)
            }
            Button(
                onClick = onReturnToMainMenu,
                colors = blackPanelButtonColors(),
                modifier = Modifier.weight(1f).spatialBlackButtonLayer().spatialHoverEffect(),
            ) {
                Text(text = "返回主界面", color = PANEL_BUTTON_LABEL, style = PicoTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ResultMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = PANEL_TEXT_PRIMARY,
            style = PicoTheme.typography.titleLarge,
        )
        Text(
            text = label,
            color = PANEL_TEXT_SECONDARY,
            style = PicoTheme.typography.labelMedium,
        )
    }
}

private fun UpgradeId.titleResId(): Int =
    when (this) {
        UpgradeId.ORBITING_SWORD -> R.string.upgrade_orbiting_sword_title
        UpgradeId.CHAIN_LIGHTNING -> R.string.upgrade_chain_lightning_title
        UpgradeId.POISON_AURA -> R.string.upgrade_poison_aura_title
        UpgradeId.ATTACK_DAMAGE -> R.string.upgrade_attack_damage_title
        UpgradeId.ATTACK_SPEED -> R.string.upgrade_attack_speed_title
        UpgradeId.ATTACK_RANGE -> R.string.upgrade_attack_range_title
        UpgradeId.EXPERIENCE_GAIN -> R.string.upgrade_experience_gain_title
        UpgradeId.MOVEMENT_SPEED -> R.string.upgrade_movement_speed_title
        UpgradeId.PICKUP_RANGE -> R.string.upgrade_pickup_range_title
        else -> R.string.hud_active_skills
    }
