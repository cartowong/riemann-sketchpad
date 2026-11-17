package riemannsketchpad.ui

import javafx.beans.property.SimpleDoubleProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableSet
import javafx.geometry.Pos
import javafx.scene.Group
import javafx.scene.control.*
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.scene.shape.Rectangle
import riemannsketchpad.geometry.*
import riemannsketchpad.model.*
import riemannsketchpad.persistence.LoadedConstruction
import kotlin.math.min

class SphereCanvasPane(
    val manager: GeometricObjectManager,
    val undoManager: UndoManager,
    val styleSettings: StyleSettings,
    val recorder: ConstructionRecorder,
    val applicator: ConstructionApplicator
) : Pane() {

    companion object {
        private const val MARGIN = 20.0
    }

    val azimuthProperty = SimpleDoubleProperty(0.0)
    val elevationProperty = SimpleDoubleProperty(0.0)

    val selectedIds: ObservableSet<Int> = FXCollections.observableSet(mutableSetOf())

    var currentTool: Tool? = null
        set(value) {
            field?.deactivate(previewGroup)
            field = value
            selectedIds.clear()
            field?.activate()
            render()
        }

    var showGrid: Boolean = true
        set(value) {
            field = value
            render()
        }

    var showBackFace: Boolean = false
        set(value) {
            field = value
            render()
        }

    private val sphereDisk = Circle()
    private val objectGroup = Group()
    val previewGroup = Group()
    private val constructionPreviewGroup = Group()
    val exportOverlayGroup = Group()
    private val hintLabel = Label()
    private val recordingBanner = Label("Recording Construction")

    val renderer = GeometryRenderer()
    val hitTester = HitTester(manager)

    private var viewMatrix = SphericalUtils.viewMatrix(0.0, 0.0)
    var displayScale = 1.0
        private set
    var diskCenterXValue = 0.0
        private set
    var diskCenterYValue = 0.0
        private set

    private var hoverId: Int? = null

    // Space+Drag camera rotation
    private var spaceDragging = false
    private var spaceDragStartX = 0.0
    private var spaceDragStartY = 0.0
    private var spaceDragStartAz = 0.0
    private var spaceDragStartEl = 0.0
    private var spacePressed = false

    // Apply mode state
    var applyConstruction: LoadedConstruction? = null
        private set
    private var currentInputIndex = 0
    private var collectedInputs = mutableListOf<Int>()
    var onApplyComplete: (() -> Unit)? = null
    var onRender: (() -> Unit)? = null

    init {
        styleClass.add("canvas-pane")

        sphereDisk.fill = Color.WHITE

        // Clip object group to sphere disk
        val clip = Circle()
        clip.centerXProperty().bind(sphereDisk.centerXProperty())
        clip.centerYProperty().bind(sphereDisk.centerYProperty())
        clip.radiusProperty().bind(sphereDisk.radiusProperty())
        objectGroup.clip = clip
        objectGroup.isMouseTransparent = true
        previewGroup.isMouseTransparent = true
        constructionPreviewGroup.isMouseTransparent = true

        hintLabel.isVisible = false
        hintLabel.style = "-fx-background-color: rgba(0,0,0,0.7); -fx-text-fill: white; -fx-padding: 4 8 4 8; -fx-font-size: 12px;"

        recordingBanner.isVisible = false
        recordingBanner.style = "-fx-background-color: #FF5722; -fx-text-fill: white; -fx-padding: 4 12 4 12; -fx-font-weight: bold; -fx-font-size: 12px;"

        children.addAll(sphereDisk, objectGroup, previewGroup, constructionPreviewGroup, exportOverlayGroup, hintLabel, recordingBanner)

        // Resize listener
        widthProperty().addListener { _, _, _ -> onResize() }
        heightProperty().addListener { _, _, _ -> onResize() }

        // Camera change listeners
        azimuthProperty.addListener { _, _, _ -> onCameraChanged() }
        elevationProperty.addListener { _, _, _ -> onCameraChanged() }

        // Mouse events
        setOnMousePressed { e -> handleMousePressed(e) }
        setOnMouseMoved { e -> handleMouseMoved(e) }
        setOnMouseDragged { e -> handleMouseDragged(e) }
        setOnMouseReleased { e -> handleMouseReleased(e) }

        // Keyboard events
        isFocusTraversable = true
        setOnKeyPressed { e ->
            when (e.code) {
                javafx.scene.input.KeyCode.SPACE -> spacePressed = true
                javafx.scene.input.KeyCode.ESCAPE -> handleEscape()
                else -> {}
            }
        }
        setOnKeyReleased { e ->
            when (e.code) {
                javafx.scene.input.KeyCode.SPACE -> {
                    spacePressed = false
                    spaceDragging = false
                }
                else -> {}
            }
        }
    }

    private fun onResize() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        val radius = min(w, h) / 2.0 - MARGIN
        displayScale = radius
        diskCenterXValue = w / 2.0
        diskCenterYValue = h / 2.0

        sphereDisk.centerX = diskCenterXValue
        sphereDisk.centerY = diskCenterYValue
        sphereDisk.radius = radius

        updateHitTester()
        render()
    }

    private fun onCameraChanged() {
        viewMatrix = SphericalUtils.viewMatrix(azimuthProperty.get(), elevationProperty.get())
        updateHitTester()
        render()
    }

    private fun updateHitTester() {
        hitTester.viewMatrix = viewMatrix
        hitTester.displayScale = displayScale
        hitTester.diskCenterX = diskCenterXValue
        hitTester.diskCenterY = diskCenterYValue
        hitTester.rebuildIntersectionCache()
    }

    fun render() {
        renderer.render(
            objectGroup, manager, viewMatrix, displayScale,
            diskCenterXValue, diskCenterYValue, selectedIds, hoverId, showGrid, showBackFace
        )

        // Render intersection preview dots
        for (candidate in hitTester.getIntersectionCache()) {
            // Ghost dots are rendered in the preview group when near hover
        }

        // Update recording banner
        recordingBanner.isVisible = recorder.isRecording
        if (recorder.isRecording) {
            recordingBanner.layoutX = (width - recordingBanner.prefWidth(-1.0)) / 2.0
            recordingBanner.layoutY = 10.0
        }

        // Update hint label
        if (applyConstruction != null) {
            hintLabel.layoutX = (width - hintLabel.prefWidth(-1.0)) / 2.0
            hintLabel.layoutY = height - 40.0
        }

        onRender?.invoke()
    }

    fun refreshAll() {
        manager.recomputeAll()
        updateHitTester()
        render()
    }

    // --- Mouse Handling ---

    private fun handleMousePressed(e: MouseEvent) {
        requestFocus()

        if (e.button == MouseButton.SECONDARY) {
            showContextMenu(e)
            return
        }

        if (spacePressed) {
            spaceDragging = true
            spaceDragStartX = e.screenX
            spaceDragStartY = e.screenY
            spaceDragStartAz = azimuthProperty.get()
            spaceDragStartEl = elevationProperty.get()
            return
        }

        val modelCoords = screenToModel(e.x, e.y) ?: return

        if (applyConstruction != null) {
            handleApplyClick(e.x, e.y, modelCoords.first, modelCoords.second, modelCoords.third)
            return
        }

        currentTool?.onMousePressed(e, modelCoords.first, modelCoords.second, modelCoords.third)
    }

    private fun handleMouseMoved(e: MouseEvent) {
        val modelCoords = screenToModel(e.x, e.y)

        if (applyConstruction != null) {
            handleApplyHover(e.x, e.y)
            return
        }

        // Update hover state
        val hit = if (modelCoords != null) hitTester.hitTest(e.x, e.y) else null
        val newHoverId = when (hit) {
            is HitResult.ConstructedPoint -> hit.id
            is HitResult.OnAngle -> hit.id
            is HitResult.OnGeodesic -> hit.id
            is HitResult.OnCircle -> hit.id
            is HitResult.OnRegion -> hit.id
            else -> null
        }
        if (newHoverId != hoverId) {
            hoverId = newHoverId
            render()
        }

        if (modelCoords != null) {
            currentTool?.onMouseMoved(e, modelCoords.first, modelCoords.second, modelCoords.third)
        }
    }

    private fun handleMouseDragged(e: MouseEvent) {
        if (spaceDragging) {
            val dx = e.screenX - spaceDragStartX
            val dy = e.screenY - spaceDragStartY
            val sensitivity = 0.5
            azimuthProperty.set((spaceDragStartAz - dx * sensitivity).coerceIn(-180.0, 180.0))
            elevationProperty.set((spaceDragStartEl + dy * sensitivity).coerceIn(-90.0, 90.0))
            return
        }

        val modelCoords = screenToModel(e.x, e.y) ?: return
        currentTool?.onMouseDragged(e, modelCoords.first, modelCoords.second, modelCoords.third)
    }

    private fun handleMouseReleased(e: MouseEvent) {
        if (spaceDragging) {
            spaceDragging = false
            return
        }

        val modelCoords = screenToModel(e.x, e.y) ?: return
        currentTool?.onMouseReleased(e, modelCoords.first, modelCoords.second, modelCoords.third)
    }

    private fun handleEscape(): Boolean {
        // Priority 1: apply mode
        if (applyConstruction != null) {
            cancelApplyMode()
            return true
        }
        // Priority 2: tool in-progress
        if (currentTool?.onEscapePressed(previewGroup) == true) {
            updateHitTester()
            render()
            return true
        }
        // Priority 3: recording
        if (recorder.isRecording) {
            recorder.cancelRecording()
            updateHitTester()
            render()
            if (styleClass.contains("recording-border")) {
                styleClass.remove("recording-border")
            }
            return true
        }
        return false
    }

    fun handleEscapeFromMenu(): Boolean = handleEscape()

    // --- Apply Mode ---

    fun startApplyMode(construction: LoadedConstruction) {
        applyConstruction = construction
        currentInputIndex = 0
        collectedInputs.clear()
        constructionPreviewGroup.children.clear()
        updateApplyHint()
    }

    private fun handleApplyClick(screenX: Double, screenY: Double, worldX: Double, worldY: Double, worldZ: Double) {
        val construction = applyConstruction ?: return
        if (currentInputIndex >= construction.inputTypes.size) return

        val expectedType = construction.inputTypes[currentInputIndex]
        val hit = hitTester.hitTestFiltered(screenX, screenY, expectedType)

        val pointId = when (hit) {
            is HitResult.ConstructedPoint -> hit.id
            is HitResult.OnGeodesic -> hit.id
            is HitResult.OnCircle -> hit.id
            else -> return // No valid target
        }

        collectedInputs.add(pointId)
        currentInputIndex++

        if (currentInputIndex >= construction.inputTypes.size) {
            // All inputs collected — apply
            val inputMapping = mutableMapOf<Int, Int>()
            for (i in construction.inputIds.indices) {
                inputMapping[construction.inputIds[i]] = collectedInputs[i]
            }
            applicator.apply(construction, inputMapping)
            undoManager.checkpoint()
            cancelApplyMode()
            updateHitTester()
            render()
            onApplyComplete?.invoke()
        } else {
            updateApplyHint()
            constructionPreviewGroup.children.clear()
        }
    }

    private fun handleApplyHover(screenX: Double, screenY: Double) {
        val construction = applyConstruction ?: return
        if (currentInputIndex >= construction.inputTypes.size) return

        val expectedType = construction.inputTypes[currentInputIndex]
        val hit = hitTester.hitTestFiltered(screenX, screenY, expectedType)

        // Update hover
        val newHoverId = when (hit) {
            is HitResult.ConstructedPoint -> hit.id
            is HitResult.OnGeodesic -> hit.id
            is HitResult.OnCircle -> hit.id
            else -> null
        }
        if (newHoverId != hoverId) {
            hoverId = newHoverId
            render()
        }

        // Preview on last input
        constructionPreviewGroup.children.clear()
        if (currentInputIndex == construction.inputTypes.size - 1 && newHoverId != null) {
            val tempInputs = collectedInputs.toMutableList()
            tempInputs.add(newHoverId)
            val inputMapping = mutableMapOf<Int, Int>()
            for (i in construction.inputIds.indices) {
                if (i < tempInputs.size) {
                    inputMapping[construction.inputIds[i]] = tempInputs[i]
                }
            }
            val previewObjs = applicator.computePreview(construction, inputMapping)
            renderer.renderPreviewObjects(constructionPreviewGroup, previewObjs, viewMatrix, displayScale, diskCenterXValue, diskCenterYValue, true, showBackFace)
        }
    }

    private fun updateApplyHint() {
        val construction = applyConstruction ?: return
        if (currentInputIndex < construction.inputTypes.size) {
            val typeName = construction.inputTypes[currentInputIndex].lowercase()
            hintLabel.text = "Input ${currentInputIndex + 1}: select a $typeName"
            hintLabel.isVisible = true
        } else {
            hintLabel.isVisible = false
        }
    }

    fun cancelApplyMode() {
        applyConstruction = null
        currentInputIndex = 0
        collectedInputs.clear()
        constructionPreviewGroup.children.clear()
        hintLabel.isVisible = false
        hoverId = null
        render()
    }

    // --- Context Menu (Style Changes) ---

    private fun showContextMenu(e: MouseEvent) {
        if (selectedIds.isEmpty()) return

        val objects = manager.getObjects()
        val metadata = manager.getMetadata()
        val selectedObjs = selectedIds.mapNotNull { objects[it] }
        if (selectedObjs.isEmpty()) return

        // Determine category
        val category = objectCategory(selectedObjs.first())
        if (category == null || selectedObjs.any { objectCategory(it) != category }) return

        val menu = ContextMenu()

        // Color submenu (all categories)
        val colorMenu = Menu("Color")
        for ((name, _) in StyleUtils.COLOR_MAP) {
            val item = RadioMenuItem(name.replaceFirstChar { it.uppercase() })
            val allSame = selectedIds.all { metadata[it]?.color == name }
            item.isSelected = allSame
            item.setOnAction {
                selectedIds.forEach { id -> metadata[id]?.color = name }
                undoManager.checkpoint()
                render()
            }
            colorMenu.items.add(item)
        }
        menu.items.add(colorMenu)

        // Size submenu (Point, Angle)
        if (category == "Point" || category == "Angle") {
            val sizeMenu = Menu("Size")
            for (size in listOf("small", "medium", "large")) {
                val item = RadioMenuItem(size.replaceFirstChar { it.uppercase() })
                val allSame = selectedIds.all { metadata[it]?.size == size }
                item.isSelected = allSame
                item.setOnAction {
                    selectedIds.forEach { id -> metadata[id]?.size = size }
                    undoManager.checkpoint()
                    render()
                }
                sizeMenu.items.add(item)
            }
            menu.items.add(sizeMenu)
        }

        // Stroke Style submenu (Geodesic, Circle)
        if (category == "Geodesic" || category == "Circle") {
            val strokeStyleMenu = Menu("Stroke Style")
            for (style in listOf("solid", "dashed")) {
                val item = RadioMenuItem(style.replaceFirstChar { it.uppercase() })
                val allSame = selectedIds.all { metadata[it]?.strokeStyle == style }
                item.isSelected = allSame
                item.setOnAction {
                    selectedIds.forEach { id -> metadata[id]?.strokeStyle = style }
                    undoManager.checkpoint()
                    render()
                }
                strokeStyleMenu.items.add(item)
            }
            menu.items.add(strokeStyleMenu)

            val thicknessMenu = Menu("Stroke Thickness")
            for (thickness in listOf("thin", "normal", "thick")) {
                val item = RadioMenuItem(thickness.replaceFirstChar { it.uppercase() })
                val allSame = selectedIds.all { metadata[it]?.strokeThickness == thickness }
                item.isSelected = allSame
                item.setOnAction {
                    selectedIds.forEach { id -> metadata[id]?.strokeThickness = thickness }
                    undoManager.checkpoint()
                    render()
                }
                thicknessMenu.items.add(item)
            }
            menu.items.add(thicknessMenu)
        }

        menu.show(this, e.screenX, e.screenY)
    }

    internal fun objectCategory(obj: GeometricObject): String? {
        return when (obj) {
            is FreePoint, is FreePointOnLine, is FreePointOnSegment, is FreePointOnCircle,
            is Midpoint, is ReflectionPoint,
            is GeodesicGeodesicIntersection, is GeodesicCircleIntersection, is CircleCircleIntersection -> "Point"
            is Line, is Segment, is PerpendicularLine -> "Geodesic"
            is riemannsketchpad.geometry.Circle -> "Circle"
            is Angle -> "Angle"
            is Region -> "Region"
        }
    }

    // --- Coordinate Conversion ---

    fun screenToModel(screenX: Double, screenY: Double): Triple<Double, Double, Double>? {
        val modelX = (screenX - diskCenterXValue) / displayScale
        val modelY = -(screenY - diskCenterYValue) / displayScale
        return SphericalUtils.unprojectFromScreen(modelX, modelY, viewMatrix)
    }

    fun getViewMatrix(): DoubleArray = viewMatrix
}
