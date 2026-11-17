package riemannsketchpad.model

import riemannsketchpad.geometry.*

class ConstructionRecorder(
    private val manager: GeometricObjectManager,
    private val undoManager: UndoManager
) {
    var isRecording: Boolean = false
        private set

    private var baselineNextId: Int = 0

    /**
     * Start recording. All existing visible PointLike, GeodesicLike, and Circle objects
     * are marked as inputs. All other objects are locked.
     */
    fun startRecording() {
        isRecording = true
        baselineNextId = manager.getNextId()

        for ((id, obj) in manager.getObjects()) {
            val meta = manager.getMetadata(id) ?: continue
            if (meta.visible && (obj is PointLike || obj is GeodesicLike || obj is Circle)) {
                meta.isInput = true
            } else {
                meta.isLocked = true
            }
        }

        undoManager.checkpoint()
        undoManager.setMinIndex()
    }

    /**
     * Cancel recording. Remove all objects created during recording, clear flags.
     */
    fun cancelRecording() {
        if (!isRecording) return

        // Remove all objects with ID >= baselineNextId
        val toRemove = manager.getObjects().keys.filter { it >= baselineNextId }
        for (id in toRemove) {
            manager.removeWithDescendants(id)
        }

        // Clear all flags
        clearFlags()
        undoManager.clearMinIndex()
        isRecording = false
    }

    /**
     * End recording (preparing for save). Clears flags.
     * The caller is responsible for serializing and saving the construction.
     */
    fun endRecording() {
        if (!isRecording) return
        clearFlags()
        undoManager.clearMinIndex()
        isRecording = false
    }

    private fun clearFlags() {
        for ((_, meta) in manager.getMetadata()) {
            meta.isInput = false
            meta.isLocked = false
        }
    }
}
