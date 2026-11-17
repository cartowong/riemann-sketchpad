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
import riemannsketchpad.model.StyleUtils
import riemannsketchpad.model.UndoManager
import kotlin.math.*

class AngleTool(
    private val canvas: SphereCanvasPane,
    private val manager: GeometricObjectManager,
    private val undoManager: UndoManager,
    private val styleSettings: StyleSettings
) : Tool {

    private companion object {
        val PREVIEW_FILL = Color.web("#4CAF50", 0.2)
        val PREVIEW_STROKE = Color.web("#4CAF50", 0.5)
    }

    private var pointAId: Int? = null
    private var vertexId: Int? = null
    private var preConstructionSnapshot: ManagerState? = null

    // Cached 3D coordinates for preview rendering
    private var pointAX = 0.0; private var pointAY = 0.0; private var pointAZ = 0.0
    private var vertexWX = 0.0; private var vertexWY = 0.0; private var vertexWZ = 0.0

    override fun onMousePressed(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {
        val hit = canvas.hitTester.hitTest(event.x, event.y)

        when {
            pointAId == null -> {
                preConstructionSnapshot = manager.snapshot()
                val id = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, worldZ, manager, canvas.hitTester, styleSettings)
                pointAId = id
                val pt = manager.getObject(id) as? PointLike
                if (pt != null) { pointAX = pt.x; pointAY = pt.y; pointAZ = pt.z }
                canvas.hitTester.rebuildIntersectionCache()
                canvas.render()
            }
            vertexId == null -> {
                val id = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, worldZ, manager, canvas.hitTester, styleSettings)
                if (id == pointAId) return
                vertexId = id
                val pt = manager.getObject(id) as? PointLike
                if (pt != null) { vertexWX = pt.x; vertexWY = pt.y; vertexWZ = pt.z }
                canvas.hitTester.rebuildIntersectionCache()
                canvas.render()
            }
            else -> {
                val pointCId = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, worldZ, manager, canvas.hitTester, styleSettings)
                if (pointCId == vertexId || pointCId == pointAId) return

                val nextId = manager.peekNextId()
                val angle = Angle(nextId, pointAId!!, vertexId!!, pointCId)
                val meta = ObjectMetadata()
                styleSettings.applyDefaults(meta, angle)
                manager.addObject(angle, meta)
                angle.recompute(manager.getObjects())

                undoManager.checkpoint()
                pointAId = null
                vertexId = null
                preConstructionSnapshot = null
                canvas.previewGroup.children.clear()
                canvas.hitTester.rebuildIntersectionCache()
                canvas.render()
            }
        }
    }

    override fun onMouseMoved(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {
        canvas.previewGroup.children.clear()

        val hit = canvas.hitTester.hitTest(event.x, event.y)

        // Intersection preview ghost dot
        if (hit is HitResult.IntersectionPreview) {
            val ghost = javafx.scene.shape.Circle(hit.candidate.screenX, hit.candidate.screenY, 4.0)
            ghost.fill = Color.BLACK; ghost.opacity = 0.4; ghost.isMouseTransparent = true
            canvas.previewGroup.children.add(ghost)
        }

        // Angle arc preview during 3rd-point selection
        if (vertexId != null) {
            val targetX: Double
            val targetY: Double
            val targetZ: Double
            when (hit) {
                is HitResult.ConstructedPoint -> {
                    val pt = manager.getObject(hit.id) as? PointLike ?: return
                    targetX = pt.x; targetY = pt.y; targetZ = pt.z
                }
                is HitResult.IntersectionPreview -> {
                    // Unproject the intersection screen position to world
                    val coords = canvas.screenToModel(hit.candidate.screenX, hit.candidate.screenY) ?: return
                    targetX = coords.first; targetY = coords.second; targetZ = coords.third
                }
                else -> {
                    targetX = worldX; targetY = worldY; targetZ = worldZ
                }
            }

            // Compute tangent vectors at vertex toward A and toward target
            val tA = SphericalUtils.tangentVector(vertexWX, vertexWY, vertexWZ, pointAX, pointAY, pointAZ) ?: return
            val tC = SphericalUtils.tangentVector(vertexWX, vertexWY, vertexWZ, targetX, targetY, targetZ) ?: return

            val vm = canvas.getViewMatrix()

            // Project tangent vectors to screen space (same approach as GeometryRenderer)
            val tAScreen = projectTangentToScreen(vertexWX, vertexWY, vertexWZ, tA.first, tA.second, tA.third, vm) ?: return
            val tCScreen = projectTangentToScreen(vertexWX, vertexWY, vertexWZ, tC.first, tC.second, tC.third, vm) ?: return

            // Vertex screen position
            val (vx, vy, vz) = SphericalUtils.projectToScreen(vertexWX, vertexWY, vertexWZ, vm)
            if (vz <= 0) return // vertex on back face
            val svx = canvas.diskCenterXValue + canvas.displayScale * vx
            val svy = canvas.diskCenterYValue - canvas.displayScale * vy

            val startAngle = atan2(-tAScreen.second, tAScreen.first)
            val endAngle = atan2(-tCScreen.second, tCScreen.first)

            // Compute sweep (matching renderAngle logic)
            val cross2D = tAScreen.first * (-tCScreen.second) - (-tAScreen.second) * tCScreen.first
            var sweep = endAngle - startAngle
            if (cross2D > 0 && sweep < 0) sweep += 2 * PI
            if (cross2D < 0 && sweep > 0) sweep -= 2 * PI

            // Compute 3D measure to match sweep magnitude
            val dotTC = SphericalUtils.dot(tA.first, tA.second, tA.third, tC.first, tC.second, tC.third).coerceIn(-1.0, 1.0)
            val crossX = SphericalUtils.crossX(tA.first, tA.second, tA.third, tC.first, tC.second, tC.third)
            val crossY = SphericalUtils.crossY(tA.first, tA.second, tA.third, tC.first, tC.second, tC.third)
            val crossZ = SphericalUtils.crossZ(tA.first, tA.second, tA.third, tC.first, tC.second, tC.third)
            val sinAngle = SphericalUtils.dot(crossX, crossY, crossZ, vertexWX, vertexWY, vertexWZ)
            val angle3D = atan2(-sinAngle, dotTC)
            val measureRadians = if (angle3D < 0) angle3D + 2 * PI else angle3D

            if ((measureRadians > PI) != (abs(sweep) > PI)) {
                sweep = if (sweep > 0) sweep - 2 * PI else sweep + 2 * PI
            }
            if (abs(sweep) < 1e-10) return

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
            path.fill = PREVIEW_FILL
            path.stroke = PREVIEW_STROKE
            path.strokeWidth = 1.0
            path.isMouseTransparent = true
            canvas.previewGroup.children.add(path)
        }
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

    override fun onEscapePressed(previewGroup: Group): Boolean {
        if (pointAId != null) {
            preConstructionSnapshot?.let { manager.restore(it) }
            pointAId = null
            vertexId = null
            preConstructionSnapshot = null
            previewGroup.children.clear()
            return true
        }
        return false
    }

    override fun deactivate(previewGroup: Group) {
        if (pointAId != null) {
            preConstructionSnapshot?.let { manager.restore(it) }
        }
        pointAId = null
        vertexId = null
        preConstructionSnapshot = null
        previewGroup.children.clear()
    }
}
