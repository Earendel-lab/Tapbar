package com.earendel.tapbar

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("statustap_prefs", Context.MODE_PRIVATE)

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
        get() = sp.getBoolean("service_enabled", false)
        set(v) = sp.edit().putBoolean("service_enabled", v).apply()

    var autoStart: Boolean
        get() = sp.getBoolean("auto_start", true)
        set(v) = sp.edit().putBoolean("auto_start", v).apply()

    var themeMode: Int
        get() = sp.getInt("theme_mode", 0)
        set(v) = sp.edit().putInt("theme_mode", v).apply()

    var hasSetInitialPosition: Boolean
        get() = sp.getBoolean("has_initial_pos", false)
        set(v) = sp.edit().putBoolean("has_initial_pos", v).apply()
}

