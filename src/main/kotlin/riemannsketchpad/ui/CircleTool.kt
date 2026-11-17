package riemannsketchpad.ui

import javafx.scene.Group
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import javafx.scene.shape.Ellipse
import riemannsketchpad.geometry.*
import riemannsketchpad.model.GeometricObjectManager
import riemannsketchpad.model.ManagerState
import riemannsketchpad.model.ObjectMetadata
import riemannsketchpad.model.StyleSettings
import riemannsketchpad.model.UndoManager

class CircleTool(
    private val canvas: SphereCanvasPane,
    private val manager: GeometricObjectManager,
    private val undoManager: UndoManager,
    private val styleSettings: StyleSettings
) : Tool {

    private var centerPointId: Int? = null
    private var preConstructionSnapshot: ManagerState? = null

    override fun onMousePressed(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {
        val hit = canvas.hitTester.hitTest(event.x, event.y)

        if (centerPointId == null) {
            preConstructionSnapshot = manager.snapshot()
            centerPointId = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, worldZ, manager, canvas.hitTester, styleSettings)
            canvas.hitTester.rebuildIntersectionCache()
            canvas.render()
        } else {
            val radiusId = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, worldZ, manager, canvas.hitTester, styleSettings)

            if (radiusId == centerPointId) return

            val center = manager.getObject(centerPointId!!) as? PointLike ?: return
            val radius = manager.getObject(radiusId) as? PointLike ?: return
            if (SphericalUtils.isAntipodal(center.x, center.y, center.z, radius.x, radius.y, radius.z)) return

            val nextId = manager.peekNextId()
            val circle = Circle(nextId, centerPointId!!, radiusId)
            val meta = ObjectMetadata()
            styleSettings.applyDefaults(meta, circle)
            manager.addObject(circle, meta)
            circle.recompute(manager.getObjects())

            undoManager.checkpoint()
            centerPointId = null
            preConstructionSnapshot = null
            canvas.previewGroup.children.clear()
            canvas.hitTester.rebuildIntersectionCache()
            canvas.render()
        }
    }

    override fun onMouseMoved(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {
        canvas.previewGroup.children.clear()

        val hit = canvas.hitTester.hitTest(event.x, event.y)

        // Intersection preview ghost dot
        if (hit is HitResult.IntersectionPreview) {
            val ghost = javafx.scene.shape.Circle(hit.candidate.screenX, hit.candidate.screenY, 4.0)
            ghost.fill = Color.BLACK
            ghost.opacity = 0.4
            ghost.isMouseTransparent = true
            canvas.previewGroup.children.add(ghost)
        }

        if (centerPointId == null) return

        val center = manager.getObject(centerPointId!!) as? PointLike ?: return

        // Determine target coordinates (snap to intersection or constructed point)
        val targetX: Double; val targetY: Double; val targetZ: Double
        when (hit) {
            is HitResult.ConstructedPoint -> {
                val pt = manager.getObject(hit.id) as? PointLike
                if (pt != null) { targetX = pt.x; targetY = pt.y; targetZ = pt.z }
                else { targetX = worldX; targetY = worldY; targetZ = worldZ }
            }
            is HitResult.IntersectionPreview -> {
                val coords = canvas.screenToModel(hit.candidate.screenX, hit.candidate.screenY)
                if (coords != null) { targetX = coords.first; targetY = coords.second; targetZ = coords.third }
                else { targetX = worldX; targetY = worldY; targetZ = worldZ }
            }
            else -> { targetX = worldX; targetY = worldY; targetZ = worldZ }
        }

        val cosR = SphericalUtils.dot(center.x, center.y, center.z, targetX, targetY, targetZ).coerceIn(-1.0, 1.0)

        val vm = canvas.getViewMatrix()
        val params = SphericalUtils.smallCircleEllipseParams(center.x, center.y, center.z, cosR, vm)
        val scaledMajor = params.semiMajor * canvas.displayScale
        val scaledMinor = params.semiMinor * canvas.displayScale
        val ecx = canvas.diskCenterXValue + params.centerX * canvas.displayScale
        val ecy = canvas.diskCenterYValue - params.centerY * canvas.displayScale
        val rotDeg = -Math.toDegrees(params.rotationAngle)

        if (scaledMajor > 0.5) {
            val ellipse = Ellipse(ecx, ecy, scaledMajor, scaledMinor)
            ellipse.fill = Color.TRANSPARENT
            ellipse.stroke = Color.BLACK
            ellipse.strokeWidth = 2.0
            ellipse.opacity = 0.4
            ellipse.rotate = rotDeg
            ellipse.isMouseTransparent = true
            canvas.previewGroup.children.add(ellipse)
        }
    }

    override fun onEscapePressed(previewGroup: Group): Boolean {
        if (centerPointId != null) {
            preConstructionSnapshot?.let { manager.restore(it) }
            centerPointId = null
            preConstructionSnapshot = null
            previewGroup.children.clear()
            return true
        }
        return false
    }

    override fun deactivate(previewGroup: Group) {
        if (centerPointId != null) {
            preConstructionSnapshot?.let { manager.restore(it) }
        }
        centerPointId = null
        preConstructionSnapshot = null
        previewGroup.children.clear()
    }
}
