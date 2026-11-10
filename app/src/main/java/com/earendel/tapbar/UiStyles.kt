package com.earendel.tapbar

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun isOneUiStyle(): Boolean = LocalUiStyle.current == STYLE_ONEUI

object OneUiColors {
    val Primary = Color(0xFF387AFF)
    val PrimaryTextLight = Color(0xFF2E65D4)
    val PrimaryTextDark = Color(0xFF578FFF)
    val White = Color(0xFFFCFCFF)
    val Black = Color(0xFF010102)
    val Red = Color(0xFFD93E36)
    val RedDark = Color(0xFFF56962)

    val LightWindow = Color(0xFFF1F1F3)
    val LightCard = Color(0xFFFCFCFF)
    val LightCardHigh = Color(0xFFF6F6F8)
    val LightCardHighest = Color(0xFFE4E4E7)
    val LightText = Color(0xFF010102)
    val LightSummary = Color(0xFF848487)
    val LightSubheader = Color(0xFF848487)
    val LightDivider = Color(0xFFE4E4E7)
    val LightOutline = Color(0xFFE4E4E7)
    val LightOutlineStrong = Color(0xFFCCCCCE)
    val LightSwitchOff = Color(0xFFA3A3A7)
    val LightSwitchRing = Color(0xFF8C8C8C)
    val LightSliderTrack = Color(0x73C9C9CC)
    val LightPrimaryContainer = Color(0xFFDDE8FF)

    val DarkWindow = Color(0xFF010102)
    val DarkCard = Color(0xFF17171A)
    val DarkCardHigh = Color(0xFF252528)
    val DarkCardHighest = Color(0xFF2D2D30)
    val DarkText = Color(0xFFFCFCFF)
    val DarkSummary = Color(0xFFA3A3A7)
    val DarkSubheader = Color(0xFFA3A3A7)
    val DarkDivider = Color(0xFF3A3A3D)
    val DarkOutline = Color(0xFF3A3A3D)
    val DarkOutlineStrong = Color(0xFF636368)
    val DarkSwitchOff = Color(0xFF636368)
    val DarkSwitchRing = Color(0xFF8F8F8F)
    val DarkSliderTrack = Color(0x66C9C9CC)
    val DarkPrimaryContainer = Color(0xFF1E2D4C)

    val DialogLight = Color(0xFFFCFCFF)
    val DialogDark = Color(0xFF252528)

    val BlockRadius = 26.dp
    val DisabledAlpha = 0.4f
}

internal fun oneUiColorScheme(dark: Boolean): ColorScheme =
    if (dark) {
        darkColorScheme(
            primary = OneUiColors.Primary,
            onPrimary = OneUiColors.White,
            secondary = OneUiColors.Primary,
            onSecondary = OneUiColors.White,
            tertiary = OneUiColors.Primary,
            onTertiary = OneUiColors.White,
            primaryContainer = OneUiColors.DarkPrimaryContainer,
            onPrimaryContainer = OneUiColors.DarkText,
            background = OneUiColors.DarkWindow,
            onBackground = OneUiColors.DarkText,
            surface = OneUiColors.DarkCard,
            onSurface = OneUiColors.DarkText,
            surfaceVariant = OneUiColors.DarkCardHigh,
            onSurfaceVariant = OneUiColors.DarkSummary,
            surfaceContainerLowest = OneUiColors.DarkWindow,
            surfaceContainerLow = OneUiColors.DarkCard,
            surfaceContainer = OneUiColors.DarkCard,
            surfaceContainerHigh = OneUiColors.DarkCardHigh,
            surfaceContainerHighest = OneUiColors.DarkCardHighest,
            outline = OneUiColors.DarkOutline,
            outlineVariant = OneUiColors.DarkOutlineStrong,
            error = OneUiColors.RedDark
        )
    } else {
        lightColorScheme(
            primary = OneUiColors.Primary,
            onPrimary = OneUiColors.White,
            secondary = OneUiColors.Primary,
            onSecondary = OneUiColors.White,
            tertiary = OneUiColors.Primary,
            onTertiary = OneUiColors.White,
            primaryContainer = OneUiColors.LightPrimaryContainer,
            onPrimaryContainer = OneUiColors.LightText,
            background = OneUiColors.LightWindow,
            onBackground = OneUiColors.LightText,
            surface = OneUiColors.LightCard,
            onSurface = OneUiColors.LightText,
            surfaceVariant = OneUiColors.LightCardHigh,
            onSurfaceVariant = OneUiColors.LightSummary,
            surfaceContainerLowest = OneUiColors.White,
            surfaceContainerLow = OneUiColors.LightCard,
            surfaceContainer = OneUiColors.LightCard,
            surfaceContainerHigh = OneUiColors.LightCardHigh,
            surfaceContainerHighest = OneUiColors.LightCardHighest,
            outline = OneUiColors.LightOutline,
            outlineVariant = OneUiColors.LightOutlineStrong,
            error = OneUiColors.Red
        )
    }

