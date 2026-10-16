package com.earendel.tapbar

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

data class AppLanguage(
    val tag: String,
    val nativeName: String,
    val englishName: String
)

private val AllLanguages = listOf(
    AppLanguage("en", "English", "English"),
    AppLanguage("pt", "Português", "Portuguese"),
    AppLanguage("ru", "Русский", "Russian"),
    AppLanguage("de", "Deutsch", "German"),
    AppLanguage("es", "Español", "Spanish"),
    AppLanguage("fr", "Français", "French"),
    AppLanguage("hi", "हिन्दी", "Hindi"),
    AppLanguage("ja", "日本語", "Japanese"),
    AppLanguage("ko", "한국어", "Korean"),
    AppLanguage("ne", "नेपाली", "Nepali"),
    AppLanguage("tr", "Türkçe", "Turkish"),
    AppLanguage("zh", "中文", "Chinese")
)

private val PinnedTags = listOf("en", "pt", "ru")

val SupportedLanguages: List<AppLanguage> =
    PinnedTags.mapNotNull { tag -> AllLanguages.firstOrNull { it.tag == tag } } +
        AllLanguages.filter { it.tag !in PinnedTags }.sortedBy { it.englishName }

@Composable
private fun primaryNavButtonColors(): ButtonColors {
    val isDark = isSystemInDarkTheme()
    val container = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFFFCFCFC)
    val content = if (isDark) MaterialTheme.colorScheme.surface else Color.Black
    return ButtonDefaults.buttonColors(
        containerColor = container,
        contentColor = content
    )
}

@Composable
private fun cardActionButtonColors(): ButtonColors {
    val isDark = isSystemInDarkTheme()
    val container = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFFE2E2E2)
    val content = if (isDark) MaterialTheme.colorScheme.surface else Color.Black
    return ButtonDefaults.buttonColors(
        containerColor = container,
        contentColor = content
    )
}

@Composable
fun LanguageRow(
    lang: AppLanguage,
    isSelected: Boolean,
    scale: Float
) {
    val localeList = LocaleList(lang.tag)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .semantics(mergeDescendants = true) {
                selected = isSelected
                role = Role.RadioButton
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(localeList = localeList)) {
                    append(lang.nativeName)
                }
            },
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = languageFontFamily(lang.tag),
                localeList = localeList
            ),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(
                if (isSelected) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected
            ),
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun LanguageOnboardingScreen(
    prefs: Prefs,
    onNext: () -> Unit
) {
    var currentStep by rememberSaveable {
        mutableIntStateOf(if (prefs.hasCompletedLanguageOnboarding) 1 else 0)
    }
    val context = LocalContext.current

    BackHandler {
        if (currentStep == 1 && !prefs.hasCompletedLanguageOnboarding) {
            currentStep = 0
        } else {
            LocaleHelper.findActivity(context)?.finish()
        }
    }

    AnimatedContent(
        targetState = currentStep,
        transitionSpec = {
            val duration = 320
            if (targetState > initialState) {
                (slideInHorizontally(
                    animationSpec = tween(duration, easing = FastOutSlowInEasing),
                    initialOffsetX = { fullWidth -> (fullWidth * 0.10f).toInt() }
                ) + fadeIn(tween(duration))).togetherWith(
                    slideOutHorizontally(
                        animationSpec = tween(duration, easing = FastOutSlowInEasing),
                        targetOffsetX = { fullWidth -> (-fullWidth * 0.10f).toInt() }
                    ) + fadeOut(tween(duration))
                )
            } else {
                (slideInHorizontally(
                    animationSpec = tween(duration, easing = FastOutSlowInEasing),
                    initialOffsetX = { fullWidth -> (-fullWidth * 0.10f).toInt() }
                ) + fadeIn(tween(duration))).togetherWith(
                    slideOutHorizontally(
                        animationSpec = tween(duration, easing = FastOutSlowInEasing),
                        targetOffsetX = { fullWidth -> (fullWidth * 0.10f).toInt() }
                    ) + fadeOut(tween(duration))
                )
            }
        },
        label = "OnboardingStepTransition"
    ) { step ->
        when (step) {
            0 -> {
                LanguageSelectionStep(
                    prefs = prefs,
                    onNext = {
                        prefs.hasCompletedLanguageOnboarding = true
                        currentStep = 1
                    }
                )
            }
            1 -> {
                PermissionStep(
                    prefs = prefs,
                    onFinish = {
                        prefs.hasCompletedPermissionOnboarding = true
                        onNext()
                    }
                )
            }
        }
    }
}

