package riemannsketchpad.ui

import javafx.scene.Group
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import javafx.scene.shape.ArcTo
import javafx.scene.shape.Ellipse
import javafx.scene.shape.MoveTo
import javafx.scene.shape.Path
import riemannsketchpad.geometry.*
import riemannsketchpad.model.GeometricObjectManager
import riemannsketchpad.model.ManagerState
import riemannsketchpad.model.ObjectMetadata
import riemannsketchpad.model.StyleSettings
import riemannsketchpad.model.UndoManager

enum class GeodesicSubMode { LINE, SEGMENT }

class GeodesicTool(
    private val canvas: SphereCanvasPane,
    private val manager: GeometricObjectManager,
    private val undoManager: UndoManager,
    private val styleSettings: StyleSettings,
    val subMode: GeodesicSubMode
) : Tool {

    private var firstPointId: Int? = null
    private var preConstructionSnapshot: ManagerState? = null

    override fun onMousePressed(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {
        val hit = canvas.hitTester.hitTest(event.x, event.y)

        if (firstPointId == null) {
            // First click
            preConstructionSnapshot = manager.snapshot()
            firstPointId = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, worldZ, manager, canvas.hitTester, styleSettings)
            canvas.hitTester.rebuildIntersectionCache()
            canvas.render()
        } else {
            // Second click
            val secondId = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, worldZ, manager, canvas.hitTester, styleSettings)

            // Same point check
            if (secondId == firstPointId) return

            // Antipodal check
            val p1 = manager.getObject(firstPointId!!) as? PointLike ?: return
            val p2 = manager.getObject(secondId) as? PointLike ?: return
            if (SphericalUtils.isAntipodal(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z)) return

            // Create the geodesic
            val nextId = manager.peekNextId()
            val obj: GeometricObject = when (subMode) {
                GeodesicSubMode.LINE -> Line(nextId, firstPointId!!, secondId)
                GeodesicSubMode.SEGMENT -> Segment(nextId, firstPointId!!, secondId)
            }
            val meta = ObjectMetadata()
            styleSettings.applyDefaults(meta, obj)
            manager.addObject(obj, meta)
            obj.recompute(manager.getObjects())

            undoManager.checkpoint()
            firstPointId = null
            preConstructionSnapshot = null
            canvas.previewGroup.children.clear()
            canvas.hitTester.rebuildIntersectionCache()
            canvas.render()
        }
    }

    override fun onMouseMoved(event: MouseEvent, worldX: Double, worldY: Double, worldZ: Double) {
        canvas.previewGroup.children.clear()

        val hit = canvas.hitTester.hitTest(event.x, event.y)

        // Intersection preview ghost dot
        if (hit is HitResult.IntersectionPreview) {
            val ghost = javafx.scene.shape.Circle(hit.candidate.screenX, hit.candidate.screenY, 4.0)
            ghost.fill = Color.BLACK
            ghost.opacity = 0.4
            ghost.isMouseTransparent = true
            canvas.previewGroup.children.add(ghost)
        }

        if (firstPointId == null) return

        val p1 = manager.getObject(firstPointId!!) as? PointLike ?: return

        // Determine target coordinates (snap to intersection or constructed point)
        val targetX: Double
        val targetY: Double
        val targetZ: Double
        when (hit) {
            is HitResult.ConstructedPoint -> {
                val pt = manager.getObject(hit.id) as? PointLike
                if (pt != null) { targetX = pt.x; targetY = pt.y; targetZ = pt.z }
                else { targetX = worldX; targetY = worldY; targetZ = worldZ }
            }
            is HitResult.IntersectionPreview -> {
                val coords = canvas.screenToModel(hit.candidate.screenX, hit.candidate.screenY)
                if (coords != null) { targetX = coords.first; targetY = coords.second; targetZ = coords.third }
                else { targetX = worldX; targetY = worldY; targetZ = worldZ }
            }
            else -> { targetX = worldX; targetY = worldY; targetZ = worldZ }
        }

        // Preview: ghost geodesic arc from first point to target
        val normal = SphericalUtils.greatCircleNormal(p1.x, p1.y, p1.z, targetX, targetY, targetZ) ?: return

        val vm = canvas.getViewMatrix()
        val params = SphericalUtils.ellipseProjectionParams(normal.first, normal.second, normal.third, vm)
        val scaledMajor = params.semiMajor * canvas.displayScale
        val scaledMinor = params.semiMinor * canvas.displayScale
        val ecx = canvas.diskCenterXValue + params.centerX * canvas.displayScale
        val ecy = canvas.diskCenterYValue - params.centerY * canvas.displayScale
        val rotDeg = -Math.toDegrees(params.rotationAngle)

        if (subMode == GeodesicSubMode.LINE) {
            // Split great circle preview into front/back arcs at silhouette
            val (npx, npy, npz) = SphericalUtils.matVec(vm, normal.first, normal.second, normal.third)
            val lenXY = kotlin.math.sqrt(npx * npx + npy * npy)

            if (lenXY < 1e-8 || scaledMinor < 0.5) {
                // Face-on or edge-on: single shape
                if (scaledMinor < 0.5) {
                    val cosR = kotlin.math.cos(params.rotationAngle)
                    val sinR = kotlin.math.sin(params.rotationAngle)
                    val dx = scaledMajor * cosR
                    val dy = -scaledMajor * sinR
                    val line = javafx.scene.shape.Line(ecx - dx, ecy - dy, ecx + dx, ecy + dy)
                    line.stroke = Color.BLACK
                    line.strokeWidth = 2.0
                    line.opacity = 0.4
                    line.isMouseTransparent = true
                    canvas.previewGroup.children.add(line)
                } else {
                    val ellipse = Ellipse(ecx, ecy, scaledMajor, scaledMinor)
                    ellipse.fill = Color.TRANSPARENT
                    ellipse.stroke = Color.BLACK
                    ellipse.strokeWidth = 2.0
                    ellipse.opacity = 0.4
                    ellipse.rotate = rotDeg
                    ellipse.isMouseTransparent = true
                    canvas.previewGroup.children.add(ellipse)
                }
            } else {
                // Crossing points (major axis endpoints for great circles)
                val c1sx = canvas.diskCenterXValue + canvas.displayScale * npy / lenXY
                val c1sy = canvas.diskCenterYValue + canvas.displayScale * npx / lenXY
                val c2sx = canvas.diskCenterXValue - canvas.displayScale * npy / lenXY
                val c2sy = canvas.diskCenterYValue - canvas.displayScale * npx / lenXY
                // Front midpoint (-e2 direction)
                val fsx = canvas.diskCenterXValue - canvas.displayScale * npx * npz / lenXY
                val fsy = canvas.diskCenterYValue + canvas.displayScale * npy * npz / lenXY
                // Back midpoint (+e2 direction)
                val bsx = canvas.diskCenterXValue + canvas.displayScale * npx * npz / lenXY
                val bsy = canvas.diskCenterYValue - canvas.displayScale * npy * npz / lenXY

                // Back arc
                if (canvas.showBackFace) {
                    val backPath = Path()
                    backPath.elements.add(MoveTo(c1sx, c1sy))
                    backPath.elements.add(ArcTo(scaledMajor, scaledMinor, rotDeg, c2sx, c2sy,
                        isLargeArc(c1sx, c1sy, c2sx, c2sy, bsx, bsy, ecx, ecy, scaledMajor, scaledMinor, rotDeg),
                        isSweep(c1sx, c1sy, c2sx, c2sy, bsx, bsy, ecx, ecy, scaledMajor, scaledMinor, rotDeg)))
                    backPath.fill = Color.TRANSPARENT
                    backPath.stroke = Color.BLACK
                    backPath.strokeWidth = 2.0
                    backPath.opacity = 0.12
                    backPath.isMouseTransparent = true
                    canvas.previewGroup.children.add(backPath)
                }

                // Front arc
                val frontPath = Path()
                frontPath.elements.add(MoveTo(c1sx, c1sy))
                frontPath.elements.add(ArcTo(scaledMajor, scaledMinor, rotDeg, c2sx, c2sy,
                    isLargeArc(c1sx, c1sy, c2sx, c2sy, fsx, fsy, ecx, ecy, scaledMajor, scaledMinor, rotDeg),
                    isSweep(c1sx, c1sy, c2sx, c2sy, fsx, fsy, ecx, ecy, scaledMajor, scaledMinor, rotDeg)))
                frontPath.fill = Color.TRANSPARENT
                frontPath.stroke = Color.BLACK
                frontPath.strokeWidth = 2.0
                frontPath.opacity = 0.4
                frontPath.isMouseTransparent = true
                canvas.previewGroup.children.add(frontPath)
            }
        } else {
            // Segment arc preview
            val (p1x, p1y, _) = SphericalUtils.projectToScreen(p1.x, p1.y, p1.z, vm)
            val (p2x, p2y, _) = SphericalUtils.projectToScreen(targetX, targetY, targetZ, vm)
            val s1x = canvas.diskCenterXValue + canvas.displayScale * p1x
            val s1y = canvas.diskCenterYValue - canvas.displayScale * p1y
            val s2x = canvas.diskCenterXValue + canvas.displayScale * p2x
            val s2y = canvas.diskCenterYValue - canvas.displayScale * p2y

            if (scaledMinor < 0.5) {
                // Edge-on: straight line
                val line = javafx.scene.shape.Line(s1x, s1y, s2x, s2y)
                line.stroke = Color.BLACK
                line.strokeWidth = 2.0
                line.opacity = 0.4
                line.isMouseTransparent = true
                canvas.previewGroup.children.add(line)
            } else {
                val (mx, my, _) = SphericalUtils.slerp(p1.x, p1.y, p1.z, targetX, targetY, targetZ, 0.5)
                val (pmx, pmy, _) = SphericalUtils.projectToScreen(mx, my, 0.0, vm)
                // Re-project midpoint properly
                val mid3d = SphericalUtils.slerp(p1.x, p1.y, p1.z, targetX, targetY, targetZ, 0.5)
                val (pmx2, pmy2, _) = SphericalUtils.projectToScreen(mid3d.first, mid3d.second, mid3d.third, vm)
                val smx = canvas.diskCenterXValue + canvas.displayScale * pmx2
                val smy = canvas.diskCenterYValue - canvas.displayScale * pmy2

                val largeArc = isLargeArc(s1x, s1y, s2x, s2y, smx, smy, ecx, ecy, scaledMajor, scaledMinor, rotDeg)
                val sweep = isSweep(s1x, s1y, s2x, s2y, smx, smy, ecx, ecy, scaledMajor, scaledMinor, rotDeg)

                val path = Path()
                path.elements.add(MoveTo(s1x, s1y))
                path.elements.add(ArcTo(scaledMajor, scaledMinor, rotDeg, s2x, s2y, largeArc, sweep))
                path.fill = Color.TRANSPARENT
                path.stroke = Color.BLACK
                path.strokeWidth = 2.0
                path.opacity = 0.4
                path.isMouseTransparent = true
                canvas.previewGroup.children.add(path)
            }
        }

        // Ghost endpoints
        val (gp1x, gp1y, _) = SphericalUtils.projectToScreen(p1.x, p1.y, p1.z, vm)
        val gs1x = canvas.diskCenterXValue + canvas.displayScale * gp1x
        val gs1y = canvas.diskCenterYValue - canvas.displayScale * gp1y
        val ghost1 = javafx.scene.shape.Circle(gs1x, gs1y, 4.0)
        ghost1.fill = Color.BLACK
        ghost1.opacity = 0.4
        ghost1.isMouseTransparent = true
        canvas.previewGroup.children.add(ghost1)

        val (gp2x, gp2y, _) = SphericalUtils.projectToScreen(targetX, targetY, targetZ, vm)
        val gs2x = canvas.diskCenterXValue + canvas.displayScale * gp2x
        val gs2y = canvas.diskCenterYValue - canvas.displayScale * gp2y
        val ghost2 = javafx.scene.shape.Circle(gs2x, gs2y, 4.0)
        ghost2.fill = Color.BLACK
        ghost2.opacity = 0.4
        ghost2.isMouseTransparent = true
        canvas.previewGroup.children.add(ghost2)
    }

    override fun onEscapePressed(previewGroup: Group): Boolean {
        if (firstPointId != null) {
            preConstructionSnapshot?.let { manager.restore(it) }
            firstPointId = null
            preConstructionSnapshot = null
            previewGroup.children.clear()
            return true
        }
        return false
    }

    override fun deactivate(previewGroup: Group) {
        if (firstPointId != null) {
            preConstructionSnapshot?.let { manager.restore(it) }
        }
        firstPointId = null
        preConstructionSnapshot = null
        previewGroup.children.clear()
    }

    // Arc flag heuristics (same as renderer)
    private fun isLargeArc(s1x: Double, s1y: Double, s2x: Double, s2y: Double, smx: Double, smy: Double, ecx: Double, ecy: Double, semiMajor: Double, semiMinor: Double, rotDeg: Double): Boolean {
        val rotRad = Math.toRadians(rotDeg)
        val a1 = eAngle(s1x - ecx, s1y - ecy, rotRad, semiMajor, semiMinor)
        val a2 = eAngle(s2x - ecx, s2y - ecy, rotRad, semiMajor, semiMinor)
        val am = eAngle(smx - ecx, smy - ecy, rotRad, semiMajor, semiMinor)
        val shortArc = normDiff(a2 - a1)
        val midInShort = normDiff(am - a1)
        val inShort = if (shortArc > 0) midInShort in 0.0..shortArc else midInShort in shortArc..0.0
        return !inShort
    }

    private fun isSweep(s1x: Double, s1y: Double, s2x: Double, s2y: Double, smx: Double, smy: Double, ecx: Double, ecy: Double, semiMajor: Double, semiMinor: Double, rotDeg: Double): Boolean {
        val rotRad = Math.toRadians(rotDeg)
        val a1 = eAngle(s1x - ecx, s1y - ecy, rotRad, semiMajor, semiMinor)
        val a2 = eAngle(s2x - ecx, s2y - ecy, rotRad, semiMajor, semiMinor)
        val am = eAngle(smx - ecx, smy - ecy, rotRad, semiMajor, semiMinor)
        val diffForward = normAngle(a2 - a1)
        val diffMidForward = normAngle(am - a1)
        return diffMidForward <= diffForward
    }

    private fun eAngle(dx: Double, dy: Double, rotRad: Double, a: Double, b: Double): Double {
        val c = kotlin.math.cos(rotRad); val s = kotlin.math.sin(rotRad)
        val lx = c * dx + s * dy
        val ly = -s * dx + c * dy
        return kotlin.math.atan2(ly / b.coerceAtLeast(0.01), lx / a.coerceAtLeast(0.01))
    }

    private fun normDiff(a: Double): Double {
        var r = a; while (r > Math.PI) r -= 2 * Math.PI; while (r < -Math.PI) r += 2 * Math.PI; return r
    }

    private fun normAngle(a: Double): Double {
        var r = a; while (r < 0) r += 2 * Math.PI; while (r >= 2 * Math.PI) r -= 2 * Math.PI; return r
    }
}
