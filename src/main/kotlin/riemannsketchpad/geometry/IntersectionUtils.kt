package riemannsketchpad.geometry

import kotlin.math.*

object IntersectionUtils {
    const val EPSILON = 1e-10

    /**
     * Intersection of two great circles with normals n1 and n2.
     * Returns 0 or 2 candidate points (antipodal pair).
     */
    fun greatCircleGreatCircleIntersection(
        n1x: Double, n1y: Double, n1z: Double,
        n2x: Double, n2y: Double, n2z: Double
    ): List<Triple<Double, Double, Double>> {
        val vx = SphericalUtils.crossX(n1x, n1y, n1z, n2x, n2y, n2z)
        val vy = SphericalUtils.crossY(n1x, n1y, n1z, n2x, n2y, n2z)
        val vz = SphericalUtils.crossZ(n1x, n1y, n1z, n2x, n2y, n2z)
        val len = SphericalUtils.length(vx, vy, vz)
        if (len < EPSILON) return emptyList() // coincident circles
        val nx = vx / len; val ny = vy / len; val nz = vz / len
        return listOf(Triple(nx, ny, nz), Triple(-nx, -ny, -nz))
    }

    /**
     * Intersection of a great circle (normal n, n·P=0) with a small circle (center c, c·P=cosR).
     * Returns 0 or 2 candidate points.
     */
    fun greatCircleSmallCircleIntersection(
        nx: Double, ny: Double, nz: Double,
        cx: Double, cy: Double, cz: Double,
        cosR: Double
    ): List<Triple<Double, Double, Double>> {
        return twoPlanesSphereIntersection(nx, ny, nz, 0.0, cx, cy, cz, cosR)
    }

    /**
     * Intersection of two small circles: c1·P=cosR1 and c2·P=cosR2.
     * Returns 0 or 2 candidate points.
     */
    fun smallCircleSmallCircleIntersection(
        c1x: Double, c1y: Double, c1z: Double, cosR1: Double,
        c2x: Double, c2y: Double, c2z: Double, cosR2: Double
    ): List<Triple<Double, Double, Double>> {
        return twoPlanesSphereIntersection(c1x, c1y, c1z, cosR1, c2x, c2y, c2z, cosR2)
    }

    /**
     * Find points on the unit sphere satisfying two plane equations:
     * a·P = d1 and b·P = d2.
     *
     * The line of intersection of the two planes is parameterized, then intersected with |P|=1.
     */
    private fun twoPlanesSphereIntersection(
        ax: Double, ay: Double, az: Double, d1: Double,
        bx: Double, by: Double, bz: Double, d2: Double
    ): List<Triple<Double, Double, Double>> {
        // Direction of the line of intersection of the two planes
        val dx = SphericalUtils.crossX(ax, ay, az, bx, by, bz)
        val dy = SphericalUtils.crossY(ax, ay, az, bx, by, bz)
        val dz = SphericalUtils.crossZ(ax, ay, az, bx, by, bz)
        val dLen2 = dx * dx + dy * dy + dz * dz
        if (dLen2 < EPSILON * EPSILON) return emptyList() // parallel planes

        // Find a particular point on the line of intersection using the formula:
        // p0 = (d1*(b x d) + d2*(d x a)) / |d|^2
        // where d = a x b (the direction)
        // Equivalently, solve: a·p0 = d1, b·p0 = d2, and p0 is in the span of a and b.
        val aDotB = SphericalUtils.dot(ax, ay, az, bx, by, bz)
        val aDotA = SphericalUtils.dot(ax, ay, az, ax, ay, az)
        val bDotB = SphericalUtils.dot(bx, by, bz, bx, by, bz)
        val det = aDotA * bDotB - aDotB * aDotB
        if (abs(det) < EPSILON) return emptyList()

        val lambda = (d1 * bDotB - d2 * aDotB) / det
        val mu = (d2 * aDotA - d1 * aDotB) / det
        val p0x = lambda * ax + mu * bx
        val p0y = lambda * ay + mu * by
        val p0z = lambda * az + mu * bz

        // Line: P = p0 + t * d, intersect with |P|^2 = 1
        // |p0 + t*d|^2 = 1
        // |p0|^2 + 2t*(p0·d) + t^2*|d|^2 = 1
        // p0·d should be 0 since p0 is in span{a,b} and d = a x b
        val p0Dot = p0x * p0x + p0y * p0y + p0z * p0z
        val p0DotD = p0x * dx + p0y * dy + p0z * dz

        val A = dLen2
        val B = 2.0 * p0DotD
        val C = p0Dot - 1.0
        val disc = B * B - 4 * A * C
        if (disc < -EPSILON) return emptyList()
        if (disc < 0) return emptyList() // tangent case — skip for robustness

        val sqrtDisc = sqrt(disc.coerceAtLeast(0.0))
        val t1 = (-B + sqrtDisc) / (2.0 * A)
        val t2 = (-B - sqrtDisc) / (2.0 * A)

        val r1 = Triple(p0x + t1 * dx, p0y + t1 * dy, p0z + t1 * dz)
        val r2 = Triple(p0x + t2 * dx, p0y + t2 * dy, p0z + t2 * dz)
        return listOf(r1, r2)
    }

