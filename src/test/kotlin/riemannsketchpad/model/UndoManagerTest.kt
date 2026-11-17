package riemannsketchpad.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import riemannsketchpad.geometry.*

class UndoManagerTest {

    private val tol = 1e-9

    @Test
    fun `undo restores previous state`() {
        val mgr = GeometricObjectManager()
        val undo = UndoManager(mgr)

        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0))
        undo.checkpoint()

        mgr.addObject(FreePoint(1, 0.0, 1.0, 0.0))
        undo.checkpoint()

        assertEquals(2, mgr.getNextId())
        undo.undo()
        assertEquals(1, mgr.getNextId())
        assertNull(mgr.getObject(1))
    }

    @Test
    fun `redo restores undone state`() {
        val mgr = GeometricObjectManager()
        val undo = UndoManager(mgr)

        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0))
        undo.checkpoint()

        mgr.addObject(FreePoint(1, 0.0, 1.0, 0.0))
        undo.checkpoint()

        undo.undo()
        assertNull(mgr.getObject(1))

        undo.redo()
        assertNotNull(mgr.getObject(1))
        assertEquals(2, mgr.getNextId())
    }

    @Test
    fun `new checkpoint truncates redo stack`() {
        val mgr = GeometricObjectManager()
        val undo = UndoManager(mgr)

        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0))
        undo.checkpoint()

        mgr.addObject(FreePoint(1, 0.0, 1.0, 0.0))
        undo.checkpoint()

        undo.undo()
        // Now add a different point — should truncate the redo
        mgr.addObject(FreePoint(1, 0.0, 0.0, 1.0))
        undo.checkpoint()

        assertFalse(undo.canRedo)
    }

    @Test
    fun `canUndo and canRedo`() {
        val mgr = GeometricObjectManager()
        val undo = UndoManager(mgr)

        assertFalse(undo.canUndo)
        assertFalse(undo.canRedo)

        undo.checkpoint()
        assertFalse(undo.canUndo) // only one checkpoint
        assertFalse(undo.canRedo)

        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0))
        undo.checkpoint()
        assertTrue(undo.canUndo)
        assertFalse(undo.canRedo)
    }

    @Test
    fun `unsaved changes detection`() {
        val mgr = GeometricObjectManager()
        val undo = UndoManager(mgr)

        undo.checkpoint()
        undo.markSaved()
        assertFalse(undo.hasUnsavedChanges)

        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0))
        undo.checkpoint()
        assertTrue(undo.hasUnsavedChanges)

        undo.markSaved()
        assertFalse(undo.hasUnsavedChanges)

        undo.undo()
        assertTrue(undo.hasUnsavedChanges)

        undo.redo()
        assertFalse(undo.hasUnsavedChanges)
    }

    @Test
    fun `minIndex prevents undoing past recording start`() {
        val mgr = GeometricObjectManager()
        val undo = UndoManager(mgr)

        // Initial state checkpoint
        undo.checkpoint()

        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0))
        undo.checkpoint()

        // Start recording — set min at current index (1)
        undo.setMinIndex()

        mgr.addObject(FreePoint(1, 0.0, 1.0, 0.0))
        undo.checkpoint()

        mgr.addObject(FreePoint(2, 0.0, 0.0, 1.0))
        undo.checkpoint()

        // Can undo within recording, but not past minIndex
        assertTrue(undo.canUndo)
        undo.undo() // index 3 -> 2
        assertTrue(undo.canUndo)
        undo.undo() // index 2 -> 1 (at minIndex)
        assertFalse(undo.canUndo) // can't go below minIndex

        undo.clearMinIndex()
        assertTrue(undo.canUndo) // can now undo past old min
    }

    @Test
    fun `reset clears everything`() {
        val mgr = GeometricObjectManager()
        val undo = UndoManager(mgr)

        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0))
        undo.checkpoint()
        undo.markSaved()

        undo.reset()
        assertFalse(undo.canUndo)
        assertFalse(undo.canRedo)
        assertFalse(undo.hasUnsavedChanges) // both -1
    }
}
