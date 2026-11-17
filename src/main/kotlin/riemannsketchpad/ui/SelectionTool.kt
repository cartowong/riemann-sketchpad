package riemannsketchpad.ui

import javafx.scene.Group
import javafx.scene.input.MouseEvent
import riemannsketchpad.geometry.SphericalUtils
import riemannsketchpad.model.GeometricObjectManager
import riemannsketchpad.model.ManagerState
import riemannsketchpad.model.UndoManager

class SelectionTool(
    private val canvas: SphereCanvasPane,
    private val manager: GeometricObjectManager,
    private val undoManager: UndoManager
) : Tool {

    private var dragging = false
    private var dragSnapshot: ManagerState? = null
    private var didDrag = false
    private var pendingDeselect: Int? = null

    override fun onMousePressed(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {
        val hit = canvas.hitTester.hitTest(event.x, event.y)
        val isModifier = event.isShortcutDown

        val clickedId = when (hit) {
            is HitResult.ConstructedPoint -> hit.id
            is HitResult.OnAngle -> hit.id
            is HitResult.OnGeodesic -> hit.id
            is HitResult.OnCircle -> hit.id
            is HitResult.OnRegion -> hit.id
            is HitResult.IntersectionPreview -> null
            null -> null
        }

        pendingDeselect = null

        if (clickedId == null) {
            // Click on empty canvas: clear selection
            canvas.selectedIds.clear()
            canvas.render()
            return
        }

        if (isModifier) {
            if (clickedId in canvas.selectedIds) {
                // Defer removal to release — user may intend to drag
                pendingDeselect = clickedId
            } else {
                canvas.selectedIds.add(clickedId)
            }
        } else {
            if (clickedId in canvas.selectedIds) {
                // Already selected — defer deselect-toggle to release so drag can start
                if (canvas.selectedIds.size == 1) {
                    pendingDeselect = clickedId
                }
                // If multiple selected, narrow to this one for dragging
                if (canvas.selectedIds.size > 1) {
                    canvas.selectedIds.clear()
                    canvas.selectedIds.add(clickedId)
                }
            } else {
                // Select only this object
                canvas.selectedIds.clear()
                canvas.selectedIds.add(clickedId)
            }
        }

        // Prepare for potential drag
        val allMovable = canvas.selectedIds.all { manager.isMovable(it) }
        if (allMovable && canvas.selectedIds.isNotEmpty()) {
            dragSnapshot = manager.snapshot()
            dragging = true
            didDrag = false
        }

        canvas.render()
    }

    override fun onMouseDragged(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {
        if (!dragging) return
        if (canvas.selectedIds.isEmpty()) return

        // Check all selected are still movable
        if (!canvas.selectedIds.all { manager.isMovable(it) }) return

        didDrag = true

        // Move each selected point
        for (id in canvas.selectedIds) {
            manager.movePoint(id, worldX, worldY, worldZ)
        }

        canvas.hitTester.rebuildIntersectionCache()
        canvas.render()
    }

    override fun onMouseReleased(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {
        if (dragging && didDrag) {
            undoManager.checkpoint()
            canvas.hitTester.rebuildIntersectionCache()
        }

        // Apply deferred deselect only if no drag occurred
        if (!didDrag && pendingDeselect != null) {
            canvas.selectedIds.remove(pendingDeselect!!)
            canvas.render()
        }

        dragging = false
        dragSnapshot = null
        didDrag = false
        pendingDeselect = null
    }

    override fun deactivate(previewGroup: Group) {
        dragging = false
        dragSnapshot = null
        didDrag = false
        pendingDeselect = null
    }
}
