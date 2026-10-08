package com.earendel.tapbar

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.MediaStore

fun getDefaultCameraPackage(context: Context): Pair<String, String>? {
    val pm = context.packageManager
    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    val resolveInfo = try {
        pm.resolveActivity(intent, 0)
    } catch (_: Throwable) {
        null
    }
    if (resolveInfo?.activityInfo != null) {
        val pkg = resolveInfo.activityInfo.packageName
        if (pkg != "android") {
            val label = AppLabels.label(context, resolveInfo)
            return Pair(pkg, label)
        }
    }
    val cameraIntent = Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_CAMERA")
    val camInfo = try {
        pm.resolveActivity(cameraIntent, 0)
    } catch (_: Throwable) {
        null
    }
    if (camInfo?.activityInfo != null) {
        val pkg = camInfo.activityInfo.packageName
        val label = AppLabels.label(context, camInfo)
        return Pair(pkg, label)
    }
    return null
}

fun getDefaultClockPackage(context: Context): Pair<String, String>? {
    val pm = context.packageManager
    val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
    val resolveInfo = try {
        pm.resolveActivity(intent, 0)
    } catch (_: Throwable) {
        null
    }
    if (resolveInfo?.activityInfo != null) {
        val pkg = resolveInfo.activityInfo.packageName
        val label = AppLabels.label(context, resolveInfo)
        return Pair(pkg, label)
    }
    return null
}

const val ZONE_COUNT = 4

class Prefs(context: Context) {

    private val appContext = context.applicationContext

    private val sp = appContext.getSharedPreferences("tapbar_prefs", Context.MODE_PRIVATE)

    private fun zk(zone: Int, base: String): String = "z${zone + 1}_$base"

    private fun defaultPosX(zone: Int): Int = when (zone) {
        0 -> 12
        1 -> 132
        else -> 252
    }

    private fun defaultPosY(zone: Int): Int = when (zone) {
        1 -> 40
        3 -> 40
        else -> 0
    }

    fun isZoneEnabled(zone: Int): Boolean =
        sp.getBoolean("zone${zone + 1}_enabled", zone == 0)

    fun setZoneEnabled(zone: Int, v: Boolean) {
        sp.edit().putBoolean("zone${zone + 1}_enabled", v).apply()
    }

    fun getSwipeEnabled(zone: Int): Boolean = sp.getBoolean(zk(zone, "swipe_enabled"), false)
    fun setSwipeEnabled(zone: Int, v: Boolean) {
        sp.edit().putBoolean(zk(zone, "swipe_enabled"), v).apply()
    }

    fun getSwipeTarget(zone: Int): Int = sp.getInt(zk(zone, "swipe_target"), SWIPE_TARGET_VOLUME)
    fun setSwipeTarget(zone: Int, v: Int) {
        sp.edit().putInt(zk(zone, "swipe_target"), v).apply()
    }

    fun resetToDefaults() {
        val keep = setOf("service_enabled", "has_seen_privacy_notice", "app_language", "has_completed_language_onboarding")
        val editor = sp.edit()
        for (key in sp.all.keys) {
            if (key !in keep) editor.remove(key)
        }
        editor.apply()
    }

    var posX: Int
        get() = sp.getInt("pos_x", 12)
        set(v) = sp.edit().putInt("pos_x", v).apply()

    var posY: Int
        get() = sp.getInt("pos_y", 0)
        set(v) = sp.edit().putInt("pos_y", v).apply()

    var zoneWidth: Int
        get() = sp.getInt("zone_w", 96)
        set(v) = sp.edit().putInt("zone_w", v).apply()

    var zoneHeight: Int
        get() = sp.getInt("zone_h", 28)
        set(v) = sp.edit().putInt("zone_h", v).apply()

    var targetPackage: String?
        get() = singleTapTargetPkg ?: sp.getString("target_pkg", null)
        set(v) {
            singleTapTargetPkg = v
            sp.edit().putString("target_pkg", v).apply()
        }

