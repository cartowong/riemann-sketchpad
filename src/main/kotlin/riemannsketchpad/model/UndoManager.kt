package riemannsketchpad.model

class UndoManager(private val manager: GeometricObjectManager) {
    private val history: MutableList<ManagerState> = mutableListOf()
    private var currentIndex: Int = -1
    private var savedIndex: Int = -1
    var minIndex: Int = -1
        private set

    val canUndo: Boolean get() = currentIndex > 0 && currentIndex > minIndex
    val canRedo: Boolean get() = currentIndex < history.size - 1
    val hasUnsavedChanges: Boolean get() = currentIndex != savedIndex

    /**
     * Create a checkpoint of the current state.
     */
    fun checkpoint() {
        // Truncate redo history
        while (history.size > currentIndex + 1) {
            history.removeAt(history.size - 1)
        }
        history.add(manager.snapshot())
        currentIndex = history.size - 1
        // If savedIndex is beyond the truncated portion, it's lost
        if (savedIndex > currentIndex) {
            savedIndex = -2 // unreachable
        }
    }

    /**
     * Undo to the previous state.
     */
    fun undo() {
        if (!canUndo) return
        currentIndex--
        manager.restore(history[currentIndex])
    }

    /**
     * Redo to the next state.
     */
    fun redo() {
        if (!canRedo) return
        currentIndex++
        manager.restore(history[currentIndex])
    }

    /**
     * Mark the current state as saved.
     */
    fun markSaved() {
        savedIndex = currentIndex
    }

    /**
     * Set the minimum undo index (for recording mode).
     */
    fun setMinIndex() {
        minIndex = currentIndex
    }

    /**
     * Clear the minimum index constraint.
     */
    fun clearMinIndex() {
        minIndex = -1
    }

    /**
     * Reset undo history entirely (e.g., on New or Open).
     */
    fun reset() {
        history.clear()
        currentIndex = -1
        savedIndex = -1
        minIndex = -1
    }
}
