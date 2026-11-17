package riemannsketchpad.model

import riemannsketchpad.geometry.*

class StyleSettings {
    // Point defaults
    var pointColor: String = "black"
    var pointSize: String = "medium"

    // Geodesic defaults
    var geodesicColor: String = "black"
    var geodesicStrokeStyle: String = "solid"
    var geodesicStrokeThickness: String = "normal"

    // Circle defaults
    var circleColor: String = "black"
    var circleStrokeStyle: String = "solid"
    var circleStrokeThickness: String = "normal"

    // Angle defaults
    var angleColor: String = "green"
    var angleSize: String = "medium"

    // Region defaults
    var regionColor: String = "yellow"

    /**
     * Apply the current default styles to the given metadata based on the object type.
     */
    fun applyDefaults(metadata: ObjectMetadata, obj: GeometricObject) {
        when (obj) {
            is FreePoint, is FreePointOnLine, is FreePointOnSegment, is FreePointOnCircle,
            is Midpoint, is ReflectionPoint,
            is GeodesicGeodesicIntersection, is GeodesicCircleIntersection, is CircleCircleIntersection -> {
                metadata.color = pointColor
                metadata.size = pointSize
            }
            is Line, is Segment, is PerpendicularLine -> {
                metadata.color = geodesicColor
                metadata.strokeStyle = geodesicStrokeStyle
                metadata.strokeThickness = geodesicStrokeThickness
            }
            is Circle -> {
                metadata.color = circleColor
                metadata.strokeStyle = circleStrokeStyle
                metadata.strokeThickness = circleStrokeThickness
            }
            is Angle -> {
                metadata.color = angleColor
                metadata.size = angleSize
            }
            is Region -> {
                metadata.color = regionColor
            }
        }
    }
}
