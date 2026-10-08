package com.earendel.tapbar

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.view.View
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

class SwipeAdjuster(
    private val context: Context,
    private val hud: SwipeHud,
    private val haptics: HapticManager
) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val resolver = context.contentResolver

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
            try {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
            } catch (_: Throwable) {
            }
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
    private var density = 1f
    private var emaSpeed = 0f
    private var volumeMax = 15
    private var lastVolumeIndex = -1
    private var lastBrightnessRaw = -1
    private var lastBucket = 0
    private var atMin = false
    private var atMax = false
    private var view: View? = null

    fun begin(target: Int, horizontal: Boolean, view: View): Boolean {
        val (screenW, screenH) = ScreenMetrics.sizePx(context)
        density = context.resources.displayMetrics.density
        rangePx = if (horizontal) screenW * 0.6f else (screenH * 0.32f).coerceAtLeast(160f * density)
        this.target = target
        this.view = view
        emaSpeed = 0f
        atMin = false
        atMax = false

        val ready = if (target == SWIPE_TARGET_BRIGHTNESS) prepareBrightness() else prepareVolume()
        if (!ready) {
            active = false
            return false
        }

        shown = level.roundToInt()
        lastBucket = shown / 5
        active = true
        if (target == SWIPE_TARGET_VOLUME) {
            hud.show(shown)
        }
        return true
    }

    fun move(deltaPx: Float, dtMs: Long) {
        if (!active || rangePx <= 0f) return
        val dpPerSecond = if (dtMs > 0L) abs(deltaPx) / density * 1000f / dtMs else 0f
        emaSpeed = emaSpeed * 0.6f + dpPerSecond * 0.4f
        val gain = 0.85f + (emaSpeed / 1800f).coerceIn(0f, 1f) * 1.65f
        level = (level + deltaPx / rangePx * scale * gain).coerceIn(0f, scale.toFloat())

        val next = level.roundToInt()
        if (next == shown) return
        shown = next
        commit(next)
        if (target == SWIPE_TARGET_VOLUME) {
            hud.show(next)
        }
        feedback(next)
    }

    fun end() {
        if (!active) return
        active = false
        view = null
        if (target == SWIPE_TARGET_VOLUME) {
            hud.hideSoon()
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
        val mode = try {
            Settings.System.getInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
        } catch (_: Throwable) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        }
        if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            executor.execute {
                try {
                    Settings.System.putInt(
                        resolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                    )
                } catch (_: Throwable) {
                }
            }
        }

        scale = 100
        level = linearToGamma(raw.coerceIn(0, 255) / 255f) * 100f
        lastBrightnessRaw = raw
        return true
    }

    private fun commit(value: Int) {
        if (target == SWIPE_TARGET_BRIGHTNESS) {
            val raw = (gammaToLinear(value / 100f) * 255f).roundToInt().coerceIn(1, 255)
            if (raw != lastBrightnessRaw) {
                lastBrightnessRaw = raw
                pendingBrightness.set(raw)
                schedule()
            }
        } else {
            val index = value.coerceIn(0, volumeMax)
            if (index != lastVolumeIndex) {
                lastVolumeIndex = index
                pendingVolume.set(index)
                schedule()
            }
        }
    }

    private fun schedule() {
        if (scheduled.compareAndSet(false, true)) {
            executor.execute(flush)
        }
    }

    private fun feedback(value: Int) {
        val v = view
        if (value <= 0) {
            if (!atMin) {
                atMin = true
                haptics.performBoundary(v)
            }
            return
        }
        atMin = false
        if (value >= scale) {
            if (!atMax) {
                atMax = true
                haptics.performBoundary(v)
            }
            return
        }
        atMax = false
        if (target == SWIPE_TARGET_VOLUME) {
            haptics.performSliderTick(v)
            return
        }
        val bucket = value / 5
        if (bucket != lastBucket) {
            lastBucket = bucket
            haptics.performSliderTick(v)
        }
    }
}
