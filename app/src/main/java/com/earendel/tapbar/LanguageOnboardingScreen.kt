package com.earendel.tapbar

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

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
    AppLanguage("zh", "中文", "Chinese")
)

private val PinnedTags = listOf("en", "pt", "ru")

val SupportedLanguages: List<AppLanguage> =
    PinnedTags.mapNotNull { tag -> AllLanguages.firstOrNull { it.tag == tag } } +
        AllLanguages.filter { it.tag !in PinnedTags }.sortedBy { it.englishName }

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
    onComplete: () -> Unit
) {
    var selectedTag by rememberSaveable { mutableStateOf("en") }
    val context = LocalContext.current
    val haptics = LocalHapticManager.current
    val view = LocalView.current

    BackHandler {
        LocaleHelper.findActivity(context)?.finish()
    }

    Scaffold(
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
                        prefs.hasCompletedLanguageOnboarding = true
                        onComplete()
                        LocaleHelper.applyLanguage(context, prefs, selectedTag)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        text = stringResource(R.string.done),
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
