package com.earendel.tapbar

import android.content.Context
import android.nfc.NfcAdapter
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object SystemToggles {

    private val toggleTitles = mapOf(
        "wifi" to R.string.action_wifi_toggle,
        "bluetooth" to R.string.action_bluetooth_toggle,
        "data" to R.string.action_data_toggle,
        "nfc" to R.string.action_nfc_toggle
    )

    private val toggleDescs = mapOf(
        "wifi" to R.string.action_wifi_toggle_desc,
        "bluetooth" to R.string.action_bluetooth_toggle_desc,
        "data" to R.string.action_data_toggle_desc,
        "nfc" to R.string.action_nfc_toggle_desc,
        "airplane" to R.string.action_airplane_toggle_desc,
        "battery_saver" to R.string.action_battery_saver_toggle_desc,
        "night_light" to R.string.action_night_light_toggle_desc,
        "usb_debugging" to R.string.action_usb_debugging_toggle_desc,
        "wireless_debugging" to R.string.action_wireless_debugging_toggle_desc
    )

    fun available(method: Int): Boolean =
        method == ShellAccess.METHOD_ROOT || ShellAccess.shizukuState() == ShellAccess.SHIZUKU_READY

    fun resolve(entry: ActionEntry, method: Int): ActionEntry {
        val desc = toggleDescs[entry.id] ?: return entry
        if (!available(method)) return entry
        return entry.copy(titleRes = toggleTitles[entry.id] ?: entry.titleRes, descRes = desc)
    }

    fun displayLabel(context: Context, id: String?, stored: String?, method: Int): String? {
        val entry = systemActions.firstOrNull { it.id == id } ?: return stored
        if (entry.id !in toggleDescs) return stored
        return context.getString(resolve(entry, method).titleRes)
    }

    fun toggle(context: Context, actionId: String, method: Int, fallback: () -> Unit) {
        val app = context.applicationContext
        val currentlyOn = isEnabled(app, actionId)
        if (currentlyOn == null) {
            fallback()
            return
        }
        ShellAccess.run(app, commandFor(actionId, !currentlyOn), method) { ok ->
            if (!ok) fallback()
        }
    }

    private fun isEnabled(context: Context, actionId: String): Boolean? = try {
        val resolver = context.contentResolver
        when (actionId) {
            "wifi" -> Settings.Global.getInt(resolver, "wifi_on", 0).let { it == 1 || it == 2 }
            "bluetooth" -> Settings.Global.getInt(resolver, "bluetooth_on", 0) == 1
            "data" -> Settings.Global.getInt(resolver, "mobile_data", 1) == 1
            "nfc" -> NfcAdapter.getDefaultAdapter(context)?.isEnabled
            "airplane" -> Settings.Global.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            "battery_saver" -> (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode
            "night_light" -> Settings.Secure.getInt(resolver, "night_display_activated", 0) == 1
            "usb_debugging" -> Settings.Global.getInt(resolver, "adb_enabled", 0) == 1
            "wireless_debugging" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Settings.Global.getInt(resolver, "adb_wifi_enabled", 0) == 1
            } else {
                null
            }
            else -> null
        }
    } catch (t: Throwable) {
        null
    }

    private fun commandFor(actionId: String, enable: Boolean): String {
        val word = if (enable) "enable" else "disable"
        val bit = if (enable) 1 else 0
        return when (actionId) {
            "wifi" -> "svc wifi $word"
            "data" -> "svc data $word"
            "nfc" -> "svc nfc $word"
            "bluetooth" -> "cmd bluetooth_manager $word || svc bluetooth $word"
            "airplane" -> "cmd connectivity airplane-mode $word || { settings put global airplane_mode_on $bit && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $enable; }"
            "battery_saver" -> "settings put global low_power $bit"
            "usb_debugging" -> "settings put global adb_enabled $bit"
            "wireless_debugging" -> "settings put global adb_wifi_enabled $bit"
            else -> "settings put secure night_display_activated $bit"
        }
    }
}
