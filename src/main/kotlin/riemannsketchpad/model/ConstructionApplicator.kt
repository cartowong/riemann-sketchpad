package riemannsketchpad.model

import riemannsketchpad.geometry.*
import riemannsketchpad.persistence.DiagramSerializer
import riemannsketchpad.persistence.LoadedConstruction
import riemannsketchpad.persistence.SerializedObject

class ConstructionApplicator(
    private val manager: GeometricObjectManager,
    private val styleSettings: StyleSettings
) {
    /**
     * Apply a loaded construction to the current diagram.
     * @param construction The loaded construction
     * @param inputMapping Map from construction input IDs to user-selected object IDs
     */
    fun apply(construction: LoadedConstruction, inputMapping: Map<Int, Int>) {
        val idRemap = HashMap<Int, Int>()

        // Map input IDs to user-selected objects
        for ((constructionId, userId) in inputMapping) {
            idRemap[constructionId] = userId
        }

        val sortedObjs = construction.diagram.objects.sortedBy { it.id }
        for (sObj in sortedObjs) {
            val metaKey = sObj.id.toString()
            val sMeta = construction.diagram.metadata[metaKey]

            // Skip locked objects
            if (sMeta?.isLocked == true) continue
            // Skip input objects (already mapped)
            if (sMeta?.isInput == true) continue

            // Remap parent IDs
            val remappedObj = remapObject(sObj, idRemap)
            val meta = ObjectMetadata()
            if (sMeta != null) {
                meta.visible = sMeta.visible
            }
            // Apply current style defaults
            val obj = DiagramSerializer.deserializeObject(remappedObj)
            styleSettings.applyDefaults(meta, obj)
            manager.addObject(obj, meta)
            idRemap[sObj.id] = obj.id

            // If the construction input is hidden in the file, hide the corresponding user-selected object
            if (sMeta?.isInput == true && !sMeta.visible) {
                val userId = inputMapping[sObj.id]
                if (userId != null) {
                    manager.getMetadata(userId)?.visible = false
                }
            }
        }

        manager.recomputeAll()
    }

    /**
     * Compute a preview of the construction result without modifying the real manager.
     * Returns a list of (object, metadata) pairs with negative IDs.
     */
    fun computePreview(construction: LoadedConstruction, inputMapping: Map<Int, Int>): List<Pair<GeometricObject, ObjectMetadata>> {
        val idRemap = HashMap<Int, Int>()
        var nextNegId = -1

        for ((constructionId, userId) in inputMapping) {
            idRemap[constructionId] = userId
        }

        // Build a temporary object map that includes real objects
        val tempObjects = LinkedHashMap<Int, GeometricObject>(manager.getObjects())
        val result = mutableListOf<Pair<GeometricObject, ObjectMetadata>>()

        val sortedObjs = construction.diagram.objects.sortedBy { it.id }
        for (sObj in sortedObjs) {
            val metaKey = sObj.id.toString()
            val sMeta = construction.diagram.metadata[metaKey]

            if (sMeta?.isLocked == true) continue
            if (sMeta?.isInput == true) continue

            val remappedObj = remapObject(sObj, idRemap, nextNegId)
            val obj = DiagramSerializer.deserializeObject(remappedObj)
            val meta = ObjectMetadata()
            if (sMeta != null) {
                meta.visible = sMeta.visible
            }

            obj.recompute(tempObjects)
            tempObjects[obj.id] = obj
            idRemap[sObj.id] = obj.id
            nextNegId--

            if (meta.visible && obj.isDefined) {
                result.add(obj to meta)
            }
        }

        return result
    }

    private fun remapObject(sObj: SerializedObject, idRemap: Map<Int, Int>, forceId: Int? = null): SerializedObject {
        val newId = forceId ?: manager.peekNextId()
        val remappedParentIds = sObj.parentIds.map { idRemap[it] ?: it }
        val remappedPointIds = sObj.pointIds.map { idRemap[it] ?: it }
        return sObj.copy(
            id = newId,
            parentIds = remappedParentIds,
            pointIds = remappedPointIds
        )
    }
}
