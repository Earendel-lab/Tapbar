package com.earendel.tapbar

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

object TapZone {
    @Volatile
    private var activeRef: WeakReference<TapZoneController>? = null

    var active: TapZoneController?
        get() = activeRef?.get()
        set(value) {
            activeRef = value?.let { WeakReference(it) }
        }

    @Volatile
    var previewRequested: Boolean = false

    @Volatile
    var currentForegroundApp: String? = null
}

class TapZoneController(
    private val context: Context,
    private val windowType: Int,
    private val yOffsetPx: Int,
    private val onSwipeDown: () -> Unit
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = Prefs(context)
    private var view: TapView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var preview = false
    private val density = context.resources.displayMetrics.density

    private val previewDrawable by lazy {
        GradientDrawable().apply {
            setColor(0x553F8EF7)
            setStroke(dp(2), 0xFF2E7DF6.toInt())
            cornerRadius = dp(6).toFloat()
        }
    }

    private val transparentDrawable by lazy {
        ColorDrawable(0x01000000)
    }

    fun isBlocked(): Boolean {
        if (preview) return false
        if (!prefs.serviceEnabled) return true
        if (prefs.disableInLandscape && context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return true
        }
        val fg = TapZone.currentForegroundApp?.trim()?.lowercase() ?: return false
        return prefs.blockedPackages.contains(fg)
    }

    fun attach() {
        if (isBlocked()) {
            detach()
            return
        }
        val p = lp ?: buildParams().also { lp = it }
        applyGeometry(p)
        if (view == null) {
            val v = TapView(context)
            view = v
            try {
                wm.addView(v, p)
            } catch (t: Throwable) {
                Log.e("Tapbar", "addView failed", t)
                view = null
                return
            }
        } else {
            try {
                wm.updateViewLayout(view, p)
            } catch (t: Throwable) {
                Log.e("Tapbar", "updateViewLayout failed", t)
            }
        }
        refreshAppearance()
    }

    fun detach() {
        val v = view ?: return
        try {
            wm.removeView(v)
        } catch (t: Throwable) {
            Log.e("Tapbar", "removeView failed", t)
        }
        view = null
    }

    fun setPreview(on: Boolean) {
        preview = on
        attach()
    }

    fun updateGeometryLive(x: Int, y: Int, w: Int, h: Int) {
        val v = view ?: return
        val p = lp ?: return
        p.width = dp(w)
        p.height = dp(h)
        p.x = dp(x)
        p.y = dp(y) + yOffsetPx
        try {
            wm.updateViewLayout(v, p)
        } catch (_: Throwable) {
        }
    }

    fun updateGeometry(x: Int, y: Int, w: Int, h: Int) {
        prefs.posX = x
        prefs.posY = y
        prefs.zoneWidth = w
        prefs.zoneHeight = h
        attach()
    }

    private fun buildParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            0,
            0,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

    private fun applyGeometry(p: WindowManager.LayoutParams) {
        p.width = dp(prefs.zoneWidth)
        p.height = dp(prefs.zoneHeight)
        p.x = dp(prefs.posX)
        p.y = dp(prefs.posY) + yOffsetPx
    }

    private fun refreshAppearance() {
        val v = view ?: return
        if (preview) {
            v.background = previewDrawable
            v.alpha = 1f
        } else {
            v.background = transparentDrawable
            v.alpha = 1f
        }
    }

    private inner class TapView(ctx: Context) : View(ctx) {
        private var downX = 0f
        private var downY = 0f
        private var downAt = 0L
        private var consumed = false
        private val slop = dp(18).toFloat()

        private var lastTapTime = 0L
        private val singleTapHandler = Handler(Looper.getMainLooper())
        private var pendingSingleTapRunnable: Runnable? = null

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (isBlocked()) return false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    downAt = SystemClock.uptimeMillis()
                    consumed = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (consumed || preview) return true
                    val dy = event.rawY - downY
                    val dx = abs(event.rawX - downX)
                    if (dy > slop && dy > dx) {
                        consumed = true
                        pendingSingleTapRunnable?.let { singleTapHandler.removeCallbacks(it) }
                        pendingSingleTapRunnable = null
                        onSwipeDown()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!consumed && !preview) {
                        val dx = abs(event.rawX - downX)
                        val dy = abs(event.rawY - downY)
                        val dt = SystemClock.uptimeMillis() - downAt
                        if (dx < slop && dy < slop && dt < 600) {
                            performClick()
                            handleTapGesture()
                        }
                    }
                    consumed = false
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    consumed = false
                    return true
                }
            }
            return true
        }

        private fun handleTapGesture() {
            val doubleTapEnabled = prefs.doubleTapEnabled
            val speedMs = prefs.doubleTapSpeedMs.toLong()
            val now = SystemClock.uptimeMillis()

            if (doubleTapEnabled && (now - lastTapTime) < speedMs) {
                pendingSingleTapRunnable?.let { singleTapHandler.removeCallbacks(it) }
                pendingSingleTapRunnable = null
                lastTapTime = 0L
                executeDoubleTap()
            } else {
                lastTapTime = now
                if (doubleTapEnabled) {
                    pendingSingleTapRunnable?.let { singleTapHandler.removeCallbacks(it) }
                    val runnable = Runnable {
                        executeSingleTap()
                        pendingSingleTapRunnable = null
                    }
                    pendingSingleTapRunnable = runnable
                    singleTapHandler.postDelayed(runnable, speedMs)
                } else {
                    executeSingleTap()
                }
            }
        }
    }

    private fun executeSingleTap() {
        if (isBlocked()) return
        if (prefs.singleTapType == 1) {
            executeAction(prefs.singleTapActionId)
        } else {
            launchPackage(prefs.singleTapTargetPkg ?: prefs.targetPackage)
        }
    }

    private fun executeDoubleTap() {
        if (isBlocked()) return
        if (prefs.doubleTapType == 1) {
            executeAction(prefs.doubleTapActionId)
        } else {
            launchPackage(prefs.doubleTapTargetPkg ?: prefs.targetPackage)
        }
    }

    private fun launchPackage(pkg: String?) {
        val launchIntent = if (pkg != null) {
            context.packageManager.getLaunchIntentForPackage(pkg)
        } else null

        val intentToRun = launchIntent ?: Intent(AlarmClock.ACTION_SHOW_ALARMS)
        intentToRun.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

        try {
            context.startActivity(intentToRun)
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not launch target app", t)
        }
    }

    private fun executeAction(actionId: String?) {
        if (actionId.isNullOrEmpty()) return
        val service = TapAccessibilityServiceHolder.service
        when (actionId) {
            "lock_screen" -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            "screenshot" -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            "power_menu" -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            "back" -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            "home" -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            "recents" -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            "notifications" -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            "quick_settings" -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            "flashlight" -> toggleFlashlight(context)
            "auto_rotate" -> toggleAutoRotate(context)
            "dnd" -> toggleDnd(context)
            "wifi" -> openPanel(context, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIRELESS_SETTINGS)
            "bluetooth" -> openBluetooth(context)
            "data" -> openPanel(context, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS)
            "volume" -> openPanel(context, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_VOLUME else Settings.ACTION_SOUND_SETTINGS)
            "hotspot" -> openSettingsIntent(context, Intent("android.settings.TETHER_SETTINGS"))
            "battery_saver" -> openSettingsIntent(context, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            "nfc" -> openSettingsIntent(context, Intent(Settings.ACTION_NFC_SETTINGS))
            "night_light" -> openSettingsIntent(context, Intent("android.settings.NIGHT_DISPLAY_SETTINGS"))
            "airplane" -> openSettingsIntent(context, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
        }
    }

    private fun openBluetooth(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent("android.settings.panel.action.BLUETOOTH").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
        } catch (_: Throwable) {
        }
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not open Bluetooth settings", t)
        }
    }

    private fun toggleAutoRotate(context: Context) {
        try {
            if (!Settings.System.canWrite(context)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
            val current = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
            val newSetting = if (current == 1) 0 else 1
            Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, newSetting)
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not toggle auto-rotate", t)
        }
    }

    private fun toggleDnd(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (!nm.isNotificationPolicyAccessGranted) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
            val current = nm.currentInterruptionFilter
            val newFilter = if (current == NotificationManager.INTERRUPTION_FILTER_ALL) {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
            nm.setInterruptionFilter(newFilter)
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not toggle DND", t)
        }
    }

    private fun openPanel(context: Context, panelAction: String) {
        try {
            val intent = Intent(panelAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not open panel $panelAction", t)
        }
    }

    private fun openSettingsIntent(context: Context, intent: Intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not open settings intent", t)
        }
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    companion object {
        private val flashlightStateMap = ConcurrentHashMap<String, Boolean>()

        private fun toggleFlashlight(context: Context) {
            try {
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
                val cameraId = cm.cameraIdList.firstOrNull() ?: return
                val currentState = flashlightStateMap[cameraId] ?: false
                val newState = !currentState
                cm.setTorchMode(cameraId, newState)
                flashlightStateMap[cameraId] = newState
            } catch (t: Throwable) {
                Log.e("Tapbar", "Could not toggle flashlight", t)
            }
        }

        fun statusBarHeightPx(context: Context): Int {
            val id = context.resources
                .getIdentifier("status_bar_height", "dimen", "android")
            return if (id > 0) context.resources.getDimensionPixelSize(id)
            else (24 * context.resources.displayMetrics.density).toInt()
        }
    }
}
