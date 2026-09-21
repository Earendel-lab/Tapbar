package com.earendel.tapbar

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class TapAccessibilityService : AccessibilityService() {

    private var controller: TapZoneController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val c = TapZoneController(
            context = this,
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            yOffsetPx = 0,
            onSwipeDown = { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) }
        )
        controller = c
        c.attach()
        c.setPreview(TapZone.previewRequested)
        TapZone.active = c
        OverlayService.stop(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        controller?.detach()
        if (TapZone.active === controller) TapZone.active = null
        controller = null
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val expected = context.packageName + "/" + TapAccessibilityService::class.java.name
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
