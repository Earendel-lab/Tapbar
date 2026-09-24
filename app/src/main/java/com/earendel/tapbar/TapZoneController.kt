package com.earendel.tapbar

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.provider.AlarmClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import java.lang.ref.WeakReference
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
                            launchTarget()
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
    }

    private fun launchTarget() {
        if (isBlocked()) return
        val pkg = prefs.targetPackage
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

    private fun dp(value: Int): Int = (value * density).toInt()

    companion object {
        fun statusBarHeightPx(context: Context): Int {
            val id = context.resources
                .getIdentifier("status_bar_height", "dimen", "android")
            return if (id > 0) context.resources.getDimensionPixelSize(id)
            else (24 * context.resources.displayMetrics.density).toInt()
        }
    }
}
