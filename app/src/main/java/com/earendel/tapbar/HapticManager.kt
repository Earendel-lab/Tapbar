package com.earendel.tapbar

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

class HapticManager(
    private val prefs: Prefs,
    private val vibrator: Vibrator?,
) {
    @Volatile
    private var lastHapticTime: Long = 0L

    val isEnabled: Boolean
        get() = prefs.hapticFeedbackEnabled

    fun performLightTap(view: View? = null) {
        if (!isEnabled) return
        if (view != null && performViewHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)) return
        performPredefinedVibration(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_CLICK else -1, 8L)
    }

    fun performSelection(view: View? = null) {
        if (!isEnabled) return
        val feedbackConstant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.GESTURE_START
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
        if (view != null && performViewHaptic(view, feedbackConstant)) return
        performPredefinedVibration(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_TICK else -1, 6L)
    }

    fun performToggle(view: View? = null, newState: Boolean = true) {
        if (!isEnabled) return
        val feedbackConstant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (newState) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.GESTURE_END
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        if (view != null && performViewHaptic(view, feedbackConstant)) return
        performPredefinedVibration(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_CLICK else -1, 10L)
    }

    fun performSliderTick(view: View? = null) {
        if (!isEnabled) return
        val now = SystemClock.uptimeMillis()
        if (now - lastHapticTime < MIN_SLIDER_TICK_INTERVAL_MS) return
        lastHapticTime = now

        val feedbackConstant = when {
            Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.SEGMENT_TICK
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> HapticFeedbackConstants.CLOCK_TICK
            else -> HapticFeedbackConstants.KEYBOARD_TAP
        }
        if (view != null && performViewHaptic(view, feedbackConstant)) return
        performPredefinedVibration(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_TICK else -1, 4L)
    }

    fun performBoundary(view: View? = null) {
        if (!isEnabled) return
        val now = SystemClock.uptimeMillis()
        if (now - lastHapticTime < MIN_BOUNDARY_INTERVAL_MS) return
        lastHapticTime = now

        val feedbackConstant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        if (view != null && performViewHaptic(view, feedbackConstant)) return
        performPredefinedVibration(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_HEAVY_CLICK else -1, 15L)
    }

    fun performConfirm(view: View? = null) {
        if (!isEnabled) return
        val feedbackConstant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        if (view != null && performViewHaptic(view, feedbackConstant)) return
        performPredefinedVibration(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_CLICK else -1, 12L)
    }

    fun performExpandCollapse(view: View? = null) {
        if (!isEnabled) return
        if (view != null && performViewHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)) return
        performPredefinedVibration(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_TICK else -1, 5L)
    }

    fun performGesture(view: View? = null) {
        if (!isEnabled) return
        val feedbackConstant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.GESTURE_START
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        if (view != null && performViewHaptic(view, feedbackConstant)) return
        performPredefinedVibration(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_CLICK else -1, 10L)
    }

    private fun performViewHaptic(view: View, constant: Int): Boolean {
        return try {
            view.performHapticFeedback(constant)
        } catch (_: Throwable) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun performPredefinedVibration(effectId: Int, fallbackDurationMs: Long) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && effectId >= 0) {
                vib.vibrate(VibrationEffect.createPredefined(effectId))
            } else if (fallbackDurationMs > 0) {
                vib.vibrate(VibrationEffect.createOneShot(fallbackDurationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val MIN_SLIDER_TICK_INTERVAL_MS = 60L
        private const val MIN_BOUNDARY_INTERVAL_MS = 120L

        fun create(context: Context): HapticManager {
            val prefs = Prefs(context)
            val vibrator = context.getSystemService(Vibrator::class.java)
            return HapticManager(prefs, vibrator)
        }
    }
}

val LocalHapticManager: ProvidableCompositionLocal<HapticManager> = staticCompositionLocalOf {
    error("No HapticManager provided")
}

@Composable
fun ProvideHapticManager(
    prefs: Prefs,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val manager = remember(context, prefs) { HapticManager.create(context) }
    CompositionLocalProvider(LocalHapticManager provides manager) {
        content()
    }
}

class SliderHapticHelper(
    private val min: Float,
    private val max: Float,
    private val interval: Float,
) {
    private var atMinBoundary = false
    private var atMaxBoundary = false
    private var lastLandmarkIndex = -1

    fun onValueChange(value: Float, view: View?, haptics: HapticManager) {
        val tolerance = 0.5f
        if (value <= min + tolerance) {
            if (!atMinBoundary) {
                atMinBoundary = true
                haptics.performBoundary(view)
            }
            return
        } else {
            atMinBoundary = false
        }

        if (value >= max - tolerance) {
            if (!atMaxBoundary) {
                atMaxBoundary = true
                haptics.performBoundary(view)
            }
            return
        } else {
            atMaxBoundary = false
        }

        val currentIndex = ((value - min) / interval).toInt()
        if (lastLandmarkIndex != -1 && currentIndex != lastLandmarkIndex) {
            haptics.performSliderTick(view)
        }
        lastLandmarkIndex = currentIndex
    }

    fun onValueChangeFinished() {
        atMinBoundary = false
        atMaxBoundary = false
        lastLandmarkIndex = -1
    }
}
