package com.earendel.tapbar

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.provider.AlarmClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

object TapZone {
    @Volatile
    var active: TapZoneController? = null

    @Volatile
    var previewRequested: Boolean = false
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

    val isAccessibilityHosted: Boolean
        get() = windowType == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

    fun attach() {
        val p = lp ?: buildParams().also { lp = it }
        applyGeometry(p)
        if (view == null) {
            val v = TapView(context)
            view = v
            try {
                wm.addView(v, p)
            } catch (t: Throwable) {
                Log.e("Tapbar", "addView failed")
                view = null
                return
            }
        } else {
            try {
                wm.updateViewLayout(view, p)
            } catch (t: Throwable) {
                Log.e("Tapbar", "updateViewLayout failed")
            }
        }
        refreshAppearance()
    }

    fun detach() {
        val v = view ?: return
        try {
            wm.removeView(v)
        } catch (t: Throwable) {
            Log.e("Tapbar", "removeView failed")
        }
        view = null
    }

    fun setPreview(on: Boolean) {
        preview = on
        refreshAppearance()
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
            v.background = GradientDrawable().apply {
                setColor(0x553F8EF7)
                setStroke(dp(2), 0xFF2E7DF6.toInt())
                cornerRadius = dp(6).toFloat()
            }
            v.alpha = 1f
        } else {
            v.background = null
            v.alpha = 0f
        }
    }

    private inner class TapView(ctx: Context) : View(ctx) {
        private var downX = 0f
        private var downY = 0f
        private var downAt = 0L
        private var consumed = false
        private val slop = dp(18).toFloat()

        override fun onTouchEvent(event: MotionEvent): Boolean {
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
                        if (dx < slop && dy < slop && dt < 600) launchTarget()
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
        val pkg = prefs.targetPackage
        val launch = if (pkg != null) {
            context.packageManager.getLaunchIntentForPackage(pkg)
        } else null
        val go = launch ?: Intent(AlarmClock.ACTION_SHOW_ALARMS)
        go.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(go)
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not launch target app")
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    companion object {
        fun statusBarHeightPx(context: Context): Int {
            val id = context.resources
                .getIdentifier("status_bar_height", "dimen", "android")
            return if (id > 0) context.resources.getDimensionPixelSize(id)
            else (24 * context.resources.displayMetrics.density).toInt()
        }
    }
}
