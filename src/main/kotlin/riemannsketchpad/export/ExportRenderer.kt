package riemannsketchpad.export

import javafx.embed.swing.SwingFXUtils
import javafx.scene.Group
import javafx.scene.SnapshotParameters
import javafx.scene.paint.Color
import javafx.scene.shape.*
import javafx.scene.text.Font
import javafx.scene.text.Text
import javafx.scene.transform.Transform
import riemannsketchpad.geometry.*
import riemannsketchpad.model.GeometricObjectManager
import riemannsketchpad.model.ObjectMetadata
import riemannsketchpad.model.StyleUtils
import riemannsketchpad.ui.GeometryRenderer
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.*

object ExportRenderer {

    fun exportPng(
        file: File,
        manager: GeometricObjectManager,
        viewMatrix: DoubleArray,
        displayScale: Double,
        diskCenterX: Double,
        diskCenterY: Double,
        width: Int,
        height: Int,
        transparent: Boolean,
        includeGrid: Boolean,
        includeBackFace: Boolean = true
    ) {
        val group = Group()

        // Background
        if (!transparent) {
            // Gray exterior
            val bg = Rectangle(0.0, 0.0, width.toDouble(), height.toDouble())
            bg.fill = Color.web("#E8E8E8")
            group.children.add(bg)

            // White sphere disk
            val disk = javafx.scene.shape.Circle(diskCenterX, diskCenterY, displayScale)
            disk.fill = Color.WHITE
            group.children.add(disk)
        }

        // Render objects
        val renderer = GeometryRenderer()
        val objectGroup = Group()
        renderer.render(objectGroup, manager, viewMatrix, displayScale, diskCenterX, diskCenterY, emptySet(), null, includeGrid, includeBackFace)

        // Clip to sphere disk
        val clip = javafx.scene.shape.Circle(diskCenterX, diskCenterY, displayScale)
        objectGroup.clip = clip
        group.children.add(objectGroup)

        // Snapshot
        val params = SnapshotParameters()
        params.fill = if (transparent) Color.TRANSPARENT else Color.web("#E8E8E8")
        params.transform = Transform.scale(1.0, 1.0)

        val image = group.snapshot(params, javafx.scene.image.WritableImage(width, height))
        val buffered = SwingFXUtils.fromFXImage(image, null)
        ImageIO.write(buffered, "png", file)
    }

    fun exportSvg(
        file: File,
        manager: GeometricObjectManager,
        viewMatrix: DoubleArray,
        displayScale: Double,
        diskCenterX: Double,
        diskCenterY: Double,
        width: Int,
        height: Int,
        transparent: Boolean,
        includeGrid: Boolean,
        includeBackFace: Boolean = true
    ) {
        val canvas = SvgExportCanvas(width, height, diskCenterX, diskCenterY, displayScale, transparent)
        SceneWalker(canvas, viewMatrix, displayScale, diskCenterX, diskCenterY, includeBackFace).draw(manager, includeGrid)
        file.writeText(canvas.finish())
    }

    /**
     * Writes a one-page PDF of exactly [width]×[height] points.
     * Returns the label characters the embedded font cannot render (drawn as '?'); empty if none.
     */
    fun exportPdf(
        file: File,
        manager: GeometricObjectManager,
        viewMatrix: DoubleArray,
        displayScale: Double,
        diskCenterX: Double,
        diskCenterY: Double,
        width: Int,
        height: Int,
        transparent: Boolean,
        includeGrid: Boolean,
        includeBackFace: Boolean = true
    ): Set<String> {
        return PdfExportCanvas.write(file, width, height, diskCenterX, diskCenterY, displayScale, transparent) { canvas ->
            SceneWalker(canvas, viewMatrix, displayScale, diskCenterX, diskCenterY, includeBackFace).draw(manager, includeGrid)
        }
    }
}

