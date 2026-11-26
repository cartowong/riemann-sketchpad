package riemannsketchpad.export

import riemannsketchpad.geometry.*
import riemannsketchpad.model.GeometricObjectManager
import riemannsketchpad.model.ObjectMetadata
import kotlin.math.sqrt

data class ExportCase(
    val name: String,
    val manager: GeometricObjectManager,
    val viewMatrix: DoubleArray,
    val width: Int,
    val height: Int,
    val transparent: Boolean,
    val includeGrid: Boolean,
    val includeBackFace: Boolean
) {
    val displayScale: Double get() = minOf(width, height) / 2.0 * 0.9
    val centerX: Double get() = width / 2.0
    val centerY: Double get() = height / 2.0
}

object ExportTestDiagrams {

    private fun point(id: Int, x: Double, y: Double, z: Double): FreePoint {
        val len = sqrt(x * x + y * y + z * z)
        return FreePoint(id, x / len, y / len, z / len)
    }

    /** Lines split by the silhouette, a dashed segment, a circle, an angle, a region, labels, a back-face point. */
    fun basic(): GeometricObjectManager {
        val m = GeometricObjectManager()
        m.addObject(point(0, 0.3, 0.2, 0.9), ObjectMetadata(label = "A", labelVisible = true))
        m.addObject(point(1, -0.5, 0.4, 0.7), ObjectMetadata(label = "B", labelVisible = true, color = "green", size = "large"))
        m.addObject(point(2, 0.1, -0.6, 0.75), ObjectMetadata(label = "C&<>", labelVisible = true))
        m.addObject(point(3, 0.2, 0.3, -0.9), ObjectMetadata(label = "D", labelVisible = true, color = "purple"))
        m.addObject(Line(4, 0, 1), ObjectMetadata(label = "l", labelVisible = true))
        m.addObject(Segment(5, 1, 2), ObjectMetadata(color = "red", strokeStyle = "dashed", strokeThickness = "thick"))
        m.addObject(Circle(6, 0, 2), ObjectMetadata(color = "brown", label = "c", labelVisible = true))
        m.addObject(Angle(7, 1, 0, 2), ObjectMetadata(color = "purple"))
        m.addObject(Region(8, listOf(0, 1, 2)), ObjectMetadata(color = "yellow"))
        m.addObject(Segment(9, 0, 3), ObjectMetadata(strokeThickness = "thin"))
        m.addObject(Circle(10, 3, 0), ObjectMetadata(strokeStyle = "dashed"))
        m.addObject(Angle(11, 0, 3, 1), ObjectMetadata(size = "large"))
        m.addObject(Region(12, listOf(3, 0, 1)), ObjectMetadata(color = "green"))
        m.recomputeAll()
        return m
    }

    /** Face-on and edge-on great circles, an edge-on segment, a back-only circle, a hidden object. */
    fun special(): GeometricObjectManager {
        val m = GeometricObjectManager()
        m.addObject(point(0, 1.0, 0.0, 0.0))
        m.addObject(point(1, 0.0, 1.0, 0.0))
        m.addObject(point(2, 0.0, 0.0, 1.0))
        m.addObject(point(3, 0.0, 0.6, 0.8))
        m.addObject(point(4, 0.0, -0.6, 0.8))
        m.addObject(point(5, 0.1, 0.1, -1.0))
        m.addObject(point(6, 0.3, 0.1, -1.0))
        m.addObject(Line(7, 0, 1), ObjectMetadata(color = "red"))
        m.addObject(Line(8, 1, 2), ObjectMetadata(color = "green", strokeStyle = "dashed"))
        m.addObject(Segment(9, 3, 4), ObjectMetadata(color = "purple"))
        m.addObject(Circle(10, 5, 6), ObjectMetadata(color = "brown"))
        m.addObject(Circle(11, 2, 3))
        m.addObject(Line(12, 0, 2), ObjectMetadata(visible = false))
        m.recomputeAll()
        return m
    }

    fun cases(): List<ExportCase> = listOf(
        ExportCase("basic_front_grid_back", basic(), SphericalUtils.viewMatrix(0.0, 0.0), 600, 600, false, true, true),
        ExportCase("basic_front_noback", basic(), SphericalUtils.viewMatrix(0.0, 0.0), 600, 600, false, false, false),
        ExportCase("basic_rotated_transparent_noback", basic(), SphericalUtils.viewMatrix(35.0, 20.0), 600, 600, true, false, false),
        ExportCase("basic_rotated_wide_grid_back", basic(), SphericalUtils.viewMatrix(-120.0, 50.0), 800, 500, false, true, true),
        ExportCase("special_front_back", special(), SphericalUtils.viewMatrix(0.0, 0.0), 500, 500, false, false, true),
        ExportCase("special_front_transparent_noback", special(), SphericalUtils.viewMatrix(0.0, 0.0), 500, 500, true, true, false),
    )
}
