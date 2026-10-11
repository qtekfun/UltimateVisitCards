package com.qtekfun.ultimatevisitcards.ocr.processing

import kotlin.math.abs

internal object Geometry {
    fun polygonArea(points: List<Point>): Double {
        var sum = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2
    }

    private fun cross(o: Point, a: Point, b: Point): Double = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

    /** Convex hull (Andrew's monotone chain), counter-clockwise in math orientation, without collinear points. */
    fun convexHull(points: List<Point>): List<Point> {
        val sorted = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
        if (sorted.size < 3) return sorted
        val lower = mutableListOf<Point>()
        for (p in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) lower.removeAt(lower.size - 1)
            lower += p
        }
        val upper = mutableListOf<Point>()
        for (p in sorted.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) upper.removeAt(upper.size - 1)
            upper += p
        }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    /**
     * Reduces a convex polygon to [target] vertices by repeatedly dropping the vertex whose triangle with its
     * neighbours has the smallest area (Visvalingam-Whyatt). On the hull of a card this keeps the corners.
     */
    fun simplify(polygon: List<Point>, target: Int): List<Point> {
        val points = polygon.toMutableList()
        while (points.size > target) {
            var smallest = -1
            var smallestArea = Double.MAX_VALUE
            for (i in points.indices) {
                val area = abs(cross(points[(i - 1 + points.size) % points.size], points[i], points[(i + 1) % points.size]))
                if (area < smallestArea) {
                    smallestArea = area
                    smallest = i
                }
            }
            points.removeAt(smallest)
        }
        return points
    }

    /** True if the quad is strictly convex (all turns in the same direction). */
    fun isConvex(quad: Quad): Boolean {
        val p = quad.points
        val signs = (0 until 4).map { i -> cross(p[i], p[(i + 1) % 4], p[(i + 2) % 4]) }
        return signs.all { it > 0 } || signs.all { it < 0 }
    }
}
