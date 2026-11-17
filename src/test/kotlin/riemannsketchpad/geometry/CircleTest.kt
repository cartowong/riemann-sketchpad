package riemannsketchpad.geometry

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.math.*

class CircleTest {

    private val tol = 1e-9

    private fun makeObjects(vararg objs: GeometricObject): Map<Int, GeometricObject> =
        objs.associateBy { it.id }

    @Test
    fun `Circle computes spherical radius`() {
        val center = FreePoint(0, 0.0, 1.0, 0.0) // north pole
        val radiusPt = FreePoint(1, 1.0, 0.0, 0.0) // on equator
        val circle = Circle(2, 0, 1)
        circle.recompute(makeObjects(center, radiusPt, circle))
        assertTrue(circle.isDefined)
        assertEquals(PI / 2, circle.sphericalRadius, tol)
        assertEquals(0.0, circle.cosRadius, tol)
    }

    @Test
    fun `Circle with small radius`() {
        val s2 = 1.0 / sqrt(2.0)
        val center = FreePoint(0, 0.0, 1.0, 0.0) // north pole
        val radiusPt = FreePoint(1, s2, s2, 0.0) // 45 degrees from north pole
        val circle = Circle(2, 0, 1)
        circle.recompute(makeObjects(center, radiusPt, circle))
        assertTrue(circle.isDefined)
        assertEquals(PI / 4, circle.sphericalRadius, tol)
        assertEquals(s2, circle.cosRadius, tol)
    }

    @Test
    fun `Circle with antipodal center and radius point is undefined`() {
        val center = FreePoint(0, 0.0, 1.0, 0.0)
        val radiusPt = FreePoint(1, 0.0, -1.0, 0.0)
        val circle = Circle(2, 0, 1)
        circle.recompute(makeObjects(center, radiusPt, circle))
        assertFalse(circle.isDefined)
    }

    @Test
    fun `FreePointOnCircle at theta=0`() {
        val center = FreePoint(0, 0.0, 1.0, 0.0) // north pole
        val radiusPt = FreePoint(1, 1.0, 0.0, 0.0)
        val circle = Circle(2, 0, 1)
        val ptOnCircle = FreePointOnCircle(3, 2, 0.0)
        val objs = makeObjects(center, radiusPt, circle, ptOnCircle)
        circle.recompute(objs)
        ptOnCircle.recompute(objs)
        assertTrue(ptOnCircle.isDefined)
        // Point should be on the unit sphere
        val len = sqrt(ptOnCircle.x * ptOnCircle.x + ptOnCircle.y * ptOnCircle.y + ptOnCircle.z * ptOnCircle.z)
        assertEquals(1.0, len, tol)
        // Distance from center should equal spherical radius
        val dist = SphericalUtils.sphericalDistance(ptOnCircle.x, ptOnCircle.y, ptOnCircle.z, 0.0, 1.0, 0.0)
        assertEquals(circle.sphericalRadius, dist, tol)
    }

    @Test
    fun `FreePointOnCircle updateParameterFromPosition round-trips`() {
        val center = FreePoint(0, 0.0, 1.0, 0.0)
        val radiusPt = FreePoint(1, 1.0, 0.0, 0.0)
        val circle = Circle(2, 0, 1)
        val ptOnCircle = FreePointOnCircle(3, 2, 1.2)
        val objs = makeObjects(center, radiusPt, circle, ptOnCircle)
        circle.recompute(objs)
        ptOnCircle.recompute(objs)
        val origX = ptOnCircle.x; val origY = ptOnCircle.y; val origZ = ptOnCircle.z
        ptOnCircle.updateParameterFromPosition(origX, origY, origZ, objs)
        ptOnCircle.recompute(objs)
        assertEquals(origX, ptOnCircle.x, tol)
        assertEquals(origY, ptOnCircle.y, tol)
        assertEquals(origZ, ptOnCircle.z, tol)
    }
}