    var targetLabel: String?
        get() = singleTapTargetLabel ?: sp.getString("target_label", null)
        set(v) {
            singleTapTargetLabel = v
            sp.edit().putString("target_label", v).apply()
        }

    var serviceEnabled: Boolean
        get() = sp.getBoolean("service_enabled", true)
        set(v) = sp.edit().putBoolean("service_enabled", v).apply()

    var autoStart: Boolean
        get() = sp.getBoolean("auto_start", true)
        set(v) = sp.edit().putBoolean("auto_start", v).apply()

    var disableInLandscape: Boolean
        get() = sp.getBoolean("disable_in_landscape", false)
        set(v) = sp.edit().putBoolean("disable_in_landscape", v).apply()

    var themeMode: Int
        get() = sp.getInt("theme_mode", 0)
        set(v) = sp.edit().putInt("theme_mode", v).apply()

    var hapticFeedbackEnabled: Boolean
        get() = sp.getBoolean("haptic_feedback_enabled", true)
        set(v) = sp.edit().putBoolean("haptic_feedback_enabled", v).apply()

    var tapHapticMode: Int
        get() = sp.getInt("tap_haptic_mode", 0)
        set(v) = sp.edit().putInt("tap_haptic_mode", v).apply()

    var appLanguage: String
        get() = sp.getString("app_language", "system") ?: "system"
        set(v) = sp.edit().putString("app_language", v).apply()

    var hasSetInitialPosition: Boolean
        get() = sp.getBoolean("has_initial_pos", false)
        set(v) = sp.edit().putBoolean("has_initial_pos", v).apply()

    var hasSeenPrivacyNotice: Boolean
        get() = sp.getBoolean("has_seen_privacy_notice", false)
        set(v) = sp.edit().putBoolean("has_seen_privacy_notice", v).apply()

    var hasCompletedLanguageOnboarding: Boolean
        get() {
            if (!sp.contains("has_completed_language_onboarding")) {
                val isExistingUser = sp.contains("has_initial_pos") || sp.contains("has_seen_privacy_notice") || sp.contains("service_enabled")
                if (isExistingUser) {
                    sp.edit().putBoolean("has_completed_language_onboarding", true).apply()
                    return true
                }
                return false
            }
            return sp.getBoolean("has_completed_language_onboarding", false)
        }
        set(v) = sp.edit().putBoolean("has_completed_language_onboarding", v).apply()

    var blockedPackages: Set<String>
        get() {
            val raw = sp.getString("blocked_pkgs_str", "") ?: ""
            if (raw.isBlank()) return emptySet()
            return raw.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        }
        set(v) {
            val cleanSet = v.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            val str = cleanSet.joinToString(",")
            sp.edit().putString("blocked_pkgs_str", str).apply()
        }

    var zone2Enabled: Boolean
        get() = sp.getBoolean("zone2_enabled", false)
        set(v) = sp.edit().putBoolean("zone2_enabled", v).apply()

    private fun geoKey(zone: Int, base: String): String = if (zone >= 1) zk(zone, base) else base

    private fun landKey(zone: Int, base: String): String = zk(zone, "land_$base")

    private fun portraitGeometry(zone: Int): ZoneGeometry = ZoneGeometry(
        sp.getInt(geoKey(zone, "pos_x"), defaultPosX(zone)),
        sp.getInt(geoKey(zone, "pos_y"), defaultPosY(zone)),
        sp.getInt(geoKey(zone, "zone_w"), 96),
        sp.getInt(geoKey(zone, "zone_h"), 28)
    )

    private fun storedLandscapeGeometry(zone: Int): ZoneGeometry? {
        if (!sp.contains(landKey(zone, "pos_x"))) return null
        return ZoneGeometry(
            sp.getInt(landKey(zone, "pos_x"), 0),
            sp.getInt(landKey(zone, "pos_y"), 0),
            sp.getInt(landKey(zone, "zone_w"), 96),
            sp.getInt(landKey(zone, "zone_h"), 28)
        )
    }

