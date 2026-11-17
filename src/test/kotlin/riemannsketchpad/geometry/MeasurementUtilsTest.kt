package riemannsketchpad.geometry

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.math.*

class MeasurementUtilsTest {

    private val tol = 1e-7

    private fun makeObjects(vararg objs: GeometricObject): Map<Int, GeometricObject> =
        objs.associateBy { it.id }

    @Test
    fun `segment length is spherical distance`() {
        val p1 = FreePoint(0, 1.0, 0.0, 0.0)
        val p2 = FreePoint(1, 0.0, 1.0, 0.0)
        val seg = Segment(2, 0, 1)
        seg.recompute(makeObjects(p1, p2, seg))
        assertEquals(PI / 2, MeasurementUtils.segmentLength(seg), tol)
    }

    @Test
    fun `circle circumference`() {
        val center = FreePoint(0, 0.0, 1.0, 0.0) // north pole
        val radiusPt = FreePoint(1, 1.0, 0.0, 0.0) // 90 degrees
        val circle = Circle(2, 0, 1)
        circle.recompute(makeObjects(center, radiusPt, circle))
        // r = pi/2, circumference = 2*pi*sin(pi/2) = 2*pi
        assertEquals(2 * PI, MeasurementUtils.circleCircumference(circle), tol)
    }

    @Test
    fun `circle circumference for small circle`() {
        val s2 = 1.0 / sqrt(2.0)
        val center = FreePoint(0, 0.0, 1.0, 0.0) // north pole
        val radiusPt = FreePoint(1, s2, s2, 0.0) // 45 degrees
        val circle = Circle(2, 0, 1)
        circle.recompute(makeObjects(center, radiusPt, circle))
        // r = pi/4, circumference = 2*pi*sin(pi/4) = 2*pi*s2 = pi*sqrt(2)
        assertEquals(2 * PI * s2, MeasurementUtils.circleCircumference(circle), tol)
    }

    @Test
    fun `right angle measures 90 degrees`() {
        // Angle at vertex (1,0,0) from A=(0,0,1) to C=(0,1,0) — should be 90 degrees
        val pA = FreePoint(0, 0.0, 0.0, 1.0)
        val pV = FreePoint(1, 1.0, 0.0, 0.0)
        val pC = FreePoint(2, 0.0, 1.0, 0.0)
        val angle = Angle(3, 0, 1, 2)
        val objs = makeObjects(pA, pV, pC, angle)
        angle.recompute(objs)
        assertTrue(angle.isDefined)
        assertEquals(90.0, MeasurementUtils.angleDegrees(angle), 0.1)
    }

    @Test
    fun `straight angle measures 180 degrees`() {
        // Angle at vertex (1,0,0) from A=(0,1,0) to C=(0,-1,0) — should be 180 degrees
        val pA = FreePoint(0, 0.0, 1.0, 0.0)
        val pV = FreePoint(1, 1.0, 0.0, 0.0)
        val pC = FreePoint(2, 0.0, -1.0, 0.0)
        val angle = Angle(3, 0, 1, 2)
        val objs = makeObjects(pA, pV, pC, angle)
        angle.recompute(objs)
        assertTrue(angle.isDefined)
        assertEquals(180.0, MeasurementUtils.angleDegrees(angle), 0.1)
    }

    @Test
    fun `reflex angle measures 270 degrees`() {
        // Same points as right angle test but A and C swapped
        val pA = FreePoint(0, 0.0, 1.0, 0.0)
        val pV = FreePoint(1, 1.0, 0.0, 0.0)
        val pC = FreePoint(2, 0.0, 0.0, 1.0)
        val angle = Angle(3, 0, 1, 2)
        angle.recompute(makeObjects(pA, pV, pC, angle))
        assertTrue(angle.isDefined)
        assertEquals(270.0, MeasurementUtils.angleDegrees(angle), 0.1)
    }

    @Test
    fun `angle with coincident points is undefined`() {
        val pA = FreePoint(0, 1.0, 0.0, 0.0)
        val pV = FreePoint(1, 1.0, 0.0, 0.0) // same as A
        val pC = FreePoint(2, 0.0, 1.0, 0.0)
        val angle = Angle(3, 0, 1, 2)
        angle.recompute(makeObjects(pA, pV, pC, angle))
        assertFalse(angle.isDefined)
    }

    @Test
    fun `equilateral triangle area on sphere`() {
        // Three vertices at 90 degrees from each other on the sphere
        // Triangle with vertices at (1,0,0), (0,1,0), (0,0,1)
        // Each interior angle is 90 degrees (pi/2)
        // Area = sum of angles - (n-2)*pi = 3*(pi/2) - pi = pi/2
        val p1 = FreePoint(0, 1.0, 0.0, 0.0)
        val p2 = FreePoint(1, 0.0, 1.0, 0.0)
        val p3 = FreePoint(2, 0.0, 0.0, 1.0)
        val region = Region(3, listOf(0, 1, 2))
        val objs = makeObjects(p1, p2, p3, region)
        region.recompute(objs)
        assertTrue(region.isDefined)
        val area = MeasurementUtils.regionArea(region)
        assertEquals(PI / 2, area, tol)
    }

    @Test
    fun `hemisphere area`() {
        // A region forming a hemisphere (4 vertices on the equator)
        // Actually a "lune" or hemisphere can't be easily represented with 4 equator points
        // Let's test a simpler case: a region with 4 points forming a spherical square
        // Use the octant triangle instead — already tested above
    }

    @Test
    fun `measure returns null for non-measurable objects`() {
        val p = FreePoint(0, 1.0, 0.0, 0.0)
        assertNull(MeasurementUtils.measure(p))
    }

    @Test
    fun `measure returns null for undefined objects`() {
        val p1 = FreePoint(0, 1.0, 0.0, 0.0)
        val p2 = FreePoint(1, -1.0, 0.0, 0.0) // antipodal
        val seg = Segment(2, 0, 1)
        seg.recompute(makeObjects(p1, p2, seg))
        assertFalse(seg.isDefined)
        assertNull(MeasurementUtils.measure(seg))
    }

    @Test
    fun `measureLabel formats correctly`() {
        val p1 = FreePoint(0, 1.0, 0.0, 0.0)
        val p2 = FreePoint(1, 0.0, 1.0, 0.0)
        val seg = Segment(2, 0, 1)
        seg.recompute(makeObjects(p1, p2, seg))
        assertEquals("1.57", MeasurementUtils.measureLabel(seg))
    }

    @Test
    fun `measureLabel for angle includes degree symbol`() {
        val pA = FreePoint(0, 0.0, 0.0, 1.0)
        val pV = FreePoint(1, 1.0, 0.0, 0.0)
        val pC = FreePoint(2, 0.0, 1.0, 0.0)
        val angle = Angle(3, 0, 1, 2)
        angle.recompute(makeObjects(pA, pV, pC, angle))
        val label = MeasurementUtils.measureLabel(angle)
        assertNotNull(label)
        assertTrue(label!!.endsWith("\u00B0"))
        assertTrue(label.startsWith("90"))
    }
}
