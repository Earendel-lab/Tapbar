package com.earendel.tapbar

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.max
import kotlin.math.sin

private const val TwoPi = 6.2831855f
private val GlassBlur = 22.dp
private val GlassBleed = 48.dp

@Stable
class GlassBackdrop(val tick: MutableFloatState) {
    var rootCoords: LayoutCoordinates? = null
    var fieldSize: Size = Size.Zero
    val grainWhite: Brush get() = GrainWhite
    val grainBlack: Brush get() = GrainBlack

    fun originOf(child: LayoutCoordinates?): Offset {
        val root = rootCoords ?: return Offset.Zero
        if (child == null || !child.isAttached || !root.isAttached) return Offset.Zero
        return root.localPositionOf(child, Offset.Zero)
    }
}

val LocalGlassBackdrop = compositionLocalOf<GlassBackdrop?> { null }

private class CoordsHolder {
    var coords: LayoutCoordinates? = null
}

private val GrainWhite: Brush by lazy { buildGrain(true) }
private val GrainBlack: Brush by lazy { buildGrain(false) }

private fun buildGrain(white: Boolean): Brush {
    val side = 128
    val random = java.util.Random(11L)
    val base = if (white) 255 else 0
    val pixels = IntArray(side * side) {
        android.graphics.Color.argb(random.nextInt(256), base, base, base)
    }
    val bitmap = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    return ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

private fun osc(t: Float, period: Float, phase: Float): Float = sin(t / period * TwoPi + phase)

private fun DrawScope.softMass(
    cx: Float,
    cy: Float,
    radius: Float,
    scaleX: Float,
    scaleY: Float,
    rotation: Float,
    color: Color
) {
    val a = color.alpha
    withTransform({
        translate(left = cx, top = cy)
        rotate(degrees = rotation, pivot = Offset.Zero)
        scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset.Zero)
    }) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to color,
                0.12f to color.copy(alpha = a * 0.92f),
                0.25f to color.copy(alpha = a * 0.74f),
                0.4f to color.copy(alpha = a * 0.5f),
                0.55f to color.copy(alpha = a * 0.3f),
                0.7f to color.copy(alpha = a * 0.14f),
                0.85f to color.copy(alpha = a * 0.04f),
                1f to Color.Transparent,
                center = Offset.Zero,
                radius = radius
            ),
            radius = radius,
            center = Offset.Zero
        )
    }
}

private fun DrawScope.fluidMass(
    t: Float,
    cx: Float,
    cy: Float,
    w: Float,
    h: Float,
    spread: Float,
    radius: Float,
    color: Color,
    seed: Float
) {
    val lobeColor = color.copy(alpha = color.alpha * 0.62f)
    for (k in 0 until 3) {
        val phase = seed + k * 2.1f
        val stretch = 1.1f + 0.28f * osc(t, 27f + 6f * k, phase)
        softMass(
            cx = cx + spread * w * osc(t, 29f + 5f * k, phase),
            cy = cy + spread * h * osc(t, 37f + 4f * k, phase + 1.7f),
            radius = radius * (0.85f + 0.2f * osc(t, 33f + 3f * k, phase + 0.5f)),
            scaleX = stretch,
            scaleY = 1.1f / stretch,
            rotation = 70f * osc(t, 61f + 5f * k, phase + 2f),
            color = lobeColor
        )
    }
}

private fun DrawScope.drawBlobField(time: Float, p: BlobPalette, field: Size) {
    val t = time * 2.5f
    val w = field.width
    val h = field.height
    if (w <= 0f || h <= 0f) return

    drawRect(
        color = p.base,
        topLeft = Offset(-4000f, -4000f),
        size = Size(w + 8000f, h + 8000f)
    )

    fluidMass(
        t = t,
        cx = w * (0.2f + 0.2f * osc(t, 47f, 0f)),
        cy = h * (0.18f + 0.12f * osc(t, 53f, 1.3f)),
        w = w,
        h = h,
        spread = 0.22f,
        radius = w * 0.95f,
        color = p.toneA.copy(alpha = p.alphaA),
        seed = 0f
    )

    fluidMass(
        t = t,
        cx = w * (0.85f + 0.1f * osc(t, 51f, 2.1f)),
        cy = h * (0.5f + 0.2f * osc(t, 57f, 0.2f)),
        w = w,
        h = h,
        spread = 0.2f,
        radius = w * 0.85f,
        color = p.toneB.copy(alpha = p.alphaB),
        seed = 2.4f
    )

    fluidMass(
        t = t,
        cx = w * (0.15f + 0.18f * osc(t, 55f, 0.9f)),
        cy = h * (0.9f + 0.06f * osc(t, 43f, 2.7f)),
        w = w,
        h = h,
        spread = 0.22f,
        radius = w * 0.9f,
        color = p.toneC.copy(alpha = p.alphaC),
        seed = 4.6f
    )

    fluidMass(
        t = t,
        cx = w * (0.55f + 0.2f * osc(t, 63f, 1.7f)),
        cy = h * (0.55f + 0.18f * osc(t, 59f, 0.6f)),
        w = w,
        h = h,
        spread = 0.25f,
        radius = w * 0.65f,
        color = p.core,
        seed = 6.8f
    )

    p.extra?.let { extra ->
        fluidMass(
            t = t,
            cx = w * (0.3f + 0.25f * osc(t, 49f, 3.1f)),
            cy = h * (0.35f + 0.22f * osc(t, 45f, 1.1f)),
            w = w,
            h = h,
            spread = 0.2f,
            radius = w * 0.45f,
            color = extra,
            seed = 9f
        )
    }
}

