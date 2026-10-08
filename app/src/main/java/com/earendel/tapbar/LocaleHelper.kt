package com.earendel.tapbar

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale

private val DevanagariFamily = FontFamily(
    Font(resId = R.font.noto_sans_devanagari, weight = FontWeight.Normal),
    Font(resId = R.font.noto_sans_devanagari, weight = FontWeight.Bold)
)

private val SimplifiedChineseFamily = FontFamily(
    Font(resId = R.font.noto_sans_sc, weight = FontWeight.Normal),
    Font(resId = R.font.noto_sans_sc, weight = FontWeight.Bold)
)

private val JapaneseFamily = FontFamily(
    Font(resId = R.font.noto_sans_jp, weight = FontWeight.Normal),
    Font(resId = R.font.noto_sans_jp, weight = FontWeight.Bold)
)

private val KoreanFamily = FontFamily(
    Font(resId = R.font.noto_sans_kr, weight = FontWeight.Normal),
    Font(resId = R.font.noto_sans_kr, weight = FontWeight.Bold)
)

fun languageFontFamily(tag: String): FontFamily = when (tag) {
    "hi", "ne" -> DevanagariFamily
    "zh" -> SimplifiedChineseFamily
    "ja" -> JapaneseFamily
    "ko" -> KoreanFamily
    else -> GeistFontFamily
}

object LocaleHelper {

    const val SYSTEM = "system"

    private val supportedTags: Set<String> by lazy { SupportedLanguages.map { it.tag }.toSet() }

    private fun savedTag(context: Context): String =
        context.getSharedPreferences("tapbar_prefs", Context.MODE_PRIVATE)
            .getString("app_language", SYSTEM) ?: SYSTEM

    private fun systemLanguage(): String =
        Resources.getSystem().configuration.locales[0].language

    fun selectedTag(prefs: Prefs): String {
        val saved = prefs.appLanguage
        if (saved in supportedTags) return saved
        val system = systemLanguage()
        return if (system in supportedTags) system else "en"
    }

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = savedTag(base)
        if (tag !in supportedTags) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    fun needsRecreate(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return false
        val saved = savedTag(activity)
        val expected = if (saved in supportedTags) saved else systemLanguage()
        val actual = activity.resources.configuration.locales[0].language
        return expected != actual
    }

    fun syncFromSystem(context: Context, prefs: Prefs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val manager = context.getSystemService(Context.LOCALE_SERVICE) as? LocaleManager ?: return
        val locales = manager.applicationLocales
        val resolved = if (locales.isEmpty) SYSTEM else locales[0].language
        if (resolved != SYSTEM && resolved !in supportedTags) return
        if (prefs.appLanguage != resolved) {
            prefs.appLanguage = resolved
            invalidateAppCache()
        }
    }

    fun applyLanguage(context: Context, prefs: Prefs, tag: String) {
        prefs.appLanguage = tag
        invalidateAppCache()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(Context.LOCALE_SERVICE) as? LocaleManager
            manager?.applicationLocales =
                if (tag == SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            findActivity(context)?.recreate()
        }
    }

    fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
