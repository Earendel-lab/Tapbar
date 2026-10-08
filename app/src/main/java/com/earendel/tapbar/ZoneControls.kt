package com.earendel.tapbar

import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

fun zoneSelectorShape(zone: Int): Shape =
    when (zone) {
        0 -> RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomEnd = 4.dp, bottomStart = 4.dp)
        1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 4.dp)
        else -> RoundedCornerShape(4.dp)
    }

@Composable
fun ZoneSelectCard(
    zone: Int,
    selected: Boolean,
    zoneOn: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val contentAlpha by animateFloatAsState(
        targetValue = if (zoneOn) 1f else 0.62f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "zoneContentAlpha"
    )

    Card(
        shape = zoneSelectorShape(zone),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp)
                .graphicsLayer { alpha = contentAlpha },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Crossfade(
                targetState = zoneOn,
                animationSpec = tween(200),
                label = "zoneIcon"
            ) { on ->
                if (on) {
                    Icon(
                        painter = painterResource(
                            if (selected) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_block_red),
                        contentDescription = stringResource(R.string.disabled),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.zone_name, zone + 1),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ZoneToggleCard(
    zone: Int,
    zoneOn: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticManager.current
    val view = LocalView.current

    Card(
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = zone,
                transitionSpec = {
                    fadeIn(tween(180, delayMillis = 40)) togetherWith fadeOut(tween(110))
                },
                label = "zoneToggleTitle",
                modifier = Modifier.weight(1f)
            ) { z ->
                Text(
                    text = stringResource(R.string.enable_zone, z + 1),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Switch(
                checked = zoneOn,
                onCheckedChange = { newState ->
                    haptics.performToggle(view, newState)
                    onChange(newState)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                    checkedTrackColor = MaterialTheme.colorScheme.onSurface,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        }
    }
}
