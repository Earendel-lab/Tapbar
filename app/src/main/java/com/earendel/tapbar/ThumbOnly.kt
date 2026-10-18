package com.earendel.tapbar

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun Modifier.thumbOnly(
    fraction: Float,
    touchRadius: Dp = 28.dp,
    thumbWidth: Dp = 20.dp
): Modifier {
    val current by rememberUpdatedState(fraction)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val width = size.width.toFloat()
            val thumbPx = thumbWidth.toPx()
            val center = thumbPx / 2f + (width - thumbPx) * current.coerceIn(0f, 1f)
            if (abs(down.position.x - center) > touchRadius.toPx()) {
                down.consume()
                var horizontal = 0f
                var vertical = 0f
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { change ->
                        val delta = change.positionChange()
                        horizontal += abs(delta.x)
                        vertical += abs(delta.y)
                        if (horizontal >= vertical) change.consume()
                    }
                } while (event.changes.any { it.pressed })
            }
        }
    }
}
