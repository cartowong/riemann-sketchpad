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

    private const val BACK_FACE_OPACITY = 0.3
    private const val GRID_BACK_OPACITY = 0.15

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
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0 0 $width $height">""")

        // Clip definition
        sb.appendLine("""<defs><clipPath id="sphere-clip"><circle cx="${f(diskCenterX)}" cy="${f(diskCenterY)}" r="${f(displayScale)}"/></clipPath></defs>""")

        // Background
        if (!transparent) {
            sb.appendLine("""<rect width="$width" height="$height" fill="#E8E8E8"/>""")
            sb.appendLine("""<circle cx="${f(diskCenterX)}" cy="${f(diskCenterY)}" r="${f(displayScale)}" fill="white"/>""")
        }

        sb.appendLine("""<g clip-path="url(#sphere-clip)">""")

        val objects = manager.getObjects()
        val metadata = manager.getMetadata()

        // Grid
        if (includeGrid) {
            renderSvgGrid(sb, viewMatrix, displayScale, diskCenterX, diskCenterY)
        }

        // Pass 1: Regions
        for ((id, obj) in objects) {
            if (obj !is Region) continue
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked || !obj.isDefined) continue
            renderSvgRegion(sb, obj, meta, viewMatrix, displayScale, diskCenterX, diskCenterY, includeBackFace)
        }

        // Pass 2: Angles
        for ((id, obj) in objects) {
            if (obj !is Angle) continue
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked || !obj.isDefined) continue
            renderSvgAngle(sb, obj, meta, viewMatrix, displayScale, diskCenterX, diskCenterY, includeBackFace)
        }

        // Pass 3: Geodesics and Circles
        for ((id, obj) in objects) {
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked || !obj.isDefined) continue
            when {
                obj is GeodesicLike && obj is GeometricObject -> renderSvgGeodesic(sb, obj, obj, meta, viewMatrix, displayScale, diskCenterX, diskCenterY, includeBackFace)
                obj is riemannsketchpad.geometry.Circle -> renderSvgCircle(sb, obj, meta, viewMatrix, displayScale, diskCenterX, diskCenterY, includeBackFace)
            }
        }

        // Pass 4: Points
        for ((id, obj) in objects) {
            if (obj !is PointLike) continue
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked || !(obj as GeometricObject).isDefined) continue
            val (px, py, pz) = SphericalUtils.projectToScreen(obj.x, obj.y, obj.z, viewMatrix)
            val sx = diskCenterX + displayScale * px
            val sy = diskCenterY - displayScale * py
            val radius = StyleUtils.pointRadius(meta.size)
            val color = StyleUtils.colorHex(meta.color)
            if (pz <= 0 && !includeBackFace) continue
            val opacity = if (pz <= 0) 0.3 else 1.0
            sb.appendLine("""<circle cx="${f(sx)}" cy="${f(sy)}" r="${f(radius)}" fill="$color" opacity="$opacity"/>""")
        }

        // Pass 5: Labels
        for ((id, obj) in objects) {
            val meta = metadata[id] ?: continue
            if (!meta.visible || meta.isLocked || !obj.isDefined || !meta.labelVisible) continue
            val label = meta.label ?: continue
            val renderer = GeometryRenderer()
            val anchor = renderer.computeLabelAnchor(obj, meta, viewMatrix, displayScale, diskCenterX, diskCenterY)
                ?: continue
            if (!anchor.third) continue
            val x = anchor.first + meta.labelOffsetX
            val y = anchor.second + meta.labelOffsetY
            sb.appendLine("""<text x="${f(x)}" y="${f(y)}" font-size="12" fill="black">${escapeXml(label)}</text>""")
        }

        sb.appendLine("</g>")
        sb.appendLine("</svg>")
        file.writeText(sb.toString())
    }

    // --- SVG rendering helpers ---

    private fun renderSvgGeodesic(
        sb: StringBuilder,
        geoObj: GeometricObject,
        geo: GeodesicLike,
        meta: ObjectMetadata,
        viewMatrix: DoubleArray,
        displayScale: Double,
        diskCenterX: Double,
        diskCenterY: Double,
        includeBackFace: Boolean
    ) {
        val params = SphericalUtils.ellipseProjectionParams(geo.normalX, geo.normalY, geo.normalZ, viewMatrix)
        val scaledMajor = params.semiMajor * displayScale
        val scaledMinor = params.semiMinor * displayScale
        val ecx = diskCenterX + params.centerX * displayScale
        val ecy = diskCenterY - params.centerY * displayScale
        val rotDeg = -Math.toDegrees(params.rotationAngle)

        val color = StyleUtils.colorHex(meta.color)
        val strokeW = StyleUtils.strokeWidth(meta.strokeThickness)
        val dashAttr = if (meta.strokeStyle == "dashed") """ stroke-dasharray="6,4"""" else ""
        val strokeWStr = f(strokeW)

        // Edge-on check
        if (scaledMinor < 0.5) {
            if (geo.geodesicType == GeodesicType.LINE) {
                val cosR = cos(params.rotationAngle)
                val sinR = sin(params.rotationAngle)
                val dx = scaledMajor * cosR
                val dy = -scaledMajor * sinR
                sb.appendLine("""<line x1="${f(ecx - dx)}" y1="${f(ecy - dy)}" x2="${f(ecx + dx)}" y2="${f(ecy + dy)}" stroke="$color" stroke-width="$strokeWStr"$dashAttr/>""")
            } else {
                val (p1x, p1y, _) = SphericalUtils.projectToScreen(geo.x1, geo.y1, geo.z1, viewMatrix)
                val (p2x, p2y, _) = SphericalUtils.projectToScreen(geo.x2, geo.y2, geo.z2, viewMatrix)
                val s1x = diskCenterX + displayScale * p1x
                val s1y = diskCenterY - displayScale * p1y
                val s2x = diskCenterX + displayScale * p2x
                val s2y = diskCenterY - displayScale * p2y
                sb.appendLine("""<line x1="${f(s1x)}" y1="${f(s1y)}" x2="${f(s2x)}" y2="${f(s2y)}" stroke="$color" stroke-width="$strokeWStr"$dashAttr/>""")
            }
            return
        }

        if (geo.geodesicType == GeodesicType.LINE) {
            val (npx, npy, npz) = SphericalUtils.matVec(viewMatrix, geo.normalX, geo.normalY, geo.normalZ)
            val split = computeSilhouetteCrossings(npx, npy, npz, 0.0, 1.0, displayScale, diskCenterX, diskCenterY)
            if (split != null) {
                buildSvgSplitArcs(sb, split, ecx, ecy, scaledMajor, scaledMinor, rotDeg, color, strokeWStr, dashAttr, BACK_FACE_OPACITY, includeBackFace)
            } else {
                // Face-on: entire great circle on the silhouette
                sb.appendLine("""<ellipse cx="${f(ecx)}" cy="${f(ecy)}" rx="${f(scaledMajor)}" ry="${f(scaledMinor)}" fill="none" stroke="$color" stroke-width="$strokeWStr"$dashAttr transform="rotate(${f(rotDeg)},${f(ecx)},${f(ecy)})"/>""")
            }
        } else {
            // Segment arc
            val (p1x, p1y, p1z) = SphericalUtils.projectToScreen(geo.x1, geo.y1, geo.z1, viewMatrix)
            val (p2x, p2y, p2z) = SphericalUtils.projectToScreen(geo.x2, geo.y2, geo.z2, viewMatrix)
            val avgDepth = (p1z + p2z) / 2.0
            if (avgDepth <= 0 && !includeBackFace) return
            val s1x = diskCenterX + displayScale * p1x
            val s1y = diskCenterY - displayScale * p1y
            val s2x = diskCenterX + displayScale * p2x
            val s2y = diskCenterY - displayScale * p2y

            val mid = SphericalUtils.slerp(geo.x1, geo.y1, geo.z1, geo.x2, geo.y2, geo.z2, 0.5)
            val (pmx, pmy, _) = SphericalUtils.projectToScreen(mid.first, mid.second, mid.third, viewMatrix)
            val smx = diskCenterX + displayScale * pmx
            val smy = diskCenterY - displayScale * pmy

            val largeArc = if (determineLargeArc(s1x, s1y, s2x, s2y, smx, smy, ecx, ecy, scaledMajor, scaledMinor, rotDeg)) 1 else 0
            val sweep = if (determineSweep(s1x, s1y, s2x, s2y, smx, smy, ecx, ecy, scaledMajor, scaledMinor, rotDeg)) 1 else 0

            val segOpacity = if (avgDepth <= 0) """ opacity="0.3"""" else ""
            sb.appendLine("""<path d="M ${f(s1x)},${f(s1y)} A ${f(scaledMajor)},${f(scaledMinor)} ${f(rotDeg)} $largeArc $sweep ${f(s2x)},${f(s2y)}" fill="none" stroke="$color" stroke-width="${f(strokeW)}"$dashAttr$segOpacity/>""")
        }
    }

    private fun renderSvgCircle(
        sb: StringBuilder,
        circle: riemannsketchpad.geometry.Circle,
        meta: ObjectMetadata,
        viewMatrix: DoubleArray,
        displayScale: Double,
        diskCenterX: Double,
        diskCenterY: Double,
        includeBackFace: Boolean
    ) {
        val params = SphericalUtils.smallCircleEllipseParams(
            circle.centerX, circle.centerY, circle.centerZ, circle.cosRadius, viewMatrix
        )
        val scaledMajor = params.semiMajor * displayScale
        val scaledMinor = params.semiMinor * displayScale
        val ecx = diskCenterX + params.centerX * displayScale
        val ecy = diskCenterY - params.centerY * displayScale
        val rotDeg = -Math.toDegrees(params.rotationAngle)

        val color = StyleUtils.colorHex(meta.color)
        val strokeW = StyleUtils.strokeWidth(meta.strokeThickness)
        val dashAttr = if (meta.strokeStyle == "dashed") """ stroke-dasharray="6,4"""" else ""
        val strokeWStr = f(strokeW)

        if (scaledMajor < 0.5) return

        // Compute back-face status
        val (npx, npy, npz) = SphericalUtils.matVec(viewMatrix, circle.centerX, circle.centerY, circle.centerZ)
        val sinR = kotlin.math.sqrt((1.0 - circle.cosRadius * circle.cosRadius).coerceAtLeast(0.0))
        val lenXY = kotlin.math.sqrt(npx * npx + npy * npy)
        val zMax = circle.cosRadius * npz + sinR * lenXY

        val split = computeSilhouetteCrossings(npx, npy, npz, circle.cosRadius, sinR, displayScale, diskCenterX, diskCenterY)
        if (split != null) {
            buildSvgSplitArcs(sb, split, ecx, ecy, scaledMajor, scaledMinor, rotDeg, color, strokeWStr, dashAttr, BACK_FACE_OPACITY, includeBackFace)
        } else {
            if (zMax <= 1e-10 && !includeBackFace) return
            val circleOpacity = if (zMax <= 1e-10) """ opacity="$BACK_FACE_OPACITY"""" else ""
            sb.appendLine("""<ellipse cx="${f(ecx)}" cy="${f(ecy)}" rx="${f(scaledMajor)}" ry="${f(scaledMinor)}" fill="none" stroke="$color" stroke-width="$strokeWStr"$dashAttr$circleOpacity transform="rotate(${f(rotDeg)},${f(ecx)},${f(ecy)})"/>""")
        }
    }

    private fun renderSvgAngle(
        sb: StringBuilder,
        angle: Angle,
        meta: ObjectMetadata,
        viewMatrix: DoubleArray,
        displayScale: Double,
        diskCenterX: Double,
        diskCenterY: Double,
        includeBackFace: Boolean
    ) {
        val (vx, vy, vz) = SphericalUtils.projectToScreen(angle.vertexX, angle.vertexY, angle.vertexZ, viewMatrix)
        if (vz <= 0 && !includeBackFace) return
        val svx = diskCenterX + displayScale * vx
        val svy = diskCenterY - displayScale * vy

        val color = StyleUtils.colorHex(meta.color)
        val arcRadius = StyleUtils.angleArcRadius(meta.size)

        // Simplified angle rendering for SVG
        val c = Color.web(color)
        val fillColor = "rgba(${(c.red * 255).toInt()},${(c.green * 255).toInt()},${(c.blue * 255).toInt()},0.3)"
        val strokeColor = "rgba(${(c.red * 255).toInt()},${(c.green * 255).toInt()},${(c.blue * 255).toInt()},0.7)"

        // Project tangent vectors for start/end angles (simplified)
        val eps = 0.001
        val (spx, spy, _) = SphericalUtils.matVec(viewMatrix, angle.vertexX, angle.vertexY, angle.vertexZ)
        val (sax, say, _) = SphericalUtils.matVec(viewMatrix, angle.vertexX + eps * angle.tangentAX, angle.vertexY + eps * angle.tangentAY, angle.vertexZ + eps * angle.tangentAZ)
        val (scx, scy, _) = SphericalUtils.matVec(viewMatrix, angle.vertexX + eps * angle.tangentCX, angle.vertexY + eps * angle.tangentCY, angle.vertexZ + eps * angle.tangentCZ)

        val startAngle = atan2(-(say - spy), sax - spx)
        val endAngle = atan2(-(scy - spy), scx - spx)

        val startX = svx + arcRadius * cos(startAngle)
        val startY = svy + arcRadius * sin(startAngle)
        val endX = svx + arcRadius * cos(endAngle)
        val endY = svy + arcRadius * sin(endAngle)

        var sweep = endAngle - startAngle
        val cross2D = (sax - spx) * (-(scy - spy)) - (-(say - spy)) * (scx - spx)
        if (cross2D > 0 && sweep < 0) sweep += 2 * PI
        if (cross2D < 0 && sweep > 0) sweep -= 2 * PI
        // Match sweep magnitude to the 3D orientation-dependent angle
        if ((angle.measureRadians > PI) != (abs(sweep) > PI)) {
            sweep = if (sweep > 0) sweep - 2 * PI else sweep + 2 * PI
        }

        val largeArc = if (abs(sweep) > PI) 1 else 0
        val sweepFlag = if (sweep > 0) 1 else 0

        val angleOpacity = if (vz <= 0) """ opacity="0.3"""" else ""
        sb.appendLine("""<path d="M ${f(svx)},${f(svy)} L ${f(startX)},${f(startY)} A ${f(arcRadius)},${f(arcRadius)} 0 $largeArc $sweepFlag ${f(endX)},${f(endY)} Z" fill="$fillColor" stroke="$strokeColor" stroke-width="1"$angleOpacity/>""")
    }

    private fun renderSvgRegion(
        sb: StringBuilder,
        region: Region,
        meta: ObjectMetadata,
        viewMatrix: DoubleArray,
        displayScale: Double,
        diskCenterX: Double,
        diskCenterY: Double,
        includeBackFace: Boolean
    ) {
        if (region.vertices.size < 3) return
        val color = StyleUtils.colorHex(meta.color)
        val c = Color.web(color)
        val fillColor = "rgba(${(c.red * 255).toInt()},${(c.green * 255).toInt()},${(c.blue * 255).toInt()},0.3)"

        val projected = region.vertices.map { (vx, vy, vz) ->
            val (px, py, pz) = SphericalUtils.projectToScreen(vx, vy, vz, viewMatrix)
            Triple(diskCenterX + displayScale * px, diskCenterY - displayScale * py, pz)
        }
        val anyFront = projected.any { it.third > 0 }
        if (!anyFront && !includeBackFace) return

        val d = StringBuilder()
        d.append("M ${f(projected[0].first)},${f(projected[0].second)}")
        for (i in 1 until projected.size) {
            d.append(" L ${f(projected[i].first)},${f(projected[i].second)}")
        }
        d.append(" Z")
        val regionOpacity = if (!anyFront) """ opacity="0.3"""" else ""
        sb.appendLine("""<path d="$d" fill="$fillColor" stroke="none"$regionOpacity/>""")
    }

    private fun renderSvgGrid(
        sb: StringBuilder,
        viewMatrix: DoubleArray,
        displayScale: Double,
        diskCenterX: Double,
        diskCenterY: Double
    ) {
        val gridColor = "#D9D9D9"
        val gridStrokeW = "0.5"
        // Meridians
        for (i in 0 until 12) {
            val azRad = Math.toRadians(i * 30.0)
            val nx = cos(azRad); val nz = sin(azRad)
            val params = SphericalUtils.ellipseProjectionParams(nx, 0.0, nz, viewMatrix)
            val scaledMajor = params.semiMajor * displayScale
            val scaledMinor = params.semiMinor * displayScale
            val ecx = diskCenterX + params.centerX * displayScale
            val ecy = diskCenterY - params.centerY * displayScale
            val rotDeg = -Math.toDegrees(params.rotationAngle)

            if (scaledMinor < 0.3) {
                // Edge-on: render as a straight line
                val cosR = cos(params.rotationAngle)
                val sinR = sin(params.rotationAngle)
                val dx = scaledMajor * cosR
                val dy = -scaledMajor * sinR
                sb.appendLine("""<line x1="${f(ecx - dx)}" y1="${f(ecy - dy)}" x2="${f(ecx + dx)}" y2="${f(ecy + dy)}" stroke="$gridColor" stroke-width="$gridStrokeW"/>""")
            } else {
                val (gnpx, gnpy, gnpz) = SphericalUtils.matVec(viewMatrix, nx, 0.0, nz)
                val split = computeSilhouetteCrossings(gnpx, gnpy, gnpz, 0.0, 1.0, displayScale, diskCenterX, diskCenterY)
                if (split != null) {
                    buildSvgSplitArcs(sb, split, ecx, ecy, scaledMajor, scaledMinor, rotDeg, gridColor, gridStrokeW, "", GRID_BACK_OPACITY)
                } else {
                    sb.appendLine("""<ellipse cx="${f(ecx)}" cy="${f(ecy)}" rx="${f(scaledMajor)}" ry="${f(scaledMinor)}" fill="none" stroke="$gridColor" stroke-width="$gridStrokeW" transform="rotate(${f(rotDeg)},${f(ecx)},${f(ecy)})"/>""")
                }
            }
        }
        // Parallels
        for (latDeg in doubleArrayOf(60.0, 30.0, 0.0, -30.0, -60.0)) {
            if (abs(latDeg) < 0.01) {
                // Equator: great circle with normal = (0,1,0)
                val gcParams = SphericalUtils.ellipseProjectionParams(0.0, 1.0, 0.0, viewMatrix)
                val scaledMajor = gcParams.semiMajor * displayScale
                val scaledMinor = gcParams.semiMinor * displayScale
                val ecx = diskCenterX + gcParams.centerX * displayScale
                val ecy = diskCenterY - gcParams.centerY * displayScale
                val rotDeg = -Math.toDegrees(gcParams.rotationAngle)

                val (eqnpx, eqnpy, eqnpz) = SphericalUtils.matVec(viewMatrix, 0.0, 1.0, 0.0)
                val eqSplit = computeSilhouetteCrossings(eqnpx, eqnpy, eqnpz, 0.0, 1.0, displayScale, diskCenterX, diskCenterY)
                if (eqSplit != null) {
                    buildSvgSplitArcs(sb, eqSplit, ecx, ecy, scaledMajor, scaledMinor, rotDeg, gridColor, gridStrokeW, "", GRID_BACK_OPACITY)
                } else {
                    sb.appendLine("""<ellipse cx="${f(ecx)}" cy="${f(ecy)}" rx="${f(scaledMajor)}" ry="${f(scaledMinor)}" fill="none" stroke="$gridColor" stroke-width="$gridStrokeW" transform="rotate(${f(rotDeg)},${f(ecx)},${f(ecy)})"/>""")
                }
            } else {
                val latRad = Math.toRadians(latDeg)
                val cosR = sin(latRad)
                val params = SphericalUtils.smallCircleEllipseParams(0.0, 1.0, 0.0, cosR, viewMatrix)
                val scaledMajor = params.semiMajor * displayScale
                val scaledMinor = params.semiMinor * displayScale
                val ecx = diskCenterX + params.centerX * displayScale
                val ecy = diskCenterY - params.centerY * displayScale
                val rotDeg = -Math.toDegrees(params.rotationAngle)

                if (scaledMajor < 0.3) continue

                val (pnpx, pnpy, pnpz) = SphericalUtils.matVec(viewMatrix, 0.0, 1.0, 0.0)
                val pSinR = sqrt((1.0 - cosR * cosR).coerceAtLeast(0.0))
                val pLenXY = sqrt(pnpx * pnpx + pnpy * pnpy)
                val pZMax = cosR * pnpz + pSinR * pLenXY

                val pSplit = computeSilhouetteCrossings(pnpx, pnpy, pnpz, cosR, pSinR, displayScale, diskCenterX, diskCenterY)
                if (pSplit != null) {
                    buildSvgSplitArcs(sb, pSplit, ecx, ecy, scaledMajor, scaledMinor, rotDeg, gridColor, gridStrokeW, "", GRID_BACK_OPACITY)
                } else {
                    val pOpacity = if (pZMax <= 1e-10) """ opacity="$GRID_BACK_OPACITY"""" else ""
                    sb.appendLine("""<ellipse cx="${f(ecx)}" cy="${f(ecy)}" rx="${f(scaledMajor)}" ry="${f(scaledMinor)}" fill="none" stroke="$gridColor" stroke-width="$gridStrokeW"$pOpacity transform="rotate(${f(rotDeg)},${f(ecx)},${f(ecy)})"/>""")
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
        cosR: Double, sinR: Double,
        displayScale: Double, diskCenterX: Double, diskCenterY: Double
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
            c1sx = diskCenterX + displayScale * p1vx,
            c1sy = diskCenterY - displayScale * p1vy,
            c2sx = diskCenterX + displayScale * p2vx,
            c2sy = diskCenterY - displayScale * p2vy,
            frontMidSx = diskCenterX + displayScale * fvx,
            frontMidSy = diskCenterY - displayScale * fvy,
            backMidSx = diskCenterX + displayScale * bvx,
            backMidSy = diskCenterY - displayScale * bvy
        )
    }

    private fun buildSvgSplitArcs(
        sb: StringBuilder,
        split: SilhouetteSplit,
        ecx: Double, ecy: Double,
        scaledMajor: Double, scaledMinor: Double, rotDeg: Double,
        color: String, strokeW: String, dashAttr: String,
        backOpacity: Double,
        showBackFace: Boolean = true
    ) {
        // Back arc
        if (showBackFace) {
            val bla = if (determineLargeArc(split.c1sx, split.c1sy, split.c2sx, split.c2sy,
                    split.backMidSx, split.backMidSy, ecx, ecy, scaledMajor, scaledMinor, rotDeg)) 1 else 0
            val bsw = if (determineSweep(split.c1sx, split.c1sy, split.c2sx, split.c2sy,
                    split.backMidSx, split.backMidSy, ecx, ecy, scaledMajor, scaledMinor, rotDeg)) 1 else 0
            sb.appendLine("""<path d="M ${f(split.c1sx)},${f(split.c1sy)} A ${f(scaledMajor)},${f(scaledMinor)} ${f(rotDeg)} $bla $bsw ${f(split.c2sx)},${f(split.c2sy)}" fill="none" stroke="$color" stroke-width="$strokeW"$dashAttr opacity="$backOpacity"/>""")
        }

        // Front arc
        val fla = if (determineLargeArc(split.c1sx, split.c1sy, split.c2sx, split.c2sy,
                split.frontMidSx, split.frontMidSy, ecx, ecy, scaledMajor, scaledMinor, rotDeg)) 1 else 0
        val fsw = if (determineSweep(split.c1sx, split.c1sy, split.c2sx, split.c2sy,
                split.frontMidSx, split.frontMidSy, ecx, ecy, scaledMajor, scaledMinor, rotDeg)) 1 else 0
        sb.appendLine("""<path d="M ${f(split.c1sx)},${f(split.c1sy)} A ${f(scaledMajor)},${f(scaledMinor)} ${f(rotDeg)} $fla $fsw ${f(split.c2sx)},${f(split.c2sy)}" fill="none" stroke="$color" stroke-width="$strokeW"$dashAttr/>""")
    }

    // --- Arc flag helpers ---

    private fun determineLargeArc(s1x: Double, s1y: Double, s2x: Double, s2y: Double, smx: Double, smy: Double, ecx: Double, ecy: Double, semiMajor: Double, semiMinor: Double, rotDeg: Double): Boolean {
        val rotRad = Math.toRadians(rotDeg)
        val a1 = eAngle(s1x - ecx, s1y - ecy, rotRad, semiMajor, semiMinor)
        val a2 = eAngle(s2x - ecx, s2y - ecy, rotRad, semiMajor, semiMinor)
        val am = eAngle(smx - ecx, smy - ecy, rotRad, semiMajor, semiMinor)
        val shortArc = normDiff(a2 - a1)
        val midInShort = normDiff(am - a1)
        val inShort = if (shortArc > 0) midInShort in 0.0..shortArc else midInShort in shortArc..0.0
        return !inShort
    }

    private fun determineSweep(s1x: Double, s1y: Double, s2x: Double, s2y: Double, smx: Double, smy: Double, ecx: Double, ecy: Double, semiMajor: Double, semiMinor: Double, rotDeg: Double): Boolean {
        val rotRad = Math.toRadians(rotDeg)
        val a1 = eAngle(s1x - ecx, s1y - ecy, rotRad, semiMajor, semiMinor)
        val a2 = eAngle(s2x - ecx, s2y - ecy, rotRad, semiMajor, semiMinor)
        val am = eAngle(smx - ecx, smy - ecy, rotRad, semiMajor, semiMinor)
        val diffForward = normAngle(a2 - a1)
        val diffMidForward = normAngle(am - a1)
        return diffMidForward <= diffForward
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

    private fun f(v: Double) = "%.2f".format(v)

    private fun escapeXml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
