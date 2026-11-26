package riemannsketchpad.export

internal class SvgExportCanvas(
    width: Int,
    height: Int,
    diskCenterX: Double,
    diskCenterY: Double,
    diskRadius: Double,
    transparent: Boolean
) : ExportCanvas {

    private val sb = StringBuilder()

    init {
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0 0 $width $height">""")
        sb.appendLine("""<defs><clipPath id="sphere-clip"><circle cx="${f(diskCenterX)}" cy="${f(diskCenterY)}" r="${f(diskRadius)}"/></clipPath></defs>""")
        if (!transparent) {
            sb.appendLine("""<rect width="$width" height="$height" fill="$BACKGROUND_GRAY"/>""")
            sb.appendLine("""<circle cx="${f(diskCenterX)}" cy="${f(diskCenterY)}" r="${f(diskRadius)}" fill="white"/>""")
        }
        sb.appendLine("""<g clip-path="url(#sphere-clip)">""")
    }

    fun finish(): String {
        sb.appendLine("</g>")
        sb.appendLine("</svg>")
        return sb.toString()
    }

    override fun strokeLine(x1: Double, y1: Double, x2: Double, y2: Double, stroke: Stroke) {
        sb.appendLine("""<line x1="${f(x1)}" y1="${f(y1)}" x2="${f(x2)}" y2="${f(y2)}"${strokeAttrs(stroke)}/>""")
    }

    override fun strokeEllipse(cx: Double, cy: Double, rx: Double, ry: Double, rotationDeg: Double, stroke: Stroke, opacity: Double?) {
        sb.appendLine("""<ellipse cx="${f(cx)}" cy="${f(cy)}" rx="${f(rx)}" ry="${f(ry)}" fill="none"${strokeAttrs(stroke)}${opacityAttr(opacity)} transform="rotate(${f(rotationDeg)},${f(cx)},${f(cy)})"/>""")
    }

    override fun strokePath(path: List<PathSeg>, stroke: Stroke, opacity: Double?) {
        sb.appendLine("""<path d="${pathData(path)}" fill="none"${strokeAttrs(stroke)}${opacityAttr(opacity)}/>""")
    }

    override fun fillPath(path: List<PathSeg>, fill: Rgba, outline: Rgba?, opacity: Double?) {
        val strokeAttr = if (outline != null) """stroke="${rgba(outline)}" stroke-width="1"""" else """stroke="none""""
        sb.appendLine("""<path d="${pathData(path)}" fill="${rgba(fill)}" $strokeAttr${opacityAttr(opacity)}/>""")
    }

    override fun fillCircle(cx: Double, cy: Double, r: Double, color: String, opacity: Double) {
        sb.appendLine("""<circle cx="${f(cx)}" cy="${f(cy)}" r="${f(r)}" fill="$color" opacity="$opacity"/>""")
    }

    override fun text(x: Double, y: Double, s: String) {
        sb.appendLine("""<text x="${f(x)}" y="${f(y)}" font-size="${LABEL_FONT_SIZE.toInt()}" fill="black">${escapeXml(s)}</text>""")
    }

    private fun strokeAttrs(stroke: Stroke): String {
        val dash = if (stroke.dashed) """ stroke-dasharray="${DASH_ON.toInt()},${DASH_OFF.toInt()}"""" else ""
        return """ stroke="${stroke.color}" stroke-width="${width(stroke.width)}"$dash"""
    }

    private fun opacityAttr(opacity: Double?) = if (opacity != null) """ opacity="$opacity"""" else ""

    private fun pathData(path: List<PathSeg>): String = path.joinToString(" ") { seg ->
        when (seg) {
            is PathSeg.MoveTo -> "M ${f(seg.x)},${f(seg.y)}"
            is PathSeg.LineTo -> "L ${f(seg.x)},${f(seg.y)}"
            is PathSeg.ArcTo -> "A ${f(seg.rx)},${f(seg.ry)} ${f(seg.rotationDeg)} ${flag(seg.largeArc)} ${flag(seg.sweep)} ${f(seg.x)},${f(seg.y)}"
            is PathSeg.CircularArcTo -> "A ${f(seg.r)},${f(seg.r)} 0 ${flag(seg.largeArc)} ${flag(seg.sweep)} ${f(seg.x)},${f(seg.y)}"
            PathSeg.Close -> "Z"
        }
    }

    private fun flag(b: Boolean) = if (b) 1 else 0

    private fun rgba(c: Rgba) = "rgba(${c.r},${c.g},${c.b},${c.a})"

    // Whole-number widths use 2 decimals ("2.00"); fractional ones (the 0.5 grid) are written as-is.
    private fun width(w: Double) = if (w % 1.0 == 0.0) f(w) else w.toString()

    private fun f(v: Double) = "%.2f".format(v)

    private fun escapeXml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
