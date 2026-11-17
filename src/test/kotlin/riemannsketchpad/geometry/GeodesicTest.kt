package riemannsketchpad.geometry

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.math.*

class GeodesicTest {

    private val tol = 1e-9

    private fun makeObjects(vararg objs: GeometricObject): Map<Int, GeometricObject> =
        objs.associateBy { it.id }

    @Test
    fun `Line computes normal for XY plane points`() {
        val p1 = FreePoint(0, 1.0, 0.0, 0.0)
        val p2 = FreePoint(1, 0.0, 1.0, 0.0)
        val line = Line(2, 0, 1)
        val objs = makeObjects(p1, p2, line)
        line.recompute(objs)
        assertTrue(line.isDefined)
        // Normal should be (0,0,1) (since (1,0,0)x(0,1,0)=(0,0,1))
        assertEquals(0.0, line.normalX, tol)
        assertEquals(0.0, line.normalY, tol)
        assertEquals(1.0, line.normalZ, tol)
    }

    @Test
    fun `Line is undefined for antipodal points`() {
        val p1 = FreePoint(0, 1.0, 0.0, 0.0)
        val p2 = FreePoint(1, -1.0, 0.0, 0.0)
        val line = Line(2, 0, 1)
        line.recompute(makeObjects(p1, p2, line))
        assertFalse(line.isDefined)
    }

    @Test
    fun `Segment stores endpoints`() {
        val p1 = FreePoint(0, 1.0, 0.0, 0.0)
        val p2 = FreePoint(1, 0.0, 0.0, 1.0)
        val seg = Segment(2, 0, 1)
        seg.recompute(makeObjects(p1, p2, seg))
        assertTrue(seg.isDefined)
        assertEquals(GeodesicType.SEGMENT, seg.geodesicType)
        assertEquals(1.0, seg.x1, tol)
        assertEquals(0.0, seg.z1, tol)
        assertEquals(0.0, seg.x2, tol)
        assertEquals(1.0, seg.z2, tol)
    }

    @Test
    fun `PerpendicularLine through point on geodesic`() {
        val p1 = FreePoint(0, 1.0, 0.0, 0.0)
        val p2 = FreePoint(1, 0.0, 1.0, 0.0)
        val line = Line(2, 0, 1)
        // Point on the line
        val p3 = FreePoint(3, 1.0, 0.0, 0.0) // same as p1
        val perp = PerpendicularLine(4, 3, 2)
        val objs = makeObjects(p1, p2, line, p3, perp)
        line.recompute(objs)
        perp.recompute(objs)
        assertTrue(perp.isDefined)
        // Perpendicular to line in XY plane at (1,0,0) should pass through (0,0,1) direction
        // The parent line's normal is (0,0,1). Perpendicular should have normal perpendicular to (0,0,1) and containing (1,0,0).
        // perp normal should be in the XY plane
        assertEquals(0.0, perp.normalZ, tol)
    }

    @Test
    fun `PerpendicularLine through point off geodesic`() {
        val p1 = FreePoint(0, 1.0, 0.0, 0.0)
        val p2 = FreePoint(1, 0.0, 1.0, 0.0)
        val line = Line(2, 0, 1)
        // Point off the line
        val p3 = FreePoint(3, 0.0, 0.0, 1.0) // north pole of XY great circle
        val perp = PerpendicularLine(4, 3, 2)
        val objs = makeObjects(p1, p2, line, p3, perp)
        line.recompute(objs)
        perp.recompute(objs)
        // (0,0,1) is the pole of the geodesic with normal (0,0,1) — should be undefined
        assertFalse(perp.isDefined)
    }

    @Test
    fun `PerpendicularLine from pole of geodesic is undefined`() {
        val p1 = FreePoint(0, 1.0, 0.0, 0.0)
        val p2 = FreePoint(1, 0.0, 0.0, 1.0)
        val line = Line(2, 0, 1)
        // Line in XZ plane, normal = (0,-1,0). Point (0,1,0) is the antipole = pole of this geodesic.
        val p3 = FreePoint(3, 0.0, 1.0, 0.0)
        val perp = PerpendicularLine(4, 3, 2)
        val objs = makeObjects(p1, p2, line, p3, perp)
        line.recompute(objs)
        perp.recompute(objs)
        assertFalse(perp.isDefined)
    }

    @Test
    fun `PerpendicularLine through valid off-geodesic point`() {
        val s2 = 1.0 / sqrt(2.0)
        val p1 = FreePoint(0, 1.0, 0.0, 0.0)
        val p2 = FreePoint(1, 0.0, 1.0, 0.0)
        val line = Line(2, 0, 1)
        // Line in XY plane, normal = (0,0,1)
        // Point: (0, s2, s2) — not a pole
        val p3 = FreePoint(3, 0.0, s2, s2)
        val perp = PerpendicularLine(4, 3, 2)
        val objs = makeObjects(p1, p2, line, p3, perp)
        line.recompute(objs)
        perp.recompute(objs)
        assertTrue(perp.isDefined)
        // Foot of (0,s2,s2) on z=0 plane: (0,s2,s2) - ((0,s2,s2)·(0,0,1))*(0,0,1) = (0,s2,0)
        // Normalized: (0,1,0)
        assertEquals(0.0, perp.x2, tol)
        assertEquals(1.0, perp.y2, tol)
        assertEquals(0.0, perp.z2, tol)
    }

