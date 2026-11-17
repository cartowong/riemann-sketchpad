package riemannsketchpad.geometry

import kotlin.math.*

class Angle(
    override val id: Int,
    val pointAId: Int,
    val vertexId: Int,
    val pointCId: Int
) : GeometricObject {
    override val parentIds: List<Int> = listOf(pointAId, vertexId, pointCId)
    override var isDefined: Boolean = true

    var vertexX: Double = 0.0
    var vertexY: Double = 0.0
    var vertexZ: Double = 0.0

    // Tangent vectors in 3D at the vertex
    var tangentAX: Double = 0.0
    var tangentAY: Double = 0.0
    var tangentAZ: Double = 0.0
    var tangentCX: Double = 0.0
    var tangentCY: Double = 0.0
    var tangentCZ: Double = 0.0

    var measureRadians: Double = 0.0

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val pA = objects[pointAId]
        val pV = objects[vertexId]
        val pC = objects[pointCId]
        if (pA == null || pV == null || pC == null ||
            !pA.isDefined || !pV.isDefined || !pC.isDefined ||
            pA !is PointLike || pV !is PointLike || pC !is PointLike
        ) {
            isDefined = false; return
        }

        // Tangent at V toward A
        val tA = SphericalUtils.tangentVector(pV.x, pV.y, pV.z, pA.x, pA.y, pA.z)
        val tC = SphericalUtils.tangentVector(pV.x, pV.y, pV.z, pC.x, pC.y, pC.z)
        if (tA == null || tC == null) { isDefined = false; return }

        isDefined = true
        vertexX = pV.x; vertexY = pV.y; vertexZ = pV.z
        tangentAX = tA.first; tangentAY = tA.second; tangentAZ = tA.third
        tangentCX = tC.first; tangentCY = tC.second; tangentCZ = tC.third

        // Angle between tangent vectors using the outward normal V for orientation
        val dotTC = SphericalUtils.dot(tangentAX, tangentAY, tangentAZ, tangentCX, tangentCY, tangentCZ)
            .coerceIn(-1.0, 1.0)
        // Cross product of tA and tC, dotted with V gives the signed area
        val crossX = SphericalUtils.crossX(tangentAX, tangentAY, tangentAZ, tangentCX, tangentCY, tangentCZ)
        val crossY = SphericalUtils.crossY(tangentAX, tangentAY, tangentAZ, tangentCX, tangentCY, tangentCZ)
        val crossZ = SphericalUtils.crossZ(tangentAX, tangentAY, tangentAZ, tangentCX, tangentCY, tangentCZ)
        val sinAngle = SphericalUtils.dot(crossX, crossY, crossZ, vertexX, vertexY, vertexZ)

        // atan2 gives signed angle, counterclockwise as seen from outside
        val angle = atan2(-sinAngle, dotTC)
        measureRadians = if (angle < 0) angle + 2 * PI else angle
    }

    fun measureDegrees(): Double = Math.toDegrees(measureRadians)
}
