package riemannsketchpad.geometry

import kotlin.math.*

class Circle(
    override val id: Int,
    val centerId: Int,
    val radiusPointId: Int
) : GeometricObject {
    override val parentIds: List<Int> = listOf(centerId, radiusPointId)
    override var isDefined: Boolean = true

    var centerX: Double = 0.0
    var centerY: Double = 0.0
    var centerZ: Double = 0.0
    var sphericalRadius: Double = 0.0
    var cosRadius: Double = 0.0

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val c = objects[centerId]
        val r = objects[radiusPointId]
        if (c == null || r == null || !c.isDefined || !r.isDefined || c !is PointLike || r !is PointLike) {
            isDefined = false; return
        }
        val dot = SphericalUtils.dot(c.x, c.y, c.z, r.x, r.y, r.z).coerceIn(-1.0, 1.0)
        if (dot < -1.0 + SphericalUtils.EPSILON) {
            isDefined = false; return // antipodal
        }
        isDefined = true
        centerX = c.x; centerY = c.y; centerZ = c.z
        cosRadius = dot
        sphericalRadius = acos(dot)
    }
}
