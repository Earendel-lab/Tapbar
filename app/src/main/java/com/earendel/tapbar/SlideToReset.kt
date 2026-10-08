package com.earendel.tapbar

import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ResetRed = Color(0xFFD71921)

@Composable
fun SlideToReset(
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.slide_to_reset),
    onReset: () -> Unit,
) {
    val haptics = LocalHapticManager.current
    val view = LocalView.current
    val resetActionLabel = stringResource(R.string.reset_settings_action)
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val trackHeight = 64.dp
    val padding = 6.dp
    val thumbSize = trackHeight - padding * 2

    val thumbPx = with(density) { thumbSize.toPx() }
    val padPx = with(density) { padding.toPx() }
    val iconSize = 26.dp
    val iconPx = with(density) { iconSize.toPx() }
    val gapPx = with(density) { 10.dp.toPx() }

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val maxTravel = (trackWidthPx - thumbPx - padPx * 2).coerceAtLeast(1f)

    val offsetPx = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    var busy by remember { mutableStateOf(false) }
    var lastBucket by remember { mutableIntStateOf(0) }
    var atEnd by remember { mutableStateOf(false) }

    val progress = (offsetPx.value / maxTravel).coerceIn(0f, 1f)

    fun finish() {
        if (busy) return
        busy = true
        scope.launch {
            offsetPx.animateTo(maxTravel, tween(140, easing = FastOutSlowInEasing))
            haptics.performConfirm(view)
            launch { spin.animateTo(360f, tween(650, easing = FastOutSlowInEasing)) }
            onReset()
            delay(420)
            offsetPx.animateTo(
                0f,
                spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow)
            )
            spin.snapTo(0f)
            lastBucket = 0
            atEnd = false
            busy = false
        }
    }

    val dragState = rememberDraggableState { delta ->
        if (!busy) {
            scope.launch {
                val value = (offsetPx.value + delta).coerceIn(0f, maxTravel)
                offsetPx.snapTo(value)

                val p = value / maxTravel
                val bucket = (p * 4f).toInt().coerceIn(0, 4)
                if (bucket != lastBucket) {
                    if (bucket in 1..3) haptics.performSliderTick(view)
                    lastBucket = bucket
                }
                if (p >= 0.995f && !atEnd) {
                    atEnd = true
                    haptics.performBoundary(view)
                } else if (p < 0.95f) {
                    atEnd = false
                }
            }
        }
    }

    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val trackColor = lerp(surface, ResetRed.copy(alpha = 0.16f).compositeOver(surface), progress)
    val thumbColor = lerp(MaterialTheme.colorScheme.primary, Color.White, progress)
    val arrowColor = lerp(MaterialTheme.colorScheme.onPrimary, ResetRed, progress)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(CircleShape)
            .background(trackColor)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .drawBehind {
                val fillWidth = (padPx + offsetPx.value + thumbPx + padPx).coerceAtMost(size.width)
                val fillAlpha = ((offsetPx.value / maxTravel) * 6f).coerceIn(0f, 1f)
                if (fillAlpha > 0f) {
                    drawRoundRect(
                        color = ResetRed.copy(alpha = fillAlpha),
                        topLeft = Offset.Zero,
                        size = Size(fillWidth, size.height),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    )
                }
            }
            .semantics {
                contentDescription = label
                customActions = listOf(
                    CustomAccessibilityAction(resetActionLabel) {
                        finish()
                        true
                    }
                )
            }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = labelColor,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = thumbSize / 2)
                .graphicsLayer { alpha = (1f - progress * 1.8f).coerceIn(0f, 1f) }
        )

        Icon(
            imageVector = Icons.Rounded.RestartAlt,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((padPx + offsetPx.value - iconPx - gapPx).roundToInt(), 0) }
                .size(iconSize)
                .graphicsLayer {
                    alpha = (progress * 8f).coerceIn(0f, 1f)
                    val pop = 0.6f + 0.4f * progress
                    scaleX = pop
                    scaleY = pop
                    rotationZ = progress * 360f + spin.value
                }
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = padding)
                .offset { IntOffset(offsetPx.value.roundToInt(), 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = !busy,
                    onDragStopped = {
                        if (!busy) {
                            if (offsetPx.value / maxTravel >= 0.92f) {
                                finish()
                            } else {
                                lastBucket = 0
                                atEnd = false
                                offsetPx.animateTo(
                                    0f,
                                    spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                        }
                    }
                )
        ) {
            Canvas(modifier = Modifier.size(22.dp)) {
                val w = size.width
                val h = size.height
                val stroke = 2.6f.dp.toPx()
                drawLine(arrowColor, Offset(w * 0.12f, h * 0.5f), Offset(w * 0.88f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(arrowColor, Offset(w * 0.56f, h * 0.18f), Offset(w * 0.88f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(arrowColor, Offset(w * 0.56f, h * 0.82f), Offset(w * 0.88f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
    }
}
