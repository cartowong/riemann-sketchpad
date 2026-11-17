package riemannsketchpad.geometry

import kotlin.math.*

object SphericalUtils {
    const val EPSILON = 1e-10

    fun sphericalDistance(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double): Double {
        val dot = (x1 * x2 + y1 * y2 + z1 * z2).coerceIn(-1.0, 1.0)
        return acos(dot)
    }

    fun dot(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double): Double =
        x1 * x2 + y1 * y2 + z1 * z2

    fun crossX(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double): Double =
        y1 * z2 - z1 * y2

    fun crossY(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double): Double =
        z1 * x2 - x1 * z2

    fun crossZ(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double): Double =
        x1 * y2 - y1 * x2

    fun length(x: Double, y: Double, z: Double): Double = sqrt(x * x + y * y + z * z)

    fun normalizeToSphere(x: Double, y: Double, z: Double): Triple<Double, Double, Double> {
        val len = length(x, y, z)
        if (len < EPSILON) return Triple(0.0, 0.0, 1.0) // fallback
        return Triple(x / len, y / len, z / len)
    }

    fun greatCircleNormal(
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double
    ): Triple<Double, Double, Double>? {
        val cx = crossX(x1, y1, z1, x2, y2, z2)
        val cy = crossY(x1, y1, z1, x2, y2, z2)
        val cz = crossZ(x1, y1, z1, x2, y2, z2)
        val len = length(cx, cy, cz)
        if (len < EPSILON) return null // coincident or antipodal
        return Triple(cx / len, cy / len, cz / len)
    }

    fun tangentVector(
        px: Double, py: Double, pz: Double,
        qx: Double, qy: Double, qz: Double
    ): Triple<Double, Double, Double>? {
        // Component of Q perpendicular to P: Q - (Q·P)*P
        val d = dot(qx, qy, qz, px, py, pz)
        val tx = qx - d * px
        val ty = qy - d * py
        val tz = qz - d * pz
        val len = length(tx, ty, tz)
        if (len < EPSILON) return null
        return Triple(tx / len, ty / len, tz / len)
    }

    fun slerp(
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double,
        t: Double
    ): Triple<Double, Double, Double> {
        val d = dot(x1, y1, z1, x2, y2, z2).coerceIn(-1.0, 1.0)
        val omega = acos(d)
        if (omega < EPSILON) return Triple(x1, y1, z1)
        val sinOmega = sin(omega)
        val a = sin((1 - t) * omega) / sinOmega
        val b = sin(t * omega) / sinOmega
        return Triple(a * x1 + b * x2, a * y1 + b * y2, a * z1 + b * z2)
    }

    fun reflectThroughGreatCircle(
        px: Double, py: Double, pz: Double,
        nx: Double, ny: Double, nz: Double
    ): Triple<Double, Double, Double> {
        val d = dot(px, py, pz, nx, ny, nz)
        return Triple(px - 2 * d * nx, py - 2 * d * ny, pz - 2 * d * nz)
    }

    fun midpoint(
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double
    ): Triple<Double, Double, Double>? {
        val mx = x1 + x2
        val my = y1 + y2
        val mz = z1 + z2
        val len = length(mx, my, mz)
        if (len < EPSILON) return null // antipodal
        return Triple(mx / len, my / len, mz / len)
    }

    fun footOfPerpendicular(
        px: Double, py: Double, pz: Double,
        nx: Double, ny: Double, nz: Double
    ): Triple<Double, Double, Double>? {
        val d = dot(px, py, pz, nx, ny, nz)
        val fx = px - d * nx
        val fy = py - d * ny
        val fz = pz - d * nz
        val len = length(fx, fy, fz)
        if (len < EPSILON) return null // P is a pole of the geodesic
        return Triple(fx / len, fy / len, fz / len)
    }

    fun isAntipodal(
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double
    ): Boolean {
        return dot(x1, y1, z1, x2, y2, z2) < -1 + EPSILON
    }

    /**
     * Compute view matrix as a DoubleArray(9) row-major 3x3 matrix.
     * V = Rx(elevation) * Ry(-azimuth)
     * azimuthDeg and elevationDeg in degrees.
     */
    fun viewMatrix(azimuthDeg: Double, elevationDeg: Double): DoubleArray {
        val theta = Math.toRadians(azimuthDeg)
        val phi = Math.toRadians(elevationDeg)
        val cosT = cos(theta)
        val sinT = sin(theta)
        val cosP = cos(phi)
        val sinP = sin(phi)
        // Ry(-theta):
        // [ cosT  0  sinT ]
        // [  0    1   0   ]
        // [-sinT  0  cosT ]
        // (note: Ry(-theta) = Ry(theta)^T, but let's expand Ry(-theta) directly)
        // Actually Ry(-theta) rotates by -theta around Y:
        // [ cos(-t)  0  sin(-t) ]   [ cosT   0  -sinT ]
        // [   0      1    0     ] = [  0     1    0    ]
        // [-sin(-t)  0  cos(-t) ]   [ sinT   0   cosT ]

        // Rx(phi):
        // [ 1    0      0    ]
        // [ 0  cosP  -sinP   ]
        // [ 0  sinP   cosP   ]

        // V = Rx(phi) * Ry(-theta):
        // Row 0: [cosT,          0,    -sinT        ]  * Rx = [cosT,  0, -sinT] (Rx row0 = [1,0,0])
        // Actually let's multiply properly:
        // V[i][j] = sum_k Rx[i][k] * Ry[k][j]
        //
        // Ry(-theta) matrix:
        // m00=cosT  m01=0  m02=-sinT
        // m10=0     m11=1  m12=0
        // m20=sinT  m21=0  m22=cosT
        //
        // V = Rx * Ry:
        // V[0][0] = 1*cosT + 0*0 + 0*sinT = cosT
        // V[0][1] = 1*0 + 0*1 + 0*0 = 0
        // V[0][2] = 1*(-sinT) + 0*0 + 0*cosT = -sinT
        // V[1][0] = 0*cosT + cosP*0 + (-sinP)*sinT = -sinP*sinT
        // V[1][1] = 0*0 + cosP*1 + (-sinP)*0 = cosP
        // V[1][2] = 0*(-sinT) + cosP*0 + (-sinP)*cosT = -sinP*cosT
        // V[2][0] = 0*cosT + sinP*0 + cosP*sinT = cosP*sinT
        // V[2][1] = 0*0 + sinP*1 + cosP*0 = sinP
        // V[2][2] = 0*(-sinT) + sinP*0 + cosP*cosT = cosP*cosT

        return doubleArrayOf(
            cosT, 0.0, -sinT,
            -sinP * sinT, cosP, -sinP * cosT,
            cosP * sinT, sinP, cosP * cosT
        )
    }

