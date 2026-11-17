package riemannsketchpad.geometry

sealed interface GeometricObject {
    val id: Int
    val parentIds: List<Int>
    var isDefined: Boolean
    fun recompute(objects: Map<Int, GeometricObject>)
}

interface PointLike {
    var x: Double
    var y: Double
    var z: Double
}

enum class GeodesicType { LINE, SEGMENT }

interface GeodesicLike {
    val point1Id: Int
    val point2Id: Int
    val geodesicType: GeodesicType
    var normalX: Double
    var normalY: Double
    var normalZ: Double
    var x1: Double
    var y1: Double
    var z1: Double
    var x2: Double
    var y2: Double
    var z2: Double
}
