package riemannsketchpad.geometry

import kotlin.math.*

class FreePoint(
    override val id: Int,
    override var x: Double,
    override var y: Double,
    override var z: Double
) : GeometricObject, PointLike {
    override val parentIds: List<Int> = emptyList()
    override var isDefined: Boolean = true
    override fun recompute(objects: Map<Int, GeometricObject>) {}
}

class FreePointOnLine(
    override val id: Int,
    val lineId: Int,
    var t: Double
) : GeometricObject, PointLike {
    override val parentIds: List<Int> = listOf(lineId)
    override var isDefined: Boolean = true
    override var x: Double = 0.0
    override var y: Double = 0.0
    override var z: Double = 0.0

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val line = objects[lineId]
        if (line == null || !line.isDefined || line !is GeodesicLike) {
            isDefined = false; return
        }
        isDefined = true
        // P(t) = P1 * cos(t) + (n x P1) * sin(t)
        val nx = line.normalX; val ny = line.normalY; val nz = line.normalZ
        val p1x = line.x1; val p1y = line.y1; val p1z = line.z1
        val bx = SphericalUtils.crossX(nx, ny, nz, p1x, p1y, p1z)
        val by = SphericalUtils.crossY(nx, ny, nz, p1x, p1y, p1z)
        val bz = SphericalUtils.crossZ(nx, ny, nz, p1x, p1y, p1z)
        val cosT = cos(t); val sinT = sin(t)
        x = p1x * cosT + bx * sinT
        y = p1y * cosT + by * sinT
        z = p1z * cosT + bz * sinT
    }

    fun updateParameterFromPosition(px: Double, py: Double, pz: Double, objects: Map<Int, GeometricObject>) {
        val line = objects[lineId] as? GeodesicLike ?: return
        val nx = line.normalX; val ny = line.normalY; val nz = line.normalZ
        val p1x = line.x1; val p1y = line.y1; val p1z = line.z1
        val bx = SphericalUtils.crossX(nx, ny, nz, p1x, p1y, p1z)
        val by = SphericalUtils.crossY(nx, ny, nz, p1x, p1y, p1z)
        val bz = SphericalUtils.crossZ(nx, ny, nz, p1x, p1y, p1z)
        // Project onto the frame: cos(t) component along P1, sin(t) component along b
        val cosComp = SphericalUtils.dot(px, py, pz, p1x, p1y, p1z)
        val sinComp = SphericalUtils.dot(px, py, pz, bx, by, bz)
        t = atan2(sinComp, cosComp)
        if (t < 0) t += 2 * PI
    }
}

class FreePointOnSegment(
    override val id: Int,
    val segmentId: Int,
    var t: Double
) : GeometricObject, PointLike {
    override val parentIds: List<Int> = listOf(segmentId)
    override var isDefined: Boolean = true
    override var x: Double = 0.0
    override var y: Double = 0.0
    override var z: Double = 0.0

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val seg = objects[segmentId]
        if (seg == null || !seg.isDefined || seg !is GeodesicLike) {
            isDefined = false; return
        }
        isDefined = true
        val (sx, sy, sz) = SphericalUtils.slerp(seg.x1, seg.y1, seg.z1, seg.x2, seg.y2, seg.z2, t)
        x = sx; y = sy; z = sz
    }

    fun updateParameterFromPosition(px: Double, py: Double, pz: Double, objects: Map<Int, GeometricObject>) {
        val seg = objects[segmentId] as? GeodesicLike ?: return
        // Find closest t in [0,1] by computing arc parameter
        val d = SphericalUtils.sphericalDistance(seg.x1, seg.y1, seg.z1, seg.x2, seg.y2, seg.z2)
        if (d < SphericalUtils.EPSILON) { t = 0.0; return }
        val dp = SphericalUtils.sphericalDistance(seg.x1, seg.y1, seg.z1, px, py, pz)
        t = (dp / d).coerceIn(0.0, 1.0)
    }
}

