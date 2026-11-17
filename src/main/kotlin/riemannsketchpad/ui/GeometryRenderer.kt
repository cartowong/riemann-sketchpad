package riemannsketchpad.ui

import javafx.scene.Group
import javafx.scene.paint.Color
import javafx.scene.shape.ArcTo
import javafx.scene.shape.ClosePath
import javafx.scene.shape.Ellipse
import javafx.scene.shape.LineTo
import javafx.scene.shape.MoveTo
import javafx.scene.shape.Path
import javafx.scene.shape.Shape
import javafx.scene.text.Font
import javafx.scene.text.Text
import riemannsketchpad.geometry.*
import riemannsketchpad.geometry.Circle as GeoCircle
import riemannsketchpad.geometry.Line as GeoLine
import riemannsketchpad.model.GeometricObjectManager
import riemannsketchpad.model.ObjectMetadata
import riemannsketchpad.model.StyleUtils
import kotlin.math.*

class GeometryRenderer {

    companion object {
        private const val SELECTION_COLOR_HEX = "#2196F3"
        private const val PREVIEW_COLOR_HEX = "#000000"
        private const val CONSTRUCTION_PREVIEW_COLOR_HEX = "#FF5722"
        private const val PREVIEW_OPACITY = 0.4
        private const val BACK_FACE_OPACITY = 0.3
        private const val GRID_COLOR_VALUE = 0.85
        private const val GRID_BACK_OPACITY = 0.15
        private const val GRID_STROKE = 0.5
        private const val LABEL_FONT_SIZE = 12.0
    }

    fun render(
        group: Group,
        manager: GeometricObjectManager,
        viewMatrix: DoubleArray,
        displayScale: Double,
        diskCenterX: Double,
        diskCenterY: Double,
        selectedIds: Set<Int>,
        hoverId: Int?,
        showGrid: Boolean,
        showBackFace: Boolean = true
    ) {
        group.children.clear()

        // Grid (behind everything)
        if (showGrid) {
            renderGrid(group, viewMatrix, displayScale, diskCenterX, diskCenterY)
        }

        val objects = manager.getObjects()
        val metadata = manager.getMetadata()

        // 5-pass rendering: regions, angles, geodesics/circles, points, labels
        // Within each pass: back-face first, then front-face

        // Pass 1: Regions
        for ((id, obj) in objects) {
            if (obj !is Region) continue
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked || !obj.isDefined) continue
            renderRegion(group, obj, meta, id, viewMatrix, displayScale, diskCenterX, diskCenterY, selectedIds, hoverId, showBackFace)
        }

