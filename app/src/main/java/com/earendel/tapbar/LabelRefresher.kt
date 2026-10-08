package com.earendel.tapbar

import android.content.Context

object LabelRefresher {

    private const val KEY_BASIC = "labels_locale_basic"
    private const val KEY_SHORTCUTS = "labels_locale_shortcuts"

    private fun store(context: Context) =
        context.getSharedPreferences("tapbar_prefs", Context.MODE_PRIVATE)

    private fun actionTitle(localized: Context, id: String?): String? {
        val entry = systemActions.firstOrNull { it.id == id } ?: return null
        return localized.getString(entry.titleRes)
    }

    fun refreshBasic(context: Context, prefs: Prefs) {
        val tag = LocaleHelper.selectedTag(prefs)
        val sp = store(context)
        if (sp.getString(KEY_BASIC, null) == tag) return

        val locale = AppLabels.appLocale(context)
        val localized = AppLabels.localizedContext(context)
        val appCache = HashMap<String, String>()

        fun appLabel(pkg: String?): String? {
            if (pkg.isNullOrEmpty()) return null
            appCache[pkg]?.let { return it }
            val label = AppLabels.label(context, pkg, locale) ?: return null
            appCache[pkg] = label
            return label
        }

        for (zone in 0 until ZONE_COUNT) {
            actionTitle(localized, prefs.getSingleTapActionId(zone))?.let { prefs.setSingleTapActionLabel(zone, it) }
            actionTitle(localized, prefs.getDoubleTapActionId(zone))?.let { prefs.setDoubleTapActionLabel(zone, it) }
            actionTitle(localized, prefs.getTripleTapActionId(zone))?.let { prefs.setTripleTapActionLabel(zone, it) }
            actionTitle(localized, prefs.getLongPressActionId(zone))?.let { prefs.setLongPressActionLabel(zone, it) }

            appLabel(prefs.getSingleTapTargetPkg(zone))?.let { prefs.setSingleTapTargetLabel(zone, it) }
            appLabel(prefs.getDoubleTapTargetPkg(zone))?.let { prefs.setDoubleTapTargetLabel(zone, it) }
            appLabel(prefs.getTripleTapTargetPkg(zone))?.let { prefs.setTripleTapTargetLabel(zone, it) }
            appLabel(prefs.getLongPressTargetPkg(zone))?.let { prefs.setLongPressTargetLabel(zone, it) }
        }

        sp.edit().putString(KEY_BASIC, tag).apply()
    }

    fun refreshShortcuts(context: Context, prefs: Prefs): Boolean {
        val tag = LocaleHelper.selectedTag(prefs)
        val sp = store(context)
        if (sp.getString(KEY_SHORTCUTS, null) == tag) return false

        val byUri = HashMap<String, String>()
        for (app in AppShortcuts.loadAll(context, forceRefresh = true)) {
            for (shortcut in app.shortcuts) {
                byUri[shortcut.intentUri] = shortcut.label
            }
        }

        var changed = false
        for (zone in 0 until ZONE_COUNT) {
            prefs.getSingleTapShortcutUri(zone)?.let { uri ->
                val label = byUri[uri]
                if (label != null && label != prefs.getSingleTapShortcutLabel(zone)) {
                    prefs.setSingleTapShortcutLabel(zone, label)
                    changed = true
                }
            }
            prefs.getDoubleTapShortcutUri(zone)?.let { uri ->
                val label = byUri[uri]
                if (label != null && label != prefs.getDoubleTapShortcutLabel(zone)) {
                    prefs.setDoubleTapShortcutLabel(zone, label)
                    changed = true
                }
            }
            prefs.getTripleTapShortcutUri(zone)?.let { uri ->
                val label = byUri[uri]
                if (label != null && label != prefs.getTripleTapShortcutLabel(zone)) {
                    prefs.setTripleTapShortcutLabel(zone, label)
                    changed = true
                }
            }
            prefs.getLongPressShortcutUri(zone)?.let { uri ->
                val label = byUri[uri]
                if (label != null && label != prefs.getLongPressShortcutLabel(zone)) {
                    prefs.setLongPressShortcutLabel(zone, label)
                    changed = true
                }
            }
        }

        sp.edit().putString(KEY_SHORTCUTS, tag).apply()
        return changed
    }
}
