package riemannsketchpad.export

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.util.Matrix
import riemannsketchpad.APP_VERSION
import java.io.File

/**
 * PDF backend. The content stream's CTM is flipped to Y-down so the walker's screen
 * coordinates are used unchanged; text re-flips its own matrix to stay upright.
 */
internal class PdfExportCanvas private constructor(
    private val cs: PDPageContentStream,
    private val font: PDType0Font
) : ExportCanvas {

    /** Label characters the font has no glyph for; they are drawn as '?'. */
    val unsupportedChars = linkedSetOf<String>()

    private val alphaStates = mutableMapOf<Pair<Double, Double>, PDExtendedGraphicsState>()

    companion object {
        private const val CREATOR_NAME = "Riemann Sketchpad"

        fun write(
            file: File,
            width: Int,
            height: Int,
            diskCenterX: Double,
            diskCenterY: Double,
            diskRadius: Double,
            transparent: Boolean,
            draw: (ExportCanvas) -> Unit
        ): Set<String> {
            PDDocument().use { doc ->
                doc.documentInformation.title = file.nameWithoutExtension
                doc.documentInformation.creator = "$CREATOR_NAME $APP_VERSION"
                val page = PDPage(PDRectangle(width.toFloat(), height.toFloat()))
                doc.addPage(page)
                val font = PdfExportCanvas::class.java.getResourceAsStream("/fonts/DejaVuSans.ttf")!!.use {
                    PDType0Font.load(doc, it)
                }
                val unsupported = PDPageContentStream(doc, page).use { cs ->
                    cs.transform(Matrix(1f, 0f, 0f, -1f, 0f, height.toFloat()))
                    val canvas = PdfExportCanvas(cs, font)
                    if (!transparent) {
                        cs.setNonStrokingColor(rgb(BACKGROUND_GRAY))
                        cs.addRect(0f, 0f, width.toFloat(), height.toFloat())
                        cs.fill()
                        cs.setNonStrokingColor(1f, 1f, 1f)
                        canvas.ellipsePath(diskCenterX, diskCenterY, diskRadius, diskRadius, 0.0)
                        cs.fill()
                    }
                    canvas.ellipsePath(diskCenterX, diskCenterY, diskRadius, diskRadius, 0.0)
                    cs.clip()
                    draw(canvas)
                    canvas.unsupportedChars
                }
                doc.save(file)
                return unsupported
            }
        }

        private fun rgb(hex: String): java.awt.Color = java.awt.Color.decode(hex)
    }

    override fun strokeLine(x1: Double, y1: Double, x2: Double, y2: Double, stroke: Stroke) {
        withStroke(stroke, null) {
            cs.moveTo(x1.toFloat(), y1.toFloat())
            cs.lineTo(x2.toFloat(), y2.toFloat())
        }
    }

    override fun strokeEllipse(cx: Double, cy: Double, rx: Double, ry: Double, rotationDeg: Double, stroke: Stroke, opacity: Double?) {
        withStroke(stroke, opacity) { ellipsePath(cx, cy, rx, ry, rotationDeg) }
    }

    override fun strokePath(path: List<PathSeg>, stroke: Stroke, opacity: Double?) {
        withStroke(stroke, opacity) { addPath(path) }
    }

    override fun fillPath(path: List<PathSeg>, fill: Rgba, outline: Rgba?, opacity: Double?) {
        val op = opacity ?: 1.0
        cs.saveGraphicsState()
        setAlpha(outline?.let { it.a * op } ?: 1.0, fill.a * op)
        cs.setNonStrokingColor(fill.r / 255f, fill.g / 255f, fill.b / 255f)
        addPath(path)
        if (outline != null) {
            cs.setStrokingColor(outline.r / 255f, outline.g / 255f, outline.b / 255f)
            cs.setLineWidth(1f)
            cs.fillAndStroke()
        } else {
            cs.fill()
        }
        cs.restoreGraphicsState()
    }

    override fun fillCircle(cx: Double, cy: Double, r: Double, color: String, opacity: Double) {
        cs.saveGraphicsState()
        setAlpha(1.0, opacity)
        cs.setNonStrokingColor(rgb(color))
        ellipsePath(cx, cy, r, r, 0.0)
        cs.fill()
        cs.restoreGraphicsState()
    }

    override fun text(x: Double, y: Double, s: String) {
        val safe = buildString {
            s.codePoints().forEach { cp ->
                val ch = String(Character.toChars(cp))
                val supported = try { font.encode(ch); true } catch (_: IllegalArgumentException) { false }
                if (supported) append(ch) else { append('?'); unsupportedChars.add(ch) }
            }
        }
        cs.beginText()
        cs.setFont(font, LABEL_FONT_SIZE.toFloat())
        cs.setNonStrokingColor(0f, 0f, 0f)
        cs.setTextMatrix(Matrix(1f, 0f, 0f, -1f, x.toFloat(), y.toFloat()))
        cs.showText(safe)
        cs.endText()
    }

    private inline fun withStroke(stroke: Stroke, opacity: Double?, buildPath: () -> Unit) {
        cs.saveGraphicsState()
        setAlpha(opacity ?: 1.0, 1.0)
        cs.setStrokingColor(rgb(stroke.color))
        cs.setLineWidth(stroke.width.toFloat())
        if (stroke.dashed) cs.setLineDashPattern(floatArrayOf(DASH_ON.toFloat(), DASH_OFF.toFloat()), 0f)
        buildPath()
        cs.stroke()
        cs.restoreGraphicsState()
    }

    private fun setAlpha(strokeAlpha: Double, fillAlpha: Double) {
        if (strokeAlpha >= 1.0 && fillAlpha >= 1.0) return
        val gs = alphaStates.getOrPut(strokeAlpha to fillAlpha) {
            PDExtendedGraphicsState().apply {
                strokingAlphaConstant = strokeAlpha.toFloat()
                nonStrokingAlphaConstant = fillAlpha.toFloat()
            }
        }
        cs.setGraphicsStateParameters(gs)
    }

    private fun ellipsePath(cx: Double, cy: Double, rx: Double, ry: Double, rotationDeg: Double) {
        val (start, cubics) = BezierArcs.ellipse(cx, cy, rx, ry, rotationDeg)
        cs.moveTo(start.first.toFloat(), start.second.toFloat())
        cubics.forEach(::curveTo)
        cs.closePath()
    }

    private fun addPath(path: List<PathSeg>) {
        var curX = 0.0; var curY = 0.0
        var startX = 0.0; var startY = 0.0
        for (seg in path) {
            when (seg) {
                is PathSeg.MoveTo -> {
                    cs.moveTo(seg.x.toFloat(), seg.y.toFloat())
                    curX = seg.x; curY = seg.y; startX = seg.x; startY = seg.y
                }
                is PathSeg.LineTo -> {
                    cs.lineTo(seg.x.toFloat(), seg.y.toFloat())
                    curX = seg.x; curY = seg.y
                }
                is PathSeg.ArcTo -> {
                    BezierArcs.arc(curX, curY, seg.rx, seg.ry, seg.rotationDeg, seg.largeArc, seg.sweep, seg.x, seg.y).forEach(::curveTo)
                    curX = seg.x; curY = seg.y
                }
                is PathSeg.CircularArcTo -> {
                    BezierArcs.arc(curX, curY, seg.r, seg.r, 0.0, seg.largeArc, seg.sweep, seg.x, seg.y).forEach(::curveTo)
                    curX = seg.x; curY = seg.y
                }
                PathSeg.Close -> {
                    cs.closePath()
                    curX = startX; curY = startY
                }
            }
        }
    }

    private fun curveTo(c: Cubic) {
        cs.curveTo(c.c1x.toFloat(), c.c1y.toFloat(), c.c2x.toFloat(), c.c2y.toFloat(), c.x.toFloat(), c.y.toFloat())
    }
}
