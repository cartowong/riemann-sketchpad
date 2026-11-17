package riemannsketchpad.geometry

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.math.*

class SphericalUtilsTest {

    private val tol = 1e-9

    @Test
    fun `spherical distance between same point is zero`() {
        assertEquals(0.0, SphericalUtils.sphericalDistance(1.0, 0.0, 0.0, 1.0, 0.0, 0.0), tol)
    }

    @Test
    fun `spherical distance between antipodal points is pi`() {
        assertEquals(PI, SphericalUtils.sphericalDistance(1.0, 0.0, 0.0, -1.0, 0.0, 0.0), tol)
    }

    @Test
    fun `spherical distance between orthogonal points is pi over 2`() {
        assertEquals(PI / 2, SphericalUtils.sphericalDistance(1.0, 0.0, 0.0, 0.0, 1.0, 0.0), tol)
    }

    @Test
    fun `normalize to sphere`() {
        val (x, y, z) = SphericalUtils.normalizeToSphere(3.0, 4.0, 0.0)
        assertEquals(0.6, x, tol)
        assertEquals(0.8, y, tol)
        assertEquals(0.0, z, tol)
    }

    @Test
    fun `normalize zero vector returns fallback`() {
        val (x, y, z) = SphericalUtils.normalizeToSphere(0.0, 0.0, 0.0)
        assertEquals(0.0, x, tol)
        assertEquals(0.0, y, tol)
        assertEquals(1.0, z, tol)
    }

