package com.earendel.tapbar

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

const val STYLE_MINIMAL = 0
const val STYLE_IMMERSIVE = 1

val LocalIsDark = compositionLocalOf { true }

val LocalImmersive = compositionLocalOf<BlobPalette?> { null }

@Immutable
class BlobPalette(
    val base: Color,
    val toneA: Color,
    val toneB: Color,
    val toneC: Color,
    val alphaA: Float,
    val alphaB: Float,
    val alphaC: Float,
    val core: Color,
    val extra: Color?,
    val tile: Color
)

fun monoPalette(dark: Boolean): BlobPalette =
    if (dark) {
        BlobPalette(
            base = Color.Black,
            toneA = Color.White,
            toneB = Color.White,
            toneC = Color.White,
            alphaA = 0.34f,
            alphaB = 0.27f,
            alphaC = 0.30f,
            core = Color.White.copy(alpha = 0.12f),
            extra = null,
            tile = Color.White
        )
    } else {
        BlobPalette(
            base = Color(0xFFE8E8E8),
            toneA = Color(0xFF7A7A7A),
            toneB = Color(0xFFA6A6A6),
            toneC = Color(0xFF686868),
            alphaA = 0.55f,
            alphaB = 0.60f,
            alphaC = 0.45f,
            core = Color.White.copy(alpha = 0.95f),
            extra = Color.White.copy(alpha = 0.9f),
            tile = Color.White
        )
    }

@Composable
fun overlayContainerColor(): Color =
    if (LocalImmersive.current != null) {
        if (LocalIsDark.current) Color(0xFF1A1A1A) else Color(0xFFF7F7F7)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

@Stable
class UiAppearance {
    var style by mutableIntStateOf(STYLE_MINIMAL)

    fun load(prefs: Prefs) {
        style = prefs.uiStyle
    }
}
