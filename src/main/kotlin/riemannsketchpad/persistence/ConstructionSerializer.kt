package riemannsketchpad.persistence

import kotlinx.serialization.json.*
import riemannsketchpad.APP_NAME
import riemannsketchpad.APP_VERSION
import riemannsketchpad.geometry.*
import riemannsketchpad.model.GeometricObjectManager

data class LoadedConstruction(
    val fileName: String,
    val diagram: SerializedDiagram,
    val inputTypes: List<String>,
    val inputIds: List<Int>
)

object ConstructionSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * Save a construction. Strips labels and style fields.
     * isInput/isLocked flags are preserved (set by the recording process).
     */
    fun saveConstruction(manager: GeometricObjectManager): String {
        val objs = manager.getObjects().values.map { serializeConstructionObject(it) }
        val metas = manager.getMetadata().mapKeys { it.key.toString() }.mapValues { (_, meta) ->
            SerializedMetadata(
                visible = meta.visible,
                label = null,
                labelVisible = false,
                isInput = meta.isInput,
                isLocked = meta.isLocked
                // Style fields use defaults (stripped)
            )
        }
        val diagram = SerializedDiagram(
            appName = APP_NAME,
            version = APP_VERSION,
            model = "riemann",
            objects = objs,
            metadata = metas
        )
        return json.encodeToString(SerializedDiagram.serializer(), diagram)
    }

    /**
     * Load a construction file into a LoadedConstruction record.
     */
    fun loadConstruction(jsonStr: String, fileName: String): LoadedConstruction {
        val diagram = DiagramSerializer.parseDiagram(jsonStr)

        val inputIds = mutableListOf<Int>()
        val inputTypes = mutableListOf<String>()

        val sortedObjs = diagram.objects.sortedBy { it.id }
        for (sObj in sortedObjs) {
            val metaKey = sObj.id.toString()
            val sMeta = diagram.metadata[metaKey]
            if (sMeta?.isInput == true) {
                inputIds.add(sObj.id)
                inputTypes.add(mapInputType(sObj.type))
            }
        }

        return LoadedConstruction(
            fileName = fileName,
            diagram = diagram,
            inputTypes = inputTypes,
            inputIds = inputIds
        )
    }

    /**
     * Map object type names to input type names.
     * All point subtypes map to "Point".
     */
    private fun mapInputType(typeName: String): String {
        return when (typeName) {
            "FreePoint", "FreePointOnLine", "FreePointOnSegment", "FreePointOnCircle",
            "Midpoint", "ReflectionPoint",
            "GeodesicGeodesicIntersection", "GeodesicCircleIntersection", "CircleCircleIntersection" -> "Point"
            else -> typeName
        }
    }

    private fun serializeConstructionObject(obj: GeometricObject): SerializedObject {
        // Same as DiagramSerializer but we reuse serialization logic
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
}
