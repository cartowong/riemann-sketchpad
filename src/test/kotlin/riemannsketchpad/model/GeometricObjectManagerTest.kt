package riemannsketchpad.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import riemannsketchpad.geometry.*
import kotlin.math.*

class GeometricObjectManagerTest {

    private val tol = 1e-9

    @Test
    fun `addObject assigns sequential IDs`() {
        val mgr = GeometricObjectManager()
        val id0 = mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0))
        val id1 = mgr.addObject(FreePoint(1, 0.0, 1.0, 0.0))
        assertEquals(0, id0)
        assertEquals(1, id1)
        assertEquals(2, mgr.getNextId())
    }

    @Test
    fun `propagateFrom recomputes dependent objects`() {
        val mgr = GeometricObjectManager()
        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0))
        mgr.addObject(FreePoint(1, 0.0, 1.0, 0.0))
        mgr.addObject(Midpoint(2, 0, 1))
        mgr.recomputeAll()

        val mid = mgr.getObject(2) as Midpoint
        assertTrue(mid.isDefined)
        val s2 = 1.0 / sqrt(2.0)
        assertEquals(s2, mid.x, tol)
        assertEquals(s2, mid.y, tol)
    }

    @Test
    fun `movePoint updates position and propagates`() {
        val mgr = GeometricObjectManager()
        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0))
        mgr.addObject(FreePoint(1, 0.0, 1.0, 0.0))
        mgr.addObject(Midpoint(2, 0, 1))
        mgr.recomputeAll()

        // Move p0 to (0,0,1)
        mgr.movePoint(0, 0.0, 0.0, 1.0)
        val p0 = mgr.getObject(0) as FreePoint
        assertEquals(0.0, p0.x, tol)
        assertEquals(0.0, p0.y, tol)
        assertEquals(1.0, p0.z, tol)

        // Midpoint should now be between (0,0,1) and (0,1,0)
        val mid = mgr.getObject(2) as Midpoint
        val s2 = 1.0 / sqrt(2.0)
        assertEquals(0.0, mid.x, tol)
        assertEquals(s2, mid.y, tol)
        assertEquals(s2, mid.z, tol)
    }

    @Test
    fun `removeWithDescendants removes object and children`() {
        val mgr = GeometricObjectManager()
        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0))
        mgr.addObject(FreePoint(1, 0.0, 1.0, 0.0))
        mgr.addObject(Line(2, 0, 1))
        mgr.addObject(Midpoint(3, 0, 1))
        mgr.recomputeAll()

        // Remove p1 — should also remove Line(2) and Midpoint(3)
        mgr.removeWithDescendants(1)
        assertNotNull(mgr.getObject(0))
        assertNull(mgr.getObject(1))
        assertNull(mgr.getObject(2))
        assertNull(mgr.getObject(3))
    }

    @Test
    fun `isMovable returns true for free points`() {
        val mgr = GeometricObjectManager()
        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0))
        mgr.addObject(FreePoint(1, 0.0, 1.0, 0.0))
        mgr.addObject(Midpoint(2, 0, 1))
        assertTrue(mgr.isMovable(0))
        assertFalse(mgr.isMovable(2))
    }

    @Test
    fun `snapshot and restore preserves state`() {
        val mgr = GeometricObjectManager()
        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0))
        mgr.addObject(FreePoint(1, 0.0, 1.0, 0.0))
        mgr.recomputeAll()

        val snap = mgr.snapshot()

        // Modify
        mgr.addObject(FreePoint(2, 0.0, 0.0, 1.0))
        assertEquals(3, mgr.getNextId())
        assertNotNull(mgr.getObject(2))

        // Restore
        mgr.restore(snap)
        assertEquals(2, mgr.getNextId())
        assertNull(mgr.getObject(2))
        assertNotNull(mgr.getObject(0))
        assertNotNull(mgr.getObject(1))
    }

    @Test
    fun `snapshot is a deep copy`() {
        val mgr = GeometricObjectManager()
        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0))
        val snap = mgr.snapshot()

        // Move the point
        mgr.movePoint(0, 0.0, 1.0, 0.0)

        // Snapshot should still have original position
        val snappedObj = snap.objects[0] as FreePoint
        assertEquals(1.0, snappedObj.x, tol)
        assertEquals(0.0, snappedObj.y, tol)
    }

    @Test
    fun `nextDefaultLabel generates sequential labels`() {
        val mgr = GeometricObjectManager()
        assertEquals("A", mgr.nextDefaultLabel())
        assertEquals("B", mgr.nextDefaultLabel())
        assertEquals("C", mgr.nextDefaultLabel())
    }

    @Test
    fun `nextDefaultLabel wraps to AA after Z`() {
        val mgr = GeometricObjectManager()
        for (i in 0 until 26) mgr.nextDefaultLabel() // A-Z
        assertEquals("AA", mgr.nextDefaultLabel())
        assertEquals("AB", mgr.nextDefaultLabel())
    }

    @Test
    fun `undefined cascading`() {
        val mgr = GeometricObjectManager()
        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0))
        mgr.addObject(FreePoint(1, 0.0, 1.0, 0.0))
        mgr.addObject(Midpoint(2, 0, 1))
        mgr.addObject(FreePoint(3, 0.0, 0.0, 1.0))
        mgr.addObject(Midpoint(4, 2, 3)) // midpoint of midpoint and p3
        mgr.recomputeAll()

        assertTrue((mgr.getObject(2) as Midpoint).isDefined)
        assertTrue((mgr.getObject(4) as Midpoint).isDefined)

        // Move p1 to be antipodal to p0 -> midpoint(0,1) becomes undefined
        mgr.movePoint(1, -1.0, 0.0, 0.0)

        assertFalse((mgr.getObject(2) as Midpoint).isDefined)
        // Midpoint(4) depends on midpoint(2) which is undefined -> also undefined
        assertFalse((mgr.getObject(4) as Midpoint).isDefined)
    }

    @Test
    fun `metadata is stored and retrievable`() {
        val mgr = GeometricObjectManager()
        val meta = ObjectMetadata(visible = true, label = "A", labelVisible = true)
        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0), meta)
        val retrieved = mgr.getMetadata(0)
        assertNotNull(retrieved)
        assertEquals("A", retrieved!!.label)
        assertTrue(retrieved.labelVisible)
    }
}
