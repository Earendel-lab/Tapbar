package com.earendel.tapbar

import kotlin.math.max
import kotlin.math.min

data class ZoneGeometry(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int
)

object ZoneOverlapResolver {

    private const val GAP = 0

    fun overlaps(a: ZoneGeometry, b: ZoneGeometry): Boolean {
        val overlapX = max(a.x, b.x) < min(a.x + a.w, b.x + b.w)
        val overlapY = max(a.y, b.y) < min(a.y + a.h, b.y + b.h)
        return overlapX && overlapY
    }

    private fun geometryOf(prefs: Prefs, zone: Int): ZoneGeometry = prefs.geometry(zone)

    private fun othersOf(prefs: Prefs, editedZone: Int): List<ZoneGeometry> =
        (0 until ZONE_COUNT)
            .filter { it != editedZone && prefs.isZoneEnabled(it) }
            .map { geometryOf(prefs, it) }

    fun constrainX(
        editedZone: Int,
        targetX: Int,
        prefs: Prefs
    ): Int {
        val current = geometryOf(prefs, editedZone)
        val others = othersOf(prefs, editedZone)
        val limit = (prefs.screenWidthDp() - current.w).coerceAtLeast(0)
        var x = targetX.coerceIn(0, limit)
        repeat(others.size + 1) {
            val hit = others.firstOrNull { overlaps(current.copy(x = x), it) } ?: return x
            x = if (current.x < hit.x) {
                (hit.x - current.w).coerceIn(0, limit)
            } else {
                (hit.x + hit.w).coerceIn(0, limit)
            }
        }
        return if (others.any { overlaps(current.copy(x = x), it) }) current.x else x
    }

    fun constrainY(
        editedZone: Int,
        targetY: Int,
        prefs: Prefs
    ): Int {
        val current = geometryOf(prefs, editedZone)
        val others = othersOf(prefs, editedZone)
        val limit = (prefs.screenHeightDp() - current.h).coerceAtLeast(0)
        var y = targetY.coerceIn(0, limit)
        repeat(others.size + 1) {
            val hit = others.firstOrNull { overlaps(current.copy(y = y), it) } ?: return y
            y = if (current.y < hit.y) {
                (hit.y - current.h).coerceIn(0, limit)
            } else {
                (hit.y + hit.h).coerceIn(0, limit)
            }
        }
        return if (others.any { overlaps(current.copy(y = y), it) }) current.y else y
    }

    fun constrainWidth(
        editedZone: Int,
        targetW: Int,
        prefs: Prefs
    ): Int {
        val current = geometryOf(prefs, editedZone)
        val limit = max(ZoneLayout.MIN_W, prefs.screenWidthDp() - current.x)
        var w = targetW.coerceIn(ZoneLayout.MIN_W, limit)
        for (other in othersOf(prefs, editedZone)) {
            if (overlaps(current.copy(w = w), other) && current.x < other.x) {
                w = (other.x - current.x).coerceAtLeast(ZoneLayout.MIN_W)
            }
        }
        return w
    }

    fun constrainHeight(
        editedZone: Int,
        targetH: Int,
        prefs: Prefs
    ): Int {
        val current = geometryOf(prefs, editedZone)
        val limit = max(ZoneLayout.MIN_H, prefs.screenHeightDp() - current.y)
        var h = targetH.coerceIn(ZoneLayout.MIN_H, limit)
        for (other in othersOf(prefs, editedZone)) {
            if (overlaps(current.copy(h = h), other) && current.y < other.y) {
                h = (other.y - current.y).coerceAtLeast(ZoneLayout.MIN_H)
            }
        }
        return h
    }

    private fun trimVertical(prefs: Prefs, zone: Int, t: ZoneGeometry, o: ZoneGeometry): Boolean {
        val tEnd = t.y + t.h
        val oEnd = o.y + o.h
        val topCut = if (o.y <= t.y && oEnd < tEnd) tEnd - oEnd else 0
        val bottomCut = if (o.y > t.y) o.y - t.y else 0
        val useTop = topCut >= bottomCut
        val best = if (useTop) topCut else bottomCut
        if (best < ZoneLayout.MIN_TRIM) return false
        if (useTop) {
            prefs.setPosY(zone, oEnd)
            prefs.setZoneHeight(zone, topCut)
        } else {
            prefs.setZoneHeight(zone, bottomCut)
        }
        return true
    }

    private fun trimHorizontal(prefs: Prefs, zone: Int, t: ZoneGeometry, o: ZoneGeometry): Boolean {
        val tEnd = t.x + t.w
        val oEnd = o.x + o.w
        val leftCut = if (o.x <= t.x && oEnd < tEnd) tEnd - oEnd else 0
        val rightCut = if (o.x > t.x) o.x - t.x else 0
        val useLeft = leftCut >= rightCut
        val best = if (useLeft) leftCut else rightCut
        if (best < ZoneLayout.MIN_TRIM) return false
        if (useLeft) {
            prefs.setPosX(zone, oEnd)
            prefs.setZoneWidth(zone, leftCut)
        } else {
            prefs.setZoneWidth(zone, rightCut)
        }
        return true
    }

    private fun trimLonger(prefs: Prefs, a: Int, ga: ZoneGeometry, b: Int, gb: ZoneGeometry): Boolean {
        val aLong = max(ga.w, ga.h)
        val bLong = max(gb.w, gb.h)
        val trimmedZone = if (aLong >= bLong) a else b
        val t = if (aLong >= bLong) ga else gb
        val o = if (aLong >= bLong) gb else ga
        return if (t.h >= t.w) trimVertical(prefs, trimmedZone, t, o) else trimHorizontal(prefs, trimmedZone, t, o)
    }

    private fun shiftAway(prefs: Prefs, zone: Int, g: ZoneGeometry, hit: ZoneGeometry) {
        val screenW = prefs.screenWidthDp()
        val screenH = prefs.screenHeightDp()
        val shiftedX = hit.x + hit.w + GAP
        if (shiftedX + g.w <= screenW) {
            prefs.setPosX(zone, shiftedX)
        } else {
            val maxY = (screenH - g.h).coerceAtLeast(0)
            prefs.setPosY(zone, (hit.y + hit.h + GAP).coerceAtMost(maxY))
        }
    }

    fun enforceNonOverlapOnLoad(prefs: Prefs) {
        val enabled = (0 until ZONE_COUNT).filter { prefs.isZoneEnabled(it) }
        var pass = 0
        var changed = true
        while (changed && pass < 8) {
            changed = false
            pass++
            for (i in enabled.indices) {
                for (j in i + 1 until enabled.size) {
                    val a = enabled[i]
                    val b = enabled[j]
                    val ga = geometryOf(prefs, a)
                    val gb = geometryOf(prefs, b)
                    if (!overlaps(ga, gb)) continue
                    if (!trimLonger(prefs, a, ga, b, gb)) shiftAway(prefs, b, gb, ga)
                    changed = true
                }
            }
        }
    }
}
