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

    fun constrainX(
        editedZone: Int,
        targetX: Int,
        prefs: Prefs
    ): Int {
        val otherZone = 1 - editedZone
        val currentW = prefs.getZoneWidth(editedZone)
        val currentY = prefs.getPosY(editedZone)
        val currentH = prefs.getZoneHeight(editedZone)

        val otherX = prefs.getPosX(otherZone)
        val otherY = prefs.getPosY(otherZone)
        val otherW = prefs.getZoneWidth(otherZone)
        val otherH = prefs.getZoneHeight(otherZone)

        val proposed = ZoneGeometry(targetX, currentY, currentW, currentH)
        val other = ZoneGeometry(otherX, otherY, otherW, otherH)

        if (!overlaps(proposed, other)) {
            return targetX
        }

        val oldX = prefs.getPosX(editedZone)
        return if (oldX < otherX) {
            (otherX - currentW).coerceAtLeast(0)
        } else {
            (otherX + otherW).coerceAtMost(400)
        }
    }

    fun constrainY(
        editedZone: Int,
        targetY: Int,
        prefs: Prefs
    ): Int {
        val otherZone = 1 - editedZone
        val currentX = prefs.getPosX(editedZone)
        val currentW = prefs.getZoneWidth(editedZone)
        val currentH = prefs.getZoneHeight(editedZone)

        val otherX = prefs.getPosX(otherZone)
        val otherY = prefs.getPosY(otherZone)
        val otherW = prefs.getZoneWidth(otherZone)
        val otherH = prefs.getZoneHeight(otherZone)

        val proposed = ZoneGeometry(currentX, targetY, currentW, currentH)
        val other = ZoneGeometry(otherX, otherY, otherW, otherH)

        if (!overlaps(proposed, other)) {
            return targetY
        }

        val oldY = prefs.getPosY(editedZone)
        return if (oldY < otherY) {
            (otherY - currentH).coerceAtLeast(0)
        } else {
            (otherY + otherH).coerceAtMost(800)
        }
    }

    fun constrainWidth(
        editedZone: Int,
        targetW: Int,
        prefs: Prefs
    ): Int {
        val otherZone = 1 - editedZone
        val currentX = prefs.getPosX(editedZone)
        val currentY = prefs.getPosY(editedZone)
        val currentH = prefs.getZoneHeight(editedZone)

        val otherX = prefs.getPosX(otherZone)
        val otherY = prefs.getPosY(otherZone)
        val otherW = prefs.getZoneWidth(otherZone)
        val otherH = prefs.getZoneHeight(otherZone)

        val proposed = ZoneGeometry(currentX, currentY, targetW, currentH)
        val other = ZoneGeometry(otherX, otherY, otherW, otherH)

        if (!overlaps(proposed, other)) {
            return targetW
        }

        return if (currentX < otherX) {
            (otherX - currentX).coerceAtLeast(20)
        } else {
            targetW
        }
    }

    fun constrainHeight(
        editedZone: Int,
        targetH: Int,
        prefs: Prefs
    ): Int {
        val otherZone = 1 - editedZone
        val currentX = prefs.getPosX(editedZone)
        val currentY = prefs.getPosY(editedZone)
        val currentW = prefs.getZoneWidth(editedZone)

        val otherX = prefs.getPosX(otherZone)
        val otherY = prefs.getPosY(otherZone)
        val otherW = prefs.getZoneWidth(otherZone)
        val otherH = prefs.getZoneHeight(otherZone)

        val proposed = ZoneGeometry(currentX, currentY, currentW, targetH)
        val other = ZoneGeometry(otherX, otherY, otherW, otherH)

        if (!overlaps(proposed, other)) {
            return targetH
        }

        return if (currentY < otherY) {
            (otherY - currentY).coerceAtLeast(16)
        } else {
            targetH
        }
    }

    fun enforceNonOverlapOnLoad(prefs: Prefs) {
        val z0 = ZoneGeometry(prefs.getPosX(0), prefs.getPosY(0), prefs.getZoneWidth(0), prefs.getZoneHeight(0))
        val z1 = ZoneGeometry(prefs.getPosX(1), prefs.getPosY(1), prefs.getZoneWidth(1), prefs.getZoneHeight(1))

        if (overlaps(z0, z1)) {
            val resolvedX1 = (z0.x + z0.w + 12).coerceAtMost(400)
            prefs.setPosX(1, resolvedX1)
        }
    }
}
