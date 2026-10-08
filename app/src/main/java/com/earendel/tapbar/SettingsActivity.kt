package com.earendel.tapbar

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class SettingsActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onResume() {
        super.onResume()
        if (LocaleHelper.needsRecreate(this)) recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )
        val prefs = Prefs(this)
        LocaleHelper.syncFromSystem(this, prefs)
        setContent {
            ProvideHapticManager(prefs) {
                SettingsRoot(prefs) { finish() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoot(prefs: Prefs, onBack: () -> Unit) {
    var themeMode by remember { mutableIntStateOf(prefs.themeMode) }
    var hapticsEnabled by remember { mutableStateOf(prefs.hapticFeedbackEnabled) }
    var tapHapticMode by remember { mutableIntStateOf(prefs.tapHapticMode) }
    val haptics = LocalHapticManager.current
    val view = LocalView.current

    TapbarTheme(themeMode = themeMode) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    navigationIcon = {
                        IconButton(onClick = {
                            haptics.performLightTap(view)
                            onBack()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            }
        ) { pad ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.appearance),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ThemeRow(
                            icon = Icons.Rounded.BrightnessAuto,
                            label = stringResource(R.string.theme_auto),
                            selected = themeMode == 0,
                            index = 0,
                            count = 3,
                            onClick = {
                                if (themeMode != 0) {
                                    haptics.performSelection(view)
                                    themeMode = 0
                                    prefs.themeMode = 0
                                }
                            }
                        )
                        ThemeRow(
                            icon = Icons.Rounded.WbSunny,
                            label = stringResource(R.string.theme_light),
                            selected = themeMode == 1,
                            index = 1,
                            count = 3,
                            onClick = {
                                if (themeMode != 1) {
                                    haptics.performSelection(view)
                                    themeMode = 1
                                    prefs.themeMode = 1
                                }
                            }
                        )
                        ThemeRow(
                            icon = Icons.Rounded.DarkMode,
                            label = stringResource(R.string.theme_dark),
                            selected = themeMode == 2,
                            index = 2,
                            count = 3,
                            onClick = {
                                if (themeMode != 2) {
                                    haptics.performSelection(view)
                                    themeMode = 2
                                    prefs.themeMode = 2
                                }
                            }
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.language),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val context = LocalContext.current
                    var isExpanded by remember { mutableStateOf(false) }
                    var currentLanguage by remember { mutableStateOf(LocaleHelper.selectedTag(prefs)) }

                    val currentLangObj = SupportedLanguages.firstOrNull { it.tag == currentLanguage }
                    val currentLangName = currentLangObj?.nativeName ?: "English"

                    val arrowRotation by animateFloatAsState(
                        targetValue = if (isExpanded) 180f else 0f,
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        label = "LangArrowRotation"
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        SegmentedCard(
                            index = 0,
                            count = if (isExpanded) SupportedLanguages.size + 1 else 1,
                            onClick = {
                                val nextExpanded = !isExpanded
                                haptics.performExpandCollapse(view)
                                isExpanded = nextExpanded
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Language,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = stringResource(R.string.choose_language),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = currentLangName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer { rotationZ = arrowRotation },
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        AnimatedVisibility(visible = isExpanded) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                SupportedLanguages.forEachIndexed { index, lang ->
                                    val isSelected = currentLanguage == lang.tag
                                    val scale by animateFloatAsState(
                                        targetValue = if (isSelected) 1.15f else 1.0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        ),
                                        label = "LangRadioScaleSettings_${lang.tag}"
                                    )

                                    SegmentedCard(
                                        index = index + 1,
                                        count = SupportedLanguages.size + 1,
                                        onClick = {
                                            haptics.performSelection(view)
                                            isExpanded = false
                                            if (lang.tag != currentLanguage) {
                                                currentLanguage = lang.tag
                                                LocaleHelper.applyLanguage(context, prefs, lang.tag)
                                            }
                                        }
                                    ) {
                                        LanguageRow(lang = lang, isSelected = isSelected, scale = scale)
                                    }
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.haptic_feedback),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SegmentedCard(index = 0, count = if (hapticsEnabled) 3 else 1) {
                            HapticToggleRow(
                                checked = hapticsEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        prefs.hapticFeedbackEnabled = true
                                        hapticsEnabled = true
                                        haptics.performToggle(view, true)
                                    } else {
                                        prefs.hapticFeedbackEnabled = false
                                        hapticsEnabled = false
                                    }
                                }
                            )
                        }

                        AnimatedVisibility(visible = hapticsEnabled) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                ThemeRow(
                                    icon = Icons.Rounded.TouchApp,
                                    label = stringResource(R.string.buzz_every_tap),
                                    selected = tapHapticMode == 0,
                                    index = 1,
                                    count = 3,
                                    onClick = {
                                        if (tapHapticMode != 0) {
                                            haptics.performSelection(view)
                                            tapHapticMode = 0
                                            prefs.tapHapticMode = 0
                                        }
                                    }
                                )
                                ThemeRow(
                                    icon = Icons.Rounded.PlayArrow,
                                    label = stringResource(R.string.buzz_action_runs),
                                    selected = tapHapticMode == 1,
                                    index = 2,
                                    count = 3,
                                    onClick = {
                                        if (tapHapticMode != 1) {
                                            haptics.performSelection(view)
                                            tapHapticMode = 1
                                            prefs.tapHapticMode = 1
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.about),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    AboutSection()
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.made_with_love),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.tagline_offline),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.data_stays_on_device),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun HapticToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Vibration,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.haptic_feedback),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedTrackColor = MaterialTheme.colorScheme.onSurface,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        )
    }
}

