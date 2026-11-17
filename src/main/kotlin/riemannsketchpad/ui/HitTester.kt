package riemannsketchpad.ui

import riemannsketchpad.geometry.*
import riemannsketchpad.model.GeometricObjectManager
import riemannsketchpad.model.ObjectMetadata
import kotlin.math.*

data class IntersectionCandidate(
    val obj1Id: Int,
    val obj2Id: Int,
    val sign: Int,
    val screenX: Double,
    val screenY: Double,
    val type: String // "GeodesicGeodesic", "GeodesicCircle", "CircleCircle"
)

sealed class HitResult {
    data class ConstructedPoint(val id: Int) : HitResult()
    data class OnAngle(val id: Int) : HitResult()
    data class IntersectionPreview(val candidate: IntersectionCandidate) : HitResult()
    data class OnGeodesic(val id: Int) : HitResult()
    data class OnCircle(val id: Int) : HitResult()
    data class OnRegion(val id: Int) : HitResult()
}

class HitTester(private val manager: GeometricObjectManager) {

    companion object {
        const val SNAP_RADIUS = 10.0 // screen pixels
        private const val ELLIPSE_SAMPLES = 100
    }

    private var intersectionCache: List<IntersectionCandidate> = emptyList()

    var viewMatrix: DoubleArray = SphericalUtils.viewMatrix(0.0, 0.0)
    var displayScale: Double = 1.0
    var diskCenterX: Double = 0.0
    var diskCenterY: Double = 0.0

    fun rebuildIntersectionCache() {
        val candidates = mutableListOf<IntersectionCandidate>()
        val objects = manager.getObjects()
        val metadata = manager.getMetadata()

        // Collect visible, non-locked geodesics and circles on front hemisphere
        val geodesics = mutableListOf<Pair<Int, GeodesicLike>>()
        val circles = mutableListOf<Pair<Int, Circle>>()

        for ((id, obj) in objects) {
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked) continue
            if (!obj.isDefined) continue
            when {
                obj is GeodesicLike && obj is GeometricObject -> geodesics.add(id to obj)
                obj is Circle -> circles.add(id to obj)
            }
        }

        // Geodesic-Geodesic intersections
        for (i in geodesics.indices) {
            for (j in i + 1 until geodesics.size) {
                val (id1, g1) = geodesics[i]
                val (id2, g2) = geodesics[j]
                addGeodesicGeodesicCandidates(candidates, id1, g1, id2, g2)
            }
        }

        // Geodesic-Circle intersections
        for ((gId, geo) in geodesics) {
            for ((cId, circle) in circles) {
                addGeodesicCircleCandidates(candidates, gId, geo, cId, circle)
            }
        }

        // Circle-Circle intersections
        for (i in circles.indices) {
            for (j in i + 1 until circles.size) {
                val (id1, c1) = circles[i]
                val (id2, c2) = circles[j]
                addCircleCircleCandidates(candidates, id1, c1, id2, c2)
            }
        }

        // Filter out candidates that are already constructed
        val existingIntersections = mutableSetOf<Triple<Int, Int, Int>>() // (obj1, obj2, sign)
        for ((_, obj) in objects) {
            when (obj) {
                is GeodesicGeodesicIntersection -> existingIntersections.add(Triple(obj.geodesic1Id, obj.geodesic2Id, obj.sign))
                is GeodesicCircleIntersection -> existingIntersections.add(Triple(obj.geodesicId, obj.circleId, obj.sign))
                is CircleCircleIntersection -> existingIntersections.add(Triple(obj.circle1Id, obj.circle2Id, obj.sign))
                else -> {}
            }
        }

