package com.earendel.tapbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SwipeSettings(
    mode: Int,
    onModeChange: (Int) -> Unit,
    target: Int,
    onTargetChange: (Int) -> Unit,
    dirTypes: List<Int>,
    dirLabels: List<String?>,
    editDir: Int?,
    onEditDir: (Int?) -> Unit,
    onDirType: (Int, Int) -> Unit,
    onPickAction: (Int) -> Unit,
    hudEnabled: Boolean,
    noticeEnabled: Boolean,
    onHudChange: (Boolean) -> Unit,
    onNoticeChange: (Boolean) -> Unit,
    header: (@Composable () -> Unit)? = null
) {
    val activeEditDir = if (mode == SWIPE_MODE_ADVANCED) editDir else null
    AnimatedContent(
        targetState = activeEditDir,
        transitionSpec = { swipePanelTransition(forward = targetState != null) },
        label = "SwipePanel"
    ) { dir ->
        if (dir != null) {
            SwipeDirectionEditor(
                dir = dir,
                type = dirTypes[dir],
                label = dirLabels[dir],
                hudEnabled = hudEnabled,
                noticeEnabled = noticeEnabled,
                onBack = { onEditDir(null) },
                onType = { onDirType(dir, it) },
                onPickAction = { onPickAction(dir) },
                onHudChange = onHudChange,
                onNoticeChange = onNoticeChange
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                header?.invoke()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SwipeModeSwitch(mode = mode, onChange = onModeChange)
                    AnimatedContent(
                        targetState = mode,
                        transitionSpec = { swipePanelTransition(forward = targetState > initialState) },
                        label = "SwipeModeContent"
                    ) { currentMode ->
                        if (currentMode == SWIPE_MODE_ADVANCED) {
                            SwipeDirectionList(types = dirTypes, labels = dirLabels, onOpen = { onEditDir(it) })
                        } else {
                            SwipeOptionsList(
                                target = target,
                                hudEnabled = hudEnabled,
                                noticeEnabled = noticeEnabled,
                                onSelect = onTargetChange,
                                onHudChange = onHudChange,
                                onNoticeChange = onNoticeChange
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun swipePanelTransition(forward: Boolean): ContentTransform {
    val offsetDivisor = 5
    val enter = slideInHorizontally(
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        initialOffsetX = { width -> if (forward) width / offsetDivisor else -width / offsetDivisor }
    ) + fadeIn(animationSpec = tween(durationMillis = 260, delayMillis = 90))
    val exit = slideOutHorizontally(
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        targetOffsetX = { width -> if (forward) -width / offsetDivisor else width / offsetDivisor }
    ) + fadeOut(animationSpec = tween(durationMillis = 150))
    return ContentTransform(
        targetContentEnter = enter,
        initialContentExit = exit,
        sizeTransform = SizeTransform(clip = true) { _, _ ->
            tween(durationMillis = 420, easing = FastOutSlowInEasing)
        }
    )
}

@Composable
private fun swipeDirectionTitle(dir: Int): String = when (dir) {
    SWIPE_DIR_LEFT -> stringResource(R.string.action_swipe_left)
    SWIPE_DIR_RIGHT -> stringResource(R.string.action_swipe_right)
    SWIPE_DIR_UP -> stringResource(R.string.swipe_dir_up)
    else -> stringResource(R.string.swipe_dir_down)
}

private fun swipeDirectionRotation(dir: Int): Float = when (dir) {
    SWIPE_DIR_LEFT -> 270f
    SWIPE_DIR_RIGHT -> 90f
    SWIPE_DIR_UP -> 0f
    else -> 180f
}

@Composable
private fun SwipeModeSwitch(mode: Int, onChange: (Int) -> Unit) {
    val haptics = LocalHapticManager.current
    val view = LocalView.current
    val position by animateFloatAsState(
        targetValue = if (mode == SWIPE_MODE_ADVANCED) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "SwipeModeIndicator"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(4.dp)
    ) {
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .fillMaxHeight()
                    .align(BiasAlignment(horizontalBias = position * 2f - 1f, verticalBias = 0f))
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurface)
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            SwipeModeSegment(
                label = stringResource(R.string.swipe_mode_basic),
                selected = mode == SWIPE_MODE_BASIC,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (mode != SWIPE_MODE_BASIC) {
                        haptics.performSelection(view)
                        onChange(SWIPE_MODE_BASIC)
                    }
                }
            )
            SwipeModeSegment(
                label = stringResource(R.string.swipe_mode_advanced),
                selected = mode == SWIPE_MODE_ADVANCED,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (mode != SWIPE_MODE_ADVANCED) {
                        haptics.performSelection(view)
                        onChange(SWIPE_MODE_ADVANCED)
                    }
                }
            )
        }
    }
}

@Composable
private fun SwipeModeSegment(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val foreground by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 280),
        label = "SwipeModeForeground"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = foreground
        )
    }
}

