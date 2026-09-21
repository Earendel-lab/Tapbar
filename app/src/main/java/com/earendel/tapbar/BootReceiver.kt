package com.earendel.tapbar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = Prefs(context)
        if (TapAccessibilityService.isEnabled(context)) return
        if (prefs.autoStart && prefs.serviceEnabled && Settings.canDrawOverlays(context)) {
            try {
                OverlayService.start(context)
            } catch (t: Throwable) {
                Log.e("Tapbar", "Boot start failed")
            }
        }
    }
}
