package com.earendel.tapbar

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("statustap_prefs", Context.MODE_PRIVATE)

    @Volatile
    private var cachedBlockedPkgs: Set<String>? = null

    @Volatile
    private var cachedServiceEnabled: Boolean? = null

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
        get() = sp.getString("target_pkg", null)
        set(v) = sp.edit().putString("target_pkg", v).apply()

    var targetLabel: String?
        get() = sp.getString("target_label", null)
        set(v) = sp.edit().putString("target_label", v).apply()

    var serviceEnabled: Boolean
        get() = cachedServiceEnabled ?: sp.getBoolean("service_enabled", true).also { cachedServiceEnabled = it }
        set(v) {
            cachedServiceEnabled = v
            sp.edit().putBoolean("service_enabled", v).apply()
        }

    var autoStart: Boolean
        get() = sp.getBoolean("auto_start", true)
        set(v) = sp.edit().putBoolean("auto_start", v).apply()

    var disableInLandscape: Boolean
        get() = sp.getBoolean("disable_in_landscape", false)
        set(v) = sp.edit().putBoolean("disable_in_landscape", v).apply()

    var themeMode: Int
        get() = sp.getInt("theme_mode", 0)
        set(v) = sp.edit().putInt("theme_mode", v).apply()

    var hasSetInitialPosition: Boolean
        get() = sp.getBoolean("has_initial_pos", false)
        set(v) = sp.edit().putBoolean("has_initial_pos", v).apply()

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

    // Gesture preferences
    var singleTapType: Int
        get() = sp.getInt("single_tap_type", 0) // 0 = App, 1 = Action
        set(v) = sp.edit().putInt("single_tap_type", v).apply()

    var singleTapActionId: String?
        get() = sp.getString("single_tap_action_id", "screenshot")
        set(v) = sp.edit().putString("single_tap_action_id", v).apply()

    var singleTapActionLabel: String?
        get() = sp.getString("single_tap_action_label", "Take screenshot")
        set(v) = sp.edit().putString("single_tap_action_label", v).apply()

    var doubleTapEnabled: Boolean
        get() = sp.getBoolean("double_tap_enabled", true)
        set(v) = sp.edit().putBoolean("double_tap_enabled", v).apply()

    var doubleTapSpeedMs: Int
        get() = sp.getInt("double_tap_speed_ms", 300)
        set(v) = sp.edit().putInt("double_tap_speed_ms", v).apply()

    var doubleTapType: Int
        get() = sp.getInt("double_tap_type", 1) // 0 = App, 1 = Action
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
}
