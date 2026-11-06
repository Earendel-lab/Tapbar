package com.earendel.tapbar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.TextStyle

@Composable
fun ShimmerText(
    text: String,
    style: TextStyle,
    baseColor: Color,
    highlightColor: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerProgress"
    )
    val brush = remember(progress, baseColor, highlightColor) {
        object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
                val width = size.width.coerceAtLeast(1f)
                val band = width * 0.55f
                val start = -band + (width + band * 2f) * progress
                return LinearGradientShader(
                    from = Offset(start, 0f),
                    to = Offset(start + band, 0f),
                    colors = listOf(baseColor, highlightColor, baseColor),
                    colorStops = listOf(0f, 0.5f, 1f),
                    tileMode = TileMode.Clamp
                )
            }
        }
    }
    Text(
        text = text,
        style = style.copy(brush = brush),
        modifier = modifier
    )
}