    @Test
    fun `FreePointOnLine position`() {
        val p1 = FreePoint(0, 1.0, 0.0, 0.0)
        val p2 = FreePoint(1, 0.0, 1.0, 0.0)
        val line = Line(2, 0, 1)
        val ptOnLine = FreePointOnLine(3, 2, 0.0)
        val objs = makeObjects(p1, p2, line, ptOnLine)
        line.recompute(objs)
        ptOnLine.recompute(objs)
        assertTrue(ptOnLine.isDefined)
        // t=0 should give P1 = (1,0,0)
        assertEquals(1.0, ptOnLine.x, tol)
        assertEquals(0.0, ptOnLine.y, tol)
        assertEquals(0.0, ptOnLine.z, tol)
    }

    @Test
    fun `FreePointOnLine at pi over 2`() {
        val p1 = FreePoint(0, 1.0, 0.0, 0.0)
        val p2 = FreePoint(1, 0.0, 1.0, 0.0)
        val line = Line(2, 0, 1)
        // normal = (0,0,1), n x P1 = (0,0,1) x (1,0,0) = (0,1,0)
        // P(pi/2) = (1,0,0)*cos(pi/2) + (0,1,0)*sin(pi/2) = (0,1,0)
        val ptOnLine = FreePointOnLine(3, 2, PI / 2)
        val objs = makeObjects(p1, p2, line, ptOnLine)
        line.recompute(objs)
        ptOnLine.recompute(objs)
        assertEquals(0.0, ptOnLine.x, tol)
        assertEquals(1.0, ptOnLine.y, tol)
        assertEquals(0.0, ptOnLine.z, tol)
    }

    @Test
    fun `FreePointOnSegment at midpoint`() {
        val p1 = FreePoint(0, 1.0, 0.0, 0.0)
        val p2 = FreePoint(1, 0.0, 1.0, 0.0)
        val seg = Segment(2, 0, 1)
        val ptOnSeg = FreePointOnSegment(3, 2, 0.5)
        val objs = makeObjects(p1, p2, seg, ptOnSeg)
        seg.recompute(objs)
        ptOnSeg.recompute(objs)
        assertTrue(ptOnSeg.isDefined)
        val s2 = 1.0 / sqrt(2.0)
        assertEquals(s2, ptOnSeg.x, tol)
        assertEquals(s2, ptOnSeg.y, tol)
        assertEquals(0.0, ptOnSeg.z, tol)
    }

    @Test
    fun `Midpoint computation`() {
        val p1 = FreePoint(0, 1.0, 0.0, 0.0)
        val p2 = FreePoint(1, 0.0, 1.0, 0.0)
        val mid = Midpoint(2, 0, 1)
        val objs = makeObjects(p1, p2, mid)
        mid.recompute(objs)
        assertTrue(mid.isDefined)
        val s2 = 1.0 / sqrt(2.0)
        assertEquals(s2, mid.x, tol)
        assertEquals(s2, mid.y, tol)
        assertEquals(0.0, mid.z, tol)
    }

    @Test
    fun `Midpoint of antipodal is undefined`() {
        val p1 = FreePoint(0, 1.0, 0.0, 0.0)
        val p2 = FreePoint(1, -1.0, 0.0, 0.0)
        val mid = Midpoint(2, 0, 1)
        mid.recompute(makeObjects(p1, p2, mid))
        assertFalse(mid.isDefined)
    }

    @Test
    fun `ReflectionPoint across great circle`() {
        val p = FreePoint(0, 1.0, 0.0, 0.0)
        val p1 = FreePoint(1, 0.0, 1.0, 0.0)
        val p2 = FreePoint(2, 0.0, 0.0, 1.0)
        val line = Line(3, 1, 2)
        // Line through (0,1,0) and (0,0,1), normal = normalize((0,1,0)x(0,0,1)) = (1,0,0)
        val refl = ReflectionPoint(4, 0, 3)
        val objs = makeObjects(p, p1, p2, line, refl)
        line.recompute(objs)
        refl.recompute(objs)
        assertTrue(refl.isDefined)
        // Reflect (1,0,0) through normal (1,0,0): (1,0,0) - 2*1*(1,0,0) = (-1,0,0)
        assertEquals(-1.0, refl.x, tol)
        assertEquals(0.0, refl.y, tol)
        assertEquals(0.0, refl.z, tol)
    }

    @Test
    fun `updateParameterFromPosition round-trips for FreePointOnLine`() {
        val p1 = FreePoint(0, 1.0, 0.0, 0.0)
        val p2 = FreePoint(1, 0.0, 1.0, 0.0)
        val line = Line(2, 0, 1)
        val ptOnLine = FreePointOnLine(3, 2, 1.5)
        val objs = makeObjects(p1, p2, line, ptOnLine)
        line.recompute(objs)
        ptOnLine.recompute(objs)
        val origX = ptOnLine.x; val origY = ptOnLine.y; val origZ = ptOnLine.z
        // Recompute parameter from the position
        ptOnLine.updateParameterFromPosition(origX, origY, origZ, objs)
        ptOnLine.recompute(objs)
        assertEquals(origX, ptOnLine.x, tol)
        assertEquals(origY, ptOnLine.y, tol)
        assertEquals(origZ, ptOnLine.z, tol)
    }
}
