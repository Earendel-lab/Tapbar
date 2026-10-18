package com.earendel.tapbar

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val RevealEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Composable
fun Modifier.revealOnEnter(order: Int): Modifier {
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Throwable) {
            false
        }
    }
    val progress = remember { Animatable(if (reducedMotion) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 620,
                    delayMillis = 120 + order.coerceIn(0, 8) * 50,
                    easing = RevealEasing
                )
            )
        }
    }

    val travel = with(LocalDensity.current) { 26.dp.toPx() }

    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * travel
    }
}
