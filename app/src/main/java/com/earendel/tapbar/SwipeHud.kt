package com.earendel.tapbar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat

private fun formatHudValue(value: Int): String = value.toString()

class SwipeHud(
    private val context: Context,
    private val windowType: Int
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = Prefs(context)
    private val main = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    private val sizePx = (46 * density).toInt()
    private val bottomPx = (56 * density).toInt()
    private val blurPx = (16 * density).toInt()

    private var dialog: Dialog? = null
    private var plain: TextView? = null
    private var label: TextView? = null
    private var blurOn = false
    private var attached = false
    private var progress = 0f
    private var animTarget = 0f
    private var animator: ValueAnimator? = null

    private val hideRunnable = Runnable {
        animateTo(0f, 220L) { dismissNow() }
    }

    fun show(value: Int) {
        main.removeCallbacks(hideRunnable)
        if (!attached) attach()
        label?.let { text ->
            val formatted = formatHudValue(value)
            text.text = formatted
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (formatted.length > 2) 12f else 15f)
        }
        if (animTarget != 1f) animateTo(1f, 120L)
        main.postDelayed(hideRunnable, 4000L)
    }

    fun hideSoon() {
        if (!attached) return
        main.removeCallbacks(hideRunnable)
        main.postDelayed(hideRunnable, 650L)
    }

    fun dismissNow() {
        main.removeCallbacks(hideRunnable)
        animator?.cancel()
        animator = null
        try {
            dialog?.dismiss()
        } catch (_: Throwable) {
        }
        dialog = null
        try {
            plain?.let { wm.removeView(it) }
        } catch (_: Throwable) {
        }
        plain = null
        label = null
        attached = false
        progress = 0f
        animTarget = 0f
    }

    private fun attach() {
        attached = true
        progress = 0f
        animTarget = 0f
        if (!attachDialog()) attachPlain()
        applyProgress(0f)
    }

    private fun attachDialog(): Boolean {
        var created: Dialog? = null
        return try {
            val d = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
            created = d
            val text = createLabel()
            d.setCancelable(false)
            d.setContentView(text, ViewGroup.LayoutParams(sizePx, sizePx))
            val w = d.window ?: throw IllegalStateException()
            val blur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && wm.isCrossWindowBlurEnabled
            w.setType(windowType)
            w.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                        (if (Build.VERSION.SDK_INT >= 31) WindowManager.LayoutParams.FLAG_BLUR_BEHIND else 0)
            )
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setBackgroundDrawable(glassBackground(blur))
            w.setLayout(sizePx, sizePx)
            w.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            w.setWindowAnimations(0)
            val lp = w.attributes
            lp.y = bottomPx
            lp.alpha = 0f
            if (blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                lp.blurBehindRadius = blurPx
            }
            w.attributes = lp
            if (blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                w.setBackgroundBlurRadius(blurPx)
            }
            d.show()
            dialog = d
            label = text
            blurOn = blur
            true
        } catch (_: Throwable) {
            try {
                created?.dismiss()
            } catch (_: Throwable) {
            }
            dialog = null
            label = null
            blurOn = false
            false
        }
    }

    private fun attachPlain() {
        try {
            val blur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && wm.isCrossWindowBlurEnabled
            val text = createLabel()
            text.background = glassBackground(blur)
            val lp = WindowManager.LayoutParams(
                sizePx,
                sizePx,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                        (if (Build.VERSION.SDK_INT >= 31) WindowManager.LayoutParams.FLAG_BLUR_BEHIND else 0),
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = bottomPx
                if (blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    blurBehindRadius = blurPx
                }
            }
            wm.addView(text, lp)
            plain = text
            label = text
            blurOn = blur
        } catch (_: Throwable) {
            plain = null
            label = null
        }
    }

    private fun createLabel(): TextView {
        val dark = isDark()
        return TextView(context).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            setSingleLine(true)
            setTextColor(if (dark) Color.WHITE else Color.rgb(17, 17, 17))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            layoutParams = ViewGroup.LayoutParams(sizePx, sizePx)
            val base = try {
                ResourcesCompat.getFont(context, R.font.geist_variable) ?: Typeface.DEFAULT
            } catch (_: Throwable) {
                Typeface.DEFAULT
            }
            typeface = base
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                fontVariationSettings = "'wght' 520"
            } else {
                typeface = Typeface.create(base, Typeface.BOLD)
            }
        }
    }

    private fun isDark(): Boolean = when (prefs.themeMode) {
        1 -> false
        2 -> true
        else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun glassBackground(blur: Boolean): GradientDrawable {
        val dark = isDark()
        val fill = when {
            dark && blur -> Color.argb(77, 28, 28, 30)
            dark -> Color.argb(179, 28, 28, 30)
            blur -> Color.argb(102, 255, 255, 255)
            else -> Color.argb(230, 255, 255, 255)
        }
        val stroke = if (dark) Color.argb(51, 255, 255, 255) else Color.argb(153, 255, 255, 255)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = sizePx / 2f
            setColor(fill)
            setStroke(1, stroke)
        }
    }

    private fun applyProgress(t: Float) {
        val d = dialog
        if (d != null) {
            val w = d.window ?: return
            val lp = w.attributes
            lp.alpha = t
            w.attributes = lp
            if (blurOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                w.setBackgroundBlurRadius((blurPx * t).toInt())
            }
        } else {
            plain?.alpha = t
        }
    }

    private fun animateTo(target: Float, durationMs: Long, onEnd: (() -> Unit)? = null) {
        animator?.cancel()
        animTarget = target
        val a = ValueAnimator.ofFloat(progress, target)
        a.duration = durationMs
        a.addUpdateListener {
            progress = it.animatedValue as Float
            applyProgress(progress)
        }
        a.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) onEnd?.invoke()
            }
        })
        animator = a
        a.start()
    }
}
