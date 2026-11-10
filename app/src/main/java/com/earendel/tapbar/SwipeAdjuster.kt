package com.earendel.tapbar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

const val SWIPE_TARGET_VOLUME = 0
const val SWIPE_TARGET_BRIGHTNESS = 1

const val SWIPE_MODE_BASIC = 0
const val SWIPE_MODE_ADVANCED = 1

const val SWIPE_DIR_LEFT = 0
const val SWIPE_DIR_RIGHT = 1
const val SWIPE_DIR_UP = 2
const val SWIPE_DIR_DOWN = 3

const val SWIPE_TYPE_OFF = 0
const val SWIPE_TYPE_VOLUME = 1
const val SWIPE_TYPE_BRIGHTNESS = 2
const val SWIPE_TYPE_ACTION = 3
const val SWIPE_TYPE_APP = 4

fun swipeTargetLabel(target: Int): String =
    if (target == SWIPE_TARGET_BRIGHTNESS) "Brightness" else "Volume"

private const val GAMMA_R = 0.5f
private const val GAMMA_A = 0.17883277f
private const val GAMMA_B = 0.28466892f
private const val GAMMA_C = 0.55991073f

private fun gammaToLinear(gamma: Float): Float {
    val x = gamma.coerceIn(0f, 1f)
    val value = if (x <= GAMMA_R) {
        (x / GAMMA_R) * (x / GAMMA_R)
    } else {
        exp((x - GAMMA_C) / GAMMA_A) + GAMMA_B
    }
    return value.coerceIn(0f, 12f) / 12f
}

private fun linearToGamma(linear: Float): Float {
    val n = linear.coerceIn(0f, 1f) * 12f
    val value = if (n <= 1f) {
        sqrt(n) * GAMMA_R
    } else {
        GAMMA_A * ln(n - GAMMA_B) + GAMMA_C
    }
    return value.coerceIn(0f, 1f)
}

private const val STATE_PREFS = "swipe_adjuster_state"
private const val KEY_AUTO_SUSPENDED = "auto_brightness_suspended"
private const val VOLUME_HYSTERESIS = 0.6f
private const val RAYS_DEGREES_PER_RANGE = 540f

