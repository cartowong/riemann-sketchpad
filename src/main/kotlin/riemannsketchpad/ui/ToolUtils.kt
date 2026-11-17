package riemannsketchpad.ui

import riemannsketchpad.geometry.*
import riemannsketchpad.model.GeometricObjectManager
import riemannsketchpad.model.ObjectMetadata
import riemannsketchpad.model.StyleSettings
import kotlin.math.*

object ToolUtils {

    /**
     * Resolve a hit result to an existing or newly created point.
     * Returns the point ID.
     */
    fun resolveOrCreatePoint(
        hitResult: HitResult?,
        worldX: Double, worldY: Double, worldZ: Double,
        manager: GeometricObjectManager,
        hitTester: HitTester,
        styleSettings: StyleSettings
    ): Int {
        return when (hitResult) {
            is HitResult.ConstructedPoint -> hitResult.id
            is HitResult.IntersectionPreview -> constructIntersection(hitResult.candidate, manager, styleSettings)
            is HitResult.OnGeodesic -> {
                val obj = manager.getObject(hitResult.id)
                if (obj is GeodesicLike) {
                    createPointOnGeodesic(obj, hitResult.id, worldX, worldY, worldZ, manager, styleSettings)
                } else {
                    createFreePoint(worldX, worldY, worldZ, manager, styleSettings)
                }
            }
            is HitResult.OnCircle -> {
                val obj = manager.getObject(hitResult.id)
                if (obj is Circle) {
                    createPointOnCircle(obj, hitResult.id, worldX, worldY, worldZ, manager, styleSettings)
                } else {
                    createFreePoint(worldX, worldY, worldZ, manager, styleSettings)
                }
            }
            else -> createFreePoint(worldX, worldY, worldZ, manager, styleSettings)
        }
    }

    fun constructIntersection(
        candidate: IntersectionCandidate,
        manager: GeometricObjectManager,
        styleSettings: StyleSettings
    ): Int {
        val nextId = manager.peekNextId()
        val meta = ObjectMetadata()
        val obj: GeometricObject = when (candidate.type) {
            "GeodesicGeodesic" -> GeodesicGeodesicIntersection(nextId, candidate.obj1Id, candidate.obj2Id, candidate.sign)
            "GeodesicCircle" -> GeodesicCircleIntersection(nextId, candidate.obj1Id, candidate.obj2Id, candidate.sign)
            "CircleCircle" -> CircleCircleIntersection(nextId, candidate.obj1Id, candidate.obj2Id, candidate.sign)
            else -> throw IllegalArgumentException("Unknown intersection type: ${candidate.type}")
        }
        styleSettings.applyDefaults(meta, obj)
        manager.addObject(obj, meta)
        obj.recompute(manager.getObjects())
        return nextId
    }

    private fun createFreePoint(
        worldX: Double, worldY: Double, worldZ: Double,
        manager: GeometricObjectManager,
        styleSettings: StyleSettings
    ): Int {
        val (nx, ny, nz) = SphericalUtils.normalizeToSphere(worldX, worldY, worldZ)
        val nextId = manager.peekNextId()
        val point = FreePoint(nextId, nx, ny, nz)
        val meta = ObjectMetadata()
        styleSettings.applyDefaults(meta, point)
        manager.addObject(point, meta)
        return nextId
    }

    private fun createPointOnGeodesic(
        geo: GeodesicLike,
        geoId: Int,
        worldX: Double, worldY: Double, worldZ: Double,
        manager: GeometricObjectManager,
        styleSettings: StyleSettings
    ): Int {
        val nextId = manager.peekNextId()
        val obj: GeometricObject
        if (geo.geodesicType == GeodesicType.SEGMENT) {
            // Compute t parameter
            val d = SphericalUtils.sphericalDistance(geo.x1, geo.y1, geo.z1, geo.x2, geo.y2, geo.z2)
            val dp = SphericalUtils.sphericalDistance(geo.x1, geo.y1, geo.z1, worldX, worldY, worldZ)
            val t = if (d < SphericalUtils.EPSILON) 0.5 else (dp / d).coerceIn(0.0, 1.0)
            obj = FreePointOnSegment(nextId, geoId, t)
        } else {
            // Line: compute angular parameter
            val nx = geo.normalX; val ny = geo.normalY; val nz = geo.normalZ
            val p1x = geo.x1; val p1y = geo.y1; val p1z = geo.z1
            val bx = SphericalUtils.crossX(nx, ny, nz, p1x, p1y, p1z)
            val by = SphericalUtils.crossY(nx, ny, nz, p1x, p1y, p1z)
            val bz = SphericalUtils.crossZ(nx, ny, nz, p1x, p1y, p1z)
            val cosComp = SphericalUtils.dot(worldX, worldY, worldZ, p1x, p1y, p1z)
            val sinComp = SphericalUtils.dot(worldX, worldY, worldZ, bx, by, bz)
            var t = atan2(sinComp, cosComp)
            if (t < 0) t += 2 * PI
            obj = FreePointOnLine(nextId, geoId, t)
        }
        val meta = ObjectMetadata()
        styleSettings.applyDefaults(meta, obj)
        manager.addObject(obj, meta)
        obj.recompute(manager.getObjects())
        return nextId
    }

    private fun createPointOnCircle(
        circle: Circle,
        circleId: Int,
        worldX: Double, worldY: Double, worldZ: Double,
        manager: GeometricObjectManager,
        styleSettings: StyleSettings
    ): Int {
        val nextId = manager.peekNextId()
        val frame = FreePointOnCircle.circleFrame(circle)
        val comp1 = SphericalUtils.dot(worldX, worldY, worldZ, frame.e1x, frame.e1y, frame.e1z)
        val comp2 = SphericalUtils.dot(worldX, worldY, worldZ, frame.e2x, frame.e2y, frame.e2z)
        var theta = atan2(comp2, comp1)
        if (theta < 0) theta += 2 * PI
        val obj = FreePointOnCircle(nextId, circleId, theta)
        val meta = ObjectMetadata()
        styleSettings.applyDefaults(meta, obj)
        manager.addObject(obj, meta)
        obj.recompute(manager.getObjects())
        return nextId
    }
}