@Composable
fun ThemeRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    index: Int,
    count: Int,
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_bold),
                    contentDescription = stringResource(R.string.cd_selected),
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun AboutSection() {
    val context = LocalContext.current
    val haptics = LocalHapticManager.current
    val view = LocalView.current

    val currentVersionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.6"
        } catch (_: Throwable) {
            "1.6"
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AboutRow(
            icon = Icons.Rounded.Update,
            title = stringResource(R.string.check_update),
            value = stringResource(R.string.current_version, currentVersionName),
            index = 0,
            count = 5,
            onClick = {
                haptics.performLightTap(view)
                openUrl(context, "https://github.com/Earendel-lab/Tapbar/releases")
            }
        )
        AboutRow(
            icon = Icons.Rounded.Person,
            title = stringResource(R.string.developer),
            value = "Earendel",
            index = 1,
            count = 5,
            onClick = {
                haptics.performLightTap(view)
                openUrl(context, "https://earendel.pages.dev/")
            }
        )
        AboutRow(
            icon = Icons.Rounded.Code,
            title = stringResource(R.string.source_code),
            value = "GitHub",
            index = 2,
            count = 5,
            onClick = {
                haptics.performLightTap(view)
                openUrl(context, "https://github.com/Earendel-lab/Tapbar")
            }
        )
        AboutRow(
            icon = Icons.Rounded.Star,
            title = stringResource(R.string.star_project),
            value = "GitHub",
            index = 3,
            count = 5,
            onClick = {
                haptics.performLightTap(view)
                openUrl(context, "https://github.com/Earendel-lab/Tapbar")
            }
        )
        AboutRow(
            icon = Icons.Rounded.Description,
            title = stringResource(R.string.license),
            value = "Open Source License",
            index = 4,
            count = 5,
            onClick = {
                haptics.performLightTap(view)
                openUrl(context, "https://github.com/Earendel-lab/Tapbar/blob/main/LICENSE")
            }
        )
    }
}

@Composable
fun AboutRow(
    icon: ImageVector,
    title: String,
    value: String,
    index: Int,
    count: Int,
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_arrow_up_right),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}
