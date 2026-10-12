package com.earendel.tapbar

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityWindowInfo

object TapAccessibilityServiceHolder {
    @Volatile
    var service: TapAccessibilityService? = null
}

class TapAccessibilityService : AccessibilityService() {

    private val prefs by lazy { Prefs(this) }
    private var controller: TapZoneController? = null
    private var lastPkg: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        TapAccessibilityServiceHolder.service = this
        val c = TapZoneController(
            context = this,
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            yOffsetProvider = { 0 },
            onSwipeDown = { downX ->
                val screenW = resources.displayMetrics.widthPixels
                performGlobalAction(
                    if (prefs.splitShade && downX > screenW / 2f) GLOBAL_ACTION_QUICK_SETTINGS
                    else GLOBAL_ACTION_NOTIFICATIONS
                )
            }
        )
        controller = c
        c.attach()
        c.setPreview(TapZone.previewRequested)
        TapZone.active = c
        OverlayService.stop(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val eventPkg = event.packageName?.toString()?.trim() ?: return
            if (eventPkg == packageName && event.className?.toString() != "$packageName.MainActivity") return
            val raw = activeApplicationPackage() ?: return
            val pkg = raw.lowercase()
            if (!isTransientSystemPackage(pkg) && isLaunchableApp(raw) && pkg != lastPkg) {
                lastPkg = pkg
                TapZone.currentForegroundApp = pkg
                controller?.attach()
            }
        }
    }

    private fun activeApplicationPackage(): String? {
        return try {
            val list = windows
            if (list.isNullOrEmpty()) return null
            var found: String? = null
            for (w in list) {
                if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION && (w.isActive || w.isFocused)) {
                    val root = w.root
                    val p = root?.packageName?.toString()?.trim()
                    root?.recycle()
                    if (!p.isNullOrEmpty()) {
                        found = p
                        break
                    }
                }
            }
            found
        } catch (_: Throwable) {
            null
        }
    }

    private fun isLaunchableApp(pkg: String): Boolean {
        return try {
            packageManager.getLaunchIntentForPackage(pkg) != null || isHomePackage(pkg)
        } catch (_: Throwable) {
            false
        }
    }

    private fun isHomePackage(pkg: String): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(home, 0).any {
            it.activityInfo?.packageName.equals(pkg, ignoreCase = true)
        }
    }

    private fun isTransientSystemPackage(pkg: String): Boolean {
        return pkg == "android" ||
                pkg == "com.android.systemui" ||
                pkg.contains("inputmethod") ||
                pkg.contains("keyboard")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller?.onConfigurationChanged()
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