@Composable
private fun LanguageSelectionStep(
    prefs: Prefs,
    onNext: () -> Unit
) {
    var selectedTag by rememberSaveable { mutableStateOf(LocaleHelper.selectedTag(prefs)) }
    val context = LocalContext.current
    val haptics = LocalHapticManager.current
    val view = LocalView.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Button(
                    onClick = {
                        haptics.performConfirm(view)
                        LocaleHelper.applyLanguage(context, prefs, selectedTag)
                        onNext()
                    },
                    colors = primaryNavButtonColors()
                ) {
                    Text(
                        text = stringResource(R.string.next),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.choose_language),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SupportedLanguages.forEachIndexed { index, lang ->
                    val isSelected = selectedTag == lang.tag
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.15f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "LangRadioScale_${lang.tag}"
                    )

                    SegmentedCard(
                        index = index,
                        count = SupportedLanguages.size,
                        onClick = {
                            haptics.performSelection(view)
                            selectedTag = lang.tag
                        }
                    ) {
                        LanguageRow(lang = lang, isSelected = isSelected, scale = scale)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PermissionStep(
    prefs: Prefs,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticManager.current
    val view = LocalView.current

    var isAccessibilityEnabled by remember {
        mutableStateOf(TapAccessibilityService.isEnabled(context))
    }
    var isOverlayEnabled by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = TapAccessibilityService.isEnabled(context)
                isOverlayEnabled = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val hasAnyPermission = isAccessibilityEnabled || isOverlayEnabled

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Button(
                    onClick = {
                        haptics.performConfirm(view)
                        onFinish()
                    },
                    colors = primaryNavButtonColors()
                ) {
                    Text(
                        text = stringResource(if (hasAnyPermission) R.string.get_started else R.string.skip_for_now),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(R.string.permissions_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (hasAnyPermission) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (isAccessibilityEnabled) {
                        SegmentedCard(index = 0, count = if (isOverlayEnabled) 2 else 1) {
                            GrantedSummaryRow(
                                title = stringResource(R.string.perm_accessibility_title),
                                description = stringResource(R.string.perm_accessibility_desc),
                                icon = Icons.Rounded.TouchApp
                            )
                        }
                    }
                    if (isOverlayEnabled) {
                        SegmentedCard(index = if (isAccessibilityEnabled) 1 else 0, count = if (isAccessibilityEnabled) 2 else 1) {
                            GrantedSummaryRow(
                                title = stringResource(R.string.perm_overlay_title),
                                description = stringResource(R.string.perm_overlay_desc),
                                icon = Icons.Rounded.Layers
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SuccessHero(
                        isAccessibility = isAccessibilityEnabled
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SegmentedCard(
                        index = 0,
                        count = 2
                    ) {
                        PermissionCardRow(
                            title = stringResource(R.string.perm_accessibility_title),
                            badge = stringResource(R.string.perm_recommended),
                            description = stringResource(R.string.perm_accessibility_desc),
                            icon = Icons.Rounded.TouchApp,
                            isEnabled = isAccessibilityEnabled,
                            isPrimary = true,
                            onTurnOn = {
                                haptics.performSelection(view)
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: ActivityNotFoundException) {
                                    try {
                                        context.startActivity(
                                            Intent(Settings.ACTION_SETTINGS)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } catch (_: Throwable) {}
                                }
                            }
                        )
                    }

                    SegmentedCard(
                        index = 1,
                        count = 2
                    ) {
                        PermissionCardRow(
                            title = stringResource(R.string.perm_overlay_title),
                            badge = stringResource(R.string.perm_fallback),
                            description = stringResource(R.string.perm_overlay_desc),
                            icon = Icons.Rounded.Layers,
                            isEnabled = isOverlayEnabled,
                            isPrimary = false,
                            onTurnOn = {
                                haptics.performSelection(view)
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                    try {
                                        context.startActivity(
                                            Intent(Settings.ACTION_SETTINGS)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } catch (_: Throwable) {}
                                }
                            }
                        )
                    }
                }

                RestrictedHelpCard(
                    onOpenAppInfo = {
                        haptics.performLightTap(view)
                        try {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (_: Throwable) {}
                    },
                    onViewVisualGuide = {
                        haptics.performLightTap(view)
                        try {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/Earendel-lab/Tapbar#installation-guide")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (_: Throwable) {}
                    }
                )
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun GrantedSummaryRow(
    title: String,
    description: String,
    icon: ImageVector
) {
    val isDark = isSystemInDarkTheme()
    val grantedBgColor = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFFE2E2E2)
    val grantedTextColor = if (isDark) MaterialTheme.colorScheme.surface else Color.Black

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            shape = RoundedCornerShape(100),
            color = grantedBgColor,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.perm_granted),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = grantedTextColor
                )
            }
        }
    }
}

@Composable
private fun SuccessHero(
    isAccessibility: Boolean
) {
    val context = LocalContext.current
    val haptics = LocalHapticManager.current
    val view = LocalView.current

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

    var hasAnimated by rememberSaveable { mutableStateOf(false) }

    val scaleAnim = remember { Animatable(if (reducedMotion || hasAnimated) 1f else 0.85f) }
    val checkAnim = remember { Animatable(if (reducedMotion || hasAnimated) 1f else 0f) }
    val textAlphaAnim = remember { Animatable(if (reducedMotion || hasAnimated) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!reducedMotion && !hasAnimated) {
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.92f,
                    stiffness = Spring.StiffnessMedium
                )
            )
            delay(150)
            checkAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            )
            haptics.performConfirm(view)
            textAlphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 250)
            )
            hasAnimated = true
        }
    }

    val isDark = isSystemInDarkTheme()
    val heroBgColor = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFFFCFCFC)
    val checkColor = if (isDark) MaterialTheme.colorScheme.surface else Color.Black
    val cdText = stringResource(R.string.cd_setup_complete)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = cdText
            }
    ) {
        Surface(
            shape = RoundedCornerShape(42.dp),
            color = heroBgColor,
            modifier = Modifier
                .size(180.dp)
                .graphicsLayer(
                    scaleX = scaleAnim.value,
                    scaleY = scaleAnim.value,
                    alpha = scaleAnim.value
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(88.dp)) {
                    val w = size.width
                    val h = size.height
                    val fullPath = Path().apply {
                        moveTo(0.28f * w, 0.50f * h)
                        lineTo(0.44f * w, 0.66f * h)
                        lineTo(0.72f * w, 0.36f * h)
                    }
                    val pathMeasure = PathMeasure()
                    pathMeasure.setPath(fullPath, false)
                    val animatedPath = Path()
                    pathMeasure.getSegment(0f, pathMeasure.length * checkAnim.value, animatedPath, true)
                    drawPath(
                        path = animatedPath,
                        color = checkColor,
                        style = Stroke(
                            width = 9.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.graphicsLayer(alpha = textAlphaAnim.value)
        ) {
            Text(
                text = stringResource(R.string.success_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(if (isAccessibility) R.string.success_desc_a11y else R.string.success_desc_overlay),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
private fun PermissionCardRow(
    title: String,
    badge: String,
    description: String,
    icon: ImageVector,
    isEnabled: Boolean,
    isPrimary: Boolean,
    onTurnOn: () -> Unit
) {
    val statusText = stringResource(if (isEnabled) R.string.perm_granted else R.string.perm_allow)
    val semanticDesc = "$title. $description. $statusText"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clearAndSetSemantics {
                contentDescription = semanticDesc
            },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (badge.isNotEmpty() && !isEnabled) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AnimatedContent(
            targetState = isEnabled,
            transitionSpec = {
                fadeIn(tween(250)) togetherWith fadeOut(tween(250))
            },
            label = "PermissionButtonCrossfade"
        ) { granted ->
            if (granted) {
                val grantedDark = isSystemInDarkTheme()
                Surface(
                    shape = RoundedCornerShape(100),
                    color = if (grantedDark) MaterialTheme.colorScheme.onSurface else Color(0xFFE2E2E2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.perm_granted),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (grantedDark) MaterialTheme.colorScheme.surface else Color.Black
                        )
                    }
                }
            } else {
                if (isPrimary) {
                    Button(
                        onClick = onTurnOn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                        colors = cardActionButtonColors()
                    ) {
                        Text(
                            text = stringResource(R.string.perm_allow),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onTurnOn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.perm_allow),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RestrictedHelpCard(
    onOpenAppInfo: () -> Unit,
    onViewVisualGuide: () -> Unit
) {
    SegmentedCard(index = 0, count = 1) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.restricted_help_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = stringResource(R.string.restricted_help_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Button(
                    onClick = onOpenAppInfo,
                    shape = segmentedShape(0, 2),
                    colors = cardActionButtonColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Text(
                        text = stringResource(R.string.open_app_info),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(
                    onClick = onViewVisualGuide,
                    shape = segmentedShape(1, 2),
                    colors = cardActionButtonColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Text(
                        text = stringResource(R.string.view_visual_guide),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