@Composable
private fun SwipeDirectionList(
    types: List<Int>,
    labels: List<String?>,
    onOpen: (Int) -> Unit
) {
    val haptics = LocalHapticManager.current
    val view = LocalView.current
    val order = listOf(SWIPE_DIR_UP, SWIPE_DIR_DOWN, SWIPE_DIR_LEFT, SWIPE_DIR_RIGHT)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        order.forEachIndexed { index, dir ->
            val summary = when (types[dir]) {
                SWIPE_TYPE_VOLUME -> stringResource(R.string.volume)
                SWIPE_TYPE_BRIGHTNESS -> stringResource(R.string.brightness)
                SWIPE_TYPE_ACTION -> labels[dir]?.takeIf { it.isNotEmpty() }
                    ?: stringResource(R.string.perform_action)
                else -> stringResource(R.string.disabled)
            }
            SegmentedCard(
                index = index,
                count = order.size,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        haptics.performSelection(view)
                        onOpen(dir)
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .rotate(swipeDirectionRotation(dir)),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = swipeDirectionTitle(dir),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeDirectionEditor(
    dir: Int,
    type: Int,
    label: String?,
    hudEnabled: Boolean,
    noticeEnabled: Boolean,
    onBack: () -> Unit,
    onType: (Int) -> Unit,
    onPickAction: () -> Unit,
    onHudChange: (Boolean) -> Unit,
    onNoticeChange: (Boolean) -> Unit
) {
    val haptics = LocalHapticManager.current
    val view = LocalView.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        haptics.performLightTap(view)
                        onBack()
                    }
                )
                .padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = swipeDirectionTitle(dir),
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        val volumeScale by animateFloatAsState(
            targetValue = if (type == SWIPE_TYPE_VOLUME) 1.15f else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "SwipeDirVolume"
        )
        val brightnessScale by animateFloatAsState(
            targetValue = if (type == SWIPE_TYPE_BRIGHTNESS) 1.15f else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "SwipeDirBrightness"
        )
        val actionScale by animateFloatAsState(
            targetValue = if (type == SWIPE_TYPE_ACTION) 1.15f else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "SwipeDirAction"
        )
        val offScale by animateFloatAsState(
            targetValue = if (type == SWIPE_TYPE_OFF) 1.15f else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "SwipeDirOff"
        )

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SwipeOptionCard(
                index = 0,
                count = 4,
                title = stringResource(R.string.volume),
                description = stringResource(R.string.swipe_vol_desc),
                selected = type == SWIPE_TYPE_VOLUME,
                scale = volumeScale,
                onClick = {
                    haptics.performSelection(view)
                    onType(SWIPE_TYPE_VOLUME)
                }
            )
            SwipeOptionCard(
                index = 1,
                count = 4,
                title = stringResource(R.string.brightness),
                description = stringResource(R.string.swipe_bright_desc),
                selected = type == SWIPE_TYPE_BRIGHTNESS,
                scale = brightnessScale,
                onClick = {
                    haptics.performSelection(view)
                    onType(SWIPE_TYPE_BRIGHTNESS)
                },
                extra = {
                    SwipeToggleRow(
                        label = stringResource(R.string.swipe_hud_toggle),
                        checked = hudEnabled,
                        onChange = onHudChange
                    )
                    SwipeToggleRow(
                        label = stringResource(R.string.swipe_notice_toggle),
                        checked = noticeEnabled,
                        onChange = onNoticeChange
                    )
                }
            )
            SwipeOptionCard(
                index = 2,
                count = 4,
                title = stringResource(R.string.perform_action),
                description = if (type == SWIPE_TYPE_ACTION) label.orEmpty() else "",
                selected = type == SWIPE_TYPE_ACTION,
                scale = actionScale,
                onClick = {
                    haptics.performSelection(view)
                    onPickAction()
                }
            )
            SwipeOptionCard(
                index = 3,
                count = 4,
                title = stringResource(R.string.disabled),
                description = "",
                selected = type == SWIPE_TYPE_OFF,
                scale = offScale,
                onClick = {
                    haptics.performSelection(view)
                    onType(SWIPE_TYPE_OFF)
                }
            )
        }
    }
}

@Composable
fun SwipeOptionsList(
    target: Int,
    hudEnabled: Boolean,
    noticeEnabled: Boolean,
    onSelect: (Int) -> Unit,
    onHudChange: (Boolean) -> Unit,
    onNoticeChange: (Boolean) -> Unit
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

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SwipeOptionCard(
            index = 0,
            count = 2,
            title = stringResource(R.string.volume),
            description = stringResource(R.string.swipe_vol_desc),
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
            description = stringResource(R.string.swipe_bright_desc),
            selected = target == SWIPE_TARGET_BRIGHTNESS,
            scale = brightnessScale,
            onClick = {
                haptics.performSelection(view)
                onSelect(SWIPE_TARGET_BRIGHTNESS)
            },
            extra = {
                SwipeToggleRow(
                    label = stringResource(R.string.swipe_hud_toggle),
                    checked = hudEnabled,
                    onChange = onHudChange
                )
                SwipeToggleRow(
                    label = stringResource(R.string.swipe_notice_toggle),
                    checked = noticeEnabled,
                    onChange = onNoticeChange
                )
            }
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
    onClick: () -> Unit,
    extra: (@Composable () -> Unit)? = null
) {
    SegmentedCard(
        index = index,
        count = count,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Column {
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
                    if (description.isNotEmpty()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
            if (extra != null) {
                AnimatedVisibility(
                    visible = selected,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.padding(bottom = 6.dp)) {
                        extra()
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val haptics = LocalHapticManager.current
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Switch(
            checked = checked,
            onCheckedChange = {
                haptics.performToggle(view, it)
                onChange(it)
            },
            modifier = Modifier.scale(0.8f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedTrackColor = MaterialTheme.colorScheme.onSurface,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        )
    }
}
