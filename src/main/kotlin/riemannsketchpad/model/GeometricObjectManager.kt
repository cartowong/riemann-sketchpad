package riemannsketchpad.model

import riemannsketchpad.geometry.*

data class ManagerState(
    val objects: LinkedHashMap<Int, GeometricObject>,
    val metadata: HashMap<Int, ObjectMetadata>,
    val nextId: Int
)

class GeometricObjectManager {
    private val objects: LinkedHashMap<Int, GeometricObject> = LinkedHashMap()
    private val metadata: HashMap<Int, ObjectMetadata> = HashMap()
    private var nextId: Int = 0

    // Label counter for nextDefaultLabel
    private var labelCounter: Int = 0

    fun getObjects(): Map<Int, GeometricObject> = objects
    fun getMetadata(): Map<Int, ObjectMetadata> = metadata
    fun getMetadata(id: Int): ObjectMetadata? = metadata[id]
    fun getObject(id: Int): GeometricObject? = objects[id]
    fun getNextId(): Int = nextId

    /**
     * Add an object. The object's ID must equal nextId.
     * Returns the assigned ID.
     */
    fun addObject(obj: GeometricObject, meta: ObjectMetadata = ObjectMetadata()): Int {
        require(obj.id == nextId) { "Object ID ${obj.id} does not match nextId $nextId" }
        objects[obj.id] = obj
        metadata[obj.id] = meta
        nextId++
        return obj.id
    }

    /**
     * Allocate the next ID without adding an object. Useful for callers that need to
     * construct the object with the correct ID.
     */
    fun peekNextId(): Int = nextId

    /**
     * Remove an object and all its descendants.
     */
    fun removeWithDescendants(id: Int) {
        val toRemove = mutableSetOf(id)
        // Find all descendants
        for ((oid, obj) in objects) {
            if (oid <= id) continue
            if (obj.parentIds.any { it in toRemove }) {
                toRemove.add(oid)
            }
        }
        for (rid in toRemove) {
            objects.remove(rid)
            metadata.remove(rid)
        }
    }

    /**
     * Move a free or semi-free point to a new position.
     */
    fun movePoint(id: Int, newX: Double, newY: Double, newZ: Double) {
        val obj = objects[id] ?: return
        when (obj) {
            is FreePoint -> {
                val (nx, ny, nz) = SphericalUtils.normalizeToSphere(newX, newY, newZ)
                obj.x = nx; obj.y = ny; obj.z = nz
            }
            is FreePointOnLine -> {
                obj.updateParameterFromPosition(newX, newY, newZ, objects)
            }
            is FreePointOnSegment -> {
                obj.updateParameterFromPosition(newX, newY, newZ, objects)
            }
            is FreePointOnCircle -> {
                obj.updateParameterFromPosition(newX, newY, newZ, objects)
            }
            else -> return
        }
        propagateFrom(id)
    }

    /**
     * Check if an object is movable (free or semi-free point).
     */
    fun isMovable(id: Int): Boolean {
        return when (objects[id]) {
            is FreePoint, is FreePointOnLine, is FreePointOnSegment, is FreePointOnCircle -> true
            else -> false
        }
    }

    /**
     * Recompute all objects from startId upward.
     */
    fun propagateFrom(startId: Int) {
        for ((oid, obj) in objects) {
            if (oid >= startId) {
                obj.recompute(objects)
            }
        }
    }

    /**
     * Recompute all objects.
     */
    fun recomputeAll() {
        propagateFrom(0)
    }

    /**
     * Take a deep-copy snapshot of the current state.
     */
    fun snapshot(): ManagerState {
        val objsCopy = LinkedHashMap<Int, GeometricObject>()
        val metaCopy = HashMap<Int, ObjectMetadata>()
        for ((id, obj) in objects) {
            objsCopy[id] = deepCopyObject(obj)
        }
        for ((id, meta) in metadata) {
            metaCopy[id] = meta.deepCopy()
        }
        return ManagerState(objsCopy, metaCopy, nextId)
    }

    /**
     * Restore from a snapshot.
     */
    fun restore(state: ManagerState) {
        objects.clear()
        metadata.clear()
        for ((id, obj) in state.objects) {
            objects[id] = deepCopyObject(obj)
        }
        for ((id, meta) in state.metadata) {
            metadata[id] = meta.deepCopy()
        }
        nextId = state.nextId
    }

    /**
     * Get the next default label (A, B, C, ..., Z, AA, AB, ...).
     */
    fun nextDefaultLabel(): String {
        val label = indexToLabel(labelCounter)
        labelCounter++
        return label
    }