internal val OneUiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

private fun Typography.withFamily(f: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f)
)

internal fun oneUiTypography(): Typography {
    val base = Typography().withFamily(FontFamily.SansSerif)
    return base.copy(
        displaySmall = base.displaySmall.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        headlineSmall = base.headlineSmall.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleLarge = base.titleLarge.copy(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleMedium = base.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
        titleSmall = base.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = 0.sp),
        bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
        labelLarge = base.labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
        labelMedium = base.labelMedium.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp)
    )
}

@Composable
fun sectionHeaderColor(): Color =
    if (LocalUiStyle.current == STYLE_ONEUI) {
        if (LocalIsDark.current) OneUiColors.DarkSubheader else OneUiColors.LightSubheader
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
fun sectionHeaderStyle(): TextStyle {
    val base = MaterialTheme.typography.labelLarge
    return if (LocalUiStyle.current == STYLE_ONEUI) {
        base.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    } else {
        base
    }
}

@Composable
fun Modifier.sectionHeaderInset(): Modifier =
    if (LocalUiStyle.current == STYLE_ONEUI) padding(start = 16.dp) else this

@Composable
fun selectedIconTint(): Color =
    if (LocalUiStyle.current == STYLE_ONEUI) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface

fun oneUiGroupShape(index: Int, count: Int): Shape {
    val large = OneUiColors.BlockRadius
    val small = 0.dp
    return when {
        count <= 1 -> RoundedCornerShape(large)
        index == 0 -> RoundedCornerShape(large, large, small, small)
        index == count - 1 -> RoundedCornerShape(small, small, large, large)
        else -> RoundedCornerShape(small)
    }
}

@Composable
fun OneUiGroupCard(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = remember(index, count) { oneUiGroupShape(index, count) }
    val card = MaterialTheme.colorScheme.surfaceContainerLow
    val dark = LocalIsDark.current
    val last = index >= count - 1
    val divider = if (dark) OneUiColors.DarkDivider else OneUiColors.LightDivider
    val gapPx = with(LocalDensity.current) { 2.dp.toPx() }
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (!last && count > 1) {
                    Modifier.drawBehind {
                        drawRect(
                            color = card,
                            topLeft = Offset(0f, size.height - 1f),
                            size = Size(size.width, gapPx + 1f)
                        )
                    }
                } else Modifier
            )
            .clip(shape)
            .background(card)
            .then(clickMod)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
        if (!last && count > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(0.75.dp)
                    .background(divider)
            )
        }
    }
}

@Composable
fun TapbarSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors()
) {
    when (LocalUiStyle.current) {
        STYLE_ONEUI -> OneUiSwitch(checked, onCheckedChange, modifier, enabled)
        else -> Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            colors = colors
        )
    }
}

@Composable
private fun OneUiSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
    enabled: Boolean
) {
    val dark = LocalIsDark.current
    val trackOff = if (dark) OneUiColors.DarkSwitchOff else OneUiColors.LightSwitchOff
    val ringOff = if (dark) OneUiColors.DarkSwitchRing else OneUiColors.LightSwitchRing
    val track by animateColorAsState(if (checked) OneUiColors.Primary else trackOff, tween(180), label = "OneUiTrack")
    val ring by animateColorAsState(if (checked) OneUiColors.Primary else ringOff, tween(180), label = "OneUiRing")
    val thumbX by animateDpAsState(
        if (checked) 12.dp else 0.dp,
        spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "OneUiThumb"
    )
    Box(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else OneUiColors.DisabledAlpha }
            .toggleable(
                value = checked,
                enabled = enabled && onCheckedChange != null,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = { onCheckedChange?.invoke(it) }
            )
            .size(width = 48.dp, height = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 20.dp)
                .clip(CircleShape)
                .background(track)
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbX.roundToPx(), 0) }
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(ring)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(OneUiColors.White)
            )
        }
    }
}

@Composable
fun tapbarSliderColors(): SliderColors {
    val scheme = MaterialTheme.colorScheme
    return when (LocalUiStyle.current) {
        STYLE_ONEUI -> SliderDefaults.colors(
            thumbColor = scheme.primary,
            activeTrackColor = scheme.primary,
            inactiveTrackColor = if (LocalIsDark.current) OneUiColors.DarkSliderTrack else OneUiColors.LightSliderTrack,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent
        )
        else -> SliderDefaults.colors(
            thumbColor = scheme.onSurface,
            activeTrackColor = scheme.onSurface,
            inactiveTrackColor = scheme.outlineVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapbarSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null
) {
    if (LocalUiStyle.current == STYLE_ONEUI) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            thumb = { OneUiSliderThumb(enabled) },
            track = { state -> OneUiSliderTrack(state, enabled) }
        )
    } else {
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            colors = tapbarSliderColors()
        )
    }
}

