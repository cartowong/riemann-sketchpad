package riemannsketchpad.ui

import javafx.scene.Group
import javafx.scene.input.MouseEvent

interface Tool {
    fun activate() {}
    fun deactivate(previewGroup: Group) {}
    fun onMousePressed(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {}
    fun onMouseMoved(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {}
    fun onMouseDragged(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {}
    fun onMouseReleased(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {}
    fun onEscapePressed(previewGroup: Group): Boolean = false
}
