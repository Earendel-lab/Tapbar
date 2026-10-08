package com.earendel.tapbar

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
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

fun hasNotificationPolicyAccess(context: Context): Boolean {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    return nm?.isNotificationPolicyAccessGranted == true
}

fun openNotificationPolicyAccessSettings(context: Context) {
    try {
        val pkg = context.packageName
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(":settings:fragment_args_key", pkg)
            putExtra(":settings:show_fragment_args", Bundle().apply {
                putString(":settings:fragment_args_key", pkg)
            })
        }
        context.startActivity(intent)
    } catch (t: Throwable) {
        Log.e("Tapbar", "Could not open notification policy access settings", t)
    }
}

class TapZoneController(
    private val context: Context,
    private val windowType: Int,
    private val yOffsetProvider: () -> Int,
    private val onSwipeDown: (downX: Float) -> Unit
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = Prefs(context)
    private val haptics by lazy { HapticManager.create(context) }
    private val hudHolder = lazy { SwipeHud(context, windowType) }
    private val swipe by lazy { SwipeAdjuster(context, hudHolder.value, haptics) }
    private val views = arrayOfNulls<TapView>(ZONE_COUNT)
    private val params = arrayOfNulls<WindowManager.LayoutParams>(ZONE_COUNT)
    private var preview = false
    private var selectedZone = 0
    private val density = context.resources.displayMetrics.density
    private val mainHandler = Handler(Looper.getMainLooper())
    private val settleRunnable = Runnable { attach() }
    private val yOffsetPx: Int
        get() = yOffsetProvider()

    private val previewDrawableActive by lazy {
        GradientDrawable().apply {
            setColor(0x553F8EF7)
            setStroke(dp(2), 0xFF2E7DF6.toInt())
            cornerRadius = dp(6).toFloat()
        }
    }

    private val previewDrawableInactive by lazy {
        GradientDrawable().apply {
            setColor(0x22888888.toInt())
            setStroke(dp(1), 0x88888888.toInt())
            cornerRadius = dp(6).toFloat()
        }
    }

    private val previewDrawableDisabled by lazy {
        GradientDrawable().apply {
            setColor(0x33E53935.toInt())
            setStroke(dp(2), 0xFFE53935.toInt())
            cornerRadius = dp(6).toFloat()
        }
    }

    private val transparentDrawable by lazy {
        ColorDrawable(0x01000000)
    }

    fun isBlocked(): Boolean {
        if (preview) return false
        if (!prefs.serviceEnabled) return true
        if (prefs.disableInLandscape && ScreenMetrics.isLandscape(context)) {
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

        ZoneOverlapResolver.enforceNonOverlapOnLoad(prefs)

        for (zone in 0 until ZONE_COUNT) {
            val active = prefs.isZoneEnabled(zone) || (preview && selectedZone == zone)
            if (active) attachZone(zone) else detachZone(zone)
        }

        refreshAppearance()
    }

    private fun attachZone(zone: Int) {
        val p = params[zone] ?: buildParams().also { params[zone] = it }
        applyGeometry(p, zone)
        val existing = views[zone]
        if (existing == null) {
            val v = TapView(context, zone)
            views[zone] = v
            try {
                wm.addView(v, p)
            } catch (t: Throwable) {
                Log.e("Tapbar", "addView failed for zone $zone", t)
                views[zone] = null
            }
        } else {
            try {
                wm.updateViewLayout(existing, p)
            } catch (t: Throwable) {
                Log.e("Tapbar", "updateViewLayout failed for zone $zone", t)
            }
        }
    }

    fun detach() {
        mainHandler.removeCallbacks(settleRunnable)
        for (zone in 0 until ZONE_COUNT) detachZone(zone)
        if (hudHolder.isInitialized()) hudHolder.value.dismissNow()
    }

    private fun detachZone(zone: Int) {
        val v = views[zone] ?: return
        try {
            wm.removeView(v)
        } catch (_: Throwable) {
        }
        views[zone] = null
    }

    fun setPreview(on: Boolean) {
        preview = on
        attach()
    }

    fun setSelectedZoneForPreview(zone: Int) {
        selectedZone = zone
        attach()
    }

    fun onConfigurationChanged() {
        if (hudHolder.isInitialized()) hudHolder.value.dismissNow()
        attach()
        mainHandler.removeCallbacks(settleRunnable)
        mainHandler.postDelayed(settleRunnable, 300L)
    }

    private fun placedY(yPx: Int, heightPx: Int): Int {
        val screenH = ScreenMetrics.sizePx(context).second
        val maxY = (screenH - heightPx).coerceAtLeast(0)
        return (yPx + yOffsetPx).coerceAtMost(maxY).coerceAtLeast(0)
    }

    private fun axisFor(zone: Int): SwipeAxis {
        val p = params[zone] ?: return SwipeAxis.HORIZONTAL
        val (screenW, screenH) = ScreenMetrics.sizePx(context)
        val localY = (p.y - yOffsetPx).coerceAtLeast(0)
        return SwipeAxisResolver.resolve(p.x, localY, p.width, p.height, screenW, screenH, dp(ZoneLayout.EDGE_DP))
    }

    fun updateGeometryLive(zone: Int, x: Int, y: Int, w: Int, h: Int) {
        val v = views.getOrNull(zone)
        val p = params.getOrNull(zone) ?: buildParams().also { if (zone in 0 until ZONE_COUNT) params[zone] = it }
        p.width = dp(w)
        p.height = dp(h)
        p.x = dp(x)
        p.y = placedY(dp(y), p.height)
        val targetView = v ?: return
        try {
            wm.updateViewLayout(targetView, p)
        } catch (_: Throwable) {
        }
    }

    fun updateGeometry(zone: Int, x: Int, y: Int, w: Int, h: Int) {
        prefs.setPosX(zone, x)
        prefs.setPosY(zone, y)
        prefs.setZoneWidth(zone, w)
        prefs.setZoneHeight(zone, h)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

    private fun applyGeometry(p: WindowManager.LayoutParams, zone: Int) {
        p.width = dp(prefs.getZoneWidth(zone))
        p.height = dp(prefs.getZoneHeight(zone))
        p.x = dp(prefs.getPosX(zone))
        p.y = placedY(dp(prefs.getPosY(zone)), p.height)
    }

    private fun refreshAppearance() {
        for (zone in 0 until ZONE_COUNT) refreshViewAppearance(views[zone], zone)
    }

    private fun refreshViewAppearance(v: View?, zone: Int) {
        if (v == null) return
        if (preview) {
            val isSelected = selectedZone == zone
            if (!prefs.isZoneEnabled(zone)) {
                v.background = previewDrawableDisabled
            } else if (isSelected) {
                v.background = previewDrawableActive
            } else {
                v.background = previewDrawableInactive
            }
            v.alpha = 1f
        } else {
            v.background = transparentDrawable
            v.alpha = 1f
        }
    }

    private inner class TapView(ctx: Context, private val zone: Int) : View(ctx) {
        private var downX = 0f
        private var downY = 0f
        private var downAt = 0L
        private var consumed = false
        private var longPressTriggered = false
        private val slop = dp(18).toFloat()
        private val swipeSlop = dp(10).toFloat()
        private var swipeArmed = false
        private var swipeEngaged = false
        private var swipeHorizontal = true
        private var swipeTarget = SWIPE_TARGET_VOLUME
        private var swipeLastX = 0f
        private var swipeLastY = 0f
        private var swipeLastT = 0L

        private var tapCount = 0
        private val gestureHandler = Handler(Looper.getMainLooper())
        private var pendingMultiTapRunnable: Runnable? = null
        private var longPressRunnable: Runnable? = null

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun cancelLongPressTimer() {
            longPressRunnable?.let { gestureHandler.removeCallbacks(it) }
            longPressRunnable = null
        }

        private fun cancelMultiTapTimer() {
            pendingMultiTapRunnable?.let { gestureHandler.removeCallbacks(it) }
            pendingMultiTapRunnable = null
        }

        private fun engageSwipe(event: MotionEvent, horizontal: Boolean) {
            cancelLongPressTimer()
            cancelMultiTapTimer()
            tapCount = 0
            consumed = true
            if (!swipe.begin(swipeTarget, horizontal, this)) return
            swipeEngaged = true
            swipeHorizontal = horizontal
            swipeLastX = downX
            swipeLastY = downY
            swipeLastT = event.eventTime
            haptics.performGesture(this)
            swipeMove(event, true)
        }

        private fun swipeMove(event: MotionEvent, first: Boolean) {
            val now = event.eventTime
            val delta = if (swipeHorizontal) event.rawX - swipeLastX else swipeLastY - event.rawY
            val dt = if (first) 0L else (now - swipeLastT).coerceAtLeast(1L)
            swipeLastX = event.rawX
            swipeLastY = event.rawY
            swipeLastT = now
            if (delta != 0f) swipe.move(delta, dt)
        }

        private fun endSwipe() {
            if (swipeEngaged) {
                swipeEngaged = false
                swipe.end()
            }
            swipeArmed = false
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (isBlocked()) return false
            if (!prefs.isZoneEnabled(zone) && !preview) return false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    downAt = SystemClock.uptimeMillis()
                    consumed = false
                    longPressTriggered = false
                    swipeEngaged = false
                    swipeArmed = prefs.getSwipeEnabled(zone)
                    swipeTarget = prefs.getSwipeTarget(zone)
                    swipeHorizontal = axisFor(zone) == SwipeAxis.HORIZONTAL

                    cancelLongPressTimer()
                    if (prefs.getLongPressEnabled(zone)) {
                        val lpRunnable = Runnable {
                            if (!consumed && !longPressTriggered) {
                                longPressTriggered = true
                                cancelMultiTapTimer()
                                tapCount = 0
                                executeLongPress(zone)
                            }
                        }
                        longPressRunnable = lpRunnable
                        gestureHandler.postDelayed(lpRunnable, 400L)
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (preview) return true
                    if (swipeEngaged) {
                        swipeMove(event, false)
                        return true
                    }
                    if (consumed) return true
                    val dy = event.rawY - downY
                    val dx = abs(event.rawX - downX)

                    if (dx > slop || abs(dy) > slop) {
                        cancelLongPressTimer()
                    }

                    if (swipeArmed) {
                        val ady = abs(dy)
                        val onAxis = if (swipeHorizontal) {
                            dx > swipeSlop && dx >= ady
                        } else {
                            ady > swipeSlop && ady > dx
                        }
                        if (onAxis) {
                            engageSwipe(event, swipeHorizontal)
                            return true
                        }
                    }

                    if (dy > slop && dy > dx) {
                        consumed = true
                        cancelLongPressTimer()
                        cancelMultiTapTimer()
                        tapCount = 0
                        haptics.performGesture(this)
                        onSwipeDown(downX)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    cancelLongPressTimer()
                    endSwipe()
                    if (consumed || preview || longPressTriggered) {
                        consumed = false
                        return true
                    }
                    val dx = abs(event.rawX - downX)
                    val dy = abs(event.rawY - downY)
                    val dt = SystemClock.uptimeMillis() - downAt
                    if (dx < slop && dy < slop && dt < 600) {
                        if (prefs.tapHapticMode == 0) haptics.performLightTap(this)
                        performClick()
                        handleTapGesture()
                    }
                    consumed = false
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    cancelLongPressTimer()
                    endSwipe()
                    consumed = false
                    return true
                }
            }
            return true
        }

        private fun handleTapGesture() {
            cancelMultiTapTimer()
            tapCount++

            val doubleEnabled = prefs.getDoubleTapEnabled(zone)
            val tripleEnabled = prefs.getTripleTapEnabled(zone)
            val speedMs = prefs.tapSpeedMs.toLong()

            if (tapCount == 3 && tripleEnabled) {
                tapCount = 0
                executeTripleTap(zone)
            } else {
                val runnable = Runnable {
                    val count = tapCount
                    tapCount = 0
                    pendingMultiTapRunnable = null
                    when (count) {
                        3 -> executeTripleTap(zone)
                        2 -> {
                            if (doubleEnabled) executeDoubleTap(zone)
                            else executeSingleTap(zone)
                        }
                        1 -> executeSingleTap(zone)
                    }
                }
                pendingMultiTapRunnable = runnable
                gestureHandler.postDelayed(runnable, speedMs)
            }
        }
    }

    private fun executeSingleTap(zone: Int) {
        if (isBlocked() || !prefs.getSingleTapEnabled(zone)) return
        if (!prefs.isZoneEnabled(zone)) return
        if (prefs.tapHapticMode == 1) {
            val view = views[zone]
            haptics.performGesture(view)
        }
        when (prefs.getSingleTapType(zone)) {
            1 -> executeAction(prefs.getSingleTapActionId(zone))
            2 -> launchShortcut(prefs.getSingleTapShortcutUri(zone), prefs.getSingleTapTargetPkg(zone, context) ?: if (zone == 0) prefs.targetPackage else null)
            else -> launchPackage(prefs.getSingleTapTargetPkg(zone, context) ?: if (zone == 0) prefs.targetPackage else null)
        }
    }

    private fun executeDoubleTap(zone: Int) {
        if (isBlocked() || !prefs.getDoubleTapEnabled(zone)) return
        if (!prefs.isZoneEnabled(zone)) return
        val view = views[zone]
        haptics.performConfirm(view)
        when (prefs.getDoubleTapType(zone)) {
            1 -> executeAction(prefs.getDoubleTapActionId(zone))
            2 -> launchShortcut(prefs.getDoubleTapShortcutUri(zone), prefs.getDoubleTapTargetPkg(zone) ?: if (zone == 0) prefs.targetPackage else null)
            else -> launchPackage(prefs.getDoubleTapTargetPkg(zone) ?: if (zone == 0) prefs.targetPackage else null)
        }
    }

    private fun executeTripleTap(zone: Int) {
        if (isBlocked() || !prefs.getTripleTapEnabled(zone)) return
        if (!prefs.isZoneEnabled(zone)) return
        val view = views[zone]
        haptics.performConfirm(view)
        when (prefs.getTripleTapType(zone)) {
            1 -> executeAction(prefs.getTripleTapActionId(zone))
            2 -> launchShortcut(prefs.getTripleTapShortcutUri(zone), prefs.getTripleTapTargetPkg(zone) ?: if (zone == 0) prefs.targetPackage else null)
            else -> launchPackage(prefs.getTripleTapTargetPkg(zone) ?: if (zone == 0) prefs.targetPackage else null)
        }
    }

    private fun executeLongPress(zone: Int) {
        if (isBlocked() || !prefs.getLongPressEnabled(zone)) return
        if (!prefs.isZoneEnabled(zone)) return
        val view = views[zone]
        haptics.performConfirm(view)
        when (prefs.getLongPressType(zone)) {
            1 -> executeAction(prefs.getLongPressActionId(zone))
            2 -> launchShortcut(prefs.getLongPressShortcutUri(zone), prefs.getLongPressTargetPkg(zone) ?: if (zone == 0) prefs.targetPackage else null)
            else -> launchPackage(prefs.getLongPressTargetPkg(zone) ?: if (zone == 0) prefs.targetPackage else null)
        }
    }

    private fun launchShortcut(uri: String?, fallbackPkg: String?) {
        if (!AppShortcuts.launch(context, uri)) {
            launchPackage(fallbackPkg)
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

    @SuppressLint("NewApi")
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
            "split_screen" -> {
                if (Build.VERSION.SDK_INT >= 24) {
                    val success = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) == true
                    if (!success) {
                        Log.w("Tapbar", "Split screen toggle failed. GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN is unsupported or restricted on this device/ROM.")
                    }
                }
            }
            "dismiss_shade" -> if (Build.VERSION.SDK_INT >= 31) service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            "all_apps" -> if (Build.VERSION.SDK_INT >= 34) service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS)
            "accessibility_button" -> if (Build.VERSION.SDK_INT >= 28) service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_BUTTON)
            "accessibility_shortcut" -> if (Build.VERSION.SDK_INT >= 30) service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_SHORTCUT)
            "headset_hook" -> if (Build.VERSION.SDK_INT >= 29) service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_KEYCODE_HEADSETHOOK)
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
            "media_play_pause" -> dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "media_next" -> dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            "media_previous" -> dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "volume_up" -> adjustVolume(context, AudioManager.ADJUST_RAISE)
            "volume_down" -> adjustVolume(context, AudioManager.ADJUST_LOWER)
            "mute" -> toggleMute(context)
            "ringer_mode" -> cycleRingerMode(context)
            "brightness_up" -> adjustBrightness(context, 25)
            "brightness_down" -> adjustBrightness(context, -25)
            "auto_brightness" -> toggleAutoBrightness(context)
            "camera" -> openCamera(context)
            "assistant" -> openAssistant(context)
            "display_settings" -> openSettingsIntent(context, Intent(Settings.ACTION_DISPLAY_SETTINGS))
            "accessibility_settings" -> openSettingsIntent(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            "default_apps" -> openSettingsIntent(context, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            "input_method" -> openSettingsIntent(context, Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            "cast" -> openSettingsIntent(context, Intent(Settings.ACTION_CAST_SETTINGS))
            "vpn_settings" -> openSettingsIntent(context, Intent(Settings.ACTION_VPN_SETTINGS))
            "add_account" -> openSettingsIntent(context, Intent(Settings.ACTION_ADD_ACCOUNT))
            "date_time_settings" -> openSettingsIntent(context, Intent(Settings.ACTION_DATE_SETTINGS))
            "locale_settings" -> openSettingsIntent(context, Intent(Settings.ACTION_LOCALE_SETTINGS))
            "app_info_list" -> openSettingsIntent(context, Intent(Settings.ACTION_APPLICATION_SETTINGS))
            "developer_options" -> openSettingsIntent(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            "security_settings" -> openSettingsIntent(context, Intent(Settings.ACTION_SECURITY_SETTINGS))
            "sync_settings" -> openSettingsIntent(context, Intent(Settings.ACTION_SYNC_SETTINGS))
            "voice_input_settings" -> openSettingsIntent(context, Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            "about_phone" -> openSettingsIntent(context, Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
            "default_home" -> openSettingsIntent(context, Intent(Settings.ACTION_HOME_SETTINGS))
            "storage_settings" -> openSettingsIntent(context, Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
            "privacy_dashboard" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) openSettingsIntent(context, Intent("android.settings.PRIVACY_CONTROLS"))
            "battery_usage" -> openSettingsIntent(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
            "battery_optimization" -> openSettingsIntent(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            "sound_settings" -> openSettingsIntent(context, Intent(Settings.ACTION_SOUND_SETTINGS))
            "location_settings" -> openSettingsIntent(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            "data_usage" -> openSettingsIntent(context, Intent("android.settings.DATA_USAGE_SETTINGS"))
            "wifi_settings" -> openSettingsIntent(context, Intent(Settings.ACTION_WIFI_SETTINGS))
            "wallpaper" -> openSettingsIntent(context, Intent(Intent.ACTION_SET_WALLPAPER))
            "sim_settings" -> openFirstAvailable(
                context,
                listOf(
                    Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
                    Intent(Settings.ACTION_DATA_ROAMING_SETTINGS),
                    Intent(Settings.ACTION_WIRELESS_SETTINGS)
                )
            )
            "mobile_network" -> openFirstAvailable(
                context,
                listOf(
                    Intent(Settings.ACTION_DATA_ROAMING_SETTINGS),
                    Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
                    Intent(Settings.ACTION_WIRELESS_SETTINGS)
                )
            )
            "network_settings" -> openSettingsIntent(context, Intent(Settings.ACTION_WIRELESS_SETTINGS))
            "nfc_payment" -> openFirstAvailable(
                context,
                listOf(
                    Intent("android.settings.NFC_PAYMENT_SETTINGS"),
                    Intent(Settings.ACTION_NFC_SETTINGS)
                )
            )
            "screen_saver" -> openSettingsIntent(context, Intent(Settings.ACTION_DREAM_SETTINGS))
            "captioning_settings" -> openSettingsIntent(context, Intent(Settings.ACTION_CAPTIONING_SETTINGS))
            "print_settings" -> openSettingsIntent(context, Intent(Settings.ACTION_PRINT_SETTINGS))
            "user_dictionary" -> openSettingsIntent(context, Intent("android.settings.USER_DICTIONARY_SETTINGS"))
            "open_settings" -> openSettingsIntent(context, Intent(Settings.ACTION_SETTINGS))
            "usage_access" -> openSettingsIntent(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            "notification_access" -> openSettingsIntent(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            "dnd_access" -> openSettingsIntent(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            "overlay_permission" -> openSettingsIntent(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            "write_settings" -> openSettingsIntent(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS))
        }
    }

    private fun dispatchMediaKey(context: Context, keyCode: Int) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val now = SystemClock.uptimeMillis()
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not dispatch media key $keyCode", t)
        }
    }

    private fun adjustVolume(context: Context, direction: Int) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not adjust volume", t)
        }
    }

    private fun toggleMute(context: Context) {
        try {
            if (!hasNotificationPolicyAccess(context)) {
                openNotificationPolicyAccessSettings(context)
                return
            }
            adjustVolume(context, AudioManager.ADJUST_TOGGLE_MUTE)
        } catch (se: SecurityException) {
            Log.e("Tapbar", "SecurityException toggling mute, redirecting to notification policy settings", se)
            openNotificationPolicyAccessSettings(context)
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not toggle mute", t)
        }
    }

    private fun cycleRingerMode(context: Context) {
        try {
            if (!hasNotificationPolicyAccess(context)) {
                openNotificationPolicyAccessSettings(context)
                return
            }
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val nextMode = when (am.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                else -> AudioManager.RINGER_MODE_NORMAL
            }
            am.ringerMode = nextMode
        } catch (se: SecurityException) {
            Log.e("Tapbar", "SecurityException cycling ringer mode, redirecting to notification policy settings", se)
            openNotificationPolicyAccessSettings(context)
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not cycle ringer mode", t)
        }
    }

    private fun adjustBrightness(context: Context, delta: Int) {
        try {
            if (!Settings.System.canWrite(context)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
            val cr = context.contentResolver
            val mode = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
            if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            }
            val current = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
            val newBrightness = (current + delta).coerceIn(1, 255)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, newBrightness)
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not adjust brightness", t)
        }
    }

    private fun toggleAutoBrightness(context: Context) {
        try {
            if (!Settings.System.canWrite(context)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
            val cr = context.contentResolver
            val mode = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
            val newMode = if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            } else {
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            }
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, newMode)
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not toggle auto brightness", t)
        }
    }

    private fun openCamera(context: Context) {
        try {
            val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (captureIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(captureIntent)
            } else {
                val mainCameraIntent = Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_CAMERA").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(mainCameraIntent)
            }
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not open camera", t)
        }
    }

    private fun openAssistant(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not open assistant", t)
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
            if (!hasNotificationPolicyAccess(context)) {
                openNotificationPolicyAccessSettings(context)
                return
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val current = nm.currentInterruptionFilter
            val newFilter = if (current == NotificationManager.INTERRUPTION_FILTER_ALL) {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
            nm.setInterruptionFilter(newFilter)
        } catch (se: SecurityException) {
            Log.e("Tapbar", "SecurityException toggling DND, redirecting to notification policy settings", se)
            openNotificationPolicyAccessSettings(context)
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

    private fun openFirstAvailable(context: Context, intents: List<Intent>) {
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Throwable) {
            }
        }
        Log.e("Tapbar", "No matching settings screen found")
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
            val res = context.resources
            val name = if (ScreenMetrics.isLandscape(context)) "status_bar_height_landscape" else "status_bar_height"
            var id = res.getIdentifier(name, "dimen", "android")
            if (id <= 0) id = res.getIdentifier("status_bar_height", "dimen", "android")
            return if (id > 0) res.getDimensionPixelSize(id)
            else (24 * res.displayMetrics.density).toInt()
        }
    }
}
