package com.earendel.tapbar

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ScreenMetrics {

    fun sizePx(context: Context): Pair<Int, Int> {
        try {
            val dm = context.applicationContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
            if (display != null) {
                val m = DisplayMetrics()
                @Suppress("DEPRECATION")
                display.getRealMetrics(m)
                if (m.widthPixels > 0 && m.heightPixels > 0) {
                    return Pair(m.widthPixels, m.heightPixels)
                }
            }
        } catch (_: Throwable) {
        }
        val m = context.resources.displayMetrics
        return Pair(m.widthPixels, m.heightPixels)
    }

    fun sizeDp(context: Context): Pair<Int, Int> {
        val (w, h) = sizePx(context)
        val d = context.resources.displayMetrics.density.coerceAtLeast(0.5f)
        return Pair((w / d).toInt(), (h / d).toInt())
    }

    fun isLandscape(context: Context): Boolean {
        val (w, h) = sizePx(context)
        return w > h
    }
}

object ZoneLayout {

    const val EDGE_DP = 48
    const val MIN_W = 10
    const val MIN_H = 16
    const val MIN_TRIM = 40

    fun clamp(g: ZoneGeometry, screenW: Int, screenH: Int): ZoneGeometry {
        val w = g.w.coerceIn(MIN_W, max(MIN_W, screenW))
        val h = g.h.coerceIn(MIN_H, max(MIN_H, screenH))
        val x = g.x.coerceIn(0, max(0, screenW - w))
        val y = g.y.coerceIn(0, max(0, screenH - h))
        return ZoneGeometry(x, y, w, h)
    }

    private fun convertAxis(pos: Int, size: Int, fromSpan: Int, toSpan: Int): Pair<Int, Int> {
        if (fromSpan <= 0 || toSpan <= 0) return Pair(pos, size)
        val startGap = pos.coerceAtLeast(0)
        val endGap = (fromSpan - (pos + size)).coerceAtLeast(0)
        val nearStart = startGap <= EDGE_DP
        val nearEnd = endGap <= EDGE_DP
        val isLong = toSpan < fromSpan && size * 4 >= fromSpan
        val scaled = if (isLong) {
            (size.toLong() * toSpan / fromSpan).toInt().coerceAtLeast(1)
        } else {
            size
        }
        return when {
            nearStart && nearEnd -> {
                Pair(startGap, (toSpan - startGap - endGap).coerceAtLeast(1))
            }
            nearStart -> {
                val s = min(scaled, toSpan - startGap).coerceAtLeast(1)
                Pair(startGap, s)
            }
            nearEnd -> {
                val s = min(scaled, toSpan - endGap).coerceAtLeast(1)
                Pair(toSpan - endGap - s, s)
            }
            else -> {
                val s = min(scaled, toSpan)
                val center = (pos + size / 2f) / fromSpan * toSpan
                Pair((center - s / 2f).roundToInt().coerceIn(0, max(0, toSpan - s)), s)
            }
        }
    }

    fun toLandscape(g: ZoneGeometry, shortSide: Int, longSide: Int): ZoneGeometry {
        val (x, w) = convertAxis(g.x, g.w, shortSide, longSide)
        val (y, h) = convertAxis(g.y, g.h, longSide, shortSide)
        return ZoneGeometry(x, y, w, h)
    }
}