    @Test
    fun `great circle normal of orthogonal points`() {
        // (1,0,0) x (0,1,0) = (0,0,1)
        val n = SphericalUtils.greatCircleNormal(1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        assertNotNull(n)
        assertEquals(0.0, n!!.first, tol)
        assertEquals(0.0, n.second, tol)
        assertEquals(1.0, n.third, tol)
    }

    @Test
    fun `great circle normal of coincident points returns null`() {
        assertNull(SphericalUtils.greatCircleNormal(1.0, 0.0, 0.0, 1.0, 0.0, 0.0))
    }

    @Test
    fun `great circle normal of antipodal points returns null`() {
        assertNull(SphericalUtils.greatCircleNormal(1.0, 0.0, 0.0, -1.0, 0.0, 0.0))
    }

    @Test
    fun `tangent vector at north pole toward point on equator`() {
        // P = (0,1,0), Q = (1,0,0)
        // t = Q - (Q·P)*P = (1,0,0) - 0*(0,1,0) = (1,0,0)
        val t = SphericalUtils.tangentVector(0.0, 1.0, 0.0, 1.0, 0.0, 0.0)
        assertNotNull(t)
        assertEquals(1.0, t!!.first, tol)
        assertEquals(0.0, t.second, tol)
        assertEquals(0.0, t.third, tol)
    }

    @Test
    fun `slerp at t=0 returns first point`() {
        val (x, y, z) = SphericalUtils.slerp(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        assertEquals(1.0, x, tol)
        assertEquals(0.0, y, tol)
        assertEquals(0.0, z, tol)
    }

    @Test
    fun `slerp at t=1 returns second point`() {
        val (x, y, z) = SphericalUtils.slerp(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0)
        assertEquals(0.0, x, tol)
        assertEquals(1.0, y, tol)
        assertEquals(0.0, z, tol)
    }

    @Test
    fun `slerp at t=0_5 returns midpoint`() {
        val (x, y, z) = SphericalUtils.slerp(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.5)
        val expected = 1.0 / sqrt(2.0)
        assertEquals(expected, x, tol)
        assertEquals(expected, y, tol)
        assertEquals(0.0, z, tol)
    }

    @Test
    fun `reflect through great circle`() {
        // Reflect (1,0,0) through the great circle with normal (1,0,0) -> (-1,0,0)
        val (rx, ry, rz) = SphericalUtils.reflectThroughGreatCircle(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        assertEquals(-1.0, rx, tol)
        assertEquals(0.0, ry, tol)
        assertEquals(0.0, rz, tol)
    }

    @Test
    fun `reflect point on the great circle is identity`() {
        // (0,1,0) reflected through normal (1,0,0) — point is on the circle
        val (rx, ry, rz) = SphericalUtils.reflectThroughGreatCircle(0.0, 1.0, 0.0, 1.0, 0.0, 0.0)
        assertEquals(0.0, rx, tol)
        assertEquals(1.0, ry, tol)
        assertEquals(0.0, rz, tol)
    }

    @Test
    fun `midpoint of orthogonal points`() {
        val m = SphericalUtils.midpoint(1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        assertNotNull(m)
        val expected = 1.0 / sqrt(2.0)
        assertEquals(expected, m!!.first, tol)
        assertEquals(expected, m.second, tol)
        assertEquals(0.0, m.third, tol)
    }

    @Test
    fun `midpoint of antipodal points returns null`() {
        assertNull(SphericalUtils.midpoint(1.0, 0.0, 0.0, -1.0, 0.0, 0.0))
    }

    @Test
    fun `foot of perpendicular`() {
        // Point (0,1,0), geodesic normal (0,0,1) — great circle is the XY equator (z=0 plane: normal is (0,0,1))
        // Wait — normal (0,0,1) means the plane n·P=0 is the z=0 plane.
        // Foot of (0,1,0) on this plane: (0,1,0) - ((0,1,0)·(0,0,1))*(0,0,1) = (0,1,0) - 0 = (0,1,0)
        val f = SphericalUtils.footOfPerpendicular(0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        assertNotNull(f)
        assertEquals(0.0, f!!.first, tol)
        assertEquals(1.0, f.second, tol)
        assertEquals(0.0, f.third, tol)
    }

    @Test
    fun `foot of perpendicular from pole returns null`() {
        // Point (0,0,1), geodesic normal (0,0,1) — point IS the pole
        assertNull(SphericalUtils.footOfPerpendicular(0.0, 0.0, 1.0, 0.0, 0.0, 1.0))
    }

    @Test
    fun `isAntipodal detects antipodal points`() {
        assertTrue(SphericalUtils.isAntipodal(1.0, 0.0, 0.0, -1.0, 0.0, 0.0))
        assertFalse(SphericalUtils.isAntipodal(1.0, 0.0, 0.0, 0.0, 1.0, 0.0))
    }

    @Test
    fun `view matrix at default orientation is identity`() {
        val vm = SphericalUtils.viewMatrix(0.0, 0.0)
        // Should be identity: azimuth=0, elevation=0
        val (px, py, pz) = SphericalUtils.matVec(vm, 1.0, 0.0, 0.0)
        assertEquals(1.0, px, tol)
        assertEquals(0.0, py, tol)
        assertEquals(0.0, pz, tol)
    }

    @Test
    fun `view matrix 90 degree azimuth rotates X to Z`() {
        val vm = SphericalUtils.viewMatrix(90.0, 0.0)
        // Ry(-90): (1,0,0) -> (0,0,1) ... let's check
        // Ry(-90): cos(-90)=0, sin(-90)=-1
        // [0   0   1]   [1]   [0*1+0*0+1*0]   hmm let me recalculate
        // Wait, Ry(-theta) with theta=90:
        // cosT=cos(90°)=0, sinT=sin(90°)=1
        // Ry(-theta):
        // [cosT  0  -sinT] = [0  0  -1]
        // [0     1   0   ]   [0  1   0]
        // [sinT  0  cosT ]   [1  0   0]
        // (1,0,0) -> (0, 0, 1)
        val (px, py, pz) = SphericalUtils.matVec(vm, 1.0, 0.0, 0.0)
        assertEquals(0.0, px, tol)
        assertEquals(0.0, py, tol)
        assertEquals(1.0, pz, tol)
    }

    @Test
    fun `project and unproject round-trip`() {
        val vm = SphericalUtils.viewMatrix(30.0, 15.0)
        val p = SphericalUtils.normalizeToSphere(1.0, 2.0, 3.0)
        val (sx, sy, sz) = SphericalUtils.projectToScreen(p.first, p.second, p.third, vm)
        if (sz > 0) { // front hemisphere
            val result = SphericalUtils.unprojectFromScreen(sx, sy, vm)
            assertNotNull(result)
            assertEquals(p.first, result!!.first, tol)
            assertEquals(p.second, result.second, tol)
            assertEquals(p.third, result.third, tol)
        }
    }

    @Test
    fun `unproject outside sphere returns null`() {
        val vm = SphericalUtils.viewMatrix(0.0, 0.0)
        assertNull(SphericalUtils.unprojectFromScreen(1.5, 0.0, vm))
    }

    @Test
    fun `isOnFrontHemisphere`() {
        val vm = SphericalUtils.viewMatrix(0.0, 0.0)
        // Default view looks along -Z, so positive Z is behind, negative Z is in front
        // Wait — the camera looks along -Z in rotated frame. pz = z component after rotation.
        // With identity rotation, (0,0,-1) has pz=-1 (back), (0,0,1) has pz=1 (front? No...)
        // Actually "front hemisphere" means pz > 0 per spec, which means the half facing the camera.
        // With default view, camera looks along -Z, so points with positive z (in world) have positive pz.
        assertTrue(SphericalUtils.isOnFrontHemisphere(0.0, 0.0, 1.0, vm))
        assertFalse(SphericalUtils.isOnFrontHemisphere(0.0, 0.0, -1.0, vm))
    }

    @Test
    fun `ellipse projection params for equatorial circle`() {
        val vm = SphericalUtils.viewMatrix(0.0, 0.0)
        // Normal (0,1,0) — great circle in the XZ plane
        val params = SphericalUtils.ellipseProjectionParams(0.0, 1.0, 0.0, vm)
        assertEquals(1.0, params.semiMajor, tol)
        assertEquals(0.0, params.semiMinor, tol) // seen edge-on: npy=1, npz=0
    }

    @Test
    fun `ellipse projection params for front-facing circle`() {
        val vm = SphericalUtils.viewMatrix(0.0, 0.0)
        // Normal (0,0,1) — great circle in the XY plane, facing the camera
        val params = SphericalUtils.ellipseProjectionParams(0.0, 0.0, 1.0, vm)
        assertEquals(1.0, params.semiMajor, tol)
        assertEquals(1.0, params.semiMinor, tol) // full circle
    }

    @Test
    fun `small circle ellipse params`() {
        val vm = SphericalUtils.viewMatrix(0.0, 0.0)
        // Center at north pole (0,1,0), radius 45 degrees
        val cosR = cos(PI / 4)
        val sinR = sin(PI / 4)
        val params = SphericalUtils.smallCircleEllipseParams(0.0, 1.0, 0.0, cosR, vm)
        // Normal is (0,1,0), after default view rotation npz=0, so semiMinor=0 (edge-on)
        assertEquals(sinR, params.semiMajor, tol)
        assertEquals(0.0, params.semiMinor, tol)
    }
}