        intersectionCache = candidates.filter { c ->
            Triple(c.obj1Id, c.obj2Id, c.sign) !in existingIntersections
        }
    }

    fun getIntersectionCache(): List<IntersectionCandidate> = intersectionCache

    fun hitTest(mouseScreenX: Double, mouseScreenY: Double): HitResult? {
        val objects = manager.getObjects()
        val metadata = manager.getMetadata()

        // Tier 1: Constructed points
        var bestPointDist = SNAP_RADIUS
        var bestPointId: Int? = null
        for ((id, obj) in objects) {
            if (obj !is PointLike) continue
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked) continue
            if (!SphericalUtils.isOnFrontHemisphere(obj.x, obj.y, obj.z, viewMatrix)) continue
            if (!(obj as GeometricObject).isDefined) continue
            val (px, py, _) = SphericalUtils.projectToScreen(obj.x, obj.y, obj.z, viewMatrix)
            val sx = diskCenterX + displayScale * px
            val sy = diskCenterY - displayScale * py
            val dist = hypot(mouseScreenX - sx, mouseScreenY - sy)
            if (dist < bestPointDist) {
                bestPointDist = dist
                bestPointId = id
            }
        }
        if (bestPointId != null) return HitResult.ConstructedPoint(bestPointId)

        // Tier 2: Angles (point in arc sector)
        for ((id, obj) in objects) {
            if (obj !is Angle) continue
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked || !obj.isDefined) continue
            if (!SphericalUtils.isOnFrontHemisphere(obj.vertexX, obj.vertexY, obj.vertexZ, viewMatrix)) continue
            if (isPointInAngleSector(mouseScreenX, mouseScreenY, obj, meta)) {
                return HitResult.OnAngle(id)
            }
        }

        // Tier 3: Intersection previews
        var bestIntDist = SNAP_RADIUS
        var bestIntCandidate: IntersectionCandidate? = null
        for (candidate in intersectionCache) {
            val dist = hypot(mouseScreenX - candidate.screenX, mouseScreenY - candidate.screenY)
            if (dist < bestIntDist) {
                bestIntDist = dist
                bestIntCandidate = candidate
            }
        }
        if (bestIntCandidate != null) return HitResult.IntersectionPreview(bestIntCandidate)

        // Tier 4: Geodesics
        var bestGeoDist = SNAP_RADIUS
        var bestGeoId: Int? = null
        for ((id, obj) in objects) {
            if (obj !is GeodesicLike || obj !is GeometricObject) continue
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked || !obj.isDefined) continue
            val dist = distanceToGeodesic(mouseScreenX, mouseScreenY, obj)
            if (dist < bestGeoDist) {
                bestGeoDist = dist
                bestGeoId = id
            }
        }
        if (bestGeoId != null) return HitResult.OnGeodesic(bestGeoId)

        // Tier 5: Circles
        var bestCircDist = SNAP_RADIUS
        var bestCircId: Int? = null
        for ((id, obj) in objects) {
            if (obj !is Circle) continue
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked || !obj.isDefined) continue
            val dist = distanceToCircle(mouseScreenX, mouseScreenY, obj)
            if (dist < bestCircDist) {
                bestCircDist = dist
                bestCircId = id
            }
        }
        if (bestCircId != null) return HitResult.OnCircle(bestCircId)

        // Tier 6: Regions
        for ((id, obj) in objects) {
            if (obj !is Region) continue
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked || !obj.isDefined) continue
            if (!SphericalUtils.isOnFrontHemisphere(
                    obj.vertices.firstOrNull()?.first ?: continue,
                    obj.vertices.first().second,
                    obj.vertices.first().third,
                    viewMatrix
                )
            ) continue
            if (isPointInRegion(mouseScreenX, mouseScreenY, obj)) {
                return HitResult.OnRegion(id)
            }
        }

        return null
    }

    fun hitTestFiltered(mouseScreenX: Double, mouseScreenY: Double, expectedType: String): HitResult? {
        val objects = manager.getObjects()
        val metadata = manager.getMetadata()

        for ((id, obj) in objects) {
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked || !(obj as GeometricObject).isDefined) continue

            val matches = when (expectedType) {
                "Point" -> obj is PointLike
                "Geodesic" -> obj is GeodesicLike
                "Line" -> obj is Line
                "Segment" -> obj is Segment
                "PerpendicularLine" -> obj is PerpendicularLine
                "Circle" -> obj is Circle
                else -> false
            }
            if (!matches) continue

            when {
                obj is PointLike -> {
                    if (!SphericalUtils.isOnFrontHemisphere(obj.x, obj.y, obj.z, viewMatrix)) continue
                    val (px, py, _) = SphericalUtils.projectToScreen(obj.x, obj.y, obj.z, viewMatrix)
                    val sx = diskCenterX + displayScale * px
                    val sy = diskCenterY - displayScale * py
                    if (hypot(mouseScreenX - sx, mouseScreenY - sy) < SNAP_RADIUS) {
                        return HitResult.ConstructedPoint(id)
                    }
                }
                obj is GeodesicLike -> {
                    if (distanceToGeodesic(mouseScreenX, mouseScreenY, obj) < SNAP_RADIUS) {
                        return HitResult.OnGeodesic(id)
                    }
                }
                obj is Circle -> {
                    if (distanceToCircle(mouseScreenX, mouseScreenY, obj) < SNAP_RADIUS) {
                        return HitResult.OnCircle(id)
                    }
                }
            }
        }
        return null
    }

    // --- Distance Computations ---

    /**
     * Computes an orthonormal basis (u, v) for the plane perpendicular to the given normal vector.
     */
    private fun circlePlaneBasis(nx: Double, ny: Double, nz: Double): Pair<Triple<Double, Double, Double>, Triple<Double, Double, Double>> {
        // Pick a vector not parallel to n
        val seed = if (abs(nx) < 0.9) Triple(1.0, 0.0, 0.0) else Triple(0.0, 1.0, 0.0)
        // u = normalize(seed × n)
        var ux = seed.second * nz - seed.third * ny
        var uy = seed.third * nx - seed.first * nz
        var uz = seed.first * ny - seed.second * nx
        val uLen = sqrt(ux * ux + uy * uy + uz * uz)
        ux /= uLen; uy /= uLen; uz /= uLen
        // v = n × u
        val vx = ny * uz - nz * uy
        val vy = nz * ux - nx * uz
        val vz = nx * uy - ny * ux
        return Pair(Triple(ux, uy, uz), Triple(vx, vy, vz))
    }

    private fun distanceToGeodesic(mx: Double, my: Double, geo: GeodesicLike): Double {
        val (u, v) = circlePlaneBasis(geo.normalX, geo.normalY, geo.normalZ)

        var startT = 0.0
        var endT = 2 * PI

        if (geo.geodesicType == GeodesicType.SEGMENT) {
            // Compute parameter t for each endpoint: P(t) = cos(t)*u + sin(t)*v
            val t1 = atan2(
                geo.x1 * v.first + geo.y1 * v.second + geo.z1 * v.third,
                geo.x1 * u.first + geo.y1 * u.second + geo.z1 * u.third
            )
            val t2 = atan2(
                geo.x2 * v.first + geo.y2 * v.second + geo.z2 * v.third,
                geo.x2 * u.first + geo.y2 * u.second + geo.z2 * u.third
            )
            // Use midpoint to determine which arc
            val (midX, midY, midZ) = SphericalUtils.slerp(geo.x1, geo.y1, geo.z1, geo.x2, geo.y2, geo.z2, 0.5)
            val tM = atan2(
                midX * v.first + midY * v.second + midZ * v.third,
                midX * u.first + midY * u.second + midZ * u.third
            )

            val diff = normalizeAngle(t2 - t1)
            val diffM = normalizeAngle(tM - t1)
            if (diffM <= diff) {
                startT = t1
                endT = t1 + diff
            } else {
                startT = t2
                endT = t2 + (2 * PI - diff)
            }
        }

        var minDist = Double.MAX_VALUE
        for (i in 0..ELLIPSE_SAMPLES) {
            val t = startT + (endT - startT) * i / ELLIPSE_SAMPLES
            val cosT = cos(t)
            val sinT = sin(t)
            val px = cosT * u.first + sinT * v.first
            val py = cosT * u.second + sinT * v.second
            val pz = cosT * u.third + sinT * v.third

            if (!SphericalUtils.isOnFrontHemisphere(px, py, pz, viewMatrix)) continue

            val (sx, sy, _) = SphericalUtils.projectToScreen(px, py, pz, viewMatrix)
            val screenX = diskCenterX + displayScale * sx
            val screenY = diskCenterY - displayScale * sy
            val dist = hypot(mx - screenX, my - screenY)
            if (dist < minDist) minDist = dist
        }

        return minDist
    }

    private fun distanceToCircle(mx: Double, my: Double, circle: Circle): Double {
        val cosR = circle.cosRadius
        val sinR = sqrt((1.0 - cosR * cosR).coerceAtLeast(0.0))
        val (u, v) = circlePlaneBasis(circle.centerX, circle.centerY, circle.centerZ)

        var minDist = Double.MAX_VALUE
        for (i in 0..ELLIPSE_SAMPLES) {
            val t = 2 * PI * i / ELLIPSE_SAMPLES
            val cosT = cos(t)
            val sinT = sin(t)
            val px = cosR * circle.centerX + sinR * (cosT * u.first + sinT * v.first)
            val py = cosR * circle.centerY + sinR * (cosT * u.second + sinT * v.second)
            val pz = cosR * circle.centerZ + sinR * (cosT * u.third + sinT * v.third)

            if (!SphericalUtils.isOnFrontHemisphere(px, py, pz, viewMatrix)) continue

            val (sx, sy, _) = SphericalUtils.projectToScreen(px, py, pz, viewMatrix)
            val screenX = diskCenterX + displayScale * sx
            val screenY = diskCenterY - displayScale * sy
            val dist = hypot(mx - screenX, my - screenY)
            if (dist < minDist) minDist = dist
        }

        return minDist
    }

    private fun normalizeAngle(a: Double): Double {
        var r = a
        while (r < 0) r += 2 * PI
        while (r >= 2 * PI) r -= 2 * PI
        return r
    }

    // --- Angle hit testing ---

    private fun isPointInAngleSector(mx: Double, my: Double, angle: Angle, meta: ObjectMetadata): Boolean {
        val (vx, vy, _) = SphericalUtils.projectToScreen(angle.vertexX, angle.vertexY, angle.vertexZ, viewMatrix)
        val svx = diskCenterX + displayScale * vx
        val svy = diskCenterY - displayScale * vy

        val arcRadius = riemannsketchpad.model.StyleUtils.angleArcRadius(meta.size)
        val dist = hypot(mx - svx, my - svy)
        if (dist > arcRadius) return false

        // Check angle from vertex to mouse (screen-Y-down convention to match tangent angles)
        val mouseAngle = atan2(my - svy, mx - svx)

        // Project tangent vectors to get start/end angles
        val eps = 0.001
        val qAx = angle.vertexX + eps * angle.tangentAX
        val qAy = angle.vertexY + eps * angle.tangentAY
        val qAz = angle.vertexZ + eps * angle.tangentAZ
        val (sAx, sAy, _) = SphericalUtils.matVec(viewMatrix, qAx, qAy, qAz)
        val (svxm, svym, _) = SphericalUtils.matVec(viewMatrix, angle.vertexX, angle.vertexY, angle.vertexZ)
        val tAsx = sAx - svxm
        val tAsy = sAy - svym

        val qCx = angle.vertexX + eps * angle.tangentCX
        val qCy = angle.vertexY + eps * angle.tangentCY
        val qCz = angle.vertexZ + eps * angle.tangentCZ
        val (sCx, sCy, _) = SphericalUtils.matVec(viewMatrix, qCx, qCy, qCz)
        val tCsx = sCx - svxm
        val tCsy = sCy - svym

        val startAngle = atan2(-tAsy, tAsx)
        val endAngle = atan2(-tCsy, tCsx)

        // Determine sweep direction from 2D cross product of projected tangents
        val cross2D = tAsx * (-tCsy) - (-tAsy) * tCsx
        var sweep = endAngle - startAngle
        if (cross2D > 0 && sweep < 0) sweep += 2 * PI
        if (cross2D < 0 && sweep > 0) sweep -= 2 * PI
        // Match sweep magnitude to the 3D orientation-dependent angle
        if ((angle.measureRadians > PI) != (abs(sweep) > PI)) {
            sweep = if (sweep > 0) sweep - 2 * PI else sweep + 2 * PI
        }

        var diffMouse = mouseAngle - startAngle
        if (sweep > 0) {
            while (diffMouse < 0) diffMouse += 2 * PI
            return diffMouse <= sweep
        } else {
            while (diffMouse > 0) diffMouse -= 2 * PI
            return diffMouse >= sweep
        }
    }

    // --- Region hit testing ---

    private fun isPointInRegion(mx: Double, my: Double, region: Region): Boolean {
        // Point-in-polygon test on projected vertices
        val projected = region.vertices.map { (vx, vy, vz) ->
            val (px, py, _) = SphericalUtils.projectToScreen(vx, vy, vz, viewMatrix)
            Pair(diskCenterX + displayScale * px, diskCenterY - displayScale * py)
        }

        val n = projected.size
        var inside = false
        var j = n - 1
        for (i in 0 until n) {
            val (xi, yi) = projected[i]
            val (xj, yj) = projected[j]
            if ((yi > my) != (yj > my) && mx < (xj - xi) * (my - yi) / (yj - yi) + xi) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    // --- Intersection candidate computation ---

    private fun addGeodesicGeodesicCandidates(
        candidates: MutableList<IntersectionCandidate>,
        id1: Int, g1: GeodesicLike, id2: Int, g2: GeodesicLike
    ) {
        val results = IntersectionUtils.greatCircleGreatCircleIntersection(
            g1.normalX, g1.normalY, g1.normalZ,
            g2.normalX, g2.normalY, g2.normalZ
        )
        if (results.isEmpty()) return

        val refDir = IntersectionUtils.geodesicGeodesicRefDirection(
            g1.normalX, g1.normalY, g1.normalZ,
            g2.normalX, g2.normalY, g2.normalZ
        )
        val signMap = IntersectionUtils.assignSign(results, refDir.first, refDir.second, refDir.third)

        for ((sign, pt) in signMap) {
            // Check segment arc constraints
            if (g1.geodesicType == GeodesicType.SEGMENT) {
                if (!IntersectionUtils.isOnSegmentArc(pt.first, pt.second, pt.third,
                        g1.x1, g1.y1, g1.z1, g1.x2, g1.y2, g1.z2,
                        g1.normalX, g1.normalY, g1.normalZ)) continue
            }
            if (g2.geodesicType == GeodesicType.SEGMENT) {
                if (!IntersectionUtils.isOnSegmentArc(pt.first, pt.second, pt.third,
                        g2.x1, g2.y1, g2.z1, g2.x2, g2.y2, g2.z2,
                        g2.normalX, g2.normalY, g2.normalZ)) continue
            }
            // Front hemisphere check
            if (!SphericalUtils.isOnFrontHemisphere(pt.first, pt.second, pt.third, viewMatrix)) continue
            val (px, py, _) = SphericalUtils.projectToScreen(pt.first, pt.second, pt.third, viewMatrix)
            val sx = diskCenterX + displayScale * px
            val sy = diskCenterY - displayScale * py
            candidates.add(IntersectionCandidate(minOf(id1, id2), maxOf(id1, id2), sign, sx, sy, "GeodesicGeodesic"))
        }
    }

    private fun addGeodesicCircleCandidates(
        candidates: MutableList<IntersectionCandidate>,
        gId: Int, geo: GeodesicLike, cId: Int, circle: Circle
    ) {
        val results = IntersectionUtils.greatCircleSmallCircleIntersection(
            geo.normalX, geo.normalY, geo.normalZ,
            circle.centerX, circle.centerY, circle.centerZ,
            circle.cosRadius
        )
        if (results.isEmpty()) return

        val refDir = IntersectionUtils.geodesicGeodesicRefDirection(
            geo.normalX, geo.normalY, geo.normalZ,
            circle.centerX, circle.centerY, circle.centerZ
        )
        val signMap = IntersectionUtils.assignSign(results, refDir.first, refDir.second, refDir.third)

        for ((sign, pt) in signMap) {
            if (geo.geodesicType == GeodesicType.SEGMENT) {
                if (!IntersectionUtils.isOnSegmentArc(pt.first, pt.second, pt.third,
                        geo.x1, geo.y1, geo.z1, geo.x2, geo.y2, geo.z2,
                        geo.normalX, geo.normalY, geo.normalZ)) continue
            }
            if (!SphericalUtils.isOnFrontHemisphere(pt.first, pt.second, pt.third, viewMatrix)) continue
            val (px, py, _) = SphericalUtils.projectToScreen(pt.first, pt.second, pt.third, viewMatrix)
            val sx = diskCenterX + displayScale * px
            val sy = diskCenterY - displayScale * py
            candidates.add(IntersectionCandidate(gId, cId, sign, sx, sy, "GeodesicCircle"))
        }
    }

    private fun addCircleCircleCandidates(
        candidates: MutableList<IntersectionCandidate>,
        id1: Int, c1: Circle, id2: Int, c2: Circle
    ) {
        val results = IntersectionUtils.smallCircleSmallCircleIntersection(
            c1.centerX, c1.centerY, c1.centerZ, c1.cosRadius,
            c2.centerX, c2.centerY, c2.centerZ, c2.cosRadius
        )
        if (results.isEmpty()) return

        val refDir = IntersectionUtils.geodesicGeodesicRefDirection(
            c1.centerX, c1.centerY, c1.centerZ,
            c2.centerX, c2.centerY, c2.centerZ
        )
        val signMap = IntersectionUtils.assignSign(results, refDir.first, refDir.second, refDir.third)

        for ((sign, pt) in signMap) {
            if (!SphericalUtils.isOnFrontHemisphere(pt.first, pt.second, pt.third, viewMatrix)) continue
            val (px, py, _) = SphericalUtils.projectToScreen(pt.first, pt.second, pt.third, viewMatrix)
            val sx = diskCenterX + displayScale * px
            val sy = diskCenterY - displayScale * py
            candidates.add(IntersectionCandidate(minOf(id1, id2), maxOf(id1, id2), sign, sx, sy, "CircleCircle"))
        }
    }
}
