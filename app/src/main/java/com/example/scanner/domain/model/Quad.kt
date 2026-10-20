package com.example.scanner.domain.model

import kotlin.math.abs
import kotlin.math.max

/** A point in normalized coordinates: (0,0) is top-left, (1,1) is bottom-right of an upright image. */
data class NormPoint(val x: Float, val y: Float) {
    fun lerp(other: NormPoint, t: Float) =
        NormPoint(x + (other.x - x) * t, y + (other.y - y) * t)
}

/** The four corners of a detected document, always in TL, TR, BR, BL order. */
data class Quad(
    val topLeft: NormPoint,
    val topRight: NormPoint,
    val bottomRight: NormPoint,
    val bottomLeft: NormPoint,
) {
    val points: List<NormPoint> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    companion object {
        /** The whole image: used when no document outline could be found. */
        val FULL = Quad(NormPoint(0f, 0f), NormPoint(1f, 0f), NormPoint(1f, 1f), NormPoint(0f, 1f))
    }

    /** Returns a copy with the corner at [index] (0=TL, 1=TR, 2=BR, 3=BL) replaced. */
    fun withPoint(index: Int, point: NormPoint) = when (index) {
        0 -> copy(topLeft = point)
        1 -> copy(topRight = point)
        2 -> copy(bottomRight = point)
        3 -> copy(bottomLeft = point)
        else -> throw IndexOutOfBoundsException("Corner index $index")
    }

    /**
     * True if the corners form a convex, non-self-intersecting shape. Used to stop a dragged corner
     * from folding the outline over itself, which would produce a garbled perspective warp.
     * Coordinates are normalized, so the check is done on the raw values.
     */
    fun isConvex(): Boolean {
        val p = points
        var sign = 0
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % 4]
            val c = p[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            val s = if (cross > 0f) 1 else if (cross < 0f) -1 else return false
            if (sign == 0) sign = s else if (sign != s) return false
        }
        return true
    }

    /** Moves each corner [t] of the way towards [other]; used to smooth jitter between frames. */
    fun lerp(other: Quad, t: Float) = Quad(
        topLeft.lerp(other.topLeft, t),
        topRight.lerp(other.topRight, t),
        bottomRight.lerp(other.bottomRight, t),
        bottomLeft.lerp(other.bottomLeft, t),
    )

    /** Largest single-corner movement (in normalized units) between this quad and [other]. */
    fun maxDelta(other: Quad): Float =
        points.zip(other.points).fold(0f) { acc, (a, b) ->
            max(acc, max(abs(a.x - b.x), abs(a.y - b.y)))
        }
}
