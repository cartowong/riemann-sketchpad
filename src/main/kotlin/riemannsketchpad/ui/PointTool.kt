package riemannsketchpad.ui

import javafx.scene.Group
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import riemannsketchpad.geometry.*
import riemannsketchpad.model.GeometricObjectManager
import riemannsketchpad.model.StyleSettings
import riemannsketchpad.model.UndoManager

class PointTool(
    private val canvas: SphereCanvasPane,
    private val manager: GeometricObjectManager,
    private val undoManager: UndoManager,
    private val styleSettings: StyleSettings
) : Tool {

    private var createdPointId: Int? = null
    private var didDrag = false

    override fun onMousePressed(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {
        val hit = canvas.hitTester.hitTest(event.x, event.y)

        // Don't create a point on an existing point
        if (hit is HitResult.ConstructedPoint) {
            createdPointId = null
            return
        }

        val id = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, worldZ, manager, canvas.hitTester, styleSettings)
        createdPointId = if (manager.isMovable(id)) id else null
        didDrag = false
        undoManager.checkpoint()
        canvas.hitTester.rebuildIntersectionCache()
        canvas.render()
    }

    override fun onMouseDragged(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {
        val id = createdPointId ?: return
        didDrag = true
        manager.movePoint(id, worldX, worldY, worldZ)
        canvas.hitTester.rebuildIntersectionCache()
        canvas.render()
    }

    override fun onMouseReleased(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {
        if (didDrag && createdPointId != null) {
            undoManager.checkpoint()
            canvas.hitTester.rebuildIntersectionCache()
        }
        createdPointId = null
        didDrag = false
    }

    override fun onMouseMoved(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {
        // Show ghost point at intersection preview
        canvas.previewGroup.children.clear()
        val hit = canvas.hitTester.hitTest(event.x, event.y)
        if (hit is HitResult.IntersectionPreview) {
            val ghost = Circle(hit.candidate.screenX, hit.candidate.screenY, 4.0)
            ghost.fill = Color.BLACK
            ghost.opacity = 0.4
            ghost.isMouseTransparent = true
            canvas.previewGroup.children.add(ghost)
        }
    }

    override fun deactivate(previewGroup: Group) {
        previewGroup.children.clear()
    }
}
