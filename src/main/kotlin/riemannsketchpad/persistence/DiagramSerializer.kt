package riemannsketchpad.persistence

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import riemannsketchpad.APP_NAME
import riemannsketchpad.APP_VERSION
import riemannsketchpad.geometry.*
import riemannsketchpad.model.GeometricObjectManager
import riemannsketchpad.model.ObjectMetadata

@Serializable
data class SerializedObject(
    val id: Int,
    val type: String,
    val parentIds: List<Int> = emptyList(),
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val t: Double = 0.0,
    val theta: Double = 0.0,
    val sign: Int = 0,
    val pointIds: List<Int> = emptyList()
)

@Serializable
data class SerializedMetadata(
    val visible: Boolean = true,
    val label: String? = null,
    val labelVisible: Boolean = false,
    val labelOffsetX: Double = 10.0,
    val labelOffsetY: Double = -10.0,
    val inMeasurementTable: Boolean = false,
    val labelAnchorAngle: Double = 0.0,
    val labelAnchorT: Double = 0.5,
    val isInput: Boolean = false,
    val isLocked: Boolean = false,
    val color: String = "black",
    val size: String = "medium",
    val strokeStyle: String = "solid",
    val strokeThickness: String = "normal"
)

@Serializable
data class SerializedDiagram(
    val appName: String,
    val version: String,
    val model: String,
    val objects: List<SerializedObject>,
    val metadata: Map<String, SerializedMetadata>
)

class IncompatibleAppNameException(appName: String) :
    Exception("Incompatible app name: $appName (expected $APP_NAME)")

class IncompatibleVersionException(fileVersion: String, appVersion: String) :
    Exception("Incompatible version: file is $fileVersion, app is $appVersion")

object DiagramSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun serialize(manager: GeometricObjectManager): String {
        val objs = manager.getObjects().values.map { serializeObject(it) }
        val metas = manager.getMetadata().mapKeys { it.key.toString() }.mapValues { serializeMetadata(it.value) }
        val diagram = SerializedDiagram(
            appName = APP_NAME,
            version = APP_VERSION,
            model = "riemann",
            objects = objs,
            metadata = metas
        )
        return json.encodeToString(diagram)
    }

    fun deserialize(jsonStr: String, manager: GeometricObjectManager) {
        val diagram = json.decodeFromString<SerializedDiagram>(jsonStr)

        // Validate app name
        if (diagram.appName != APP_NAME) {
            throw IncompatibleAppNameException(diagram.appName)
        }

        // Validate version (major version must match)
        val fileMajor = diagram.version.split(".").firstOrNull()?.toIntOrNull() ?: 0
        val appMajor = APP_VERSION.split(".").firstOrNull()?.toIntOrNull() ?: 0
        if (fileMajor != appMajor) {
            throw IncompatibleVersionException(diagram.version, APP_VERSION)
        }

        // Validate model
        if (diagram.model != "riemann") {
            throw IllegalArgumentException("Unsupported model: ${diagram.model}")
        }

        // Deserialize objects in ID order
        val sortedObjs = diagram.objects.sortedBy { it.id }
        for (sObj in sortedObjs) {
            val obj = deserializeObject(sObj)
            val metaKey = sObj.id.toString()
            val sMeta = diagram.metadata[metaKey]
            val meta = if (sMeta != null) deserializeMetadata(sMeta, obj) else ObjectMetadata()
            manager.addObject(obj, meta)
        }
        manager.recomputeAll()
    }

    fun parseDiagram(jsonStr: String): SerializedDiagram {
        val diagram = json.decodeFromString<SerializedDiagram>(jsonStr)
        if (diagram.appName != APP_NAME) throw IncompatibleAppNameException(diagram.appName)
        val fileMajor = diagram.version.split(".").firstOrNull()?.toIntOrNull() ?: 0
        val appMajor = APP_VERSION.split(".").firstOrNull()?.toIntOrNull() ?: 0
        if (fileMajor != appMajor) throw IncompatibleVersionException(diagram.version, APP_VERSION)
        if (diagram.model != "riemann") throw IllegalArgumentException("Unsupported model: ${diagram.model}")
        return diagram
    }

    private fun serializeObject(obj: GeometricObject): SerializedObject {
        return when (obj) {
            is FreePoint -> SerializedObject(obj.id, "FreePoint", x = obj.x, y = obj.y, z = obj.z)
            is FreePointOnLine -> SerializedObject(obj.id, "FreePointOnLine", parentIds = listOf(obj.lineId), t = obj.t)
            is FreePointOnSegment -> SerializedObject(obj.id, "FreePointOnSegment", parentIds = listOf(obj.segmentId), t = obj.t)
            is FreePointOnCircle -> SerializedObject(obj.id, "FreePointOnCircle", parentIds = listOf(obj.circleId), theta = obj.theta)
            is Midpoint -> SerializedObject(obj.id, "Midpoint", parentIds = listOf(obj.point1Id, obj.point2Id))
            is ReflectionPoint -> SerializedObject(obj.id, "ReflectionPoint", parentIds = listOf(obj.pointId, obj.geodesicId))
            is GeodesicGeodesicIntersection -> SerializedObject(obj.id, "GeodesicGeodesicIntersection", parentIds = listOf(obj.geodesic1Id, obj.geodesic2Id), sign = obj.sign)
            is GeodesicCircleIntersection -> SerializedObject(obj.id, "GeodesicCircleIntersection", parentIds = listOf(obj.geodesicId, obj.circleId), sign = obj.sign)
            is CircleCircleIntersection -> SerializedObject(obj.id, "CircleCircleIntersection", parentIds = listOf(obj.circle1Id, obj.circle2Id), sign = obj.sign)
            is Line -> SerializedObject(obj.id, "Line", parentIds = listOf(obj.point1Id, obj.point2Id))
            is Segment -> SerializedObject(obj.id, "Segment", parentIds = listOf(obj.point1Id, obj.point2Id))
            is PerpendicularLine -> SerializedObject(obj.id, "PerpendicularLine", parentIds = listOf(obj.pointId, obj.geodesicId))
            is Circle -> SerializedObject(obj.id, "Circle", parentIds = listOf(obj.centerId, obj.radiusPointId))
            is Angle -> SerializedObject(obj.id, "Angle", parentIds = listOf(obj.pointAId, obj.vertexId, obj.pointCId))
            is Region -> SerializedObject(obj.id, "Region", pointIds = obj.pointIds)
        }
    }

    private fun serializeMetadata(meta: ObjectMetadata): SerializedMetadata {
        return SerializedMetadata(
            visible = meta.visible,
            label = meta.label,
            labelVisible = meta.labelVisible,
            labelOffsetX = meta.labelOffsetX,
            labelOffsetY = meta.labelOffsetY,
            inMeasurementTable = meta.inMeasurementTable,
            labelAnchorAngle = meta.labelAnchorAngle,
            labelAnchorT = meta.labelAnchorT,
            isInput = meta.isInput,
            isLocked = meta.isLocked,
            color = meta.color,
            size = meta.size,
            strokeStyle = meta.strokeStyle,
            strokeThickness = meta.strokeThickness
        )
    }

    fun deserializeObject(sObj: SerializedObject): GeometricObject {
        return when (sObj.type) {
            "FreePoint" -> FreePoint(sObj.id, sObj.x, sObj.y, sObj.z)
            "FreePointOnLine" -> FreePointOnLine(sObj.id, sObj.parentIds[0], sObj.t)
            "FreePointOnSegment" -> FreePointOnSegment(sObj.id, sObj.parentIds[0], sObj.t)
            "FreePointOnCircle" -> FreePointOnCircle(sObj.id, sObj.parentIds[0], sObj.theta)
            "Midpoint" -> Midpoint(sObj.id, sObj.parentIds[0], sObj.parentIds[1])
            "ReflectionPoint" -> ReflectionPoint(sObj.id, sObj.parentIds[0], sObj.parentIds[1])
            "GeodesicGeodesicIntersection" -> GeodesicGeodesicIntersection(sObj.id, sObj.parentIds[0], sObj.parentIds[1], sObj.sign)
            "GeodesicCircleIntersection" -> GeodesicCircleIntersection(sObj.id, sObj.parentIds[0], sObj.parentIds[1], sObj.sign)
            "CircleCircleIntersection" -> CircleCircleIntersection(sObj.id, sObj.parentIds[0], sObj.parentIds[1], sObj.sign)
            "Line" -> Line(sObj.id, sObj.parentIds[0], sObj.parentIds[1])
            "Segment" -> Segment(sObj.id, sObj.parentIds[0], sObj.parentIds[1])
            "PerpendicularLine" -> PerpendicularLine(sObj.id, sObj.parentIds[0], sObj.parentIds[1])
            "Circle" -> Circle(sObj.id, sObj.parentIds[0], sObj.parentIds[1])
            "Angle" -> Angle(sObj.id, sObj.parentIds[0], sObj.parentIds[1], sObj.parentIds[2])
            "Region" -> Region(sObj.id, sObj.pointIds)
            else -> throw IllegalArgumentException("Unknown object type: ${sObj.type}")
        }
    }

    fun deserializeMetadata(sMeta: SerializedMetadata, obj: GeometricObject): ObjectMetadata {
        val meta = ObjectMetadata(
            visible = sMeta.visible,
            label = sMeta.label,
            labelVisible = sMeta.labelVisible,
            labelOffsetX = sMeta.labelOffsetX,
            labelOffsetY = sMeta.labelOffsetY,
            inMeasurementTable = sMeta.inMeasurementTable,
            labelAnchorAngle = sMeta.labelAnchorAngle,
            labelAnchorT = sMeta.labelAnchorT,
            isInput = sMeta.isInput,
            isLocked = sMeta.isLocked,
            color = sMeta.color,
            size = sMeta.size,
            strokeStyle = sMeta.strokeStyle,
            strokeThickness = sMeta.strokeThickness
        )
        // Override default color for Angle and Region if color is "black" (default)
        // to match pre-styling rendering behavior
        if (sMeta.color == "black") {
            when (obj) {
                is Angle -> meta.color = "green"
                is Region -> meta.color = "yellow"
                else -> {}
            }
        }
        return meta
    }
}