class FreePointOnCircle(
    override val id: Int,
    val circleId: Int,
    var theta: Double
) : GeometricObject, PointLike {
    override val parentIds: List<Int> = listOf(circleId)
    override var isDefined: Boolean = true
    override var x: Double = 0.0
    override var y: Double = 0.0
    override var z: Double = 0.0

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val circle = objects[circleId]
        if (circle == null || !circle.isDefined || circle !is Circle) {
            isDefined = false; return
        }
        isDefined = true
        // Build orthonormal frame at center
        val (e1x, e1y, e1z, e2x, e2y, e2z) = circleFrame(circle)
        val cosR = circle.cosRadius
        val sinR = sin(circle.sphericalRadius)
        val cosTheta = cos(theta); val sinTheta = sin(theta)
        x = circle.centerX * cosR + sinR * (e1x * cosTheta + e2x * sinTheta)
        y = circle.centerY * cosR + sinR * (e1y * cosTheta + e2y * sinTheta)
        z = circle.centerZ * cosR + sinR * (e1z * cosTheta + e2z * sinTheta)
    }

    fun updateParameterFromPosition(px: Double, py: Double, pz: Double, objects: Map<Int, GeometricObject>) {
        val circle = objects[circleId] as? Circle ?: return
        val (e1x, e1y, e1z, e2x, e2y, e2z) = circleFrame(circle)
        val comp1 = SphericalUtils.dot(px, py, pz, e1x, e1y, e1z)
        val comp2 = SphericalUtils.dot(px, py, pz, e2x, e2y, e2z)
        theta = atan2(comp2, comp1)
        if (theta < 0) theta += 2 * PI
    }

    companion object {
        fun circleFrame(circle: Circle): CircleFrame {
            val cx = circle.centerX; val cy = circle.centerY; val cz = circle.centerZ
            // Pick a vector not parallel to center
            val refX: Double; val refY: Double; val refZ: Double
            if (abs(cx) < 0.9) { refX = 1.0; refY = 0.0; refZ = 0.0 }
            else { refX = 0.0; refY = 1.0; refZ = 0.0 }
            // e1 = normalize(ref - (ref·c)*c)
            val d = SphericalUtils.dot(refX, refY, refZ, cx, cy, cz)
            val e1xr = refX - d * cx; val e1yr = refY - d * cy; val e1zr = refZ - d * cz
            val len1 = SphericalUtils.length(e1xr, e1yr, e1zr)
            val e1x = e1xr / len1; val e1y = e1yr / len1; val e1z = e1zr / len1
            // e2 = c x e1
            val e2x = SphericalUtils.crossX(cx, cy, cz, e1x, e1y, e1z)
            val e2y = SphericalUtils.crossY(cx, cy, cz, e1x, e1y, e1z)
            val e2z = SphericalUtils.crossZ(cx, cy, cz, e1x, e1y, e1z)
            return CircleFrame(e1x, e1y, e1z, e2x, e2y, e2z)
        }
    }
}

data class CircleFrame(
    val e1x: Double, val e1y: Double, val e1z: Double,
    val e2x: Double, val e2y: Double, val e2z: Double
)

class Midpoint(
    override val id: Int,
    val point1Id: Int,
    val point2Id: Int
) : GeometricObject, PointLike {
    override val parentIds: List<Int> = listOf(point1Id, point2Id)
    override var isDefined: Boolean = true
    override var x: Double = 0.0
    override var y: Double = 0.0
    override var z: Double = 0.0

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val p1 = objects[point1Id]
        val p2 = objects[point2Id]
        if (p1 == null || p2 == null || !p1.isDefined || !p2.isDefined || p1 !is PointLike || p2 !is PointLike) {
            isDefined = false; return
        }
        val m = SphericalUtils.midpoint(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z)
        if (m == null) { isDefined = false; return }
        isDefined = true
        x = m.first; y = m.second; z = m.third
    }
}

class ReflectionPoint(
    override val id: Int,
    val pointId: Int,
    val geodesicId: Int
) : GeometricObject, PointLike {
    override val parentIds: List<Int> = listOf(pointId, geodesicId)
    override var isDefined: Boolean = true
    override var x: Double = 0.0
    override var y: Double = 0.0
    override var z: Double = 0.0

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val pt = objects[pointId]
        val geo = objects[geodesicId]
        if (pt == null || geo == null || !pt.isDefined || !geo.isDefined || pt !is PointLike || geo !is GeodesicLike) {
            isDefined = false; return
        }
        isDefined = true
        val (rx, ry, rz) = SphericalUtils.reflectThroughGreatCircle(pt.x, pt.y, pt.z, geo.normalX, geo.normalY, geo.normalZ)
        x = rx; y = ry; z = rz
    }
}