    /** Apply 3x3 row-major matrix to vector. */
    fun matVec(m: DoubleArray, x: Double, y: Double, z: Double): Triple<Double, Double, Double> {
        return Triple(
            m[0] * x + m[1] * y + m[2] * z,
            m[3] * x + m[4] * y + m[5] * z,
            m[6] * x + m[7] * y + m[8] * z
        )
    }

    /** Apply transpose of 3x3 row-major matrix to vector (inverse rotation). */
    fun matTVec(m: DoubleArray, x: Double, y: Double, z: Double): Triple<Double, Double, Double> {
        return Triple(
            m[0] * x + m[3] * y + m[6] * z,
            m[1] * x + m[4] * y + m[7] * z,
            m[2] * x + m[5] * y + m[8] * z
        )
    }

    /**
     * Orthographic projection: apply view rotation, return (screenX, screenY, depth).
     * screenX = px, screenY = py, depth = pz.
     * Front hemisphere: depth > 0.
     */
    fun projectToScreen(
        x: Double, y: Double, z: Double, viewMatrix: DoubleArray
    ): Triple<Double, Double, Double> {
        return matVec(viewMatrix, x, y, z)
    }

    /**
     * Inverse projection: given (sx, sy) in model space, compute point on the visible hemisphere.
     * Returns null if sx²+sy² > 1 (outside sphere disk).
     */
    fun unprojectFromScreen(sx: Double, sy: Double, viewMatrix: DoubleArray): Triple<Double, Double, Double>? {
        val r2 = sx * sx + sy * sy
        if (r2 > 1.0) return null
        val sz = sqrt(1.0 - r2)
        return matTVec(viewMatrix, sx, sy, sz)
    }

    fun isOnFrontHemisphere(x: Double, y: Double, z: Double, viewMatrix: DoubleArray): Boolean {
        val (_, _, pz) = projectToScreen(x, y, z, viewMatrix)
        return pz > 0
    }

    /**
     * Compute projected ellipse parameters for a great circle with given plane normal.
     * Returns (centerX, centerY, semiMajor, semiMinor, rotationAngle) or null if degenerate.
     * For great circles, center is always (0,0) and semiMajor is always 1.
     */
    fun ellipseProjectionParams(
        normalX: Double, normalY: Double, normalZ: Double,
        viewMatrix: DoubleArray
    ): EllipseParams {
        val (npx, npy, npz) = matVec(viewMatrix, normalX, normalY, normalZ)
        val semiMajor = 1.0
        val semiMinor = abs(npz)
        val rotationAngle = atan2(npx, -npy)
        return EllipseParams(0.0, 0.0, semiMajor, semiMinor, rotationAngle)
    }

    /**
     * Compute projected ellipse parameters for a small circle.
     * center: spherical center of the circle, cosRadius: cos(spherical radius).
     */
    fun smallCircleEllipseParams(
        centerX: Double, centerY: Double, centerZ: Double,
        cosRadius: Double,
        viewMatrix: DoubleArray
    ): EllipseParams {
        val sinRadius = sqrt((1.0 - cosRadius * cosRadius).coerceAtLeast(0.0))
        // 3D Euclidean center of the circle: center * cosRadius
        val cx3 = centerX * cosRadius
        val cy3 = centerY * cosRadius
        val cz3 = centerZ * cosRadius
        // Project the 3D center
        val (pcx, pcy, _) = matVec(viewMatrix, cx3, cy3, cz3)
        // Project the normal (the spherical center direction)
        val (npx, npy, npz) = matVec(viewMatrix, centerX, centerY, centerZ)
        val semiMajor = sinRadius
        val semiMinor = sinRadius * abs(npz)
        val rotationAngle = atan2(npx, -npy)
        return EllipseParams(pcx, pcy, semiMajor, semiMinor, rotationAngle)
    }
}

data class EllipseParams(
    val centerX: Double,
    val centerY: Double,
    val semiMajor: Double,
    val semiMinor: Double,
    val rotationAngle: Double
)
