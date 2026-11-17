package riemannsketchpad.geometry

class GeodesicGeodesicIntersection(
    override val id: Int,
    val geodesic1Id: Int,
    val geodesic2Id: Int,
    val sign: Int
) : GeometricObject, PointLike {
    override val parentIds: List<Int> = listOf(geodesic1Id, geodesic2Id)
    override var isDefined: Boolean = true
    override var x: Double = 0.0
    override var y: Double = 0.0
    override var z: Double = 0.0

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val g1 = objects[geodesic1Id]
        val g2 = objects[geodesic2Id]
        if (g1 == null || g2 == null || !g1.isDefined || !g2.isDefined || g1 !is GeodesicLike || g2 !is GeodesicLike) {
            isDefined = false; return
        }
        val candidates = IntersectionUtils.greatCircleGreatCircleIntersection(
            g1.normalX, g1.normalY, g1.normalZ,
            g2.normalX, g2.normalY, g2.normalZ
        )
        if (candidates.isEmpty()) { isDefined = false; return }

        val refDir = IntersectionUtils.geodesicGeodesicRefDirection(
            g1.normalX, g1.normalY, g1.normalZ,
            g2.normalX, g2.normalY, g2.normalZ
        )
        val signMap = IntersectionUtils.assignSign(candidates, refDir.first, refDir.second, refDir.third)
        val selected = signMap[sign]
        if (selected == null) { isDefined = false; return }

        // For segments: check if the selected point lies on the arc(s)
        if (g1.geodesicType == GeodesicType.SEGMENT) {
            if (!IntersectionUtils.isOnSegmentArc(
                    selected.first, selected.second, selected.third,
                    g1.x1, g1.y1, g1.z1, g1.x2, g1.y2, g1.z2,
                    g1.normalX, g1.normalY, g1.normalZ
                )
            ) { isDefined = false; return }
        }
        if (g2.geodesicType == GeodesicType.SEGMENT) {
            if (!IntersectionUtils.isOnSegmentArc(
                    selected.first, selected.second, selected.third,
                    g2.x1, g2.y1, g2.z1, g2.x2, g2.y2, g2.z2,
                    g2.normalX, g2.normalY, g2.normalZ
                )
            ) { isDefined = false; return }
        }

        isDefined = true
        x = selected.first; y = selected.second; z = selected.third
    }
}

class GeodesicCircleIntersection(
    override val id: Int,
    val geodesicId: Int,
    val circleId: Int,
    val sign: Int
) : GeometricObject, PointLike {
    override val parentIds: List<Int> = listOf(geodesicId, circleId)
    override var isDefined: Boolean = true
    override var x: Double = 0.0
    override var y: Double = 0.0
    override var z: Double = 0.0

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val geo = objects[geodesicId]
        val circle = objects[circleId]
        if (geo == null || circle == null || !geo.isDefined || !circle.isDefined || geo !is GeodesicLike || circle !is Circle) {
            isDefined = false; return
        }
        val candidates = IntersectionUtils.greatCircleSmallCircleIntersection(
            geo.normalX, geo.normalY, geo.normalZ,
            circle.centerX, circle.centerY, circle.centerZ,
            circle.cosRadius
        )
        if (candidates.isEmpty()) { isDefined = false; return }

        val refDir = IntersectionUtils.geodesicGeodesicRefDirection(
            geo.normalX, geo.normalY, geo.normalZ,
            circle.centerX, circle.centerY, circle.centerZ
        )
        val signMap = IntersectionUtils.assignSign(candidates, refDir.first, refDir.second, refDir.third)
        val selected = signMap[sign]
        if (selected == null) { isDefined = false; return }

        // For segments: check if on arc
        if (geo.geodesicType == GeodesicType.SEGMENT) {
            if (!IntersectionUtils.isOnSegmentArc(
                    selected.first, selected.second, selected.third,
                    geo.x1, geo.y1, geo.z1, geo.x2, geo.y2, geo.z2,
                    geo.normalX, geo.normalY, geo.normalZ
                )
            ) { isDefined = false; return }
        }

        isDefined = true
        x = selected.first; y = selected.second; z = selected.third
    }
}

class CircleCircleIntersection(
    override val id: Int,
    val circle1Id: Int,
    val circle2Id: Int,
    val sign: Int
) : GeometricObject, PointLike {
    override val parentIds: List<Int> = listOf(circle1Id, circle2Id)
    override var isDefined: Boolean = true
    override var x: Double = 0.0
    override var y: Double = 0.0
    override var z: Double = 0.0

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val c1 = objects[circle1Id]
        val c2 = objects[circle2Id]
        if (c1 == null || c2 == null || !c1.isDefined || !c2.isDefined || c1 !is Circle || c2 !is Circle) {
            isDefined = false; return
        }
        val candidates = IntersectionUtils.smallCircleSmallCircleIntersection(
            c1.centerX, c1.centerY, c1.centerZ, c1.cosRadius,
            c2.centerX, c2.centerY, c2.centerZ, c2.cosRadius
        )
        if (candidates.isEmpty()) { isDefined = false; return }

        val refDir = IntersectionUtils.geodesicGeodesicRefDirection(
            c1.centerX, c1.centerY, c1.centerZ,
            c2.centerX, c2.centerY, c2.centerZ
        )
        val signMap = IntersectionUtils.assignSign(candidates, refDir.first, refDir.second, refDir.third)
        val selected = signMap[sign]
        if (selected == null) { isDefined = false; return }

        isDefined = true
        x = selected.first; y = selected.second; z = selected.third
    }
}