    fun screenWidthDp(): Int = ScreenMetrics.sizeDp(appContext).first

    fun screenHeightDp(): Int = ScreenMetrics.sizeDp(appContext).second

    fun geometry(zone: Int): ZoneGeometry {
        val (sw, sh) = ScreenMetrics.sizeDp(appContext)
        val shortSide = minOf(sw, sh)
        val longSide = maxOf(sw, sh)
        val raw = if (sw > sh) {
            storedLandscapeGeometry(zone) ?: ZoneLayout.toLandscape(
                ZoneLayout.clamp(portraitGeometry(zone), shortSide, longSide),
                shortSide,
                longSide
            )
        } else {
            portraitGeometry(zone)
        }
        return ZoneLayout.clamp(raw, sw, sh)
    }

    private fun writeGeometry(zone: Int, base: String, v: Int) {
        if (ScreenMetrics.isLandscape(appContext)) {
            if (!sp.contains(landKey(zone, "pos_x"))) {
                val g = geometry(zone)
                sp.edit()
                    .putInt(landKey(zone, "pos_x"), g.x)
                    .putInt(landKey(zone, "pos_y"), g.y)
                    .putInt(landKey(zone, "zone_w"), g.w)
                    .putInt(landKey(zone, "zone_h"), g.h)
                    .apply()
            }
            sp.edit().putInt(landKey(zone, base), v).apply()
        } else {
            sp.edit().putInt(geoKey(zone, base), v).apply()
        }
    }

    fun getPosX(zone: Int): Int = geometry(zone).x
    fun setPosX(zone: Int, v: Int) = writeGeometry(zone, "pos_x", v)

    fun getPosY(zone: Int): Int = geometry(zone).y
    fun setPosY(zone: Int, v: Int) = writeGeometry(zone, "pos_y", v)

    fun getZoneWidth(zone: Int): Int = geometry(zone).w
    fun setZoneWidth(zone: Int, v: Int) = writeGeometry(zone, "zone_w", v)

    fun getZoneHeight(zone: Int): Int = geometry(zone).h
    fun setZoneHeight(zone: Int, v: Int) = writeGeometry(zone, "zone_h", v)

    var singleTapType: Int
        get() = sp.getInt("single_tap_type", 0)
        set(v) = sp.edit().putInt("single_tap_type", v).apply()

    var singleTapTargetPkg: String?
        get() = sp.getString("single_tap_target_pkg", sp.getString("target_pkg", null))
        set(v) = sp.edit().putString("single_tap_target_pkg", v).apply()

    var singleTapTargetLabel: String?
        get() = sp.getString("single_tap_target_label", sp.getString("target_label", null))
        set(v) = sp.edit().putString("single_tap_target_label", v).apply()

    var singleTapActionId: String?
        get() = sp.getString("single_tap_action_id", "screenshot")
        set(v) = sp.edit().putString("single_tap_action_id", v).apply()

    var singleTapActionLabel: String?
        get() = sp.getString("single_tap_action_label", "Take screenshot")
        set(v) = sp.edit().putString("single_tap_action_label", v).apply()

    fun getSingleTapType(zone: Int): Int = if (zone >= 1) sp.getInt(zk(zone, "single_tap_type"), 0) else singleTapType
    fun setSingleTapType(zone: Int, v: Int) {
        if (zone >= 1) sp.edit().putInt(zk(zone, "single_tap_type"), v).apply() else singleTapType = v
    }

    fun getSingleTapTargetPkg(zone: Int, context: Context? = null): String? {
        if (zone >= 1) {
            val saved = sp.getString(zk(zone, "single_tap_target_pkg"), null)
            if (saved != null) return saved
            return context?.let { getDefaultCameraPackage(it)?.first }
        }
        val saved = singleTapTargetPkg
        if (saved != null) return saved
        return context?.let { getDefaultClockPackage(it)?.first }
    }

