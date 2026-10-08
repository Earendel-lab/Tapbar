package com.earendel.tapbar

import android.content.Context
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

object AppLabels {

    fun appLocale(context: Context): Locale =
        Locale.forLanguageTag(LocaleHelper.selectedTag(Prefs(context)))

    fun localizedContext(context: Context): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(appLocale(context))
        return context.createConfigurationContext(config)
    }

    fun packageResources(
        context: Context,
        packageName: String,
        locale: Locale = appLocale(context)
    ): Resources? {
        return try {
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            context.createPackageContext(packageName, 0)
                .createConfigurationContext(config)
                .resources
        } catch (_: Throwable) {
            null
        }
    }

    fun label(
        context: Context,
        info: ResolveInfo,
        locale: Locale = appLocale(context)
    ): String {
        val pm = context.packageManager
        val fallback = try {
            info.loadLabel(pm).toString()
        } catch (_: Throwable) {
            info.activityInfo?.packageName ?: ""
        }
        val activity = info.activityInfo ?: return fallback
        val labelRes = if (activity.labelRes != 0) {
            activity.labelRes
        } else {
            activity.applicationInfo?.labelRes ?: 0
        }
        if (labelRes == 0) return fallback
        val res = packageResources(context, activity.packageName, locale) ?: return fallback
        return try {
            res.getString(labelRes).ifBlank { fallback }
        } catch (_: Throwable) {
            fallback
        }
    }

    fun label(
        context: Context,
        packageName: String,
        locale: Locale = appLocale(context)
    ): String? {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return null
        val info = try {
            pm.resolveActivity(intent, 0)
        } catch (_: Throwable) {
            null
        } ?: return null
        return label(context, info, locale)
    }
}
