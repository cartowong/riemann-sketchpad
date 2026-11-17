package riemannsketchpad.ui

import javafx.scene.Group
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import javafx.scene.shape.ArcTo
import javafx.scene.shape.ClosePath
import javafx.scene.shape.LineTo
import javafx.scene.shape.MoveTo
import javafx.scene.shape.Path
import riemannsketchpad.geometry.*
import riemannsketchpad.model.GeometricObjectManager
import riemannsketchpad.model.ManagerState
import riemannsketchpad.model.ObjectMetadata
import riemannsketchpad.model.StyleSettings
import riemannsketchpad.model.UndoManager
import kotlin.math.hypot

class RegionTool(
    private val canvas: SphereCanvasPane,
    private val manager: GeometricObjectManager,
    private val undoManager: UndoManager,
    private val styleSettings: StyleSettings
) : Tool {

    private companion object {
        val EDGE_COLOR: Color = Color.web("#2196F3")
        const val EDGE_OPACITY = 0.4
        const val EDGE_STROKE_WIDTH = 2.0
    }

    private val vertexIds = mutableListOf<Int>()
    private var preConstructionSnapshot: ManagerState? = null

    override fun onMousePressed(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {
        val hit = canvas.hitTester.hitTest(event.x, event.y)

        // Check if closing the polygon (snap to first vertex)
        if (vertexIds.size >= 3 && hit is HitResult.ConstructedPoint && hit.id == vertexIds.first()) {
            // Close the region
            val nextId = manager.peekNextId()
            val region = Region(nextId, vertexIds.toList())
            val meta = ObjectMetadata()
            styleSettings.applyDefaults(meta, region)
            manager.addObject(region, meta)
            region.recompute(manager.getObjects())

            undoManager.checkpoint()
            vertexIds.clear()
            preConstructionSnapshot = null
            canvas.previewGroup.children.clear()
            canvas.hitTester.rebuildIntersectionCache()
            canvas.render()
            return
        }

        if (vertexIds.isEmpty()) {
            preConstructionSnapshot = manager.snapshot()
        }

        val id = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, worldZ, manager, canvas.hitTester, styleSettings)

        // Don't add duplicates
        if (vertexIds.isNotEmpty() && id == vertexIds.last()) return

        vertexIds.add(id)
        canvas.hitTester.rebuildIntersectionCache()
        canvas.render()
    }

    override fun onMouseMoved(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {
        if (vertexIds.isEmpty()) return

        canvas.previewGroup.children.clear()

        val hit = canvas.hitTester.hitTest(event.x, event.y)

        // Intersection preview ghost dot
        if (hit is HitResult.IntersectionPreview) {
            val ghost = javafx.scene.shape.Circle(hit.candidate.screenX, hit.candidate.screenY, 4.0)
            ghost.fill = Color.BLACK; ghost.opacity = 0.4; ghost.isMouseTransparent = true
            canvas.previewGroup.children.add(ghost)
        }

        // Project all existing vertices — store both 3D and screen coords
        val vm = canvas.getViewMatrix()
        val displayScale = canvas.displayScale
        val diskCenterX = canvas.diskCenterXValue
        val diskCenterY = canvas.diskCenterYValue
        val points3d = vertexIds.mapNotNull { id ->
            val pt = manager.getObject(id) as? PointLike ?: return@mapNotNull null
            Triple(pt.x, pt.y, pt.z)
        }
        if (points3d.size != vertexIds.size) return
        val screenPositions = points3d.map { (x, y, z) ->
            val (px, py, _) = SphericalUtils.projectToScreen(x, y, z, vm)
            Pair(diskCenterX + displayScale * px, diskCenterY - displayScale * py)
        }

        // Determine cursor target screen position
        val cursorSx: Double
        val cursorSy: Double
        when (hit) {
            is HitResult.ConstructedPoint -> {
                val pt = manager.getObject(hit.id) as? PointLike
                if (pt != null) {
                    val (px, py, _) = SphericalUtils.projectToScreen(pt.x, pt.y, pt.z, vm)
                    cursorSx = diskCenterX + displayScale * px
                    cursorSy = diskCenterY - displayScale * py
                } else {
                    cursorSx = event.x; cursorSy = event.y
                }
            }
            is HitResult.IntersectionPreview -> {
                cursorSx = hit.candidate.screenX; cursorSy = hit.candidate.screenY
            }
            else -> {
                cursorSx = event.x; cursorSy = event.y
            }
        }

        // Check if closing
        val firstPos = screenPositions[0]
        val isClosing = vertexIds.size >= 3 && hypot(cursorSx - firstPos.first, cursorSy - firstPos.second) < HitTester.SNAP_RADIUS

        // Resolve cursor 3D coordinates for geodesic arc computation
        val end3d = when {
            isClosing -> points3d[0]
            hit is HitResult.ConstructedPoint -> {
                val pt = manager.getObject(hit.id) as? PointLike
                if (pt != null) Triple(pt.x, pt.y, pt.z) else Triple(worldX, worldY, worldZ)
            }
            hit is HitResult.IntersectionPreview -> {
                val coords = canvas.screenToModel(hit.candidate.screenX, hit.candidate.screenY)
                coords ?: Triple(worldX, worldY, worldZ)
            }
            else -> Triple(worldX, worldY, worldZ)
        }
        val endSx = if (isClosing) firstPos.first else cursorSx
        val endSy = if (isClosing) firstPos.second else cursorSy

        // Draw existing edges between consecutive vertices as geodesic arcs
        for (i in 0 until screenPositions.size - 1) {
            val edge = Path()
            edge.elements.add(MoveTo(screenPositions[i].first, screenPositions[i].second))
            appendGeodesicArc(edge, points3d[i], screenPositions[i],
                points3d[i + 1], screenPositions[i + 1],
                vm, displayScale, diskCenterX, diskCenterY)
            edge.fill = Color.TRANSPARENT
            edge.stroke = EDGE_COLOR; edge.strokeWidth = EDGE_STROKE_WIDTH; edge.opacity = EDGE_OPACITY
            edge.isMouseTransparent = true
            canvas.previewGroup.children.add(edge)
        }

        // Preview edge from last vertex to cursor or closing point
        val previewEdge = Path()
        previewEdge.elements.add(MoveTo(screenPositions.last().first, screenPositions.last().second))
        appendGeodesicArc(previewEdge, points3d.last(), screenPositions.last(),
            end3d, Pair(endSx, endSy),
            vm, displayScale, diskCenterX, diskCenterY)
        previewEdge.fill = Color.TRANSPARENT
        previewEdge.stroke = EDGE_COLOR; previewEdge.strokeWidth = EDGE_STROKE_WIDTH; previewEdge.opacity = EDGE_OPACITY
        previewEdge.isMouseTransparent = true
        canvas.previewGroup.children.add(previewEdge)

        // Closing highlight with geodesic arc fill
        if (isClosing) {
            val fillPath = Path()
            fillPath.elements.add(MoveTo(screenPositions[0].first, screenPositions[0].second))
            for (i in 0 until screenPositions.size) {
                val j = (i + 1) % screenPositions.size
                appendGeodesicArc(fillPath, points3d[i], screenPositions[i],
                    points3d[j], screenPositions[j],
                    vm, displayScale, diskCenterX, diskCenterY)
            }
            fillPath.elements.add(ClosePath())
            fillPath.fill = Color.web("#FFEB3B", 0.3)
            fillPath.stroke = null
            fillPath.isMouseTransparent = true
            canvas.previewGroup.children.add(fillPath)

            val highlight = javafx.scene.shape.Circle(firstPos.first, firstPos.second, 6.0)
            highlight.fill = Color.TRANSPARENT
            highlight.stroke = EDGE_COLOR
            highlight.strokeWidth = 2.0
            highlight.isMouseTransparent = true
            canvas.previewGroup.children.add(highlight)
        }
    }

    override fun onEscapePressed(previewGroup: Group): Boolean {
        if (vertexIds.isNotEmpty()) {
            preConstructionSnapshot?.let { manager.restore(it) }
            vertexIds.clear()
            preConstructionSnapshot = null
            previewGroup.children.clear()
            return true
        }
        return false
    }

    override fun deactivate(previewGroup: Group) {
        if (vertexIds.isNotEmpty()) {
            preConstructionSnapshot?.let { manager.restore(it) }
        }
        vertexIds.clear()
        preConstructionSnapshot = null
        previewGroup.children.clear()
    }

    private fun appendGeodesicArc(
        path: Path,
        p1: Triple<Double, Double, Double>, s1: Pair<Double, Double>,
        p2: Triple<Double, Double, Double>, s2: Pair<Double, Double>,
        vm: DoubleArray, displayScale: Double, diskCenterX: Double, diskCenterY: Double
    ) {
        val normal = SphericalUtils.greatCircleNormal(p1.first, p1.second, p1.third, p2.first, p2.second, p2.third)
        if (normal == null) { path.elements.add(LineTo(s2.first, s2.second)); return }
        val params = SphericalUtils.ellipseProjectionParams(normal.first, normal.second, normal.third, vm)
        val scaledMajor = params.semiMajor * displayScale
        val scaledMinor = params.semiMinor * displayScale
        val rotDeg = -Math.toDegrees(params.rotationAngle)
        if (scaledMinor < 0.5) { path.elements.add(LineTo(s2.first, s2.second)); return }

        val (mx, my, mz) = SphericalUtils.slerp(p1.first, p1.second, p1.third, p2.first, p2.second, p2.third, 0.5)
        val (pmx, pmy, _) = SphericalUtils.projectToScreen(mx, my, mz, vm)
        val smx = diskCenterX + displayScale * pmx
        val smy = diskCenterY - displayScale * pmy
        val ecx = diskCenterX + params.centerX * displayScale
        val ecy = diskCenterY - params.centerY * displayScale

        val largeArc = isLargeArc(s1.first, s1.second, s2.first, s2.second, smx, smy, ecx, ecy, scaledMajor, scaledMinor, rotDeg)
        val sweep = isSweep(s1.first, s1.second, s2.first, s2.second, smx, smy, ecx, ecy, scaledMajor, scaledMinor, rotDeg)
        path.elements.add(ArcTo(scaledMajor, scaledMinor, rotDeg, s2.first, s2.second, largeArc, sweep))
    }

    private fun isLargeArc(s1x: Double, s1y: Double, s2x: Double, s2y: Double, smx: Double, smy: Double, ecx: Double, ecy: Double, a: Double, b: Double, rotDeg: Double): Boolean {
        val rotRad = Math.toRadians(rotDeg)
        val a1 = eAngle(s1x - ecx, s1y - ecy, rotRad, a, b)
        val a2 = eAngle(s2x - ecx, s2y - ecy, rotRad, a, b)
        val am = eAngle(smx - ecx, smy - ecy, rotRad, a, b)
        val shortArc = normDiff(a2 - a1)
        val midInShort = normDiff(am - a1)
        val inShort = if (shortArc > 0) midInShort in 0.0..shortArc else midInShort in shortArc..0.0
        return !inShort
    }

    private fun isSweep(s1x: Double, s1y: Double, s2x: Double, s2y: Double, smx: Double, smy: Double, ecx: Double, ecy: Double, a: Double, b: Double, rotDeg: Double): Boolean {
        val rotRad = Math.toRadians(rotDeg)
        val a1 = eAngle(s1x - ecx, s1y - ecy, rotRad, a, b)
        val a2 = eAngle(s2x - ecx, s2y - ecy, rotRad, a, b)
        val am = eAngle(smx - ecx, smy - ecy, rotRad, a, b)
        val diffForward = normAngle(a2 - a1)
        val diffMidForward = normAngle(am - a1)
        return diffMidForward <= diffForward
    }

    private fun eAngle(dx: Double, dy: Double, rotRad: Double, a: Double, b: Double): Double {
        val c = kotlin.math.cos(rotRad); val s = kotlin.math.sin(rotRad)
        val lx = c * dx + s * dy; val ly = -s * dx + c * dy
        return kotlin.math.atan2(ly / b.coerceAtLeast(0.01), lx / a.coerceAtLeast(0.01))
    }

    private fun normDiff(a: Double): Double {
        var r = a; while (r > Math.PI) r -= 2 * Math.PI; while (r < -Math.PI) r += 2 * Math.PI; return r
    }

    private fun normAngle(a: Double): Double {
        var r = a; while (r < 0) r += 2 * Math.PI; while (r >= 2 * Math.PI) r -= 2 * Math.PI; return r
    }
}
