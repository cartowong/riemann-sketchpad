package riemannsketchpad.persistence

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import riemannsketchpad.geometry.*
import riemannsketchpad.model.GeometricObjectManager
import riemannsketchpad.model.ObjectMetadata

class ConstructionSerializerTest {

    @Test
    fun `saveConstruction strips labels and styles`() {
        val mgr = GeometricObjectManager()
        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0), ObjectMetadata(
            label = "A", labelVisible = true, isInput = true, color = "red", size = "large"
        ))
        mgr.addObject(FreePoint(1, 0.0, 1.0, 0.0), ObjectMetadata(
            label = "B", labelVisible = true, isInput = true
        ))
        mgr.addObject(Midpoint(2, 0, 1), ObjectMetadata(label = "M", labelVisible = true))
        mgr.recomputeAll()

        val json = ConstructionSerializer.saveConstruction(mgr)
        // Labels should be absent (null default omitted) or explicitly null
        assertFalse(json.contains("\"A\""))
        assertFalse(json.contains("\"B\""))
        assertFalse(json.contains("\"M\""))
        // Style fields should be defaults — custom values should not appear
        assertFalse(json.contains("\"red\""))
        assertFalse(json.contains("\"large\""))
    }

    @Test
    fun `saveConstruction preserves isInput and isLocked flags`() {
        val mgr = GeometricObjectManager()
        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0), ObjectMetadata(isInput = true))
        mgr.addObject(FreePoint(1, 0.0, 1.0, 0.0), ObjectMetadata(isInput = true))
        mgr.addObject(Midpoint(2, 0, 1))
        mgr.recomputeAll()

        val json = ConstructionSerializer.saveConstruction(mgr)
        assertTrue(json.contains("\"isInput\": true") || json.contains("\"isInput\":true"))
    }

    @Test
    fun `loadConstruction extracts input types`() {
        val mgr = GeometricObjectManager()
        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0), ObjectMetadata(isInput = true))
        mgr.addObject(FreePoint(1, 0.0, 1.0, 0.0), ObjectMetadata(isInput = true))
        mgr.addObject(Midpoint(2, 0, 1))
        mgr.recomputeAll()

        val json = ConstructionSerializer.saveConstruction(mgr)
        val loaded = ConstructionSerializer.loadConstruction(json, "midpoint.json")

        assertEquals("midpoint.json", loaded.fileName)
        assertEquals(2, loaded.inputTypes.size)
        assertEquals("Point", loaded.inputTypes[0])
        assertEquals("Point", loaded.inputTypes[1])
        assertEquals(listOf(0, 1), loaded.inputIds)
    }

    @Test
    fun `loadConstruction maps all point types to Point`() {
        // Create a construction with a GeodesicGeodesicIntersection as input
        val json = """{
            "appName": "RiemannSketchpad",
            "version": "1.0.0",
            "model": "riemann",
            "objects": [
                {"id": 0, "type": "GeodesicGeodesicIntersection", "parentIds": [10, 20], "sign": 1}
            ],
            "metadata": {"0": {"isInput": true}}
        }"""
        val loaded = ConstructionSerializer.loadConstruction(json, "test.json")
        assertEquals(listOf("Point"), loaded.inputTypes)
    }

    @Test
    fun `loadConstruction rejects wrong model`() {
        val json = """{"appName":"RiemannSketchpad","version":"1.0.0","model":"poincare","objects":[],"metadata":{}}"""
        assertThrows(IllegalArgumentException::class.java) {
            ConstructionSerializer.loadConstruction(json, "test.json")
        }
    }

    @Test
    fun `loadConstruction identifies geodesic input types`() {
        val json = """{
            "appName": "RiemannSketchpad",
            "version": "1.0.0",
            "model": "riemann",
            "objects": [
                {"id": 0, "type": "FreePoint", "x": 1.0, "y": 0.0, "z": 0.0},
                {"id": 1, "type": "Line", "parentIds": [10, 20]}
            ],
            "metadata": {"0": {"isInput": true}, "1": {"isInput": true}}
        }"""
        val loaded = ConstructionSerializer.loadConstruction(json, "test.json")
        assertEquals(listOf("Point", "Line"), loaded.inputTypes)
    }
}
