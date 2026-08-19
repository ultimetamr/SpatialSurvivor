package com.example.spatialsurvivor.ui.hud.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.spatialsurvivor.R
import com.example.spatialsurvivor.game.PlayerHudState
import com.example.spatialsurvivor.game.SpatialHudRules
import com.pico.spatial.ui.design.LinearProgressIndicator
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material

@Composable
fun HealthHudPanel(
    state: PlayerHudState,
    modifier: Modifier = Modifier,
) {
    val damageFlash = remember { Animatable(0f) }
    LaunchedEffect(state.damageEventSequence) {
        if (state.damageEventSequence > 0L) {
            damageFlash.snapTo(1f)
            damageFlash.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = DAMAGE_FLASH_MILLIS),
            )
        }
    }
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier =
            Modifier
                .size(width = 520.dp, height = 104.dp)
                .then(modifier)
                .graphicsLayer { alpha = HEALTH_PANEL_ALPHA }
                .clip(shape)
                .backgroundMaterial(enable = true, style = Material.Thin),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.hud_health),
                    color = PicoTheme.colorScheme.labelSecondary,
                    style = PicoTheme.typography.labelLarge,
                )
                Text(
                    text = stringResource(R.string.hud_health_value, state.health, state.maxHealth),
                    color =
                        if (state.health * LOW_HEALTH_DENOMINATOR <= state.maxHealth) {
                            PicoTheme.colorScheme.error
                        } else {
                            PicoTheme.colorScheme.labelPrimary
                        },
                    style = PicoTheme.typography.titleMedium,
                )
            }
            LinearProgressIndicator(
                progress = {
                    SpatialHudRules.healthProgress(
                        current = state.health,
                        maximum = state.maxHealth,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
    }
}

private const val DAMAGE_FLASH_MILLIS = 520
private const val DAMAGE_FLASH_MAX_ALPHA = 0.42f
private const val HEALTH_PANEL_ALPHA = 0.86f
private const val LOW_HEALTH_DENOMINATOR = 4
