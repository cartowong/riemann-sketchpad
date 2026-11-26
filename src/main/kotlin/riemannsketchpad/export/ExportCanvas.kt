package riemannsketchpad.export

/** Drawing primitives used by the shared SVG/PDF export walker. Coordinates are screen pixels, Y down. */
internal interface ExportCanvas {
    fun strokeLine(x1: Double, y1: Double, x2: Double, y2: Double, stroke: Stroke)
    fun strokeEllipse(cx: Double, cy: Double, rx: Double, ry: Double, rotationDeg: Double, stroke: Stroke, opacity: Double?)
    fun strokePath(path: List<PathSeg>, stroke: Stroke, opacity: Double?)
    /** Fills [path]; [outline], if given, is stroked 1px wide. */
    fun fillPath(path: List<PathSeg>, fill: Rgba, outline: Rgba?, opacity: Double?)
    fun fillCircle(cx: Double, cy: Double, r: Double, color: String, opacity: Double)
    /** Draws black 12px text with its baseline starting at (x, y). */
    fun text(x: Double, y: Double, s: String)
}

/** [color] is `#RRGGBB`. */
internal data class Stroke(val color: String, val width: Double, val dashed: Boolean = false)

internal data class Rgba(val r: Int, val g: Int, val b: Int, val a: Double)

internal sealed interface PathSeg {
    data class MoveTo(val x: Double, val y: Double) : PathSeg
    data class LineTo(val x: Double, val y: Double) : PathSeg
    /** SVG-style endpoint-parameterized elliptical arc. */
    data class ArcTo(
        val rx: Double, val ry: Double, val rotationDeg: Double,
        val largeArc: Boolean, val sweep: Boolean, val x: Double, val y: Double
    ) : PathSeg
    /** Circular arc (an [ArcTo] with rx == ry and no rotation). */
    data class CircularArcTo(val r: Double, val largeArc: Boolean, val sweep: Boolean, val x: Double, val y: Double) : PathSeg
    data object Close : PathSeg
}

internal const val DASH_ON = 6.0
internal const val DASH_OFF = 4.0
internal const val LABEL_FONT_SIZE = 12.0
internal const val BACKGROUND_GRAY = "#E8E8E8"
