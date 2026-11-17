package riemannsketchpad.geometry

import kotlin.math.*

object MeasurementUtils {

    fun segmentLength(seg: Segment): Double {
        return SphericalUtils.sphericalDistance(seg.x1, seg.y1, seg.z1, seg.x2, seg.y2, seg.z2)
    }

    fun circleCircumference(circle: Circle): Double {
        return 2 * PI * sin(circle.sphericalRadius)
    }

    fun angleDegrees(angle: Angle): Double {
        return angle.measureDegrees()
    }

    fun regionArea(region: Region): Double {
        return region.measureArea()
    }

    /**
     * Compute a measurement value for the given object. Returns null for non-measurable types.
     */
    fun measure(obj: GeometricObject): Double? {
        if (!obj.isDefined) return null
        return when (obj) {
            is Segment -> segmentLength(obj)
            is Circle -> circleCircumference(obj)
            is Angle -> angleDegrees(obj)
            is Region -> regionArea(obj)
            else -> null
        }
    }

    /**
     * Format a measurement value as a display string.
     */
    fun measureLabel(obj: GeometricObject): String? {
        val value = measure(obj) ?: return null
        return when (obj) {
            is Angle -> "%.1f\u00B0".format(value)
            else -> "%.2f".format(value)
        }
    }
}