    private fun indexToLabel(index: Int): String {
        var i = index
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + (i % 26)))
            i = i / 26 - 1
        } while (i >= 0)
        return sb.toString()
    }

    companion object {
        fun deepCopyObject(obj: GeometricObject): GeometricObject {
            return when (obj) {
                is FreePoint -> FreePoint(obj.id, obj.x, obj.y, obj.z).also { it.isDefined = obj.isDefined }
                is FreePointOnLine -> FreePointOnLine(obj.id, obj.lineId, obj.t).also {
                    it.isDefined = obj.isDefined; it.x = obj.x; it.y = obj.y; it.z = obj.z
                }
                is FreePointOnSegment -> FreePointOnSegment(obj.id, obj.segmentId, obj.t).also {
                    it.isDefined = obj.isDefined; it.x = obj.x; it.y = obj.y; it.z = obj.z
                }
                is FreePointOnCircle -> FreePointOnCircle(obj.id, obj.circleId, obj.theta).also {
                    it.isDefined = obj.isDefined; it.x = obj.x; it.y = obj.y; it.z = obj.z
                }
                is Midpoint -> Midpoint(obj.id, obj.point1Id, obj.point2Id).also {
                    it.isDefined = obj.isDefined; it.x = obj.x; it.y = obj.y; it.z = obj.z
                }
                is ReflectionPoint -> ReflectionPoint(obj.id, obj.pointId, obj.geodesicId).also {
                    it.isDefined = obj.isDefined; it.x = obj.x; it.y = obj.y; it.z = obj.z
                }
                is GeodesicGeodesicIntersection -> GeodesicGeodesicIntersection(obj.id, obj.geodesic1Id, obj.geodesic2Id, obj.sign).also {
                    it.isDefined = obj.isDefined; it.x = obj.x; it.y = obj.y; it.z = obj.z
                }
                is GeodesicCircleIntersection -> GeodesicCircleIntersection(obj.id, obj.geodesicId, obj.circleId, obj.sign).also {
                    it.isDefined = obj.isDefined; it.x = obj.x; it.y = obj.y; it.z = obj.z
                }
                is CircleCircleIntersection -> CircleCircleIntersection(obj.id, obj.circle1Id, obj.circle2Id, obj.sign).also {
                    it.isDefined = obj.isDefined; it.x = obj.x; it.y = obj.y; it.z = obj.z
                }
                is Line -> Line(obj.id, obj.point1Id, obj.point2Id).also {
                    it.isDefined = obj.isDefined
                    it.normalX = obj.normalX; it.normalY = obj.normalY; it.normalZ = obj.normalZ
                    it.x1 = obj.x1; it.y1 = obj.y1; it.z1 = obj.z1
                    it.x2 = obj.x2; it.y2 = obj.y2; it.z2 = obj.z2
                }
                is Segment -> Segment(obj.id, obj.point1Id, obj.point2Id).also {
                    it.isDefined = obj.isDefined
                    it.normalX = obj.normalX; it.normalY = obj.normalY; it.normalZ = obj.normalZ
                    it.x1 = obj.x1; it.y1 = obj.y1; it.z1 = obj.z1
                    it.x2 = obj.x2; it.y2 = obj.y2; it.z2 = obj.z2
                }
                is PerpendicularLine -> PerpendicularLine(obj.id, obj.pointId, obj.geodesicId).also {
                    it.isDefined = obj.isDefined
                    it.normalX = obj.normalX; it.normalY = obj.normalY; it.normalZ = obj.normalZ
                    it.x1 = obj.x1; it.y1 = obj.y1; it.z1 = obj.z1
                    it.x2 = obj.x2; it.y2 = obj.y2; it.z2 = obj.z2
                }
                is Circle -> Circle(obj.id, obj.centerId, obj.radiusPointId).also {
                    it.isDefined = obj.isDefined
                    it.centerX = obj.centerX; it.centerY = obj.centerY; it.centerZ = obj.centerZ
                    it.sphericalRadius = obj.sphericalRadius; it.cosRadius = obj.cosRadius
                }
                is Angle -> Angle(obj.id, obj.pointAId, obj.vertexId, obj.pointCId).also {
                    it.isDefined = obj.isDefined
                    it.vertexX = obj.vertexX; it.vertexY = obj.vertexY; it.vertexZ = obj.vertexZ
                    it.tangentAX = obj.tangentAX; it.tangentAY = obj.tangentAY; it.tangentAZ = obj.tangentAZ
                    it.tangentCX = obj.tangentCX; it.tangentCY = obj.tangentCY; it.tangentCZ = obj.tangentCZ
                    it.measureRadians = obj.measureRadians
                }
                is Region -> Region(obj.id, obj.pointIds.toList()).also {
                    it.isDefined = obj.isDefined
                    it.vertices = obj.vertices.toList()
                }
            }
        }
    }
}
