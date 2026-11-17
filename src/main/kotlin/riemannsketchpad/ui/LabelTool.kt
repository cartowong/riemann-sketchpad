package riemannsketchpad.ui

import javafx.scene.Group
import javafx.scene.input.MouseEvent
import riemannsketchpad.geometry.*
import riemannsketchpad.model.GeometricObjectManager
import riemannsketchpad.model.UndoManager
import kotlin.math.abs
import kotlin.math.hypot

class LabelTool(
    private val canvas: SphereCanvasPane,
    private val manager: GeometricObjectManager,
    private val undoManager: UndoManager
) : Tool {

    companion object {
        private const val DRAG_THRESHOLD = 3.0
    }

    private var dragging = false
    private var dragLabelId: Int? = null
    private var dragStartX = 0.0
    private var dragStartY = 0.0
    private var dragStartOffsetX = 0.0
    private var dragStartOffsetY = 0.0
    private var didDrag = false

    override fun onMousePressed(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {
        dragStartX = event.x
        dragStartY = event.y
        didDrag = false

        // Check if clicking on a visible label first
        val labelId = hitTestLabel(event.x, event.y)
        if (labelId != null) {
            val meta = manager.getMetadata(labelId)
            if (meta != null) {
                dragLabelId = labelId
                dragStartOffsetX = meta.labelOffsetX
                dragStartOffsetY = meta.labelOffsetY
                dragging = true
                return
            }
        }

        // Check if clicking on an object
        val hit = canvas.hitTester.hitTest(event.x, event.y)
        val objId = when (hit) {
            is HitResult.ConstructedPoint -> hit.id
            is HitResult.OnAngle -> hit.id
            is HitResult.OnGeodesic -> hit.id
            is HitResult.OnCircle -> hit.id
            is HitResult.OnRegion -> hit.id
            else -> null
        }

        if (objId != null) {
            dragLabelId = objId
            val meta = manager.getMetadata(objId)
            if (meta != null) {
                dragStartOffsetX = meta.labelOffsetX
                dragStartOffsetY = meta.labelOffsetY
                dragging = true
            }
        }
    }

    override fun onMouseDragged(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {
        if (!dragging || dragLabelId == null) return

        val dx = event.x - dragStartX
        val dy = event.y - dragStartY
        if (hypot(dx, dy) < DRAG_THRESHOLD && !didDrag) return

        didDrag = true
        val meta = manager.getMetadata(dragLabelId!!) ?: return
        meta.labelOffsetX = dragStartOffsetX + dx
        meta.labelOffsetY = dragStartOffsetY + dy
        canvas.render()
    }

    override fun onMouseReleased(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {
        val id = dragLabelId
        if (id != null) {
            if (didDrag) {
                // Drag complete — checkpoint
                undoManager.checkpoint()
            } else {
                // Click — toggle label
                val meta = manager.getMetadata(id) ?: return
                // Check if this was a click on a visible label
                val clickedOnLabel = hitTestLabel(event.x, event.y) == id
                if (clickedOnLabel && meta.labelVisible) {
                    // Hide the label
                    meta.labelVisible = false
                } else if (meta.label == null) {
                    // Assign default label
                    meta.label = manager.nextDefaultLabel()
                    meta.labelVisible = true
                } else if (meta.labelVisible) {
                    // Already visible — hide
                    meta.labelVisible = false
                } else {
                    // Hidden — re-show
                    meta.labelVisible = true
                }
                undoManager.checkpoint()
            }
            canvas.render()
        }
        dragging = false
        dragLabelId = null
        didDrag = false
    }

    private fun hitTestLabel(screenX: Double, screenY: Double): Int? {
        val objects = manager.getObjects()
        val metadata = manager.getMetadata()
        val vm = canvas.getViewMatrix()

        for ((id, obj) in objects) {
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked || !meta.labelVisible || !(obj as GeometricObject).isDefined) continue
            val label = meta.label ?: continue

            val anchor = canvas.renderer.computeLabelAnchor(obj, meta, vm, canvas.displayScale, canvas.diskCenterXValue, canvas.diskCenterYValue)
                ?: continue
            if (!anchor.third) continue // back hemisphere

            val labelX = anchor.first + meta.labelOffsetX
            val labelY = anchor.second + meta.labelOffsetY
            // Approximate label bounding box
            val labelWidth = label.length * 8.0
            val labelHeight = 14.0
            if (screenX >= labelX && screenX <= labelX + labelWidth &&
                screenY >= labelY - labelHeight && screenY <= labelY
            ) {
                return id
            }
        }
        return null
    }

    override fun deactivate(previewGroup: Group) {
        dragging = false
        dragLabelId = null
        didDrag = false
    }
}
