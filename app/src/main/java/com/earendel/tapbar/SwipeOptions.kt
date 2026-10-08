package com.earendel.tapbar

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
private fun swipeDescription(target: Int, axis: SwipeAxis): String {
    return if (target == SWIPE_TARGET_BRIGHTNESS) {
        if (axis == SwipeAxis.HORIZONTAL) {
            stringResource(R.string.swipe_bright_horiz_desc)
        } else {
            stringResource(R.string.swipe_bright_vert_desc)
        }
    } else {
        if (axis == SwipeAxis.HORIZONTAL) {
            stringResource(R.string.swipe_vol_horiz_desc)
        } else {
            stringResource(R.string.swipe_vol_vert_desc)
        }
    }
}

@Composable
private fun swipeFootnote(axis: SwipeAxis): String =
    if (axis == SwipeAxis.HORIZONTAL) {
        stringResource(R.string.swipe_horiz_footnote)
    } else {
        stringResource(R.string.swipe_vert_footnote)
    }

@Composable
fun SwipeOptionsList(
    target: Int,
    axis: SwipeAxis,
    onSelect: (Int) -> Unit
) {
    val haptics = LocalHapticManager.current
    val view = LocalView.current

    val volumeScale by animateFloatAsState(
        targetValue = if (target == SWIPE_TARGET_VOLUME) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "SwipeRadioVolume"
    )
    val brightnessScale by animateFloatAsState(
        targetValue = if (target == SWIPE_TARGET_BRIGHTNESS) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "SwipeRadioBrightness"
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SwipeOptionCard(
                index = 0,
                count = 2,
                title = stringResource(R.string.volume),
                description = swipeDescription(SWIPE_TARGET_VOLUME, axis),
                selected = target == SWIPE_TARGET_VOLUME,
                scale = volumeScale,
                onClick = {
                    haptics.performSelection(view)
                    onSelect(SWIPE_TARGET_VOLUME)
                }
            )
            SwipeOptionCard(
                index = 1,
                count = 2,
                title = stringResource(R.string.brightness),
                description = swipeDescription(SWIPE_TARGET_BRIGHTNESS, axis),
                selected = target == SWIPE_TARGET_BRIGHTNESS,
                scale = brightnessScale,
                onClick = {
                    haptics.performSelection(view)
                    onSelect(SWIPE_TARGET_BRIGHTNESS)
                }
            )
        }

        Text(
            text = swipeFootnote(axis),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun SwipeOptionCard(
    index: Int,
    count: Int,
    title: String,
    description: String,
    selected: Boolean,
    scale: Float,
    onClick: () -> Unit
) {
    SegmentedCard(
        index = index,
        count = count,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                painter = painterResource(
                    if (selected) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected
                ),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}