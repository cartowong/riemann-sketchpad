package riemannsketchpad.persistence

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import riemannsketchpad.geometry.*
import riemannsketchpad.model.GeometricObjectManager
import riemannsketchpad.model.ObjectMetadata
import kotlin.math.*

class DiagramSerializerTest {

    private val tol = 1e-9

    @Test
    fun `serialize and deserialize round-trip`() {
        val mgr = GeometricObjectManager()
        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0), ObjectMetadata(label = "A", labelVisible = true))
        mgr.addObject(FreePoint(1, 0.0, 1.0, 0.0), ObjectMetadata(label = "B", labelVisible = true))
        mgr.addObject(Line(2, 0, 1))
        mgr.addObject(Midpoint(3, 0, 1))
        mgr.recomputeAll()

        val json = DiagramSerializer.serialize(mgr)

        val mgr2 = GeometricObjectManager()
        DiagramSerializer.deserialize(json, mgr2)

        assertEquals(4, mgr2.getNextId())
        val p0 = mgr2.getObject(0) as FreePoint
        assertEquals(1.0, p0.x, tol)
        assertEquals("A", mgr2.getMetadata(0)?.label)

        val mid = mgr2.getObject(3) as Midpoint
        assertTrue(mid.isDefined)
        val s2 = 1.0 / sqrt(2.0)
        assertEquals(s2, mid.x, tol)
    }

    @Test
    fun `serialize includes model field`() {
        val mgr = GeometricObjectManager()
        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0))
        val json = DiagramSerializer.serialize(mgr)
        assertTrue(json.contains("\"model\": \"riemann\"") || json.contains("\"model\":\"riemann\""))
    }

    @Test
    fun `deserialize rejects wrong app name`() {
        val json = """{"appName":"OtherApp","version":"1.0.0","model":"riemann","objects":[],"metadata":{}}"""
        val mgr = GeometricObjectManager()
        assertThrows(IncompatibleAppNameException::class.java) {
            DiagramSerializer.deserialize(json, mgr)
        }
    }

    @Test
    fun `deserialize rejects wrong major version`() {
        val json = """{"appName":"RiemannSketchpad","version":"2.0.0","model":"riemann","objects":[],"metadata":{}}"""
        val mgr = GeometricObjectManager()
        assertThrows(IncompatibleVersionException::class.java) {
            DiagramSerializer.deserialize(json, mgr)
        }
    }

    @Test
    fun `deserialize rejects wrong model`() {
        val json = """{"appName":"RiemannSketchpad","version":"1.0.0","model":"poincare","objects":[],"metadata":{}}"""
        val mgr = GeometricObjectManager()
        assertThrows(IllegalArgumentException::class.java) {
            DiagramSerializer.deserialize(json, mgr)
        }
    }

    @Test
    fun `deserialize accepts same major version`() {
        val json = """{"appName":"RiemannSketchpad","version":"1.5.0","model":"riemann","objects":[],"metadata":{}}"""
        val mgr = GeometricObjectManager()
        assertDoesNotThrow {
            DiagramSerializer.deserialize(json, mgr)
        }
    }

    @Test
    fun `all object types round-trip`() {
        val mgr = GeometricObjectManager()
        val s2 = 1.0 / sqrt(2.0)
        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0))
        mgr.addObject(FreePoint(1, 0.0, 1.0, 0.0))
        mgr.addObject(FreePoint(2, 0.0, 0.0, 1.0))
        mgr.addObject(Line(3, 0, 1))
        mgr.addObject(Segment(4, 0, 2))
        mgr.addObject(Circle(5, 0, 1))
        mgr.addObject(Midpoint(6, 0, 1))
        mgr.addObject(Angle(7, 1, 0, 2))
        mgr.addObject(Region(8, listOf(0, 1, 2)))
        mgr.addObject(PerpendicularLine(9, 2, 3))
        mgr.addObject(ReflectionPoint(10, 2, 3))
        mgr.addObject(GeodesicGeodesicIntersection(11, 3, 4, 1))
        mgr.addObject(GeodesicCircleIntersection(12, 3, 5, 1))
        mgr.addObject(CircleCircleIntersection(13, 5, 5, 1)) // same circle, degenerate but tests serialization
        mgr.addObject(FreePointOnLine(14, 3, 0.5))
        mgr.addObject(FreePointOnSegment(15, 4, 0.5))
        mgr.addObject(FreePointOnCircle(16, 5, 0.5))
        mgr.recomputeAll()

        val json = DiagramSerializer.serialize(mgr)
        val mgr2 = GeometricObjectManager()
        DiagramSerializer.deserialize(json, mgr2)

        assertEquals(17, mgr2.getNextId())
        assertTrue(mgr2.getObject(3) is Line)
        assertTrue(mgr2.getObject(4) is Segment)
        assertTrue(mgr2.getObject(5) is Circle)
        assertTrue(mgr2.getObject(6) is Midpoint)
        assertTrue(mgr2.getObject(7) is Angle)
        assertTrue(mgr2.getObject(8) is Region)
        assertTrue(mgr2.getObject(9) is PerpendicularLine)
        assertTrue(mgr2.getObject(10) is ReflectionPoint)
        assertTrue(mgr2.getObject(11) is GeodesicGeodesicIntersection)
        assertTrue(mgr2.getObject(12) is GeodesicCircleIntersection)
        assertTrue(mgr2.getObject(13) is CircleCircleIntersection)
        assertTrue(mgr2.getObject(14) is FreePointOnLine)
        assertTrue(mgr2.getObject(15) is FreePointOnSegment)
        assertTrue(mgr2.getObject(16) is FreePointOnCircle)
    }

    @Test
    fun `metadata style fields are preserved`() {
        val mgr = GeometricObjectManager()
        mgr.addObject(FreePoint(0, 1.0, 0.0, 0.0), ObjectMetadata(color = "red", size = "large"))
        val json = DiagramSerializer.serialize(mgr)

        val mgr2 = GeometricObjectManager()
        DiagramSerializer.deserialize(json, mgr2)
        assertEquals("red", mgr2.getMetadata(0)?.color)
        assertEquals("large", mgr2.getMetadata(0)?.size)
    }

    @Test
    fun `angle with default black color gets green on load`() {
        // Simulate an old file where angle has default "black" color
        val json = """{
            "appName": "RiemannSketchpad",
            "version": "1.0.0",
            "model": "riemann",
            "objects": [
                {"id": 0, "type": "FreePoint", "x": 1.0, "y": 0.0, "z": 0.0},
                {"id": 1, "type": "FreePoint", "x": 0.0, "y": 1.0, "z": 0.0},
                {"id": 2, "type": "FreePoint", "x": 0.0, "y": 0.0, "z": 1.0},
                {"id": 3, "type": "Angle", "parentIds": [0, 1, 2]}
            ],
            "metadata": {
                "0": {},
                "1": {},
                "2": {},
                "3": {"color": "black"}
            }
        }"""
        val mgr = GeometricObjectManager()
        DiagramSerializer.deserialize(json, mgr)
        assertEquals("green", mgr.getMetadata(3)?.color)
    }

    @Test
    fun `unknown keys in metadata are ignored`() {
        val json = """{
            "appName": "RiemannSketchpad",
            "version": "1.0.0",
            "model": "riemann",
            "objects": [{"id": 0, "type": "FreePoint", "x": 1.0, "y": 0.0, "z": 0.0}],
            "metadata": {"0": {"visible": true, "futureField": "value"}}
        }"""
        val mgr = GeometricObjectManager()
        assertDoesNotThrow {
            DiagramSerializer.deserialize(json, mgr)
        }
    }
}
