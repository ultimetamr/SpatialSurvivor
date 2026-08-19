package com.example.spatialsurvivor.ui.progression

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.spatialsurvivor.game.AppUiPresentationState
import com.example.spatialsurvivor.game.AppUiRuntime
import com.example.spatialsurvivor.progression.PermanentProgressionRules
import com.example.spatialsurvivor.progression.PermanentProgressionRuntime
import com.example.spatialsurvivor.progression.PermanentProgressionState
import com.example.spatialsurvivor.progression.PermanentUpgradeCatalog
import com.example.spatialsurvivor.progression.PermanentUpgradeCategory
import com.example.spatialsurvivor.progression.PermanentUpgradeDefinition
import com.example.spatialsurvivor.progression.PermanentUpgradePreview
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
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material

/** World-locked permanent progression panel. Glass sunk behind clickable layers. */
@Composable
fun PermanentProgressionScreen(
    appState: AppUiPresentationState,
    progressionState: PermanentProgressionState,
    modifier: Modifier = Modifier,
) {
    val visible = SpatialOverlayVisibility.permanent(appState)
    if (!visible) return
    key(visible, progressionState.totalCrystals) {
        PermanentProgressionContent(
            progressionState = progressionState,
            modifier = modifier,
        )
    }
}

@Composable
private fun PermanentProgressionContent(
    progressionState: PermanentProgressionState,
    modifier: Modifier = Modifier,
) {
    val combatBonuses = PermanentProgressionRules.combatBonuses(progressionState)

    SpatialPanelScaffold(
        width = 1200.dp,
        height = 960.dp,
        modifier = modifier,
        horizontalPadding = 28.dp,
        verticalPadding = 24.dp,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "永久养成",
            color = PANEL_TEXT_PRIMARY,
            style = PicoTheme.typography.headlineLarge,
        )
        Text(
            text = "当前总晶核 ${progressionState.totalCrystals}",
            color = PANEL_TEXT_ACCENT,
            style = PicoTheme.typography.titleLarge,
        )
        BonusSummaryRow(
            health = combatBonuses.maxHealth,
            damage = combatBonuses.projectileDamage,
            intervalSeconds = combatBonuses.attackIntervalSeconds,
            rangeMeters = combatBonuses.attackRangeMeters,
            pickupMeters = combatBonuses.pickupRangeMeters,
            experienceMultiplier = combatBonuses.experienceGainMultiplier,
            crystalMultiplier =
                PermanentProgressionRules.crystalIncomeMultiplier(
                    progressionState.crystalMultiplierLevel,
                ),
            waveMultiplier =
                PermanentProgressionRules.waveRewardMultiplier(
                    progressionState.waveRewardLevel,
                ),
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategorySection(
                title = "战斗养成",
                definitions = PermanentUpgradeCatalog.byCategory(PermanentUpgradeCategory.COMBAT),
                progressionState = progressionState,
            )
            CategorySection(
                title = "经济养成",
                definitions = PermanentUpgradeCatalog.byCategory(PermanentUpgradeCategory.ECONOMY),
                progressionState = progressionState,
            )
        }
        Button(
            onClick = { AppUiRuntime.closePermanentPanel() },
            colors = blackPanelButtonColors(),
            modifier = Modifier.fillMaxWidth().spatialBlackButtonLayer().spatialHoverEffect(),
        ) {
            Text("关闭面板", color = PANEL_BUTTON_LABEL, style = PicoTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun BonusSummaryRow(
    health: Int,
    damage: Int,
    intervalSeconds: Float,
    rangeMeters: Float,
    pickupMeters: Float,
    experienceMultiplier: Float,
    crystalMultiplier: Float,
    waveMultiplier: Float,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .backgroundMaterial(enable = true, style = Material.Regular)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text =
                "战斗：生命 $health · 伤害 $damage · 间隔 ${"%.2f".format(intervalSeconds)}s · " +
                    "范围 ${"%.1f".format(rangeMeters)}m · 拾取 ${"%.1f".format(pickupMeters)}m",
            color = PANEL_TEXT_SECONDARY,
            style = PicoTheme.typography.bodyMedium,
        )
        Text(
            text =
                "成长：经验 ×${"%.2f".format(experienceMultiplier)} · " +
                    "晶核 ×${"%.2f".format(crystalMultiplier)} · " +
                    "波次奖励 ×${"%.2f".format(waveMultiplier)}",
            color = PANEL_TEXT_SECONDARY,
            style = PicoTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CategorySection(
    title: String,
    definitions: List<PermanentUpgradeDefinition>,
    progressionState: PermanentProgressionState,
) {
    Text(
        text = title,
        color = PANEL_TEXT_PRIMARY,
        style = PicoTheme.typography.titleMedium,
    )
    definitions.forEach { definition ->
        val preview =
            PermanentProgressionRules.preview(
                definition = definition,
                level = progressionState.levelOf(definition.type),
            )
        UpgradeCard(
            definition = definition,
            preview = preview,
            canAfford =
                preview.nextCost?.let { cost -> progressionState.totalCrystals >= cost } == true,
            onPurchase = { PermanentProgressionRuntime.purchase(definition.type) },
        )
    }
}

@Composable
private fun UpgradeCard(
    definition: PermanentUpgradeDefinition,
    preview: PermanentUpgradePreview,
    canAfford: Boolean,
    onPurchase: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .backgroundMaterial(enable = true, style = Material.Regular)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${definition.title}  Lv.${preview.level}/${preview.maxLevel}",
                color = PANEL_TEXT_PRIMARY,
                style = PicoTheme.typography.titleMedium,
            )
            Text(
                text = if (preview.isMaxed) "已满级" else "可升级",
                color =
                    if (preview.isMaxed) {
                        PANEL_TEXT_SECONDARY
                    } else {
                        PANEL_TEXT_ACCENT
                    },
                style = PicoTheme.typography.labelLarge,
            )
        }
        Text(
            text = definition.description,
            color = PANEL_TEXT_SECONDARY,
            style = PicoTheme.typography.bodyMedium,
        )
        Text(
            text =
                if (preview.nextEffectText == null) {
                    "当前：${preview.currentEffectText}"
                } else {
                    "当前：${preview.currentEffectText}  →  下一级：${preview.nextEffectText}"
                },
            color = PANEL_TEXT_SECONDARY,
            style = PicoTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    when {
                        preview.isMaxed -> "已达上限"
                        preview.nextCost != null -> "升级消耗 ${preview.nextCost} 晶核"
                        else -> "升级消耗 —"
                    },
                color = PANEL_TEXT_SECONDARY,
                style = PicoTheme.typography.bodyMedium,
            )
            Button(
                onClick = onPurchase,
                enabled = !preview.isMaxed && canAfford,
                colors = blackPanelButtonColors(),
                modifier = Modifier.spatialBlackButtonLayer().spatialHoverEffect(),
            ) {
                Text(
                    text =
                        when {
                            preview.isMaxed -> "满级"
                            canAfford -> "升级"
                            else -> "晶核不足"
                        },
                    color = PANEL_BUTTON_LABEL,
                    style = PicoTheme.typography.labelLarge,
                )
            }
        }
    }
}
