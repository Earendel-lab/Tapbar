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

    fun overlaps(a: ZoneGeometry, b: ZoneGeometry): Boolean {
        val overlapX = max(a.x, b.x) < min(a.x + a.w, b.x + b.w)
        val overlapY = max(a.y, b.y) < min(a.y + a.h, b.y + b.h)
        return overlapX && overlapY
    }

    private fun geometryOf(prefs: Prefs, zone: Int): ZoneGeometry =
        ZoneGeometry(
            prefs.getPosX(zone),
            prefs.getPosY(zone),
            prefs.getZoneWidth(zone),
            prefs.getZoneHeight(zone)
        )

    private fun othersOf(prefs: Prefs, editedZone: Int): List<ZoneGeometry> =
        (0 until ZONE_COUNT).filter { it != editedZone }.map { geometryOf(prefs, it) }

    fun constrainX(
        editedZone: Int,
        targetX: Int,
        prefs: Prefs
    ): Int {
        val current = geometryOf(prefs, editedZone)
        val others = othersOf(prefs, editedZone)
        var x = targetX
        repeat(others.size + 1) {
            val hit = others.firstOrNull { overlaps(current.copy(x = x), it) } ?: return x
            x = if (current.x < hit.x) {
                (hit.x - current.w).coerceAtLeast(0)
            } else {
                (hit.x + hit.w).coerceAtMost(400)
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
        var y = targetY
        repeat(others.size + 1) {
            val hit = others.firstOrNull { overlaps(current.copy(y = y), it) } ?: return y
            y = if (current.y < hit.y) {
                (hit.y - current.h).coerceAtLeast(0)
            } else {
                (hit.y + hit.h).coerceAtMost(800)
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
        var w = targetW
        for (other in othersOf(prefs, editedZone)) {
            if (overlaps(current.copy(w = w), other) && current.x < other.x) {
                w = (other.x - current.x).coerceAtLeast(20)
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
        var h = targetH
        for (other in othersOf(prefs, editedZone)) {
            if (overlaps(current.copy(h = h), other) && current.y < other.y) {
                h = (other.y - current.y).coerceAtLeast(16)
            }
        }
        return h
    }

    fun enforceNonOverlapOnLoad(prefs: Prefs) {
        for (zone in 1 until ZONE_COUNT) {
            var attempts = 0
            while (attempts < 8) {
                val zoneGeometry = geometryOf(prefs, zone)
                val hit = (0 until zone)
                    .map { geometryOf(prefs, it) }
                    .firstOrNull { overlaps(zoneGeometry, it) } ?: break
                val shiftedX = hit.x + hit.w + 12
                if (shiftedX + zoneGeometry.w <= 400) {
                    prefs.setPosX(zone, shiftedX)
                } else {
                    prefs.setPosY(zone, (hit.y + hit.h + 12).coerceAtMost(800))
                }
                attempts++
            }
        }
    }
}