/** Grid, 5-pass object rendering, silhouette splitting and back-face opacity shared by SVG and PDF export. */
private class SceneWalker(
    private val canvas: ExportCanvas,
    private val viewMatrix: DoubleArray,
    private val displayScale: Double,
    private val diskCenterX: Double,
    private val diskCenterY: Double,
    private val includeBackFace: Boolean
) {
    companion object {
        private const val BACK_FACE_OPACITY = 0.3
        private const val GRID_BACK_OPACITY = 0.15
        private val GRID_STROKE = Stroke("#D9D9D9", 0.5)
    }

    fun draw(manager: GeometricObjectManager, includeGrid: Boolean) {
        val objects = manager.getObjects()
        val metadata = manager.getMetadata()

        if (includeGrid) drawGrid()

        // Pass 1: Regions
        for ((id, obj) in objects) {
            if (obj !is Region) continue
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked || !obj.isDefined) continue
            drawRegion(obj, meta)
        }

        // Pass 2: Angles
        for ((id, obj) in objects) {
            if (obj !is Angle) continue
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked || !obj.isDefined) continue
            drawAngle(obj, meta)
        }

        // Pass 3: Geodesics and Circles
        for ((id, obj) in objects) {
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked || !obj.isDefined) continue
            when {
                obj is GeodesicLike && obj is GeometricObject -> drawGeodesic(obj, meta)
                obj is riemannsketchpad.geometry.Circle -> drawCircle(obj, meta)
            }
        }

        // Pass 4: Points
        for ((id, obj) in objects) {
            if (obj !is PointLike) continue
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked || !(obj as GeometricObject).isDefined) continue
            val (px, py, pz) = SphericalUtils.projectToScreen(obj.x, obj.y, obj.z, viewMatrix)
            if (pz <= 0 && !includeBackFace) continue
            val opacity = if (pz <= 0) BACK_FACE_OPACITY else 1.0
            canvas.fillCircle(sx(px), sy(py), StyleUtils.pointRadius(meta.size), StyleUtils.colorHex(meta.color), opacity)
        }

        // Pass 5: Labels
        val renderer = GeometryRenderer()
        for ((id, obj) in objects) {
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked || !obj.isDefined || !meta.labelVisible) continue
            val label = meta.label ?: continue
            val anchor = renderer.computeLabelAnchor(obj, meta, viewMatrix, displayScale, diskCenterX, diskCenterY)
                ?: continue
            if (!anchor.third) continue
            canvas.text(anchor.first + meta.labelOffsetX, anchor.second + meta.labelOffsetY, label)
        }
    }

    private fun sx(px: Double) = diskCenterX + displayScale * px
    private fun sy(py: Double) = diskCenterY - displayScale * py

    private fun objectStroke(meta: ObjectMetadata) =
        Stroke(StyleUtils.colorHex(meta.color), StyleUtils.strokeWidth(meta.strokeThickness), meta.strokeStyle == "dashed")

    private class ProjectedEllipse(val cx: Double, val cy: Double, val major: Double, val minor: Double, val rotationAngle: Double) {
        val rotDeg: Double get() = -Math.toDegrees(rotationAngle)
    }

    private fun project(params: EllipseParams) = ProjectedEllipse(
        diskCenterX + params.centerX * displayScale,
        diskCenterY - params.centerY * displayScale,
        params.semiMajor * displayScale,
        params.semiMinor * displayScale,
        params.rotationAngle
    )

    /** Edge-on great circle drawn as its projected major axis. */
    private fun drawMajorAxis(e: ProjectedEllipse, stroke: Stroke) {
        val dx = e.major * cos(e.rotationAngle)
        val dy = -e.major * sin(e.rotationAngle)
        canvas.strokeLine(e.cx - dx, e.cy - dy, e.cx + dx, e.cy + dy, stroke)
    }

    private fun drawGeodesic(geo: GeodesicLike, meta: ObjectMetadata) {
        val e = project(SphericalUtils.ellipseProjectionParams(geo.normalX, geo.normalY, geo.normalZ, viewMatrix))
        val stroke = objectStroke(meta)

        if (e.minor < 0.5) {
            if (geo.geodesicType == GeodesicType.LINE) {
                drawMajorAxis(e, stroke)
            } else {
                val (p1x, p1y, _) = SphericalUtils.projectToScreen(geo.x1, geo.y1, geo.z1, viewMatrix)
                val (p2x, p2y, _) = SphericalUtils.projectToScreen(geo.x2, geo.y2, geo.z2, viewMatrix)
                canvas.strokeLine(sx(p1x), sy(p1y), sx(p2x), sy(p2y), stroke)
            }
            return
        }

        if (geo.geodesicType == GeodesicType.LINE) {
            val (npx, npy, npz) = SphericalUtils.matVec(viewMatrix, geo.normalX, geo.normalY, geo.normalZ)
            val split = computeSilhouetteCrossings(npx, npy, npz, 0.0, 1.0)
            if (split != null) {
                drawSplitArcs(split, e, stroke, BACK_FACE_OPACITY, includeBackFace)
            } else {
                // Face-on: entire great circle on the silhouette
                canvas.strokeEllipse(e.cx, e.cy, e.major, e.minor, e.rotDeg, stroke, null)
            }
        } else {
            val (p1x, p1y, p1z) = SphericalUtils.projectToScreen(geo.x1, geo.y1, geo.z1, viewMatrix)
            val (p2x, p2y, p2z) = SphericalUtils.projectToScreen(geo.x2, geo.y2, geo.z2, viewMatrix)
            val avgDepth = (p1z + p2z) / 2.0
            if (avgDepth <= 0 && !includeBackFace) return
            val s1x = sx(p1x); val s1y = sy(p1y)
            val s2x = sx(p2x); val s2y = sy(p2y)

            val mid = SphericalUtils.slerp(geo.x1, geo.y1, geo.z1, geo.x2, geo.y2, geo.z2, 0.5)
            val (pmx, pmy, _) = SphericalUtils.projectToScreen(mid.first, mid.second, mid.third, viewMatrix)
            val arc = ellipseArc(s1x, s1y, s2x, s2y, sx(pmx), sy(pmy), e)
            canvas.strokePath(arc, stroke, if (avgDepth <= 0) BACK_FACE_OPACITY else null)
        }
    }

    private fun drawCircle(circle: riemannsketchpad.geometry.Circle, meta: ObjectMetadata) {
        val e = project(SphericalUtils.smallCircleEllipseParams(
            circle.centerX, circle.centerY, circle.centerZ, circle.cosRadius, viewMatrix
        ))
        if (e.major < 0.5) return
        val stroke = objectStroke(meta)

        val (npx, npy, npz) = SphericalUtils.matVec(viewMatrix, circle.centerX, circle.centerY, circle.centerZ)
        val sinR = sqrt((1.0 - circle.cosRadius * circle.cosRadius).coerceAtLeast(0.0))
        val lenXY = sqrt(npx * npx + npy * npy)
        val zMax = circle.cosRadius * npz + sinR * lenXY

        val split = computeSilhouetteCrossings(npx, npy, npz, circle.cosRadius, sinR)
        if (split != null) {
            drawSplitArcs(split, e, stroke, BACK_FACE_OPACITY, includeBackFace)
        } else {
            if (zMax <= 1e-10 && !includeBackFace) return
            canvas.strokeEllipse(e.cx, e.cy, e.major, e.minor, e.rotDeg, stroke, if (zMax <= 1e-10) BACK_FACE_OPACITY else null)
        }
    }

    private fun drawAngle(angle: Angle, meta: ObjectMetadata) {
        val (vx, vy, vz) = SphericalUtils.projectToScreen(angle.vertexX, angle.vertexY, angle.vertexZ, viewMatrix)
        if (vz <= 0 && !includeBackFace) return
        val svx = sx(vx)
        val svy = sy(vy)

        val arcRadius = StyleUtils.angleArcRadius(meta.size)
        val c = Color.web(StyleUtils.colorHex(meta.color))
        val r = (c.red * 255).toInt(); val g = (c.green * 255).toInt(); val b = (c.blue * 255).toInt()

        // Project tangent vectors for start/end angles
        val eps = 0.001
        val (spx, spy, _) = SphericalUtils.matVec(viewMatrix, angle.vertexX, angle.vertexY, angle.vertexZ)
        val (sax, say, _) = SphericalUtils.matVec(viewMatrix, angle.vertexX + eps * angle.tangentAX, angle.vertexY + eps * angle.tangentAY, angle.vertexZ + eps * angle.tangentAZ)
        val (scx, scy, _) = SphericalUtils.matVec(viewMatrix, angle.vertexX + eps * angle.tangentCX, angle.vertexY + eps * angle.tangentCY, angle.vertexZ + eps * angle.tangentCZ)

        val startAngle = atan2(-(say - spy), sax - spx)
        val endAngle = atan2(-(scy - spy), scx - spx)

        var sweep = endAngle - startAngle
        val cross2D = (sax - spx) * (-(scy - spy)) - (-(say - spy)) * (scx - spx)
        if (cross2D > 0 && sweep < 0) sweep += 2 * PI
        if (cross2D < 0 && sweep > 0) sweep -= 2 * PI
        // Match sweep magnitude to the 3D orientation-dependent angle
        if ((angle.measureRadians > PI) != (abs(sweep) > PI)) {
            sweep = if (sweep > 0) sweep - 2 * PI else sweep + 2 * PI
        }

        val path = listOf(
            PathSeg.MoveTo(svx, svy),
            PathSeg.LineTo(svx + arcRadius * cos(startAngle), svy + arcRadius * sin(startAngle)),
            PathSeg.CircularArcTo(arcRadius, abs(sweep) > PI, sweep > 0, svx + arcRadius * cos(endAngle), svy + arcRadius * sin(endAngle)),
            PathSeg.Close
        )
        canvas.fillPath(path, Rgba(r, g, b, 0.3), Rgba(r, g, b, 0.7), if (vz <= 0) BACK_FACE_OPACITY else null)
    }

    private fun drawRegion(region: Region, meta: ObjectMetadata) {
        if (region.vertices.size < 3) return
        val c = Color.web(StyleUtils.colorHex(meta.color))
        val fill = Rgba((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), 0.3)

        val projected = region.vertices.map { (vx, vy, vz) ->
            val (px, py, pz) = SphericalUtils.projectToScreen(vx, vy, vz, viewMatrix)
            Triple(sx(px), sy(py), pz)
        }
        val anyFront = projected.any { it.third > 0 }
        if (!anyFront && !includeBackFace) return

        val path = mutableListOf<PathSeg>(PathSeg.MoveTo(projected[0].first, projected[0].second))
        for (i in 1 until projected.size) path.add(PathSeg.LineTo(projected[i].first, projected[i].second))
        path.add(PathSeg.Close)
        canvas.fillPath(path, fill, null, if (!anyFront) BACK_FACE_OPACITY else null)
    }

    private fun drawGrid() {
        // Meridians
        for (i in 0 until 12) {
            val azRad = Math.toRadians(i * 30.0)
            val nx = cos(azRad); val nz = sin(azRad)
            val e = project(SphericalUtils.ellipseProjectionParams(nx, 0.0, nz, viewMatrix))
            if (e.minor < 0.3) {
                drawMajorAxis(e, GRID_STROKE)
            } else {
                val (gnpx, gnpy, gnpz) = SphericalUtils.matVec(viewMatrix, nx, 0.0, nz)
                val split = computeSilhouetteCrossings(gnpx, gnpy, gnpz, 0.0, 1.0)
                if (split != null) {
                    drawSplitArcs(split, e, GRID_STROKE, GRID_BACK_OPACITY)
                } else {
                    canvas.strokeEllipse(e.cx, e.cy, e.major, e.minor, e.rotDeg, GRID_STROKE, null)
                }
            }
        }
        // Parallels
        for (latDeg in doubleArrayOf(60.0, 30.0, 0.0, -30.0, -60.0)) {
            if (abs(latDeg) < 0.01) {
                // Equator: great circle with normal = (0,1,0)
                val e = project(SphericalUtils.ellipseProjectionParams(0.0, 1.0, 0.0, viewMatrix))
                val (eqnpx, eqnpy, eqnpz) = SphericalUtils.matVec(viewMatrix, 0.0, 1.0, 0.0)
                val eqSplit = computeSilhouetteCrossings(eqnpx, eqnpy, eqnpz, 0.0, 1.0)
                if (eqSplit != null) {
                    drawSplitArcs(eqSplit, e, GRID_STROKE, GRID_BACK_OPACITY)
                } else {
                    canvas.strokeEllipse(e.cx, e.cy, e.major, e.minor, e.rotDeg, GRID_STROKE, null)
                }
            } else {
                val cosR = sin(Math.toRadians(latDeg))
                val e = project(SphericalUtils.smallCircleEllipseParams(0.0, 1.0, 0.0, cosR, viewMatrix))
                if (e.major < 0.3) continue

                val (pnpx, pnpy, pnpz) = SphericalUtils.matVec(viewMatrix, 0.0, 1.0, 0.0)
                val pSinR = sqrt((1.0 - cosR * cosR).coerceAtLeast(0.0))
                val pLenXY = sqrt(pnpx * pnpx + pnpy * pnpy)
                val pZMax = cosR * pnpz + pSinR * pLenXY

                val pSplit = computeSilhouetteCrossings(pnpx, pnpy, pnpz, cosR, pSinR)
                if (pSplit != null) {
                    drawSplitArcs(pSplit, e, GRID_STROKE, GRID_BACK_OPACITY)
                } else {
                    canvas.strokeEllipse(e.cx, e.cy, e.major, e.minor, e.rotDeg, GRID_STROKE, if (pZMax <= 1e-10) GRID_BACK_OPACITY else null)
                }
            }
        }
    }

    // --- Silhouette Split Helpers ---

    private data class SilhouetteSplit(
        val c1sx: Double, val c1sy: Double,
        val c2sx: Double, val c2sy: Double,
        val frontMidSx: Double, val frontMidSy: Double,
        val backMidSx: Double, val backMidSy: Double
    )

    private fun computeSilhouetteCrossings(
        npx: Double, npy: Double, npz: Double,
        cosR: Double, sinR: Double
    ): SilhouetteSplit? {
        if (sinR < 1e-10) return null
        val lenXY = sqrt(npx * npx + npy * npy)
        if (lenXY < 1e-8) return null

        val sinTCross = cosR * npz / (sinR * lenXY)
        if (abs(sinTCross) > 1.0 - 1e-10) return null

        val cosTCross = sqrt((1.0 - sinTCross * sinTCross).coerceAtLeast(0.0))

        val e1x = npy / lenXY; val e1y = -npx / lenXY
        val e2x = npx * npz / lenXY; val e2y = npy * npz / lenXY

        val p1vx = cosR * npx + sinR * (cosTCross * e1x + sinTCross * e2x)
        val p1vy = cosR * npy + sinR * (cosTCross * e1y + sinTCross * e2y)
        val p2vx = cosR * npx + sinR * (-cosTCross * e1x + sinTCross * e2x)
        val p2vy = cosR * npy + sinR * (-cosTCross * e1y + sinTCross * e2y)

        val fvx = cosR * npx - sinR * e2x
        val fvy = cosR * npy - sinR * e2y
        val bvx = cosR * npx + sinR * e2x
        val bvy = cosR * npy + sinR * e2y

        return SilhouetteSplit(
            c1sx = sx(p1vx), c1sy = sy(p1vy),
            c2sx = sx(p2vx), c2sy = sy(p2vy),
            frontMidSx = sx(fvx), frontMidSy = sy(fvy),
            backMidSx = sx(bvx), backMidSy = sy(bvy)
        )
    }

    private fun drawSplitArcs(
        split: SilhouetteSplit,
        e: ProjectedEllipse,
        stroke: Stroke,
        backOpacity: Double,
        showBackFace: Boolean = true
    ) {
        if (showBackFace) {
            val back = ellipseArc(split.c1sx, split.c1sy, split.c2sx, split.c2sy, split.backMidSx, split.backMidSy, e)
            canvas.strokePath(back, stroke, backOpacity)
        }
        val front = ellipseArc(split.c1sx, split.c1sy, split.c2sx, split.c2sy, split.frontMidSx, split.frontMidSy, e)
        canvas.strokePath(front, stroke, null)
    }

    /** Arc of [e] from (s1x, s1y) to (s2x, s2y) passing through (smx, smy). */
    private fun ellipseArc(
        s1x: Double, s1y: Double, s2x: Double, s2y: Double, smx: Double, smy: Double, e: ProjectedEllipse
    ): List<PathSeg> {
        val rotRad = Math.toRadians(e.rotDeg)
        val a1 = eAngle(s1x - e.cx, s1y - e.cy, rotRad, e.major, e.minor)
        val a2 = eAngle(s2x - e.cx, s2y - e.cy, rotRad, e.major, e.minor)
        val am = eAngle(smx - e.cx, smy - e.cy, rotRad, e.major, e.minor)

        val shortArc = normDiff(a2 - a1)
        val midInShort = normDiff(am - a1)
        val inShort = if (shortArc > 0) midInShort in 0.0..shortArc else midInShort in shortArc..0.0
        val sweep = normAngle(am - a1) <= normAngle(a2 - a1)

        return listOf(
            PathSeg.MoveTo(s1x, s1y),
            PathSeg.ArcTo(e.major, e.minor, e.rotDeg, !inShort, sweep, s2x, s2y)
        )
    }

    private fun eAngle(dx: Double, dy: Double, rotRad: Double, a: Double, b: Double): Double {
        val c = cos(rotRad); val s = sin(rotRad)
        val lx = c * dx + s * dy
        val ly = -s * dx + c * dy
        return atan2(ly / b.coerceAtLeast(0.01), lx / a.coerceAtLeast(0.01))
    }

    private fun normDiff(a: Double): Double {
        var r = a; while (r > PI) r -= 2 * PI; while (r < -PI) r += 2 * PI; return r
    }

    private fun normAngle(a: Double): Double {
        var r = a; while (r < 0) r += 2 * PI; while (r >= 2 * PI) r -= 2 * PI; return r
    }
}
