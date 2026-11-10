package com.earendel.tapbar

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast

object ShellActions {

    private const val CONFIRM_WINDOW_MS = 3000L

    private val dangerous = setOf("reboot", "reboot_recovery", "reboot_bootloader")

    private val ids = setOf(
        "force_stop_app",
        "restart_systemui",
        "reboot",
        "reboot_recovery",
        "reboot_bootloader",
        "dark_mode",
        "grayscale",
        "invert_colors",
        "extra_dim",
        "show_taps",
        "pointer_location",
        "animations_off",
        "stay_awake",
        "location_toggle",
        "private_dns"
    )

    private val pendingConfirm = HashMap<String, Long>()

    fun handles(id: String): Boolean = id in ids

    fun run(context: Context, id: String, method: Int) {
        val app = context.applicationContext
        if (id in dangerous && !confirmed(app, id)) return
        val command = commandFor(app, id)
        if (command == null) {
            toast(app, R.string.shell_no_target)
            return
        }
        ShellAccess.run(app, command, method) { ok ->
            if (!ok) toast(app, R.string.shell_failed)
        }
    }

    private fun confirmed(context: Context, id: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val last = pendingConfirm[id]
        if (last != null && now - last <= CONFIRM_WINDOW_MS) {
            pendingConfirm.remove(id)
            return true
        }
        pendingConfirm[id] = now
        toast(context, R.string.confirm_repeat)
        return false
    }

    private fun toast(context: Context, res: Int) {
        Toast.makeText(context, LocaleHelper.wrap(context).getString(res), Toast.LENGTH_SHORT).show()
    }

    private fun commandFor(context: Context, id: String): String? {
        val resolver = context.contentResolver
        return when (id) {
            "force_stop_app" -> foregroundPackage(context)?.let { "am force-stop $it" }
            "restart_systemui" -> "am crash com.android.systemui || killall com.android.systemui"
            "reboot" -> "svc power reboot || reboot"
            "reboot_recovery" -> "svc power reboot recovery || reboot recovery"
            "reboot_bootloader" -> "svc power reboot bootloader || reboot bootloader"
            "dark_mode" -> "cmd uimode night " + if (isNight()) "no" else "yes"
            "grayscale" -> if (secureInt(resolver, "accessibility_display_daltonizer_enabled") == 1) {
                "settings put secure accessibility_display_daltonizer_enabled 0"
            } else {
                "settings put secure accessibility_display_daltonizer 0; settings put secure accessibility_display_daltonizer_enabled 1"
            }
            "invert_colors" -> "settings put secure accessibility_display_inversion_enabled " +
                if (secureInt(resolver, "accessibility_display_inversion_enabled") == 1) "0" else "1"
            "extra_dim" -> "settings put secure reduce_bright_colors_activated " +
                if (secureInt(resolver, "reduce_bright_colors_activated") == 1) "0" else "1"
            "show_taps" -> "settings put system show_touches " +
                if (systemInt(resolver, "show_touches") == 1) "0" else "1"
            "pointer_location" -> "settings put system pointer_location " +
                if (systemInt(resolver, "pointer_location") == 1) "0" else "1"
            "animations_off" -> {
                val value = if (animationsOff(resolver)) "1" else "0"
                "settings put global window_animation_scale $value; " +
                    "settings put global transition_animation_scale $value; " +
                    "settings put global animator_duration_scale $value"
            }
            "stay_awake" -> "settings put global stay_on_while_plugged_in " +
                if (globalInt(resolver, "stay_on_while_plugged_in") != 0) "0" else "7"
            "location_toggle" -> if (secureInt(resolver, "location_mode") != 0) {
                "cmd location set-location-enabled false || settings put secure location_mode 0"
            } else {
                "cmd location set-location-enabled true || settings put secure location_mode 3"
            }
            "private_dns" -> "settings put global private_dns_mode " + privateDnsTarget(resolver)
            else -> null
        }
    }

    private fun secureInt(resolver: android.content.ContentResolver, key: String): Int =
        try {
            Settings.Secure.getInt(resolver, key, 0)
        } catch (_: Throwable) {
            0
        }

    private fun systemInt(resolver: android.content.ContentResolver, key: String): Int =
        try {
            Settings.System.getInt(resolver, key, 0)
        } catch (_: Throwable) {
            0
        }

    private fun globalInt(resolver: android.content.ContentResolver, key: String): Int =
        try {
            Settings.Global.getInt(resolver, key, 0)
        } catch (_: Throwable) {
            0
        }

    private fun animationsOff(resolver: android.content.ContentResolver): Boolean =
        try {
            Settings.Global.getFloat(resolver, "animator_duration_scale", 1f) == 0f
        } catch (_: Throwable) {
            false
        }

    private fun privateDnsTarget(resolver: android.content.ContentResolver): String {
        val mode = try {
            Settings.Global.getString(resolver, "private_dns_mode")
        } catch (_: Throwable) {
            null
        }
        if (mode != "off") return "off"
        val specifier = try {
            Settings.Global.getString(resolver, "private_dns_specifier")
        } catch (_: Throwable) {
            null
        }
        return if (specifier.isNullOrEmpty()) "opportunistic" else "hostname"
    }

    private fun isNight(): Boolean =
        (Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun foregroundPackage(context: Context): String? {
        val pkg = TapZone.currentForegroundRaw ?: return null
        if (pkg == context.packageName || pkg == "android" || pkg.startsWith("com.android.systemui")) return null
        if (!PACKAGE_PATTERN.matches(pkg)) return null
        if (isLauncher(context, pkg)) return null
        return pkg
    }

    private fun isLauncher(context: Context, pkg: String): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName
        return resolved == pkg
    }

    private val PACKAGE_PATTERN = Regex("[A-Za-z0-9._]+")
}