    fun setSingleTapTargetPkg(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "single_tap_target_pkg"), v).apply() else singleTapTargetPkg = v
    }

    fun getSingleTapTargetLabel(zone: Int, context: Context? = null): String? {
        if (zone >= 1) {
            val saved = sp.getString(zk(zone, "single_tap_target_label"), null)
            if (saved != null) return saved
            return context?.let { getDefaultCameraPackage(it)?.second } ?: "Camera"
        }
        val saved = singleTapTargetLabel
        if (saved != null) return saved
        return context?.let { getDefaultClockPackage(it)?.second } ?: "Clock"
    }

    fun setSingleTapTargetLabel(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "single_tap_target_label"), v).apply() else singleTapTargetLabel = v
    }

    fun getSingleTapActionId(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "single_tap_action_id"), "camera") else singleTapActionId
    fun setSingleTapActionId(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "single_tap_action_id"), v).apply() else singleTapActionId = v
    }

    fun getSingleTapActionLabel(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "single_tap_action_label"), "Open camera") else singleTapActionLabel
    fun setSingleTapActionLabel(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "single_tap_action_label"), v).apply() else singleTapActionLabel = v
    }

    fun getSingleTapShortcutUri(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "single_tap_shortcut_uri"), null) else singleTapShortcutUri
    fun setSingleTapShortcutUri(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "single_tap_shortcut_uri"), v).apply() else singleTapShortcutUri = v
    }

    fun getSingleTapShortcutLabel(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "single_tap_shortcut_label"), null) else singleTapShortcutLabel
    fun setSingleTapShortcutLabel(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "single_tap_shortcut_label"), v).apply() else singleTapShortcutLabel = v
    }

    var singleTapEnabled: Boolean
        get() = sp.getBoolean("single_tap_enabled", true)
        set(v) = sp.edit().putBoolean("single_tap_enabled", v).apply()

    fun getSingleTapEnabled(zone: Int): Boolean = if (zone >= 1) sp.getBoolean(zk(zone, "single_tap_enabled"), true) else singleTapEnabled
    fun setSingleTapEnabled(zone: Int, v: Boolean) {
        if (zone >= 1) sp.edit().putBoolean(zk(zone, "single_tap_enabled"), v).apply() else singleTapEnabled = v
    }

    var doubleTapEnabled: Boolean
        get() = sp.getBoolean("double_tap_enabled", true)
        set(v) = sp.edit().putBoolean("double_tap_enabled", v).apply()

    var doubleTapSpeedMs: Int
        get() = sp.getInt("double_tap_speed_ms", 300)
        set(v) = sp.edit().putInt("double_tap_speed_ms", v).apply()

    var tapSpeedMs: Int
        get() = doubleTapSpeedMs
        set(v) { doubleTapSpeedMs = v }

    var doubleTapType: Int
        get() = sp.getInt("double_tap_type", 1)
        set(v) = sp.edit().putInt("double_tap_type", v).apply()

    var doubleTapTargetPkg: String?
        get() = sp.getString("double_tap_target_pkg", null)
        set(v) = sp.edit().putString("double_tap_target_pkg", v).apply()

    var doubleTapTargetLabel: String?
        get() = sp.getString("double_tap_target_label", null)
        set(v) = sp.edit().putString("double_tap_target_label", v).apply()

    var doubleTapActionId: String?
        get() = sp.getString("double_tap_action_id", "lock_screen")
        set(v) = sp.edit().putString("double_tap_action_id", v).apply()

    var doubleTapActionLabel: String?
        get() = sp.getString("double_tap_action_label", "Turn off screen")
        set(v) = sp.edit().putString("double_tap_action_label", v).apply()

    fun getDoubleTapEnabled(zone: Int): Boolean = if (zone >= 1) sp.getBoolean(zk(zone, "double_tap_enabled"), true) else doubleTapEnabled
    fun setDoubleTapEnabled(zone: Int, v: Boolean) {
        if (zone >= 1) sp.edit().putBoolean(zk(zone, "double_tap_enabled"), v).apply() else doubleTapEnabled = v
    }

    fun getDoubleTapType(zone: Int): Int = if (zone >= 1) sp.getInt(zk(zone, "double_tap_type"), 1) else doubleTapType
    fun setDoubleTapType(zone: Int, v: Int) {
        if (zone >= 1) sp.edit().putInt(zk(zone, "double_tap_type"), v).apply() else doubleTapType = v
    }

    fun getDoubleTapTargetPkg(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "double_tap_target_pkg"), null) else doubleTapTargetPkg
    fun setDoubleTapTargetPkg(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "double_tap_target_pkg"), v).apply() else doubleTapTargetPkg = v
    }

    fun getDoubleTapTargetLabel(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "double_tap_target_label"), null) else doubleTapTargetLabel
    fun setDoubleTapTargetLabel(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "double_tap_target_label"), v).apply() else doubleTapTargetLabel = v
    }

    fun getDoubleTapActionId(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "double_tap_action_id"), "flashlight") else doubleTapActionId
    fun setDoubleTapActionId(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "double_tap_action_id"), v).apply() else doubleTapActionId = v
    }

    fun getDoubleTapActionLabel(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "double_tap_action_label"), "Toggle flashlight") else doubleTapActionLabel
    fun setDoubleTapActionLabel(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "double_tap_action_label"), v).apply() else doubleTapActionLabel = v
    }

    fun getDoubleTapShortcutUri(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "double_tap_shortcut_uri"), null) else doubleTapShortcutUri
    fun setDoubleTapShortcutUri(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "double_tap_shortcut_uri"), v).apply() else doubleTapShortcutUri = v
    }

    fun getDoubleTapShortcutLabel(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "double_tap_shortcut_label"), null) else doubleTapShortcutLabel
    fun setDoubleTapShortcutLabel(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "double_tap_shortcut_label"), v).apply() else doubleTapShortcutLabel = v
    }

    var singleTapShortcutUri: String?
        get() = sp.getString("single_tap_shortcut_uri", null)
        set(v) = sp.edit().putString("single_tap_shortcut_uri", v).apply()

    var singleTapShortcutLabel: String?
        get() = sp.getString("single_tap_shortcut_label", null)
        set(v) = sp.edit().putString("single_tap_shortcut_label", v).apply()

    var doubleTapShortcutUri: String?
        get() = sp.getString("double_tap_shortcut_uri", null)
        set(v) = sp.edit().putString("double_tap_shortcut_uri", v).apply()

    var doubleTapShortcutLabel: String?
        get() = sp.getString("double_tap_shortcut_label", null)
        set(v) = sp.edit().putString("double_tap_shortcut_label", v).apply()

    var tripleTapEnabled: Boolean
        get() = sp.getBoolean("triple_tap_enabled", true)
        set(v) = sp.edit().putBoolean("triple_tap_enabled", v).apply()

    var tripleTapType: Int
        get() = sp.getInt("triple_tap_type", 1)
        set(v) = sp.edit().putInt("triple_tap_type", v).apply()

    var tripleTapTargetPkg: String?
        get() = sp.getString("triple_tap_target_pkg", null)
        set(v) = sp.edit().putString("triple_tap_target_pkg", v).apply()

    var tripleTapTargetLabel: String?
        get() = sp.getString("triple_tap_target_label", null)
        set(v) = sp.edit().putString("triple_tap_target_label", v).apply()

    var tripleTapActionId: String?
        get() = sp.getString("triple_tap_action_id", "notifications")
        set(v) = sp.edit().putString("triple_tap_action_id", v).apply()

    var tripleTapActionLabel: String?
        get() = sp.getString("triple_tap_action_label", "Expand notifications")
        set(v) = sp.edit().putString("triple_tap_action_label", v).apply()

    var tripleTapShortcutUri: String?
        get() = sp.getString("triple_tap_shortcut_uri", null)
        set(v) = sp.edit().putString("triple_tap_shortcut_uri", v).apply()

    var tripleTapShortcutLabel: String?
        get() = sp.getString("triple_tap_shortcut_label", null)
        set(v) = sp.edit().putString("triple_tap_shortcut_label", v).apply()

    fun getTripleTapEnabled(zone: Int): Boolean = if (zone >= 1) sp.getBoolean(zk(zone, "triple_tap_enabled"), true) else tripleTapEnabled
    fun setTripleTapEnabled(zone: Int, v: Boolean) {
        if (zone >= 1) sp.edit().putBoolean(zk(zone, "triple_tap_enabled"), v).apply() else tripleTapEnabled = v
    }

    fun getTripleTapType(zone: Int): Int = if (zone >= 1) sp.getInt(zk(zone, "triple_tap_type"), 1) else tripleTapType
    fun setTripleTapType(zone: Int, v: Int) {
        if (zone >= 1) sp.edit().putInt(zk(zone, "triple_tap_type"), v).apply() else tripleTapType = v
    }

    fun getTripleTapTargetPkg(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "triple_tap_target_pkg"), null) else tripleTapTargetPkg
    fun setTripleTapTargetPkg(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "triple_tap_target_pkg"), v).apply() else tripleTapTargetPkg = v
    }

    fun getTripleTapTargetLabel(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "triple_tap_target_label"), null) else tripleTapTargetLabel
    fun setTripleTapTargetLabel(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "triple_tap_target_label"), v).apply() else tripleTapTargetLabel = v
    }

    fun getTripleTapActionId(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "triple_tap_action_id"), "notifications") else tripleTapActionId
    fun setTripleTapActionId(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "triple_tap_action_id"), v).apply() else tripleTapActionId = v
    }

    fun getTripleTapActionLabel(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "triple_tap_action_label"), "Expand notifications") else tripleTapActionLabel
    fun setTripleTapActionLabel(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "triple_tap_action_label"), v).apply() else tripleTapActionLabel = v
    }

    fun getTripleTapShortcutUri(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "triple_tap_shortcut_uri"), null) else tripleTapShortcutUri
    fun setTripleTapShortcutUri(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "triple_tap_shortcut_uri"), v).apply() else tripleTapShortcutUri = v
    }

    fun getTripleTapShortcutLabel(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "triple_tap_shortcut_label"), null) else tripleTapShortcutLabel
    fun setTripleTapShortcutLabel(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "triple_tap_shortcut_label"), v).apply() else tripleTapShortcutLabel = v
    }

    var longPressEnabled: Boolean
        get() = sp.getBoolean("long_press_enabled", true)
        set(v) = sp.edit().putBoolean("long_press_enabled", v).apply()

    var longPressType: Int
        get() = sp.getInt("long_press_type", 1)
        set(v) = sp.edit().putInt("long_press_type", v).apply()

    var longPressTargetPkg: String?
        get() = sp.getString("long_press_target_pkg", null)
        set(v) = sp.edit().putString("long_press_target_pkg", v).apply()

    var longPressTargetLabel: String?
        get() = sp.getString("long_press_target_label", null)
        set(v) = sp.edit().putString("long_press_target_label", v).apply()

    var longPressActionId: String?
        get() = sp.getString("long_press_action_id", "power_menu")
        set(v) = sp.edit().putString("long_press_action_id", v).apply()

    var longPressActionLabel: String?
        get() = sp.getString("long_press_action_label", "Power menu")
        set(v) = sp.edit().putString("long_press_action_label", v).apply()

    var longPressShortcutUri: String?
        get() = sp.getString("long_press_shortcut_uri", null)
        set(v) = sp.edit().putString("long_press_shortcut_uri", v).apply()

    var longPressShortcutLabel: String?
        get() = sp.getString("long_press_shortcut_label", null)
        set(v) = sp.edit().putString("long_press_shortcut_label", v).apply()

    fun getLongPressEnabled(zone: Int): Boolean = if (zone >= 1) sp.getBoolean(zk(zone, "long_press_enabled"), true) else longPressEnabled
    fun setLongPressEnabled(zone: Int, v: Boolean) {
        if (zone >= 1) sp.edit().putBoolean(zk(zone, "long_press_enabled"), v).apply() else longPressEnabled = v
    }

    fun getLongPressType(zone: Int): Int = if (zone >= 1) sp.getInt(zk(zone, "long_press_type"), 1) else longPressType
    fun setLongPressType(zone: Int, v: Int) {
        if (zone >= 1) sp.edit().putInt(zk(zone, "long_press_type"), v).apply() else longPressType = v
    }

    fun getLongPressTargetPkg(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "long_press_target_pkg"), null) else longPressTargetPkg
    fun setLongPressTargetPkg(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "long_press_target_pkg"), v).apply() else longPressTargetPkg = v
    }

    fun getLongPressTargetLabel(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "long_press_target_label"), null) else longPressTargetLabel
    fun setLongPressTargetLabel(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "long_press_target_label"), v).apply() else longPressTargetLabel = v
    }

    fun getLongPressActionId(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "long_press_action_id"), "power_menu") else longPressActionId
    fun setLongPressActionId(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "long_press_action_id"), v).apply() else longPressActionId = v
    }

    fun getLongPressActionLabel(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "long_press_action_label"), "Power menu") else longPressActionLabel
    fun setLongPressActionLabel(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "long_press_action_label"), v).apply() else longPressActionLabel = v
    }

    fun getLongPressShortcutUri(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "long_press_shortcut_uri"), null) else longPressShortcutUri
    fun setLongPressShortcutUri(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "long_press_shortcut_uri"), v).apply() else longPressShortcutUri = v
    }

    fun getLongPressShortcutLabel(zone: Int): String? = if (zone >= 1) sp.getString(zk(zone, "long_press_shortcut_label"), null) else longPressShortcutLabel
    fun setLongPressShortcutLabel(zone: Int, v: String?) {
        if (zone >= 1) sp.edit().putString(zk(zone, "long_press_shortcut_label"), v).apply() else longPressShortcutLabel = v
    }

    val singleTapShortcutDisplay: String?
        get() {
            val sc = singleTapShortcutLabel
            val app = singleTapTargetLabel ?: targetLabel
            return sc ?: app
        }

    val doubleTapShortcutDisplay: String?
        get() {
            val sc = doubleTapShortcutLabel
            val app = doubleTapTargetLabel
            return sc ?: app
        }

    val tripleTapShortcutDisplay: String?
        get() {
            val sc = tripleTapShortcutLabel
            val app = tripleTapTargetLabel
            return sc ?: app
        }

    val longPressShortcutDisplay: String?
        get() {
            val sc = longPressShortcutLabel
            val app = longPressTargetLabel
            return sc ?: app
        }

    fun getSingleTapShortcutDisplay(zone: Int, context: Context? = null): String? {
        val sc = getSingleTapShortcutLabel(zone)
        val app = getSingleTapTargetLabel(zone, context)
        return sc ?: app
    }

    fun getDoubleTapShortcutDisplay(zone: Int): String? {
        val sc = getDoubleTapShortcutLabel(zone)
        val app = getDoubleTapTargetLabel(zone)
        return sc ?: app
    }

    fun getTripleTapShortcutDisplay(zone: Int): String? {
        val sc = getTripleTapShortcutLabel(zone)
        val app = getTripleTapTargetLabel(zone)
        return sc ?: app
    }

    fun getLongPressShortcutDisplay(zone: Int): String? {
        val sc = getLongPressShortcutLabel(zone)
        val app = getLongPressTargetLabel(zone)
        return sc ?: app
    }
}
