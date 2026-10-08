package com.earendel.tapbar

enum class SwipeAxis { HORIZONTAL, VERTICAL }

object SwipeAxisResolver {

    fun resolve(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        screenW: Int,
        screenH: Int,
        edge: Int
    ): SwipeAxis {
        val nearTop = y <= edge
        val nearBottom = screenH - (y + h) <= edge
        val nearLeft = x <= edge
        val nearRight = screenW - (x + w) <= edge

        val onHorizontalEdge = nearTop || nearBottom
        val onVerticalEdge = nearLeft || nearRight

        return when {
            onHorizontalEdge && !onVerticalEdge -> SwipeAxis.HORIZONTAL
            onVerticalEdge && !onHorizontalEdge -> SwipeAxis.VERTICAL
            else -> if (w >= h) SwipeAxis.HORIZONTAL else SwipeAxis.VERTICAL
        }
    }
}