        // Pass 2: Angles
        for ((id, obj) in objects) {
            if (obj !is Angle) continue
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked || !obj.isDefined) continue
            renderAngle(group, obj, meta, id, viewMatrix, displayScale, diskCenterX, diskCenterY, selectedIds, hoverId, showBackFace)
        }

        // Pass 3: Geodesics and Circles
        for ((id, obj) in objects) {
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked || !obj.isDefined) continue
            when {
                obj is GeodesicLike && obj is GeometricObject -> renderGeodesic(group, obj as GeometricObject, obj, meta, id, viewMatrix, displayScale, diskCenterX, diskCenterY, selectedIds, hoverId, showBackFace)
                obj is GeoCircle -> renderCircle(group, obj, meta, id, viewMatrix, displayScale, diskCenterX, diskCenterY, selectedIds, hoverId, showBackFace)
            }
        }

        // Pass 4: Points
        for ((id, obj) in objects) {
            if (obj !is PointLike) continue
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked || !obj.isDefined) continue
            renderPoint(group, obj, meta, id, viewMatrix, displayScale, diskCenterX, diskCenterY, selectedIds, hoverId, showBackFace)
        }

        // Pass 5: Labels
        for ((id, obj) in objects) {
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked || !obj.isDefined || !meta.labelVisible) continue
            val label = meta.label ?: continue
            renderLabel(group, obj, meta, label, viewMatrix, displayScale, diskCenterX, diskCenterY, showBackFace)
        }
    }

    fun renderPreviewObjects(
        group: Group,
        previewObjects: List<Pair<GeometricObject, ObjectMetadata>>,
        viewMatrix: DoubleArray,
        displayScale: Double,
        diskCenterX: Double,
        diskCenterY: Double,
        isConstructionPreview: Boolean,
        showBackFace: Boolean = true
    ) {
        group.children.clear()
        val color = if (isConstructionPreview) Color.web(CONSTRUCTION_PREVIEW_COLOR_HEX) else Color.web(PREVIEW_COLOR_HEX)

        // Pass 1: Regions (bottom)
        for ((obj, _) in previewObjects) {
            if (obj !is Region || !obj.isDefined) continue
            if (obj.vertices.size < 3) continue
            val projected = obj.vertices.map { (vx, vy, vz) ->
                val (px, py, pz) = SphericalUtils.projectToScreen(vx, vy, vz, viewMatrix)
                Triple(diskCenterX + displayScale * px, diskCenterY - displayScale * py, pz)
            }
            val anyFront = projected.any { it.third > 0 }
            if (!anyFront && !showBackFace) continue

            val coords = DoubleArray(projected.size * 2)
            for (i in projected.indices) {
                coords[i * 2] = projected[i].first
                coords[i * 2 + 1] = projected[i].second
            }
            val polygon = javafx.scene.shape.Polygon(*coords)
            polygon.fill = Color.color(color.red, color.green, color.blue, 0.2)
            polygon.stroke = null
            polygon.opacity = PREVIEW_OPACITY
            polygon.isMouseTransparent = true
            group.children.add(polygon)
        }

        // Pass 2: Angles
        for ((obj, _) in previewObjects) {
            if (obj !is Angle || !obj.isDefined) continue
            val (vx, vy, vz) = SphericalUtils.projectToScreen(obj.vertexX, obj.vertexY, obj.vertexZ, viewMatrix)
            if (!showBackFace && vz <= 0) continue
            val svx = diskCenterX + displayScale * vx
            val svy = diskCenterY - displayScale * vy

            val tAScreen = projectTangentToScreen(obj.vertexX, obj.vertexY, obj.vertexZ,
                obj.tangentAX, obj.tangentAY, obj.tangentAZ, viewMatrix) ?: continue
            val tCScreen = projectTangentToScreen(obj.vertexX, obj.vertexY, obj.vertexZ,
                obj.tangentCX, obj.tangentCY, obj.tangentCZ, viewMatrix) ?: continue

            val startAngle = atan2(-tAScreen.second, tAScreen.first)
            val endAngle = atan2(-tCScreen.second, tCScreen.first)

            var sweep = endAngle - startAngle
            val cross2D = tAScreen.first * (-tCScreen.second) - (-tAScreen.second) * tCScreen.first
            if (cross2D > 0 && sweep < 0) sweep += 2 * PI
            if (cross2D < 0 && sweep > 0) sweep -= 2 * PI
            if ((obj.measureRadians > PI) != (abs(sweep) > PI)) {
                sweep = if (sweep > 0) sweep - 2 * PI else sweep + 2 * PI
            }
            if (abs(sweep) < 1e-10) continue

            val arcRadius = StyleUtils.angleArcRadius("medium")
            val sweepDeg = Math.toDegrees(sweep)

            val startPx = svx + arcRadius * cos(startAngle)
            val startPy = svy + arcRadius * sin(startAngle)
            val endPx = svx + arcRadius * cos(endAngle)
            val endPy = svy + arcRadius * sin(endAngle)

            val path = Path()
            path.elements.add(MoveTo(svx, svy))
            path.elements.add(LineTo(startPx, startPy))
            val largeArc = abs(sweepDeg) > 180
            val sweepFlag = sweep > 0
            path.elements.add(ArcTo(arcRadius, arcRadius, 0.0, endPx, endPy, largeArc, sweepFlag))
            path.elements.add(ClosePath())
            path.fill = Color.color(color.red, color.green, color.blue, 0.2)
            path.stroke = color
            path.strokeWidth = 1.0
            path.opacity = PREVIEW_OPACITY
            path.isMouseTransparent = true
            group.children.add(path)
        }

        // Pass 3: Geodesics and Circles
        for ((obj, _) in previewObjects) {
            if (!obj.isDefined) continue
            when {
                obj is GeodesicLike && obj is GeometricObject -> {
                    val shapes = buildGeodesicShapes(obj, obj, viewMatrix, displayScale, diskCenterX, diskCenterY, color, 2.0, null, !showBackFace, showBackFace)
                    for (shape in shapes) {
                        shape.opacity = PREVIEW_OPACITY
                        shape.isMouseTransparent = true
                        group.children.add(shape)
                    }
                }
                obj is GeoCircle -> {
                    val shapes = buildCircleShapes(obj, viewMatrix, displayScale, diskCenterX, diskCenterY, color, 2.0, null, !showBackFace, showBackFace)
                    for (shape in shapes) {
                        shape.opacity = PREVIEW_OPACITY
                        shape.isMouseTransparent = true
                        group.children.add(shape)
                    }
                }
            }
        }

        // Pass 4: Points on top
        for ((obj, _) in previewObjects) {
            if (obj !is PointLike || !obj.isDefined) continue
            val (px, py, pz) = SphericalUtils.projectToScreen(obj.x, obj.y, obj.z, viewMatrix)
            if (pz <= 0) continue
            val sx = diskCenterX + displayScale * px
            val sy = diskCenterY - displayScale * py
            val circle = javafx.scene.shape.Circle(sx, sy, 4.0)
            circle.fill = color
            circle.opacity = PREVIEW_OPACITY
            circle.isMouseTransparent = true
            group.children.add(circle)
        }
    }

    // --- Point Rendering ---

    private fun renderPoint(
        group: Group,
        obj: PointLike,
        meta: ObjectMetadata,
        id: Int,
        viewMatrix: DoubleArray,
        displayScale: Double,
        diskCenterX: Double,
        diskCenterY: Double,
        selectedIds: Set<Int>,
        hoverId: Int?,
        showBackFace: Boolean
    ) {
        val (px, py, pz) = SphericalUtils.projectToScreen(obj.x, obj.y, obj.z, viewMatrix)
        val sx = diskCenterX + displayScale * px
        val sy = diskCenterY - displayScale * py

        val isSelected = id in selectedIds
        val isHover = id == hoverId

        var radius = StyleUtils.pointRadius(meta.size)
        val color = when {
            isSelected || isHover -> Color.web(SELECTION_COLOR_HEX)
            else -> Color.web(StyleUtils.colorHex(meta.color))
        }
        if (isHover && !isSelected) radius *= 1.3

        val circle = javafx.scene.shape.Circle(sx, sy, radius)
        circle.fill = color
        if (pz <= 0) {
            if (!showBackFace) return
            circle.opacity = BACK_FACE_OPACITY
        }
        group.children.add(circle)
    }

    // --- Geodesic Rendering ---

    private fun renderGeodesic(
        group: Group,
        geoObj: GeometricObject,
        geo: GeodesicLike,
        meta: ObjectMetadata,
        id: Int,
        viewMatrix: DoubleArray,
        displayScale: Double,
        diskCenterX: Double,
        diskCenterY: Double,
        selectedIds: Set<Int>,
        hoverId: Int?,
        showBackFace: Boolean
    ) {
        val isSelected = id in selectedIds
        val isHover = id == hoverId

        var strokeW = StyleUtils.strokeWidth(meta.strokeThickness)
        val color = when {
            isSelected || isHover -> Color.web(SELECTION_COLOR_HEX)
            else -> Color.web(StyleUtils.colorHex(meta.color))
        }
        if (isHover && !isSelected) strokeW *= 1.5
        val dashArray = if (meta.strokeStyle == "dashed") doubleArrayOf(6.0, 4.0) else null

        val shapes = buildGeodesicShapes(geoObj, geo, viewMatrix, displayScale, diskCenterX, diskCenterY, color, strokeW, dashArray, true, showBackFace)
        group.children.addAll(shapes)
    }

    private fun buildGeodesicShapes(
        geoObj: GeometricObject,
        geo: GeodesicLike,
        viewMatrix: DoubleArray,
        displayScale: Double,
        diskCenterX: Double,
        diskCenterY: Double,
        color: Color,
        strokeWidth: Double,
        dashArray: DoubleArray?,
        splitRender: Boolean,
        showBackFace: Boolean = true
    ): List<Shape> {
        val params = SphericalUtils.ellipseProjectionParams(geo.normalX, geo.normalY, geo.normalZ, viewMatrix)
        val shapes = mutableListOf<Shape>()

        val scaledSemiMajor = params.semiMajor * displayScale
        val scaledSemiMinor = params.semiMinor * displayScale
        val ecx = diskCenterX + params.centerX * displayScale
        val ecy = diskCenterY - params.centerY * displayScale
        val rotDeg = -Math.toDegrees(params.rotationAngle)

        // Edge-on check
        if (scaledSemiMinor < 0.5) {
            // Render as a straight line (diameter)
            if (geo.geodesicType == GeodesicType.LINE) {
                // Edge-on: great circle on the silhouette, render as a single line
                val cosR = cos(params.rotationAngle)
                val sinR = sin(params.rotationAngle)
                val dx = scaledSemiMajor * cosR
                val dy = -scaledSemiMajor * sinR
                val line = javafx.scene.shape.Line(ecx - dx, ecy - dy, ecx + dx, ecy + dy)
                applyStroke(line, color, strokeWidth, dashArray)
                shapes.add(line)
            } else {
                // Segment as straight line between projected endpoints
                val (p1x, p1y, _) = SphericalUtils.projectToScreen(geo.x1, geo.y1, geo.z1, viewMatrix)
                val (p2x, p2y, _) = SphericalUtils.projectToScreen(geo.x2, geo.y2, geo.z2, viewMatrix)
                val s1x = diskCenterX + displayScale * p1x
                val s1y = diskCenterY - displayScale * p1y
                val s2x = diskCenterX + displayScale * p2x
                val s2y = diskCenterY - displayScale * p2y
                val line = javafx.scene.shape.Line(s1x, s1y, s2x, s2y)
                applyStroke(line, color, strokeWidth, dashArray)
                shapes.add(line)
            }
            return shapes
        }

        if (geo.geodesicType == GeodesicType.LINE) {
            if (splitRender) {
                val (npx, npy, npz) = SphericalUtils.matVec(viewMatrix, geo.normalX, geo.normalY, geo.normalZ)
                val split = computeSilhouetteCrossings(npx, npy, npz, 0.0, 1.0, displayScale, diskCenterX, diskCenterY)
                if (split != null) {
                    shapes.addAll(buildSplitArcs(split, ecx, ecy, scaledSemiMajor, scaledSemiMinor, rotDeg, color, strokeWidth, dashArray, BACK_FACE_OPACITY, showBackFace))
                } else {
                    // Face-on: entire great circle on the silhouette
                    val ellipse = Ellipse(ecx, ecy, scaledSemiMajor, scaledSemiMinor)
                    ellipse.fill = Color.TRANSPARENT
                    applyStroke(ellipse, color, strokeWidth, dashArray)
                    ellipse.rotate = rotDeg
                    shapes.add(ellipse)
                }
            } else {
                val ellipse = Ellipse(ecx, ecy, scaledSemiMajor, scaledSemiMinor)
                ellipse.fill = Color.TRANSPARENT
                applyStroke(ellipse, color, strokeWidth, dashArray)
                ellipse.rotate = rotDeg
                shapes.add(ellipse)
            }
        } else {
            // Segment: arc between two projected endpoints
            val (p1x, p1y, p1z) = SphericalUtils.projectToScreen(geo.x1, geo.y1, geo.z1, viewMatrix)
            val (p2x, p2y, p2z) = SphericalUtils.projectToScreen(geo.x2, geo.y2, geo.z2, viewMatrix)
            val s1x = diskCenterX + displayScale * p1x
            val s1y = diskCenterY - displayScale * p1y
            val s2x = diskCenterX + displayScale * p2x
            val s2y = diskCenterY - displayScale * p2y

            // Determine arc flags by sampling midpoint
            val (mx, my, mz) = SphericalUtils.slerp(geo.x1, geo.y1, geo.z1, geo.x2, geo.y2, geo.z2, 0.5)
            val (pmx, pmy, _) = SphericalUtils.projectToScreen(mx, my, mz, viewMatrix)
            val smx = diskCenterX + displayScale * pmx
            val smy = diskCenterY - displayScale * pmy

            val largeArc = determineLargeArcFlag(s1x, s1y, s2x, s2y, smx, smy, ecx, ecy, scaledSemiMajor, scaledSemiMinor, rotDeg)
            val sweep = determineSweepFlag(s1x, s1y, s2x, s2y, smx, smy, scaledSemiMajor, scaledSemiMinor, rotDeg, ecx, ecy)

            // Build the arc Path
            val path = Path()
            path.elements.add(MoveTo(s1x, s1y))
            val arcTo = ArcTo(scaledSemiMajor, scaledSemiMinor, rotDeg, s2x, s2y, largeArc, sweep)
            path.elements.add(arcTo)
            path.fill = Color.TRANSPARENT
            applyStroke(path, color, strokeWidth, dashArray)

            // Simple front/back: use average depth
            val avgDepth = (p1z + p2z) / 2.0
            if (splitRender && avgDepth <= 0) {
                if (!showBackFace) return shapes
                path.opacity = BACK_FACE_OPACITY
            }
            shapes.add(path)
        }

        return shapes
    }

    // --- Circle Rendering ---

    private fun renderCircle(
        group: Group,
        circle: GeoCircle,
        meta: ObjectMetadata,
        id: Int,
        viewMatrix: DoubleArray,
        displayScale: Double,
        diskCenterX: Double,
        diskCenterY: Double,
        selectedIds: Set<Int>,
        hoverId: Int?,
        showBackFace: Boolean
    ) {
        val isSelected = id in selectedIds
        val isHover = id == hoverId

        var strokeW = StyleUtils.strokeWidth(meta.strokeThickness)
        val color = when {
            isSelected || isHover -> Color.web(SELECTION_COLOR_HEX)
            else -> Color.web(StyleUtils.colorHex(meta.color))
        }
        if (isHover && !isSelected) strokeW *= 1.5
        val dashArray = if (meta.strokeStyle == "dashed") doubleArrayOf(6.0, 4.0) else null

        val shapes = buildCircleShapes(circle, viewMatrix, displayScale, diskCenterX, diskCenterY, color, strokeW, dashArray, true, showBackFace)
        group.children.addAll(shapes)
    }

    private fun buildCircleShapes(
        circle: GeoCircle,
        viewMatrix: DoubleArray,
        displayScale: Double,
        diskCenterX: Double,
        diskCenterY: Double,
        color: Color,
        strokeWidth: Double,
        dashArray: DoubleArray?,
        splitRender: Boolean,
        showBackFace: Boolean = true
    ): List<Shape> {
        val params = SphericalUtils.smallCircleEllipseParams(
            circle.centerX, circle.centerY, circle.centerZ, circle.cosRadius, viewMatrix
        )
        val shapes = mutableListOf<Shape>()

        val scaledSemiMajor = params.semiMajor * displayScale
        val scaledSemiMinor = params.semiMinor * displayScale
        val ecx = diskCenterX + params.centerX * displayScale
        val ecy = diskCenterY - params.centerY * displayScale
        val rotDeg = -Math.toDegrees(params.rotationAngle)

        if (scaledSemiMajor < 0.5) return shapes

        if (splitRender) {
            val (npx, npy, npz) = SphericalUtils.matVec(viewMatrix, circle.centerX, circle.centerY, circle.centerZ)
            val sinR = sqrt((1.0 - circle.cosRadius * circle.cosRadius).coerceAtLeast(0.0))
            val lenXY = sqrt(npx * npx + npy * npy)
            val zMax = circle.cosRadius * npz + sinR * lenXY

            val split = computeSilhouetteCrossings(npx, npy, npz, circle.cosRadius, sinR, displayScale, diskCenterX, diskCenterY)
            if (split != null) {
                shapes.addAll(buildSplitArcs(split, ecx, ecy, scaledSemiMajor, scaledSemiMinor, rotDeg, color, strokeWidth, dashArray, BACK_FACE_OPACITY, showBackFace))
            } else {
                val ellipse = Ellipse(ecx, ecy, scaledSemiMajor, scaledSemiMinor)
                ellipse.fill = Color.TRANSPARENT
                applyStroke(ellipse, color, strokeWidth, dashArray)
                ellipse.rotate = rotDeg
                if (zMax <= 1e-10) {
                    if (!showBackFace) return shapes
                    ellipse.opacity = BACK_FACE_OPACITY
                }
                shapes.add(ellipse)
            }
        } else {
            val ellipse = Ellipse(ecx, ecy, scaledSemiMajor, scaledSemiMinor)
            ellipse.fill = Color.TRANSPARENT
            applyStroke(ellipse, color, strokeWidth, dashArray)
            ellipse.rotate = rotDeg
            shapes.add(ellipse)
        }

        return shapes
    }

    // --- Angle Rendering ---

    private fun renderAngle(
        group: Group,
        angle: Angle,
        meta: ObjectMetadata,
        id: Int,
        viewMatrix: DoubleArray,
        displayScale: Double,
        diskCenterX: Double,
        diskCenterY: Double,
        selectedIds: Set<Int>,
        hoverId: Int?,
        showBackFace: Boolean
    ) {
        val (vx, vy, vz) = SphericalUtils.projectToScreen(angle.vertexX, angle.vertexY, angle.vertexZ, viewMatrix)
        if (!showBackFace && vz <= 0) return
        val svx = diskCenterX + displayScale * vx
        val svy = diskCenterY - displayScale * vy

        // Project tangent vectors to screen space
        val tAScreen = projectTangentToScreen(angle.vertexX, angle.vertexY, angle.vertexZ,
            angle.tangentAX, angle.tangentAY, angle.tangentAZ, viewMatrix)
        val tCScreen = projectTangentToScreen(angle.vertexX, angle.vertexY, angle.vertexZ,
            angle.tangentCX, angle.tangentCY, angle.tangentCZ, viewMatrix)

        if (tAScreen == null || tCScreen == null) return

        val startAngle = atan2(-tAScreen.second, tAScreen.first)
        val endAngle = atan2(-tCScreen.second, tCScreen.first)

        // Compute sweep (counterclockwise from A to C, consistent with the 3D angle orientation)
        var sweep = endAngle - startAngle
        // Use the sign from the 3D angle: if measureRadians < PI, sweep should be positive short way
        // Check cross product in screen space to determine orientation
        val cross2D = tAScreen.first * (-tCScreen.second) - (-tAScreen.second) * tCScreen.first
        if (cross2D > 0 && sweep < 0) sweep += 2 * PI
        if (cross2D < 0 && sweep > 0) sweep -= 2 * PI
        // Match sweep magnitude to the 3D orientation-dependent angle
        if ((angle.measureRadians > PI) != (abs(sweep) > PI)) {
            sweep = if (sweep > 0) sweep - 2 * PI else sweep + 2 * PI
        }
        if (abs(sweep) < 1e-10) return

        val isSelected = id in selectedIds
        val isHover = id == hoverId

        val arcRadius = StyleUtils.angleArcRadius(meta.size)
        val baseColor = when {
            isSelected || isHover -> Color.web(SELECTION_COLOR_HEX)
            else -> Color.web(StyleUtils.colorHex(meta.color))
        }

        val fillColor = Color.color(baseColor.red, baseColor.green, baseColor.blue, 0.3)
        val strokeColor = Color.color(baseColor.red, baseColor.green, baseColor.blue, 0.7)

        // Draw arc sector using Path
        val startDeg = Math.toDegrees(startAngle)
        val sweepDeg = Math.toDegrees(sweep)

        val startPx = svx + arcRadius * cos(startAngle)
        val startPy = svy + arcRadius * sin(startAngle)
        val endPx = svx + arcRadius * cos(endAngle)
        val endPy = svy + arcRadius * sin(endAngle)

        val path = Path()
        path.elements.add(MoveTo(svx, svy))
        path.elements.add(LineTo(startPx, startPy))
        val largeArc = abs(sweepDeg) > 180
        // JavaFX ArcTo sweep: sweep flag determines direction
        val sweepFlag = sweep > 0
        path.elements.add(ArcTo(arcRadius, arcRadius, 0.0, endPx, endPy, largeArc, sweepFlag))
        path.elements.add(ClosePath())
        path.fill = fillColor
        path.stroke = strokeColor
        path.strokeWidth = 1.0
        group.children.add(path)
    }

    private fun projectTangentToScreen(
        px: Double, py: Double, pz: Double,
        tx: Double, ty: Double, tz: Double,
        viewMatrix: DoubleArray
    ): Pair<Double, Double>? {
        val (spx, spy, _) = SphericalUtils.matVec(viewMatrix, px, py, pz)
        val eps = 0.001
        val qx = px + eps * tx
        val qy = py + eps * ty
        val qz = pz + eps * tz
        val (sqx, sqy, _) = SphericalUtils.matVec(viewMatrix, qx, qy, qz)
        val dx = sqx - spx
        val dy = sqy - spy
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1e-12) return null
        return Pair(dx / len, dy / len)
    }

    // --- Region Rendering ---

    private fun renderRegion(
        group: Group,
        region: Region,
        meta: ObjectMetadata,
        id: Int,
        viewMatrix: DoubleArray,
        displayScale: Double,
        diskCenterX: Double,
        diskCenterY: Double,
        selectedIds: Set<Int>,
        hoverId: Int?,
        showBackFace: Boolean
    ) {
        if (region.vertices.size < 3) return

        val isSelected = id in selectedIds
        val isHover = id == hoverId

        val baseColor = when {
            isSelected || isHover -> Color.web(SELECTION_COLOR_HEX)
            else -> Color.web(StyleUtils.colorHex(meta.color))
        }
        val fillColor = Color.color(baseColor.red, baseColor.green, baseColor.blue, 0.3)

        // Project vertices
        val projected = region.vertices.map { (vx, vy, vz) ->
            val (px, py, pz) = SphericalUtils.projectToScreen(vx, vy, vz, viewMatrix)
            Triple(diskCenterX + displayScale * px, diskCenterY - displayScale * py, pz)
        }

        // Simple opacity rule: any vertex on front -> full opacity, all on back -> reduced
        val anyFront = projected.any { it.third > 0 }

        // Build region path with geodesic arc edges
        val path = Path()
        val n = projected.size

        path.elements.add(MoveTo(projected[0].first, projected[0].second))
        for (i in 0 until n) {
            val j = (i + 1) % n
            val vi = region.vertices[i]
            val vj = region.vertices[j]

            // Compute the geodesic arc between vertices i and j
            val normal = SphericalUtils.greatCircleNormal(vi.first, vi.second, vi.third, vj.first, vj.second, vj.third)
            if (normal == null) {
                // Coincident/antipodal — just draw a line
                path.elements.add(LineTo(projected[j].first, projected[j].second))
                continue
            }
            val params = SphericalUtils.ellipseProjectionParams(normal.first, normal.second, normal.third, viewMatrix)
            val scaledSemiMajor = params.semiMajor * displayScale
            val scaledSemiMinor = params.semiMinor * displayScale
            val rotDeg = -Math.toDegrees(params.rotationAngle)

            if (scaledSemiMinor < 0.5) {
                // Edge-on: straight line
                path.elements.add(LineTo(projected[j].first, projected[j].second))
            } else {
                // Elliptical arc
                val (mx, my, mz) = SphericalUtils.slerp(vi.first, vi.second, vi.third, vj.first, vj.second, vj.third, 0.5)
                val (pmx, pmy, _) = SphericalUtils.projectToScreen(mx, my, mz, viewMatrix)
                val smx = diskCenterX + displayScale * pmx
                val smy = diskCenterY - displayScale * pmy
                val ecx = diskCenterX + params.centerX * displayScale
                val ecy = diskCenterY - params.centerY * displayScale

                val largeArc = determineLargeArcFlag(projected[i].first, projected[i].second, projected[j].first, projected[j].second, smx, smy, ecx, ecy, scaledSemiMajor, scaledSemiMinor, rotDeg)
                val sweep = determineSweepFlag(projected[i].first, projected[i].second, projected[j].first, projected[j].second, smx, smy, scaledSemiMajor, scaledSemiMinor, rotDeg, ecx, ecy)

                path.elements.add(ArcTo(scaledSemiMajor, scaledSemiMinor, rotDeg, projected[j].first, projected[j].second, largeArc, sweep))
            }
        }
        path.elements.add(ClosePath())
        path.fill = fillColor
        path.stroke = Color.TRANSPARENT
        if (!anyFront) {
            if (!showBackFace) return
            path.opacity = BACK_FACE_OPACITY
        }
        group.children.add(path)
    }

    // --- Label Rendering ---

    private fun renderLabel(
        group: Group,
        obj: GeometricObject,
        meta: ObjectMetadata,
        label: String,
        viewMatrix: DoubleArray,
        displayScale: Double,
        diskCenterX: Double,
        diskCenterY: Double,
        showBackFace: Boolean = true
    ) {
        val anchor = computeLabelAnchor(obj, meta, viewMatrix, displayScale, diskCenterX, diskCenterY)
            ?: return

        // Check front hemisphere for the anchor
        if (!anchor.third) return // back hemisphere — no label

        val text = Text(anchor.first + meta.labelOffsetX, anchor.second + meta.labelOffsetY, label)
        text.fill = Color.BLACK
        text.font = Font.font(LABEL_FONT_SIZE)
        text.isMouseTransparent = true
        group.children.add(text)
    }

    fun computeLabelAnchor(
        obj: GeometricObject,
        meta: ObjectMetadata,
        viewMatrix: DoubleArray,
        displayScale: Double,
        diskCenterX: Double,
        diskCenterY: Double
    ): Triple<Double, Double, Boolean>? {
        return when {
            obj is PointLike -> {
                val (px, py, pz) = SphericalUtils.projectToScreen(obj.x, obj.y, obj.z, viewMatrix)
                Triple(diskCenterX + displayScale * px, diskCenterY - displayScale * py, pz > 0)
            }
            obj is GeodesicLike && obj is GeometricObject -> {
                val t = meta.labelAnchorT
                val (ix, iy, iz) = SphericalUtils.slerp(obj.x1, obj.y1, obj.z1, obj.x2, obj.y2, obj.z2, t)
                val (px, py, pz) = SphericalUtils.projectToScreen(ix, iy, iz, viewMatrix)
                Triple(diskCenterX + displayScale * px, diskCenterY - displayScale * py, pz > 0)
            }
            obj is GeoCircle -> {
                val theta = meta.labelAnchorAngle
                val frame = FreePointOnCircle.circleFrame(obj)
                val sinR = sin(obj.sphericalRadius)
                val cosTheta = cos(theta); val sinTheta = sin(theta)
                val lx = obj.centerX * obj.cosRadius + sinR * (frame.e1x * cosTheta + frame.e2x * sinTheta)
                val ly = obj.centerY * obj.cosRadius + sinR * (frame.e1y * cosTheta + frame.e2y * sinTheta)
                val lz = obj.centerZ * obj.cosRadius + sinR * (frame.e1z * cosTheta + frame.e2z * sinTheta)
                val (px, py, pz) = SphericalUtils.projectToScreen(lx, ly, lz, viewMatrix)
                Triple(diskCenterX + displayScale * px, diskCenterY - displayScale * py, pz > 0)
            }
            obj is Angle -> {
                val (px, py, pz) = SphericalUtils.projectToScreen(obj.vertexX, obj.vertexY, obj.vertexZ, viewMatrix)
                Triple(diskCenterX + displayScale * px, diskCenterY - displayScale * py, pz > 0)
            }
            obj is Region -> {
                if (obj.vertices.isEmpty()) return null
                var sx = 0.0; var sy = 0.0; var anyFront = false
                for ((vx, vy, vz) in obj.vertices) {
                    val (px, py, pz) = SphericalUtils.projectToScreen(vx, vy, vz, viewMatrix)
                    sx += diskCenterX + displayScale * px
                    sy += diskCenterY - displayScale * py
                    if (pz > 0) anyFront = true
                }
                Triple(sx / obj.vertices.size, sy / obj.vertices.size, anyFront)
            }
            else -> null
        }
    }

    // --- Grid Rendering ---

    private fun renderGrid(
        group: Group,
        viewMatrix: DoubleArray,
        displayScale: Double,
        diskCenterX: Double,
        diskCenterY: Double
    ) {
        val gridColor = Color.gray(GRID_COLOR_VALUE)

        // 12 meridians (great circles at 30° azimuth intervals)
        for (i in 0 until 12) {
            val azRad = Math.toRadians(i * 30.0)
            // Meridian plane normal: perpendicular to the plane containing the Y axis and the azimuth direction
            val nx = cos(azRad)
            val nz = sin(azRad)
            val params = SphericalUtils.ellipseProjectionParams(nx, 0.0, nz, viewMatrix)
            val scaledMajor = params.semiMajor * displayScale
            val scaledMinor = params.semiMinor * displayScale
            val ecx = diskCenterX + params.centerX * displayScale
            val ecy = diskCenterY - params.centerY * displayScale
            val rotDeg = -Math.toDegrees(params.rotationAngle)

            if (scaledMinor < 0.3) {
                // Edge-on: on the silhouette, single line
                val cosR = cos(params.rotationAngle)
                val sinR = sin(params.rotationAngle)
                val dx = scaledMajor * cosR
                val dy = -scaledMajor * sinR
                val line = javafx.scene.shape.Line(ecx - dx, ecy - dy, ecx + dx, ecy + dy)
                line.stroke = gridColor
                line.strokeWidth = GRID_STROKE
                group.children.add(line)
            } else {
                val (gnpx, gnpy, gnpz) = SphericalUtils.matVec(viewMatrix, nx, 0.0, nz)
                val split = computeSilhouetteCrossings(gnpx, gnpy, gnpz, 0.0, 1.0, displayScale, diskCenterX, diskCenterY)
                if (split != null) {
                    group.children.addAll(buildSplitArcs(split, ecx, ecy, scaledMajor, scaledMinor, rotDeg, gridColor, GRID_STROKE, null, GRID_BACK_OPACITY))
                } else {
                    val ellipse = Ellipse(ecx, ecy, scaledMajor, scaledMinor)
                    ellipse.fill = Color.TRANSPARENT
                    ellipse.stroke = gridColor
                    ellipse.strokeWidth = GRID_STROKE
                    ellipse.rotate = rotDeg
                    group.children.add(ellipse)
                }
            }
        }

        // 5 parallels at latitudes: 60°, 30°, 0° (equator), -30°, -60°
        val latitudes = doubleArrayOf(60.0, 30.0, 0.0, -30.0, -60.0)
        for (latDeg in latitudes) {
            val latRad = Math.toRadians(latDeg)
            // Center of the parallel: (0, sin(lat), 0), cosRadius = sin(lat) from center (0,1,0)
            // Actually: the parallel at latitude λ is a small circle centered at (0,1,0) with
            // spherical radius = π/2 - λ. Or centered at (0, sin(λ), 0) with 3D radius cos(λ).
            // Using smallCircleEllipseParams: center = (0,1,0), cosRadius = sin(λ)
            val centerY = sin(latRad)  // on sphere Y-axis
            val cosR = sin(latRad)  // cos(spherical radius from north pole) = sin(latitude)
            val params = SphericalUtils.smallCircleEllipseParams(0.0, 1.0, 0.0, cosR, viewMatrix)

            // The equator is a great circle — use great circle params instead
            if (abs(latDeg) < 0.01) {
                // Equator: great circle with normal = (0,1,0)
                val gcParams = SphericalUtils.ellipseProjectionParams(0.0, 1.0, 0.0, viewMatrix)
                val scaledMajor = gcParams.semiMajor * displayScale
                val scaledMinor = gcParams.semiMinor * displayScale
                val ecx = diskCenterX + gcParams.centerX * displayScale
                val ecy = diskCenterY - gcParams.centerY * displayScale
                val rotDeg = -Math.toDegrees(gcParams.rotationAngle)

                val (eqnpx, eqnpy, eqnpz) = SphericalUtils.matVec(viewMatrix, 0.0, 1.0, 0.0)
                val eqSplit = computeSilhouetteCrossings(eqnpx, eqnpy, eqnpz, 0.0, 1.0, displayScale, diskCenterX, diskCenterY)
                if (eqSplit != null) {
                    group.children.addAll(buildSplitArcs(eqSplit, ecx, ecy, scaledMajor, scaledMinor, rotDeg, gridColor, GRID_STROKE, null, GRID_BACK_OPACITY))
                } else {
                    val ellipse = Ellipse(ecx, ecy, scaledMajor, scaledMinor)
                    ellipse.fill = Color.TRANSPARENT
                    ellipse.stroke = gridColor
                    ellipse.strokeWidth = GRID_STROKE
                    ellipse.rotate = rotDeg
                    group.children.add(ellipse)
                }
                continue
            }

            val scaledMajor = params.semiMajor * displayScale
            val scaledMinor = params.semiMinor * displayScale
            val ecx = diskCenterX + params.centerX * displayScale
            val ecy = diskCenterY - params.centerY * displayScale
            val rotDeg = -Math.toDegrees(params.rotationAngle)

            if (scaledMajor < 0.3) continue

            val (pnpx, pnpy, pnpz) = SphericalUtils.matVec(viewMatrix, 0.0, 1.0, 0.0)
            val pSinR = sqrt((1.0 - cosR * cosR).coerceAtLeast(0.0))
            val pLenXY = sqrt(pnpx * pnpx + pnpy * pnpy)
            val pZMax = cosR * pnpz + pSinR * pLenXY

            val pSplit = computeSilhouetteCrossings(pnpx, pnpy, pnpz, cosR, pSinR, displayScale, diskCenterX, diskCenterY)
            if (pSplit != null) {
                group.children.addAll(buildSplitArcs(pSplit, ecx, ecy, scaledMajor, scaledMinor, rotDeg, gridColor, GRID_STROKE, null, GRID_BACK_OPACITY))
            } else {
                val ellipse = Ellipse(ecx, ecy, scaledMajor, scaledMinor)
                ellipse.fill = Color.TRANSPARENT
                ellipse.stroke = gridColor
                ellipse.strokeWidth = GRID_STROKE
                ellipse.rotate = rotDeg
                if (pZMax <= 1e-10) ellipse.opacity = GRID_BACK_OPACITY
                group.children.add(ellipse)
            }
        }
    }

    // --- Silhouette Split Helpers ---

    private data class SilhouetteSplit(
        val c1sx: Double, val c1sy: Double,
        val c2sx: Double, val c2sy: Double,
        val frontMidSx: Double, val frontMidSy: Double,
        val backMidSx: Double, val backMidSy: Double
    )

    /**
     * Compute where a great or small circle crosses the silhouette (z=0 in view space).
     * For great circles, pass cosR=0.0, sinR=1.0.
     * Returns null if the circle doesn't cross (face-on, or entirely one hemisphere).
     */
    private fun computeSilhouetteCrossings(
        npx: Double, npy: Double, npz: Double,
        cosR: Double, sinR: Double,
        displayScale: Double, diskCenterX: Double, diskCenterY: Double
    ): SilhouetteSplit? {
        if (sinR < 1e-10) return null
        val lenXY = sqrt(npx * npx + npy * npy)
        if (lenXY < 1e-8) return null

        val sinTCross = cosR * npz / (sinR * lenXY)
        if (abs(sinTCross) > 1.0 - 1e-10) return null

        val cosTCross = sqrt((1.0 - sinTCross * sinTCross).coerceAtLeast(0.0))

        val e1x = npy / lenXY; val e1y = -npx / lenXY
        val e2x = npx * npz / lenXY; val e2y = npy * npz / lenXY

        val p1vx = cosR * npx + sinR * (cosTCross * e1x + sinTCross * e2x)
        val p1vy = cosR * npy + sinR * (cosTCross * e1y + sinTCross * e2y)
        val p2vx = cosR * npx + sinR * (-cosTCross * e1x + sinTCross * e2x)
        val p2vy = cosR * npy + sinR * (-cosTCross * e1y + sinTCross * e2y)

        val fvx = cosR * npx - sinR * e2x
        val fvy = cosR * npy - sinR * e2y
        val bvx = cosR * npx + sinR * e2x
        val bvy = cosR * npy + sinR * e2y

        return SilhouetteSplit(
            c1sx = diskCenterX + displayScale * p1vx,
            c1sy = diskCenterY - displayScale * p1vy,
            c2sx = diskCenterX + displayScale * p2vx,
            c2sy = diskCenterY - displayScale * p2vy,
            frontMidSx = diskCenterX + displayScale * fvx,
            frontMidSy = diskCenterY - displayScale * fvy,
            backMidSx = diskCenterX + displayScale * bvx,
            backMidSy = diskCenterY - displayScale * bvy
        )
    }

    /**
     * Build two arc shapes (front at full opacity, back at [backOpacity]) from silhouette split data.
     * Back arc is added first so it renders behind the front arc.
     */
    private fun buildSplitArcs(
        split: SilhouetteSplit,
        ecx: Double, ecy: Double,
        scaledSemiMajor: Double, scaledSemiMinor: Double, rotDeg: Double,
        color: Color, strokeWidth: Double, dashArray: DoubleArray?,
        backOpacity: Double,
        showBackFace: Boolean = true
    ): List<Shape> {
        val shapes = mutableListOf<Shape>()

        if (showBackFace) {
            val bla = determineLargeArcFlag(split.c1sx, split.c1sy, split.c2sx, split.c2sy,
                split.backMidSx, split.backMidSy, ecx, ecy, scaledSemiMajor, scaledSemiMinor, rotDeg)
            val bsw = determineSweepFlag(split.c1sx, split.c1sy, split.c2sx, split.c2sy,
                split.backMidSx, split.backMidSy, scaledSemiMajor, scaledSemiMinor, rotDeg, ecx, ecy)
            val backPath = Path()
            backPath.elements.add(MoveTo(split.c1sx, split.c1sy))
            backPath.elements.add(ArcTo(scaledSemiMajor, scaledSemiMinor, rotDeg, split.c2sx, split.c2sy, bla, bsw))
            backPath.fill = Color.TRANSPARENT
            applyStroke(backPath, color, strokeWidth, dashArray)
            backPath.opacity = backOpacity
            shapes.add(backPath)
        }

        val fla = determineLargeArcFlag(split.c1sx, split.c1sy, split.c2sx, split.c2sy,
            split.frontMidSx, split.frontMidSy, ecx, ecy, scaledSemiMajor, scaledSemiMinor, rotDeg)
        val fsw = determineSweepFlag(split.c1sx, split.c1sy, split.c2sx, split.c2sy,
            split.frontMidSx, split.frontMidSy, scaledSemiMajor, scaledSemiMinor, rotDeg, ecx, ecy)
        val frontPath = Path()
        frontPath.elements.add(MoveTo(split.c1sx, split.c1sy))
        frontPath.elements.add(ArcTo(scaledSemiMajor, scaledSemiMinor, rotDeg, split.c2sx, split.c2sy, fla, fsw))
        frontPath.fill = Color.TRANSPARENT
        applyStroke(frontPath, color, strokeWidth, dashArray)
        shapes.add(frontPath)

        return shapes
    }

    // --- Utility Functions ---

    private fun applyStroke(shape: Shape, color: Color, width: Double, dashArray: DoubleArray?) {
        shape.stroke = color
        shape.strokeWidth = width
        shape.fill = Color.TRANSPARENT
        if (dashArray != null) {
            shape.strokeDashArray.addAll(dashArray.toList())
        }
    }

    private fun determineLargeArcFlag(
        s1x: Double, s1y: Double, s2x: Double, s2y: Double,
        smx: Double, smy: Double,
        ecx: Double, ecy: Double,
        semiMajor: Double, semiMinor: Double, rotDeg: Double
    ): Boolean {
        // Sample the midpoint of the short arc. If the midpoint is close to the elliptic arc
        // that uses largeArc=false, use false. Otherwise use true.
        // Heuristic: compute angles on the ellipse for start, end, mid, and check if mid is between
        val rotRad = Math.toRadians(rotDeg)
        val a1 = ellipseAngle(s1x - ecx, s1y - ecy, rotRad, semiMajor, semiMinor)
        val a2 = ellipseAngle(s2x - ecx, s2y - ecy, rotRad, semiMajor, semiMinor)
        val am = ellipseAngle(smx - ecx, smy - ecy, rotRad, semiMajor, semiMinor)

        // Check whether midpoint angle is in the short arc (without large arc)
        val shortArc = normalizeAngleDiff(a2 - a1)
        val midInShort = normalizeAngleDiff(am - a1)
        val isInShort = if (shortArc > 0) midInShort in 0.0..shortArc else midInShort in shortArc..0.0
        return !isInShort
    }

    private fun determineSweepFlag(
        s1x: Double, s1y: Double, s2x: Double, s2y: Double,
        smx: Double, smy: Double,
        semiMajor: Double, semiMinor: Double, rotDeg: Double,
        ecx: Double, ecy: Double
    ): Boolean {
        // Try sweep=true and sweep=false; pick the one whose midpoint is closer to the slerp midpoint
        val rotRad = Math.toRadians(rotDeg)
        val a1 = ellipseAngle(s1x - ecx, s1y - ecy, rotRad, semiMajor, semiMinor)
        val a2 = ellipseAngle(s2x - ecx, s2y - ecy, rotRad, semiMajor, semiMinor)
        val am = ellipseAngle(smx - ecx, smy - ecy, rotRad, semiMajor, semiMinor)

        // Sweep=true means angle increases from start to end (in JavaFX this is clockwise)
        // Check which direction from a1 to a2 contains am
        val diffForward = normalizeAngle(a2 - a1) // positive, 0..2PI
        val diffMidForward = normalizeAngle(am - a1)
        val midInForward = diffMidForward <= diffForward

        // sweep=true in JavaFX means clockwise (positive angle direction in screen coords)
        return midInForward
    }

    private fun ellipseAngle(dx: Double, dy: Double, rotRad: Double, semiMajor: Double, semiMinor: Double): Double {
        val cosR = cos(rotRad); val sinR = sin(rotRad)
        val lx = cosR * dx + sinR * dy
        val ly = -sinR * dx + cosR * dy
        return atan2(ly / semiMinor.coerceAtLeast(0.01), lx / semiMajor.coerceAtLeast(0.01))
    }

    private fun normalizeAngleDiff(a: Double): Double {
        var r = a
        while (r > PI) r -= 2 * PI
        while (r < -PI) r += 2 * PI
        return r
    }

    private fun normalizeAngle(a: Double): Double {
        var r = a
        while (r < 0) r += 2 * PI
        while (r >= 2 * PI) r -= 2 * PI
        return r
    }
}
