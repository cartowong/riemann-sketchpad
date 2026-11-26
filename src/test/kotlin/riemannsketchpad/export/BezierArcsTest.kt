package riemannsketchpad.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.*

class BezierArcsTest {

    private val cx = 100.0
    private val cy = 50.0
    private val rx = 80.0
    private val ry = 30.0
    private val rotDeg = 25.0
    private val phi = Math.toRadians(rotDeg)

    private fun onEllipse(t: Double) = Pair(
        cx + rx * cos(phi) * cos(t) - ry * sin(phi) * sin(t),
        cy + rx * sin(phi) * cos(t) + ry * cos(phi) * sin(t)
    )

    /** Normalized radius of (x, y) in the ellipse's own frame; 1.0 means on the ellipse. */
    private fun ellipseRadius(x: Double, y: Double): Double {
        val dx = x - cx; val dy = y - cy
        val lx = cos(phi) * dx + sin(phi) * dy
        val ly = -sin(phi) * dx + cos(phi) * dy
        return sqrt((lx / rx).pow(2) + (ly / ry).pow(2))
    }

    private fun bezierAt(x0: Double, y0: Double, c: Cubic, s: Double): Pair<Double, Double> {
        val u = 1 - s
        return Pair(
            u * u * u * x0 + 3 * u * u * s * c.c1x + 3 * u * s * s * c.c2x + s * s * s * c.x,
            u * u * u * y0 + 3 * u * u * s * c.c1y + 3 * u * s * s * c.c2y + s * s * s * c.y
        )
    }

    private fun samples(x0: Double, y0: Double, cubics: List<Cubic>): List<Pair<Double, Double>> {
        var px = x0; var py = y0
        return cubics.flatMap { c ->
            val pts = (0..20).map { bezierAt(px, py, c, it / 20.0) }
            px = c.x; py = c.y
            pts
        }
    }

    private fun checkArc(t1: Double, dTheta: Double) {
        val (x1, y1) = onEllipse(t1)
        val (x2, y2) = onEllipse(t1 + dTheta)
        val cubics = BezierArcs.arc(x1, y1, rx, ry, rotDeg, abs(dTheta) > PI, dTheta > 0, x2, y2)

        assertEquals(ceil(abs(dTheta) / (PI / 2)).toInt(), cubics.size)
        assertEquals(x2, cubics.last().x, 1e-9)
        assertEquals(y2, cubics.last().y, 1e-9)

        val pts = samples(x1, y1, cubics)
        for ((x, y) in pts) assertEquals(1.0, ellipseRadius(x, y), 1e-3)

        val (mx, my) = onEllipse(t1 + dTheta / 2)
        val nearest = pts.minOf { (x, y) -> hypot(x - mx, y - my) }
        assertTrue(nearest < 1.0, "arc should pass through its parametric midpoint, was $nearest px away")
    }

    @Test
    fun `small arc in positive direction`() = checkArc(0.3, 2.2)

    @Test
    fun `small arc in negative direction`() = checkArc(0.3, -1.0)

    @Test
    fun `large arc in positive direction`() = checkArc(0.3, 4.5)

    @Test
    fun `large arc in negative direction`() = checkArc(2.0, -5.0)

    @Test
    fun `radii too small are scaled up so the arc still reaches its end point`() {
        val cubics = BezierArcs.arc(0.0, 0.0, 1.0, 1.0, 0.0, false, true, 100.0, 0.0)
        assertEquals(100.0, cubics.last().x, 1e-9)
        assertEquals(0.0, cubics.last().y, 1e-9)
        // Scaled to a half circle of radius 50 centered at (50, 0)
        for ((x, y) in samples(0.0, 0.0, cubics)) assertEquals(50.0, hypot(x - 50.0, y), 0.05)
    }

    @Test
    fun `full ellipse closes on itself`() {
        val (start, cubics) = BezierArcs.ellipse(cx, cy, rx, ry, rotDeg)
        assertEquals(4, cubics.size)
        assertEquals(start.first, cubics.last().x, 1e-9)
        assertEquals(start.second, cubics.last().y, 1e-9)
        for ((x, y) in samples(start.first, start.second, cubics)) assertEquals(1.0, ellipseRadius(x, y), 1e-3)
    }
}
