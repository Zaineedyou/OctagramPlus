package com.octagram.plus.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquid
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState

private val LocalOctagramLiquidState = staticCompositionLocalOf<LiquidState?> { null }

/** Records the app canvas so glass surfaces can refract and frost the live UI beneath them. */
@Composable
fun LiquidGlassProvider(content: @Composable () -> Unit) {
    val state = rememberLiquidState()
    CompositionLocalProvider(LocalOctagramLiquidState provides state) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .liquefiable(state),
        ) {
            content()
        }
    }
}

/** True runtime liquid-glass effect with a blush translucent tint and a readable rim. */
@Composable
fun Modifier.liquidGlassBackground(
    shape: Shape,
): Modifier {
    val state = LocalOctagramLiquidState.current
    val effect = if (state == null) {
        Modifier
    } else {
        Modifier.liquid(state) {
            refraction = 0.28f
            curve = 0.30f
            edge = 0.42f
            dispersion = 0.025f
            saturation = 1.06f
            contrast = 1.04f
            frost = 8.dp
            tint = Color.White.copy(alpha = 0.22f)
            this.shape = shape
        }
    }

    return this
        .shadow(
            elevation = 15.dp,
            shape = shape,
            clip = false,
            ambientColor = Primary.copy(alpha = 0.13f),
            spotColor = Primary.copy(alpha = 0.18f),
        )
        .then(effect)
        .clip(shape)
        .background(Color.White.copy(alpha = 0.13f), shape)
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.90f),
                    Color.White.copy(alpha = 0.30f),
                    Primary.copy(alpha = 0.34f),
                ),
            ),
            shape = shape,
        )
}