private fun DrawScope.drawGlassOverlay(dark: Boolean, tile: Color, grain: Brush) {
    val w = size.width
    val h = size.height
    drawRect(tile.copy(alpha = if (dark) 0.06f else 0.42f))
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                tile.copy(alpha = if (dark) 0.13f else 0.5f),
                Color.Transparent
            ),
            center = Offset.Zero,
            radius = max(w * 0.85f, h)
        )
    )
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = if (dark) 0.14f else 0.04f)
            )
        )
    )
    drawRect(brush = grain, alpha = 0.04f)
}

@Composable
fun OnboardingBackdrop(pulseKey: Int = 0, content: @Composable () -> Unit) {
    val dark = LocalIsDark.current
    val palette = LocalImmersive.current ?: remember(dark) { monoPalette(dark) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val active = remember { mutableStateOf(true) }
    val tick = remember { mutableFloatStateOf(10f) }
    val backdrop = remember { GlassBackdrop(tick) }
    val pulse = remember { Animatable(0f) }

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

    LaunchedEffect(pulseKey) {
        if (!reducedMotion) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
            pulse.animateTo(0f, tween(1100, easing = FastOutSlowInEasing))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> active.value = true
                Lifecycle.Event.ON_STOP -> active.value = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(reducedMotion, active.value) {
        if (!reducedMotion && active.value) {
            var last = withFrameNanos { it }
            while (true) {
                withFrameNanos { now ->
                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                    last = now
                    tick.floatValue += dt * (1f + 1.4f * pulse.value)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { backdrop.rootCoords = it }
            .drawBehind {
                backdrop.fieldSize = size
                drawBlobField(tick.floatValue, palette, size)
                drawRect(
                    brush = if (dark) backdrop.grainWhite else backdrop.grainBlack,
                    alpha = 0.035f
                )
            }
    ) {
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            content()
        }
    }
}

@Composable
private fun rememberBlobClock(): androidx.compose.runtime.MutableFloatState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val active = remember { mutableStateOf(true) }
    val tick = remember { mutableFloatStateOf(10f) }

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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> active.value = true
                Lifecycle.Event.ON_STOP -> active.value = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(reducedMotion, active.value) {
        if (!reducedMotion && active.value) {
            var last = withFrameNanos { it }
            while (true) {
                withFrameNanos { now ->
                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                    last = now
                    tick.floatValue += dt
                }
            }
        }
    }
    return tick
}

@Composable
fun Modifier.immersiveSheetBackdrop(): Modifier {
    val palette = LocalImmersive.current ?: return this
    val dark = LocalIsDark.current
    val tick = rememberBlobClock()
    return this
        .clipToBounds()
        .drawBehind {
            drawBlobField(tick.floatValue, palette, size)
            drawRect(
                brush = if (dark) GrainWhite else GrainBlack,
                alpha = 0.035f
            )
        }
}

@Composable
fun GlassPanel(
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dark = LocalIsDark.current
    val palette = LocalImmersive.current ?: remember(dark) { monoPalette(dark) }
    val grain = if (dark) GrainWhite else GrainBlack
    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind { drawGlassOverlay(dark, palette.tile, grain) }
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

@Composable
fun GlassCard(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val backdrop = LocalGlassBackdrop.current
    val shape = segmentedShape(index, count)
    val dark = LocalIsDark.current
    val holder = remember { CoordsHolder() }
    val bleedPx = with(LocalDensity.current) { GlassBleed.roundToPx() }
    val fallback = MaterialTheme.colorScheme.surfaceContainerLow
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    val immersive = LocalImmersive.current

    if (immersive != null) {
        GlassPanel(
            shape = shape,
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .then(clickModifier),
            content = content
        )
        return
    }

    val palette = remember(dark) { monoPalette(dark) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { holder.coords = it }
            .clip(shape)
            .then(clickModifier)
    ) {
        if (backdrop == null) {
            Box(Modifier.matchParentSize().background(fallback))
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            Constraints.fixed(
                                constraints.maxWidth + bleedPx * 2,
                                constraints.maxHeight + bleedPx * 2
                            )
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(-bleedPx, -bleedPx)
                        }
                    }
                    .blur(GlassBlur)
                    .drawBehind {
                        val t = backdrop.tick.floatValue
                        val origin = backdrop.originOf(holder.coords)
                        translate(left = bleedPx - origin.x, top = bleedPx - origin.y) {
                            drawBlobField(t, palette, backdrop.fieldSize)
                        }
                    }
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawGlassOverlay(
                            dark = dark,
                            tile = palette.tile,
                            grain = if (dark) backdrop.grainWhite else backdrop.grainBlack
                        )
                    }
            )
        }
        content()
    }
}
