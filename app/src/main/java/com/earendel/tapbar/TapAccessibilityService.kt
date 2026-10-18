package com.earendel.tapbar

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean

object TapAccessibilityServiceHolder {
    @Volatile
    var service: TapAccessibilityService? = null
}

class TapAccessibilityService : AccessibilityService() {

    private val prefs by lazy { Prefs(this) }
    private var controller: TapZoneController? = null
    private var lastPkg: String? = null
    private var previousPkg: String? = null
    private val scrolling = AtomicBoolean(false)
    @Volatile private var scrollCancelled = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var flingTarget: AccessibilityNodeInfo? = null
    private var flingDeadline = 0L
    private var flingStalls = 0
    @Volatile private var lastScrollEventAt = 0L
    private var flingStartedAt = 0L
    private var flingActionId = 0
    private var flingUp = true
    private var caffeineView: View? = null

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
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            lastScrollEventAt = SystemClock.uptimeMillis()
            return
        }
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val eventPkg = event.packageName?.toString()?.trim() ?: return
            if (eventPkg == packageName && event.className?.toString() != "$packageName.MainActivity") return
            val raw = activeApplicationPackage() ?: return
            val pkg = raw.lowercase()
            if (!isTransientSystemPackage(pkg) && isLaunchableApp(raw) && pkg != lastPkg) {
                val outgoing = lastPkg
                if (outgoing != null && !isHomePackage(outgoing)) previousPkg = outgoing
                lastPkg = pkg
                TapZone.currentForegroundApp = pkg
                controller?.attach()
            }
        }
    }

    fun switchToPreviousApp() {
        val target = previousPkg ?: return
        if (target == lastPkg || isHomePackage(target)) return
        val intent = try {
            packageManager.getLaunchIntentForPackage(target)
        } catch (_: Throwable) {
            null
        } ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not switch to previous app", t)
        }
    }

    fun openCurrentAppInfo() {
        val pkg = activeApplicationPackage() ?: lastPkg ?: return
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not open app info", t)
        }
    }

    private fun canDispatchGestures(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val caps = serviceInfo?.capabilities ?: return false
        return caps and AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES != 0
    }

    fun scrollToTop() = scrollEdge(true)

    fun scrollToBottom() = scrollEdge(false)

    private fun scrollActionId(up: Boolean): Int =
        if (up) AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
        else AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id

    private fun scrollEdge(up: Boolean) {
        if (flingTarget != null) {
            stopFling()
            return
        }
        if (!scrolling.compareAndSet(false, true)) {
            scrollCancelled = true
            return
        }
        scrollCancelled = false
        if (canDispatchGestures()) {
            val root = targetWindowRoot()
            val found = root?.let { findScrollTarget(it, scrollActionId(up)) }
            if (found == null) {
                scrolling.set(false)
                return
            }
            startFling(found.node, up)
            return
        }
        try {
            Thread({
                try {
                    runScrollEdge(up)
                } catch (t: Throwable) {
                    Log.e("Tapbar", "Scroll failed", t)
                } finally {
                    scrolling.set(false)
                }
            }, "tapbar-scroll-edge").apply { isDaemon = true }.start()
        } catch (t: Throwable) {
            scrolling.set(false)
        }
    }

    fun pageScroll(up: Boolean) {
        if (flingTarget != null) stopFling()
        val root = targetWindowRoot() ?: return
        val id = scrollActionId(up)
        val found = findScrollTarget(root, id) ?: return
        found.node.performAction(id)
    }

    private fun startFling(node: AccessibilityNodeInfo, up: Boolean) {
        flingTarget = node
        flingUp = up
        flingActionId = scrollActionId(up)
        flingStalls = 0
        flingDeadline = SystemClock.uptimeMillis() + FLING_MAX_MS
        dispatchFling()
    }

    private fun stopFling() {
        flingTarget = null
        scrolling.set(false)
    }

    private fun dispatchFling() {
        val node = flingTarget ?: return
        if (SystemClock.uptimeMillis() >= flingDeadline) {
            stopFling()
            return
        }
        val actionId = flingActionId
        if (!node.refresh() || node.actionList.none { it.id == actionId }) {
            stopFling()
            return
        }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val metrics = resources.displayMetrics
        val visible = Rect(bounds).apply {
            intersect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        if (visible.height() < 200 || visible.width() < 50) {
            stopFling()
            return
        }
        val x = visible.exactCenterX()
        val top = visible.top + visible.height() * FLING_START_FRACTION
        val bottom = visible.top + visible.height() * FLING_END_FRACTION
        val path = Path().apply {
            if (flingUp) {
                moveTo(x, top)
                lineTo(x, bottom)
            } else {
                moveTo(x, bottom)
                lineTo(x, top)
            }
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, FLING_DURATION_MS))
            .build()
        flingStartedAt = SystemClock.uptimeMillis()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (flingTarget == null) return
                if (lastScrollEventAt < flingStartedAt) {
                    flingStalls++
                    if (flingStalls >= FLING_MAX_STALLS) {
                        stopFling()
                        return
                    }
                } else {
                    flingStalls = 0
                }
                dispatchFling()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                stopFling()
            }
        }, mainHandler)
        if (!dispatched) stopFling()
    }

    private fun runScrollEdge(up: Boolean) {
        val root = targetWindowRoot() ?: return
        val actionId = scrollActionId(up)
        val found = findScrollTarget(root, actionId) ?: return
        val target = found.node
        val stepMs = if (found.web) SCROLL_STEP_WEB_MS else SCROLL_STEP_MS
        val maxMs = if (found.web) SCROLL_MAX_WEB_MS else SCROLL_MAX_MS
        val deadline = SystemClock.uptimeMillis() + maxMs
        while (!scrollCancelled && SystemClock.uptimeMillis() < deadline) {
            if (!target.performAction(actionId)) break
            SystemClock.sleep(stepMs)
            if (scrollCancelled) break
            if (!target.refresh()) break
            if (target.actionList.none { it.id == actionId }) break
        }
    }

    private fun focusedTextNode(): AccessibilityNodeInfo? {
        val root = targetWindowRoot() ?: return null
        return root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
    }

    fun copyText() {
        focusedTextNode()?.performAction(AccessibilityNodeInfo.ACTION_COPY)
    }

    fun cutText() {
        focusedTextNode()?.performAction(AccessibilityNodeInfo.ACTION_CUT)
    }

    fun pasteText() {
        focusedTextNode()?.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    fun selectAllText() {
        val node = focusedTextNode() ?: return
        val length = if (node.isShowingHintText) 0 else node.text?.length ?: 0
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, length)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
    }

    fun toggleCaffeine() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val existing = caffeineView
        if (existing != null) {
            try {
                wm.removeView(existing)
            } catch (_: Throwable) {
            }
            caffeineView = null
            Toast.makeText(this, R.string.caffeine_off, Toast.LENGTH_SHORT).show()
            return
        }
        val view = View(this)
        val lp = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        try {
            wm.addView(view, lp)
            caffeineView = view
            Toast.makeText(this, R.string.caffeine_on, Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not enable caffeine", t)
        }
    }

    private fun removeCaffeine() {
        val view = caffeineView ?: return
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view)
        } catch (_: Throwable) {
        }
        caffeineView = null
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long) {
        if (!canDispatchGestures()) return
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun contentBounds(): Rect {
        val metrics = resources.displayMetrics
        val screen = Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        val root = targetWindowRoot() ?: return screen
        val found = findScrollTarget(root, scrollActionId(true))
            ?: findScrollTarget(root, scrollActionId(false))
        val bounds = Rect()
        (found?.node ?: root).getBoundsInScreen(bounds)
        bounds.intersect(screen)
        return if (bounds.width() < 100 || bounds.height() < 200) screen else bounds
    }

    fun refreshPage() {
        if (flingTarget != null) stopFling()
        val b = contentBounds()
        swipe(
            b.exactCenterX(),
            b.top + b.height() * REFRESH_START_FRACTION,
            b.exactCenterX(),
            b.top + b.height() * REFRESH_END_FRACTION,
            REFRESH_DURATION_MS
        )
    }

    fun swipeHorizontal(left: Boolean) {
        if (flingTarget != null) stopFling()
        val b = contentBounds()
        val y = b.exactCenterY()
        val from = b.left + b.width() * SWIPE_FAR_FRACTION
        val to = b.left + b.width() * SWIPE_NEAR_FRACTION
        if (left) swipe(from, y, to, y, SWIPE_DURATION_MS) else swipe(to, y, from, y, SWIPE_DURATION_MS)
    }

    fun clearClipboard() {
        val cm = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                cm.clearPrimaryClip()
            } else {
                cm.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            Toast.makeText(this, R.string.clipboard_cleared, Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not clear clipboard", t)
        }
    }

    fun showKeyboardPicker() {
        try {
            startActivity(
                Intent(this, KeyboardPickerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not show keyboard picker", t)
        }
    }

    private fun targetWindowRoot(): AccessibilityNodeInfo? {
        try {
            val list = windows
            if (!list.isNullOrEmpty()) {
                for (w in list) {
                    if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION && (w.isActive || w.isFocused)) {
                        val root = w.root
                        if (root != null) return root
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return rootInActiveWindow
    }

    private fun isWebNode(node: AccessibilityNodeInfo): Boolean {
        val name = node.className?.toString() ?: return false
        return name.contains("WebView", ignoreCase = true) || name.contains("GeckoView", ignoreCase = true)
    }

    private fun findScrollTarget(root: AccessibilityNodeInfo, actionId: Int): ScrollTarget? {
        var best: ScrollTarget? = null
        var bestArea = 0L
        val bounds = Rect()
        val stack = ArrayList<Pair<AccessibilityNodeInfo, Boolean>>()
        stack.add(Pair(root, false))
        var visited = 0
        while (stack.isNotEmpty() && visited < SCROLL_MAX_NODES) {
            val (node, insideWeb) = stack.removeAt(stack.size - 1)
            visited++
            if (!node.isVisibleToUser) continue
            val web = insideWeb || isWebNode(node)
            if (node.actionList.any { it.id == actionId }) {
                node.getBoundsInScreen(bounds)
                val area = bounds.width().toLong() * bounds.height()
                if (area > bestArea) {
                    best = ScrollTarget(node, web)
                    bestArea = area
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.add(Pair(it, web)) }
            }
        }
        return best
    }

    private class ScrollTarget(val node: AccessibilityNodeInfo, val web: Boolean)

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

    override fun onInterrupt() {
        stopFling()
    }

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
        stopFling()
        removeCaffeine()
        controller?.detach()
        if (TapZone.active === controller) TapZone.active = null
        controller = null
        lastPkg = null
        previousPkg = null
    }

    companion object {
        private const val SCROLL_STEP_MS = 80L
        private const val SCROLL_MAX_MS = 15000L
        private const val SCROLL_STEP_WEB_MS = 350L
        private const val SCROLL_MAX_WEB_MS = 40000L
        private const val SCROLL_MAX_NODES = 5000
        private const val REFRESH_START_FRACTION = 0.2f
        private const val REFRESH_END_FRACTION = 0.7f
        private const val REFRESH_DURATION_MS = 450L
        private const val SWIPE_FAR_FRACTION = 0.8f
        private const val SWIPE_NEAR_FRACTION = 0.2f
        private const val SWIPE_DURATION_MS = 160L
        private const val FLING_DURATION_MS = 90L
        private const val FLING_MAX_MS = 45000L
        private const val FLING_MAX_STALLS = 3
        private const val FLING_START_FRACTION = 0.2f
        private const val FLING_END_FRACTION = 0.85f

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