class SwipeAdjuster(
    private val context: Context,
    private val hud: SwipeHud,
    private val haptics: HapticManager
) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val resolver = context.contentResolver
    private val prefs = Prefs(context)
    private val main = Handler(Looper.getMainLooper())
    private val statePrefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)

    private val executor = ThreadPoolExecutor(
        1, 1, 5L, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>()
    ).apply { allowCoreThreadTimeOut(true) }

    private val scheduled = AtomicBoolean(false)
    private val pendingVolume = AtomicInteger(-1)
    private val pendingBrightness = AtomicInteger(-1)

    private val flush = Runnable {
        scheduled.set(false)
        val volume = pendingVolume.getAndSet(-1)
        if (volume >= 0) {
            applyVolume(volume)
        }
        val brightness = pendingBrightness.getAndSet(-1)
        if (brightness >= 0) {
            try {
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
            } catch (_: Throwable) {
            }
        }
    }

    private var active = false
    private var target = SWIPE_TARGET_VOLUME
    private var scale = 100
    private var level = 0f
    private var shown = -1
    private var rangePx = 1f
    private var volumeMax = 15
    private var lastVolumeIndex = -1
    private var lastBrightnessRaw = -1
    private var lastBucket = 0
    private var atMin = false
    private var atMax = false
    private var autoSuspendRequested = false
    private var hudOn = false

    @Volatile
    private var noticeOn = false
    private var view: View? = null

    private var autoSuspended: Boolean
        get() = statePrefs.getBoolean(KEY_AUTO_SUSPENDED, false)
        set(value) {
            statePrefs.edit().putBoolean(KEY_AUTO_SUSPENDED, value).apply()
        }
    private var receiverRegistered = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            executor.execute { restoreAutoBrightness() }
        }
    }

    init {
        if (autoSuspended) registerScreenOffReceiver()
    }

    fun begin(target: Int, horizontal: Boolean, view: View, zone: Int): Boolean {
        val (screenW, screenH) = ScreenMetrics.sizePx(context)
        val density = context.resources.displayMetrics.density
        rangePx = if (horizontal) screenW * 0.6f else (screenH * 0.32f).coerceAtLeast(160f * density)
        this.target = target
        this.view = view
        atMin = false
        atMax = false
        hudOn = target == SWIPE_TARGET_BRIGHTNESS && prefs.getSwipeBrightnessHud(zone)
        noticeOn = target == SWIPE_TARGET_BRIGHTNESS && prefs.getSwipeAutoNotice(zone)

        val ready = if (target == SWIPE_TARGET_BRIGHTNESS) prepareBrightness() else prepareVolume()
        if (!ready) {
            active = false
            return false
        }

        shown = level.roundToInt()
        lastBucket = (level / 5f).toInt()
        active = true
        if (target == SWIPE_TARGET_VOLUME || hudOn) {
            hud.show(shown, target == SWIPE_TARGET_BRIGHTNESS)
        }
        return true
    }

    fun move(deltaPx: Float, dtMs: Long) {
        if (!active || rangePx <= 0f) return
        if (dtMs <= 0L) return
        val before = level
        level = (level + deltaPx / rangePx * scale).coerceIn(0f, scale.toFloat())
        if (hudOn) hud.rotateRays((level - before) / scale * RAYS_DEGREES_PER_RANGE)
        if (target == SWIPE_TARGET_BRIGHTNESS) moveBrightness() else moveVolume()
    }

    fun end() {
        if (!active) return
        active = false
        view = null
        if (target == SWIPE_TARGET_VOLUME || hudOn) {
            hud.hideSoon()
        }
    }

    private fun moveBrightness() {
        val raw = (gammaToLinear(level / 100f) * 255f).roundToInt().coerceIn(1, 255)
        if (raw != lastBrightnessRaw) {
            lastBrightnessRaw = raw
            if (!autoSuspendRequested) {
                autoSuspendRequested = true
                executor.execute { suspendAutoBrightness() }
            }
            pendingBrightness.set(raw)
            schedule()
        }

        val percent = level.roundToInt()
        if (percent != shown) {
            shown = percent
            if (hudOn) hud.show(percent, true)
        }

        val v = view
        if (level <= 0f) {
            if (!atMin) {
                atMin = true
                haptics.performBoundary(v)
            }
            return
        }
        atMin = false
        if (level >= scale) {
            if (!atMax) {
                atMax = true
                haptics.performBoundary(v)
            }
            return
        }
        atMax = false
        val bucket = (level / 5f).toInt()
        if (bucket != lastBucket) {
            lastBucket = bucket
            haptics.performSliderTick(v)
        }
    }

    private fun moveVolume() {
        if (abs(level - shown) < VOLUME_HYSTERESIS) return
        val next = level.roundToInt().coerceIn(0, volumeMax)
        if (next == shown) return
        shown = next
        if (next != lastVolumeIndex) {
            lastVolumeIndex = next
            pendingVolume.set(next)
            schedule()
        }
        hud.show(next)

        val v = view
        if (next <= 0) {
            if (!atMin) {
                atMin = true
                haptics.performBoundary(v)
            }
            return
        }
        atMin = false
        if (next >= volumeMax) {
            if (!atMax) {
                atMax = true
                haptics.performBoundary(v)
            }
            return
        }
        atMax = false
        haptics.performSliderTick(v)
    }

    private fun schedule() {
        if (scheduled.compareAndSet(false, true)) {
            executor.execute(flush)
        }
    }

    private fun suspendAutoBrightness() {
        try {
            val mode = Settings.System.getInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                autoSuspended = true
                if (noticeOn) showAutoOffNotice()
                registerScreenOffReceiver()
            }
        } catch (_: Throwable) {
        }
    }

    private fun showAutoOffNotice() {
        main.post {
            try {
                val text = LocaleHelper.wrap(context).getString(R.string.swipe_auto_off_notice)
                Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {
            }
        }
    }

    private fun restoreAutoBrightness() {
        if (!autoSuspended) return
        try {
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            )
        } catch (_: Throwable) {
            return
        }
        autoSuspended = false
        unregisterScreenOffReceiver()
    }

    private fun registerScreenOffReceiver() {
        synchronized(this) {
            if (receiverRegistered) return
            try {
                ContextCompat.registerReceiver(
                    context,
                    screenOffReceiver,
                    IntentFilter(Intent.ACTION_SCREEN_OFF),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                receiverRegistered = true
            } catch (_: Throwable) {
            }
        }
    }

    private fun unregisterScreenOffReceiver() {
        synchronized(this) {
            if (!receiverRegistered) return
            try {
                context.unregisterReceiver(screenOffReceiver)
            } catch (_: Throwable) {
            }
            receiverRegistered = false
        }
    }

    private fun applyVolume(target: Int) {
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        } catch (t: Throwable) {
            Log.e("Tapbar", "setStreamVolume failed", t)
        }
        var actual = try {
            audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        } catch (_: Throwable) {
            return
        }
        if (actual == target) return

        var previousDiff = target - actual
        var guard = 0
        while (previousDiff != 0 && guard < volumeMax + 2) {
            guard++
            try {
                audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    if (previousDiff > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                    0
                )
            } catch (t: Throwable) {
                Log.e("Tapbar", "adjustStreamVolume failed", t)
                return
            }
            val now = try {
                audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            } catch (_: Throwable) {
                return
            }
            if (now == actual) {
                Log.w("Tapbar", "Volume stuck at $now, wanted $target")
                return
            }
            actual = now
            val diff = target - actual
            if (diff == 0 || (diff > 0) != (previousDiff > 0)) return
            previousDiff = diff
        }
    }

    private fun prepareVolume(): Boolean {
        if (audio.isVolumeFixed) return false
        volumeMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (volumeMax <= 0) return false
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        scale = volumeMax
        level = current.toFloat()
        lastVolumeIndex = current
        return true
    }

    companion object {
        fun clearAutoSuspension(context: Context) {
            context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_AUTO_SUSPENDED, false)
                .apply()
        }
    }

    private fun prepareBrightness(): Boolean {
        if (!Settings.System.canWrite(context)) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Throwable) {
            }
            return false
        }

        val raw = try {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        } catch (_: Throwable) {
            128
        }
        autoSuspendRequested = false
        scale = 100
        level = linearToGamma(raw.coerceIn(0, 255) / 255f) * 100f
        lastBrightnessRaw = raw
        return true
    }
}
