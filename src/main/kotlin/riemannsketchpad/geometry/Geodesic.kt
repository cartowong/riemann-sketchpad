package riemannsketchpad.geometry

import kotlin.math.*

class Line(
    override val id: Int,
    override val point1Id: Int,
    override val point2Id: Int
) : GeometricObject, GeodesicLike {
    override val parentIds: List<Int> = listOf(point1Id, point2Id)
    override var isDefined: Boolean = true
    override val geodesicType: GeodesicType = GeodesicType.LINE
    override var normalX: Double = 0.0
    override var normalY: Double = 0.0
    override var normalZ: Double = 0.0
    override var x1: Double = 0.0
    override var y1: Double = 0.0
    override var z1: Double = 0.0
    override var x2: Double = 0.0
    override var y2: Double = 0.0
    override var z2: Double = 0.0

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val p1 = objects[point1Id]
        val p2 = objects[point2Id]
        if (p1 == null || p2 == null || !p1.isDefined || !p2.isDefined || p1 !is PointLike || p2 !is PointLike) {
            isDefined = false; return
        }
        if (SphericalUtils.isAntipodal(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z)) {
            isDefined = false; return
        }
        val n = SphericalUtils.greatCircleNormal(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z)
        if (n == null) { isDefined = false; return }
        isDefined = true
        x1 = p1.x; y1 = p1.y; z1 = p1.z
        x2 = p2.x; y2 = p2.y; z2 = p2.z
        normalX = n.first; normalY = n.second; normalZ = n.third
    }
}

class Segment(
    override val id: Int,
    override val point1Id: Int,
    override val point2Id: Int
) : GeometricObject, GeodesicLike {
    override val parentIds: List<Int> = listOf(point1Id, point2Id)
    override var isDefined: Boolean = true
    override val geodesicType: GeodesicType = GeodesicType.SEGMENT
    override var normalX: Double = 0.0
    override var normalY: Double = 0.0
    override var normalZ: Double = 0.0
    override var x1: Double = 0.0
    override var y1: Double = 0.0
    override var z1: Double = 0.0
    override var x2: Double = 0.0
    override var y2: Double = 0.0
    override var z2: Double = 0.0

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val p1 = objects[point1Id]
        val p2 = objects[point2Id]
        if (p1 == null || p2 == null || !p1.isDefined || !p2.isDefined || p1 !is PointLike || p2 !is PointLike) {
            isDefined = false; return
        }
        if (SphericalUtils.isAntipodal(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z)) {
            isDefined = false; return
        }
        val n = SphericalUtils.greatCircleNormal(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z)
        if (n == null) { isDefined = false; return }
        isDefined = true
        x1 = p1.x; y1 = p1.y; z1 = p1.z
        x2 = p2.x; y2 = p2.y; z2 = p2.z
        normalX = n.first; normalY = n.second; normalZ = n.third
    }
}

class PerpendicularLine(
    override val id: Int,
    val pointId: Int,
    val geodesicId: Int
) : GeometricObject, GeodesicLike {
    override val parentIds: List<Int> = listOf(pointId, geodesicId)
    override var isDefined: Boolean = true
    override val geodesicType: GeodesicType = GeodesicType.LINE
    override var normalX: Double = 0.0
    override var normalY: Double = 0.0
    override var normalZ: Double = 0.0
    override var x1: Double = 0.0
    override var y1: Double = 0.0
    override var z1: Double = 0.0
    override var x2: Double = 0.0
    override var y2: Double = 0.0
    override var z2: Double = 0.0

    // For GeodesicLike interface — point1Id is the through-point, point2Id is the foot
    override val point1Id: Int get() = pointId
    override val point2Id: Int get() = geodesicId // Not a point, but satisfies the interface. The actual defining objects are pointId and geodesicId.

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val pt = objects[pointId]
        val geo = objects[geodesicId]
        if (pt == null || geo == null || !pt.isDefined || !geo.isDefined || pt !is PointLike || geo !is GeodesicLike) {
            isDefined = false; return
        }
        val foot = SphericalUtils.footOfPerpendicular(pt.x, pt.y, pt.z, geo.normalX, geo.normalY, geo.normalZ)
        if (foot == null) { isDefined = false; return } // P is a pole

        x1 = pt.x; y1 = pt.y; z1 = pt.z

        // If foot == point (P lies on the geodesic), use a point along the perpendicular direction
        val dist = SphericalUtils.sphericalDistance(pt.x, pt.y, pt.z, foot.first, foot.second, foot.third)
        if (dist < SphericalUtils.EPSILON) {
            // P is on the geodesic; the perpendicular direction is along the parent's normal projected onto the tangent plane at P
            // Actually, perpendicular to the geodesic at P is along n (the parent's normal) rotated into the tangent plane
            // The perpendicular great circle has normal = normalize(P x F), but F=P, so we need a different approach.
            // Use the parent geodesic's normal as the second point direction:
            // The perpendicular to geodesic g at point P (on g) passes through P and the pole of g.
            // But the pole of g is ±n. If P is not ±n (which we already checked), use n as second point direction.
            val n = SphericalUtils.greatCircleNormal(pt.x, pt.y, pt.z, geo.normalX, geo.normalY, geo.normalZ)
            if (n == null) { isDefined = false; return }
            isDefined = true
            x2 = geo.normalX; y2 = geo.normalY; z2 = geo.normalZ
            normalX = n.first; normalY = n.second; normalZ = n.third
        } else {
            val n = SphericalUtils.greatCircleNormal(pt.x, pt.y, pt.z, foot.first, foot.second, foot.third)
            if (n == null) { isDefined = false; return }
            isDefined = true
            x2 = foot.first; y2 = foot.second; z2 = foot.third
            normalX = n.first; normalY = n.second; normalZ = n.third
        }
    }
}