@Composable
private fun OneUiSliderThumb(enabled: Boolean) {
    val dark = LocalIsDark.current
    Box(
        modifier = Modifier
            .graphicsLayer { alpha = if (enabled) 1f else OneUiColors.DisabledAlpha }
            .size(20.dp)
            .clip(CircleShape)
            .background(OneUiColors.Primary)
            .padding(2.dp)
            .clip(CircleShape)
            .background(if (dark) OneUiColors.Black else OneUiColors.White)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OneUiSliderTrack(state: SliderState, enabled: Boolean) {
    val dark = LocalIsDark.current
    val inactive = if (dark) OneUiColors.DarkSliderTrack else OneUiColors.LightSliderTrack
    Box(
        modifier = Modifier
            .graphicsLayer { alpha = if (enabled) 1f else OneUiColors.DisabledAlpha }
            .fillMaxWidth()
            .height(14.dp)
            .drawBehind {
                val range = state.valueRange
                val span = range.endInclusive - range.start
                val fraction = if (span <= 0f) 0f else ((state.value - range.start) / span).coerceIn(0f, 1f)
                val ext = 6.dp.toPx()
                val radius = CornerRadius(size.height / 2f, size.height / 2f)
                val full = size.width + ext * 2f
                drawRoundRect(
                    color = inactive,
                    topLeft = Offset(-ext, 0f),
                    size = Size(full, size.height),
                    cornerRadius = radius
                )
                drawRoundRect(
                    color = OneUiColors.Primary,
                    topLeft = Offset(-ext, 0f),
                    size = Size(ext + fraction * size.width, size.height),
                    cornerRadius = radius
                )
            }
    )
}

@Composable
fun tapbarPrimaryButtonColors(): ButtonColors {
    val scheme = MaterialTheme.colorScheme
    return if (LocalUiStyle.current == STYLE_ONEUI) {
        ButtonDefaults.buttonColors(
            containerColor = scheme.primary,
            contentColor = scheme.onPrimary
        )
    } else {
        ButtonDefaults.buttonColors(
            containerColor = scheme.onSurface,
            contentColor = scheme.surface
        )
    }
}

@Composable
private fun expandedBarTop(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp

@Composable
private fun expandedHeaderHeight(): Dp {
    val cfg = LocalConfiguration.current
    return (cfg.screenHeightDp.dp * 0.3967f - expandedBarTop()).coerceAtLeast(96.dp)
}

@Composable
private fun expandedHeaderAvailable(): Boolean {
    val cfg = LocalConfiguration.current
    return cfg.orientation != Configuration.ORIENTATION_LANDSCAPE
}

private fun Modifier.bleedHorizontal(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = amount.roundToPx() * 2
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-extra / 2, 0)
    }
}

@Composable
fun Modifier.collapsedToolbarFade(scroll: ScrollState?): Modifier {
    if (LocalUiStyle.current != STYLE_ONEUI || scroll == null || !expandedHeaderAvailable()) return this
    val hPx = with(LocalDensity.current) { expandedHeaderHeight().toPx() }
    return graphicsLayer {
        alpha = (((scroll.value / hPx) - 0.5f) / 0.3f).coerceIn(0f, 1f)
    }
}

@Composable
fun ExpandedAppBarTitle(
    title: String,
    scroll: ScrollState?,
    navigation: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null
) {
    if (LocalUiStyle.current != STYLE_ONEUI) return
    if (!expandedHeaderAvailable()) return
    val cfg = LocalConfiguration.current
    val height = expandedHeaderHeight()
    val hPx = with(LocalDensity.current) { height.toPx() }
    val titleCenter = cfg.screenHeightDp.dp * 0.205f
    val topPad = (titleCenter - expandedBarTop() - 20.dp)
        .coerceIn(0.dp, (height - 40.dp).coerceAtLeast(0.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topPad)
                .graphicsLayer {
                    val p = ((scroll?.value ?: 0) / hPx).coerceIn(0f, 1f)
                    alpha = 1f - p * 1.15f
                }
        )
        if (navigation != null || actions != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .bleedHorizontal(9.dp)
                    .graphicsLayer {
                        alpha = (1f - ((scroll?.value ?: 0) / hPx) * 2f).coerceIn(0f, 1f)
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (navigation != null) navigation() else Spacer(Modifier.size(48.dp))
                if (actions != null) actions() else Spacer(Modifier.size(48.dp))
            }
        }
    }
}

@Composable
fun CollapsingTitle(text: String, scroll: ScrollState?, inset: Dp = 0.dp) {
    if (LocalUiStyle.current != STYLE_ONEUI || scroll == null || !expandedHeaderAvailable()) {
        Text(text)
        return
    }
    val hPx = with(LocalDensity.current) { expandedHeaderHeight().toPx() }
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .padding(start = inset)
            .graphicsLayer {
                alpha = ((scroll.value - hPx * 0.45f) / (hPx * 0.4f)).coerceIn(0f, 1f)
            }
    )
}
