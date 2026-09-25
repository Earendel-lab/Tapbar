package com.earendel.tapbar

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

object TapAccessibilityServiceHolder {
    @Volatile
    var service: TapAccessibilityService? = null
}

class TapAccessibilityService : AccessibilityService() {

    private var controller: TapZoneController? = null
    private var lastPkg: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        TapAccessibilityServiceHolder.service = this
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()?.trim()?.lowercase() ?: return
            if (!isTransientSystemPackage(pkg) && pkg != lastPkg) {
                lastPkg = pkg
                TapZone.currentForegroundApp = pkg
                controller?.attach()
            }
        }
    }

    private fun isTransientSystemPackage(pkg: String): Boolean {
        return pkg == "android" ||
               pkg == "com.android.systemui" ||
               pkg.contains("inputmethod") ||
               pkg.contains("keyboard")
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        TapAccessibilityServiceHolder.service = null
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        TapAccessibilityServiceHolder.service = null
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        controller?.detach()
        if (TapZone.active === controller) TapZone.active = null
        controller = null
        lastPkg = null
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val am = context.getSystemService(ACCESSIBILITY_SERVICE) as? AccessibilityManager
            if (am != null) {
                try {
                    val list = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    for (info in list) {
                        val sInfo = info.resolveInfo?.serviceInfo ?: continue
                        if (sInfo.packageName == context.packageName &&
                            (sInfo.name == TapAccessibilityService::class.java.name || sInfo.name.endsWith("TapAccessibilityService"))
                        ) {
                            return true
                        }
                    }
                } catch (_: Throwable) {
                }
            }
            val expectedFull = context.packageName + "/" + TapAccessibilityService::class.java.name
            val expectedShort = context.packageName + "/." + TapAccessibilityService::class.java.simpleName
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any {
                it.equals(expectedFull, ignoreCase = true) || it.equals(expectedShort, ignoreCase = true)
            }
        }
    }
}