    /**
     * Check if a candidate point lies on the shorter arc from P1 to P2 on a great circle.
     */
    fun isOnSegmentArc(
        px: Double, py: Double, pz: Double,
        p1x: Double, p1y: Double, p1z: Double,
        p2x: Double, p2y: Double, p2z: Double,
        normalX: Double, normalY: Double, normalZ: Double
    ): Boolean {
        // The shorter arc from P1 to P2: a point Q is on it if the arc from P1 through Q to P2
        // equals the arc from P1 to P2. Equivalently:
        // d(P1, Q) + d(Q, P2) ≈ d(P1, P2)
        val d12 = SphericalUtils.sphericalDistance(p1x, p1y, p1z, p2x, p2y, p2z)
        val d1p = SphericalUtils.sphericalDistance(p1x, p1y, p1z, px, py, pz)
        val dp2 = SphericalUtils.sphericalDistance(px, py, pz, p2x, p2y, p2z)
        return abs(d1p + dp2 - d12) < 1e-6
    }

    /**
     * Assign sign (+1 or -1) to each candidate point based on a reference direction.
     * The reference direction is computed from the parent objects at construction time.
     * Returns (+1 point, -1 point) if there are two candidates, maintaining a canonical ordering.
     */
    fun assignSign(
        candidates: List<Triple<Double, Double, Double>>,
        refDirX: Double, refDirY: Double, refDirZ: Double
    ): Map<Int, Triple<Double, Double, Double>> {
        if (candidates.size != 2) return emptyMap()
        val (c1, c2) = candidates
        val dot1 = SphericalUtils.dot(c1.first, c1.second, c1.third, refDirX, refDirY, refDirZ)
        return if (dot1 >= 0) {
            mapOf(1 to c1, -1 to c2)
        } else {
            mapOf(1 to c2, -1 to c1)
        }
    }

    /**
     * Compute a stable reference direction for geodesic-geodesic intersection sign assignment.
     * Uses n1 x n2 direction (always perpendicular to both normals).
     */
    fun geodesicGeodesicRefDirection(
        n1x: Double, n1y: Double, n1z: Double,
        n2x: Double, n2y: Double, n2z: Double
    ): Triple<Double, Double, Double> {
        val vx = SphericalUtils.crossX(n1x, n1y, n1z, n2x, n2y, n2z)
        val vy = SphericalUtils.crossY(n1x, n1y, n1z, n2x, n2y, n2z)
        val vz = SphericalUtils.crossZ(n1x, n1y, n1z, n2x, n2y, n2z)
        val len = SphericalUtils.length(vx, vy, vz)
        if (len < EPSILON) return Triple(1.0, 0.0, 0.0) // fallback
        return Triple(vx / len, vy / len, vz / len)
    }
}
