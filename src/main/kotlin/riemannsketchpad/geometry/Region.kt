package riemannsketchpad.geometry

import kotlin.math.*

class Region(
    override val id: Int,
    val pointIds: List<Int>
) : GeometricObject {
    override val parentIds: List<Int> = pointIds
    override var isDefined: Boolean = true

    var vertices: List<Triple<Double, Double, Double>> = emptyList()

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val pts = pointIds.map { objects[it] }
        if (pts.any { it == null || !it.isDefined || it !is PointLike }) {
            isDefined = false; return
        }
        isDefined = true
        vertices = pts.map {
            val p = it as PointLike
            Triple(p.x, p.y, p.z)
        }
    }

    /**
     * Spherical area via spherical excess: sum of interior angles - (n-2)*pi.
     * Each interior angle is computed at each vertex from the tangent vectors of adjacent edges.
     */
    fun measureArea(): Double {
        val n = vertices.size
        if (n < 3) return 0.0

        // Compute the interior angle at each vertex using unsigned angle (acos).
        // This gives the correct result for convex polygons. For non-convex polygons
        // on the sphere, more sophisticated orientation detection would be needed.
        var angleSum = 0.0
        for (i in 0 until n) {
            val prev = vertices[(i + n - 1) % n]
            val curr = vertices[i]
            val next = vertices[(i + 1) % n]
            val tPrev = SphericalUtils.tangentVector(curr.first, curr.second, curr.third, prev.first, prev.second, prev.third)
            val tNext = SphericalUtils.tangentVector(curr.first, curr.second, curr.third, next.first, next.second, next.third)
            if (tPrev == null || tNext == null) return 0.0

            val dotT = SphericalUtils.dot(tPrev.first, tPrev.second, tPrev.third, tNext.first, tNext.second, tNext.third)
                .coerceIn(-1.0, 1.0)
            angleSum += acos(dotT)
        }
        val excess = angleSum - (n - 2) * PI
        return abs(excess)
    }
}
