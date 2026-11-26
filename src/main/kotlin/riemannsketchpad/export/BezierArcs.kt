package riemannsketchpad.export

import kotlin.math.*

/** A cubic Bézier segment continuing from the current point: controls (c1, c2), end point (x, y). */
internal data class Cubic(val c1x: Double, val c1y: Double, val c2x: Double, val c2y: Double, val x: Double, val y: Double)

/** Converts elliptical arcs to cubic Béziers, each spanning at most 90°, for backends without an arc operator. */
internal object BezierArcs {

    /**
     * SVG endpoint-parameterized arc from (x1, y1) to (x2, y2), converted to center
     * parameterization per SVG 1.1 Appendix F.6.5–F.6.6 (radii scaled up if too small).
     */
    fun arc(
        x1: Double, y1: Double,
        rx: Double, ry: Double, rotationDeg: Double,
        largeArc: Boolean, sweep: Boolean,
        x2: Double, y2: Double
    ): List<Cubic> {
        if (x1 == x2 && y1 == y2) return emptyList()
        var a = abs(rx); var b = abs(ry)
        if (a == 0.0 || b == 0.0) return listOf(Cubic(x1, y1, x2, y2, x2, y2))

        val phi = Math.toRadians(rotationDeg)
        val cosP = cos(phi); val sinP = sin(phi)
        val dx = (x1 - x2) / 2; val dy = (y1 - y2) / 2
        val x1p = cosP * dx + sinP * dy
        val y1p = -sinP * dx + cosP * dy

        val lambda = x1p * x1p / (a * a) + y1p * y1p / (b * b)
        if (lambda > 1) { a *= sqrt(lambda); b *= sqrt(lambda) }

        val num = a * a * b * b - a * a * y1p * y1p - b * b * x1p * x1p
        val den = a * a * y1p * y1p + b * b * x1p * x1p
        val coef = (if (largeArc != sweep) 1.0 else -1.0) * sqrt((num / den).coerceAtLeast(0.0))
        val cxp = coef * a * y1p / b
        val cyp = -coef * b * x1p / a
        val cx = cosP * cxp - sinP * cyp + (x1 + x2) / 2
        val cy = sinP * cxp + cosP * cyp + (y1 + y2) / 2

        val theta1 = vectorAngle(1.0, 0.0, (x1p - cxp) / a, (y1p - cyp) / b)
        var dTheta = vectorAngle((x1p - cxp) / a, (y1p - cyp) / b, (-x1p - cxp) / a, (-y1p - cyp) / b)
        if (!sweep && dTheta > 0) dTheta -= 2 * PI
        if (sweep && dTheta < 0) dTheta += 2 * PI

        val cubics = centerArc(cx, cy, a, b, phi, theta1, dTheta)
        val last = cubics.last()
        return cubics.dropLast(1) + last.copy(x = x2, y = y2)
    }

    /** Start point and Béziers of a full rotated ellipse. */
    fun ellipse(cx: Double, cy: Double, rx: Double, ry: Double, rotationDeg: Double): Pair<Pair<Double, Double>, List<Cubic>> {
        val phi = Math.toRadians(rotationDeg)
        return point(cx, cy, rx, ry, phi, 0.0) to centerArc(cx, cy, rx, ry, phi, 0.0, 2 * PI)
    }

    private fun point(cx: Double, cy: Double, rx: Double, ry: Double, phi: Double, t: Double): Pair<Double, Double> =
        Pair(
            cx + rx * cos(phi) * cos(t) - ry * sin(phi) * sin(t),
            cy + rx * sin(phi) * cos(t) + ry * cos(phi) * sin(t)
        )

    private fun centerArc(cx: Double, cy: Double, rx: Double, ry: Double, phi: Double, theta1: Double, dTheta: Double): List<Cubic> {
        val n = max(1, ceil(abs(dTheta) / (PI / 2) - 1e-9).toInt())
        val delta = dTheta / n
        val k = 4.0 / 3.0 * tan(delta / 4)
        val cosP = cos(phi); val sinP = sin(phi)
        fun deriv(t: Double) = Pair(
            -rx * cosP * sin(t) - ry * sinP * cos(t),
            -rx * sinP * sin(t) + ry * cosP * cos(t)
        )
        return (0 until n).map { i ->
            val t0 = theta1 + i * delta
            val t1 = t0 + delta
            val (p0x, p0y) = point(cx, cy, rx, ry, phi, t0)
            val (p3x, p3y) = point(cx, cy, rx, ry, phi, t1)
            val (d0x, d0y) = deriv(t0)
            val (d1x, d1y) = deriv(t1)
            Cubic(p0x + k * d0x, p0y + k * d0y, p3x - k * d1x, p3y - k * d1y, p3x, p3y)
        }
    }

    private fun vectorAngle(ux: Double, uy: Double, vx: Double, vy: Double): Double =
        atan2(ux * vy - uy * vx, ux * vx + uy * vy)
}
