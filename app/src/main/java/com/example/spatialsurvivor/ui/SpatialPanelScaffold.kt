package com.example.spatialsurvivor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.foundation.layout.zOffset
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material

/**
 * Frosted-glass panel plate with interactive content floated in front so rays
 * hit Buttons instead of the glass compositor surface.
 */
@Composable
fun SpatialPanelScaffold(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    glassStyle: Material = Material.Thick,
    horizontalPadding: Dp = 36.dp,
    verticalPadding: Dp = 32.dp,
    verticalArrangement: Arrangement.Vertical =
        Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    fun Modifier.panelZ(dp: Dp): Modifier =
        zOffset { with(density) { dp.toPx() } }

    Box(modifier = Modifier.size(width = width, height = height).then(modifier)) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .panelZ(GLASS_SINK_Z)
                    .backgroundMaterial(enable = true, style = glassStyle),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .panelZ(CONTENT_FLOAT_Z)
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/** Black fill + forward Z so button chrome and ray hits stay reliable on glass. */
@Composable
fun Modifier.spatialBlackButtonLayer(): Modifier {
    val density = LocalDensity.current
    return this
        .background(PANEL_BUTTON_BLACK, RoundedCornerShape(BUTTON_CORNER))
        .zOffset { with(density) { BUTTON_FLOAT_Z.toPx() } }
}

@Composable
fun blackPanelButtonColors() =
    ButtonDefaults.buttonColors(
        containerColor = PANEL_BUTTON_BLACK,
        contentColor = PANEL_BUTTON_LABEL,
    )

/** Dark body copy for frosted glass over bright MR passthrough. */
val PANEL_TEXT_PRIMARY: Color = Color(0xFF111111)
val PANEL_TEXT_SECONDARY: Color = Color(0xFF333333)
val PANEL_TEXT_ACCENT: Color = Color(0xFF0A7A3E)
val PANEL_TEXT_ERROR: Color = Color(0xFFB00020)

/** White labels only on solid black buttons. */
val PANEL_BUTTON_BLACK: Color = Color.Black
val PANEL_BUTTON_LABEL: Color = Color.White

private val GLASS_SINK_Z = (-6).dp
private val CONTENT_FLOAT_Z = 8.dp
private val BUTTON_FLOAT_Z = 12.dp
private val BUTTON_CORNER = 14.dp
