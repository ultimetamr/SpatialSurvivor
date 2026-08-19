package com.example.spatialsurvivor.ui.hud.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.spatialsurvivor.R
import com.example.spatialsurvivor.game.ActiveSkillHud
import com.example.spatialsurvivor.game.PlayerHudState
import com.example.spatialsurvivor.game.SpatialHudRules
import com.example.spatialsurvivor.upgrade.UpgradeId
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.LinearProgressIndicator
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material

@Composable
fun PlayerHudPanel(
    state: PlayerHudState,
    modifier: Modifier = Modifier,
    pauseInteractionEnabled: Boolean = true,
    onPauseRequested: (() -> Unit)? = null,
) {
    val targetAlpha = SpatialHudRules.hudTargetAlpha(state.hudDimmed)
    val damageFlash = remember { Animatable(0f) }
    LaunchedEffect(state.damageEventSequence) {
        if (state.damageEventSequence > 0L) {
            damageFlash.snapTo(1f)
            damageFlash.animateTo(0f, animationSpec = tween(durationMillis = DAMAGE_FLASH_MILLIS))
        }
    }
    val panelAlpha by
        animateFloatAsState(
            targetValue = targetAlpha,
            animationSpec = tween(durationMillis = HUD_OVERLAY_FADE_MILLIS),
            label = "viewHudAlpha",
        )
    val time = SpatialHudRules.timeParts(state.remainingSeconds)
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier =
            Modifier
                .size(width = 360.dp, height = 302.dp)
                .then(modifier)
                .graphicsLayer { alpha = panelAlpha }
                .clip(shape)
                .backgroundMaterial(enable = true, style = Material.Thin),
    ) {
        if (damageFlash.value > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            PicoTheme.colorScheme.error.copy(
                                alpha = damageFlash.value * DAMAGE_FLASH_MAX_ALPHA,
                            ),
                        ),
            )
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.hud_health),
                color = PicoTheme.colorScheme.labelPrimary,
                style = PicoTheme.typography.titleMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.hud_health_value, state.health, state.maxHealth),
                    color = if (state.health * LOW_HEALTH_DENOMINATOR <= state.maxHealth) PicoTheme.colorScheme.error else PicoTheme.colorScheme.labelPrimary,
                    style = PicoTheme.typography.labelLarge,
                )
                if (onPauseRequested != null && pauseInteractionEnabled) {
                    Button(
                        onClick = onPauseRequested,
                        modifier = Modifier.height(32.dp).spatialHoverEffect(),
                    ) {
                        Text(
                            text = "暂停",
                            style = PicoTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
        LinearProgressIndicator(
            progress = { SpatialHudRules.healthProgress(state.health, state.maxHealth) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.hud_level, state.level),
                color = PicoTheme.colorScheme.labelPrimary,
                style = PicoTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.hud_remaining_time, time.minutes, time.seconds),
                color = PicoTheme.colorScheme.labelSecondary,
                style = PicoTheme.typography.labelLarge,
            )
        }
        LinearProgressIndicator(
            progress = {
                SpatialHudRules.experienceProgress(
                    current = state.experience,
                    required = state.experienceRequired,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.hud_experience, state.experience, state.experienceRequired),
            color = PicoTheme.colorScheme.labelTertiary,
            style = PicoTheme.typography.labelSmall,
        )
        Text(
            text = stringResource(
                R.string.hud_combat_stats,
                state.attackDamage,
                state.attackIntervalSeconds,
                state.attackRangeMeters,
                state.projectileCount,
            ),
            color = PicoTheme.colorScheme.labelSecondary,
            style = PicoTheme.typography.labelSmall,
        )
        Text(
            text = stringResource(
                R.string.hud_growth_stats,
                state.pickupRangeMeters,
                state.healthRegenerationPerSecond,
                state.experienceGainMultiplier,
            ),
            color = PicoTheme.colorScheme.labelTertiary,
            style = PicoTheme.typography.labelSmall,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.hud_active_skills),
            color = PicoTheme.colorScheme.labelSecondary,
            style = PicoTheme.typography.labelMedium,
        )
        if (state.activeSkills.isEmpty()) {
            Text(
                text = stringResource(R.string.hud_no_active_skills),
                color = PicoTheme.colorScheme.labelTertiary,
                style = PicoTheme.typography.bodySmall,
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.activeSkills.forEach { skill -> ActiveSkillIcon(skill = skill) }
            }
        }
        }
        state.levelUpFeedback?.let { message ->
            Text(
                text = message,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                color = PicoTheme.colorScheme.labelPrimary,
                style = PicoTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ActiveSkillIcon(
    skill: ActiveSkillHud,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(PicoTheme.colorScheme.fillTertiary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(skill.id.iconResId()),
                contentDescription = stringResource(skill.id.titleResId()),
                tint = PicoTheme.colorScheme.labelPrimary,
                modifier = Modifier.size(19.dp),
            )
        }
        Text(
            text = stringResource(R.string.hud_skill_stacks, skill.stacks),
            color = PicoTheme.colorScheme.labelSecondary,
            style = PicoTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}

@DrawableRes
private fun UpgradeId.iconResId(): Int =
    when (this) {
        UpgradeId.ORBITING_SWORD -> R.drawable.ic_hud_orbiting_sword
        UpgradeId.CHAIN_LIGHTNING -> R.drawable.ic_hud_chain_lightning
        UpgradeId.POISON_AURA -> R.drawable.ic_hud_poison_aura
        else -> R.drawable.ic_hud_skill
    }

@StringRes
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

private const val HUD_OVERLAY_FADE_MILLIS = 160
private const val DAMAGE_FLASH_MILLIS = 300
private const val DAMAGE_FLASH_MAX_ALPHA = 0.42f
private const val LOW_HEALTH_DENOMINATOR = 4
