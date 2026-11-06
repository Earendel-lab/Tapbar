package com.earendel.tapbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun IconChip(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun Modifier.selectedTint(selected: Boolean): Modifier {
    val color by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = tween(250),
        label = "SelectedTint"
    )
    return this.background(color)
}
