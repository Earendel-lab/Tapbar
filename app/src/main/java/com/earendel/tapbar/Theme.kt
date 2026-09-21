package com.earendel.tapbar

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.earendel.tapbar.R

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val GeistFontName = GoogleFont("Geist")

val GeistFontFamily = FontFamily(
    Font(googleFont = GeistFontName, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GeistFontName, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = GeistFontName, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = GeistFontName, fontProvider = provider, weight = FontWeight.Bold)
)

private val GeistTypography by lazy {
    Typography(
        displayLarge   = Typography().displayLarge.copy(fontFamily   = GeistFontFamily),
        displayMedium  = Typography().displayMedium.copy(fontFamily  = GeistFontFamily),
        displaySmall   = Typography().displaySmall.copy(fontFamily   = GeistFontFamily),
        headlineLarge  = Typography().headlineLarge.copy(fontFamily  = GeistFontFamily),
        headlineMedium = Typography().headlineMedium.copy(fontFamily = GeistFontFamily),
        headlineSmall  = Typography().headlineSmall.copy(fontFamily  = GeistFontFamily),
        titleLarge     = Typography().titleLarge.copy(fontFamily     = GeistFontFamily),
        titleMedium    = Typography().titleMedium.copy(fontFamily    = GeistFontFamily),
        titleSmall     = Typography().titleSmall.copy(fontFamily     = GeistFontFamily),
        bodyLarge      = Typography().bodyLarge.copy(fontFamily      = GeistFontFamily),
        bodyMedium     = Typography().bodyMedium.copy(fontFamily     = GeistFontFamily),
        bodySmall      = Typography().bodySmall.copy(fontFamily      = GeistFontFamily),
        labelLarge     = Typography().labelLarge.copy(fontFamily     = GeistFontFamily),
        labelMedium    = Typography().labelMedium.copy(fontFamily    = GeistFontFamily),
        labelSmall     = Typography().labelSmall.copy(fontFamily     = GeistFontFamily)
    )
}

private val DarkScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color.White,
    onSecondary = Color.Black,
    tertiary = Color.White,
    onTertiary = Color.Black,
    background = Color(0xFF000000),
    onBackground = Color.White,
    surface = Color(0xFF121212),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF121212),
    onSurfaceVariant = Color(0xFFB0B0B0),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF121212),
    surfaceContainerHighest = Color(0xFF121212),
    outline = Color(0xFF303030),
    outlineVariant = Color(0xFF404040)
)

private val LightScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    secondary = Color.Black,
    onSecondary = Color.White,
    tertiary = Color.Black,
    onTertiary = Color.White,
    background = Color(0xFFF2F2F2),
    onBackground = Color.Black,
    surface = Color(0xFFFCFCFC),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFFCFCFC),
    onSurfaceVariant = Color(0xFF606060),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFCFC),
    surfaceContainer = Color(0xFFFCFCFC),
    surfaceContainerHigh = Color(0xFFFCFCFC),
    surfaceContainerHighest = Color(0xFFFCFCFC),
    outline = Color(0xFFE0E0E0),
    outlineVariant = Color(0xFFD0D0D0)
)

@Composable
fun StatusTapTheme(
    themeMode: Int,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    val scheme = remember(dark) { if (dark) DarkScheme else LightScheme }

    MaterialTheme(
        colorScheme = scheme,
        typography = GeistTypography,
        content = content
    )
}

