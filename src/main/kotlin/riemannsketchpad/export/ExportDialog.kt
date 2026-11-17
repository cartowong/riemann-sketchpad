package riemannsketchpad.export

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.stage.FileChooser
import javafx.stage.Modality
import javafx.stage.Stage
import riemannsketchpad.geometry.SphericalUtils
import riemannsketchpad.model.GeometricObjectManager
import riemannsketchpad.ui.SphereCanvasPane
import java.io.File
import kotlin.math.min

class ExportDialog(
    private val canvas: SphereCanvasPane,
    private val manager: GeometricObjectManager,
    private val ownerStage: Stage
) {
    private val stage = Stage()

    // State
    private var method = "Canvas" // "Canvas" or "Custom"
    private var format = "PNG"
    private var transparent = false
    private var includeGrid = true
    private var includeBackFace = false
    private var customWidth = 600
    private var customHeight = 600
    private var lockAspect = true
    private var scalePercent = 100.0

    // Overlay shapes
    private val overlayShapes = mutableListOf<javafx.scene.Node>()

    fun show() {
        stage.initModality(Modality.NONE)
        stage.initOwner(ownerStage)
        stage.title = "Export"

        val root = VBox(10.0)
        root.padding = Insets(16.0)

        // Method
        val methodGroup = ToggleGroup()
        val canvasRadio = RadioButton("Canvas")
        canvasRadio.toggleGroup = methodGroup
        canvasRadio.isSelected = true
        val customRadio = RadioButton("Custom")
        customRadio.toggleGroup = methodGroup
        val methodBox = HBox(16.0, Label("Method:"), canvasRadio, customRadio)
        methodBox.alignment = Pos.CENTER_LEFT

        // Format
        val formatGroup = ToggleGroup()
        val pngRadio = RadioButton("PNG")
        pngRadio.toggleGroup = formatGroup
        pngRadio.isSelected = true
        val svgRadio = RadioButton("SVG")
        svgRadio.toggleGroup = formatGroup
        val formatBox = HBox(16.0, Label("Format:"), pngRadio, svgRadio)
        formatBox.alignment = Pos.CENTER_LEFT

        // Options
        val transparentCb = CheckBox("Transparent background")
        val gridCb = CheckBox("Include grid")
        gridCb.isSelected = true
        val backFaceCb = CheckBox("Include back face")
        backFaceCb.isSelected = false
        val optionsRow1 = HBox(16.0, transparentCb, gridCb)
        optionsRow1.alignment = Pos.CENTER_LEFT
        val optionsRow2 = HBox(16.0, backFaceCb)
        optionsRow2.alignment = Pos.CENTER_LEFT
        val optionsBox = VBox(6.0, optionsRow1, optionsRow2)

        // Dimensions — Canvas mode
        val diameterField = TextField()
        diameterField.isEditable = false
        diameterField.prefWidth = 100.0
        val canvasDimsBox = HBox(8.0, Label("Diameter:"), diameterField)
        canvasDimsBox.alignment = Pos.CENTER_LEFT
        updateCanvasDiameter(diameterField)

        // Dimensions — Custom mode
        val widthField = TextField(customWidth.toString())
        widthField.prefWidth = 80.0
        val heightField = TextField(customHeight.toString())
        heightField.prefWidth = 80.0
        val lockToggle = CheckBox("Lock aspect ratio")
        lockToggle.isSelected = lockAspect

        val scaleSlider = Slider(10.0, 100.0, scalePercent)
        scaleSlider.prefWidth = 150.0
        val scaleField = TextField("${scalePercent.toInt()}%")
        scaleField.prefWidth = 60.0

        val customDimsBox = VBox(8.0,
            HBox(8.0, Label("Width:"), widthField, Label("Height:"), heightField, lockToggle),
            HBox(8.0, Label("Scale:"), scaleSlider, scaleField)
        )
        customDimsBox.isVisible = false
        customDimsBox.isManaged = false

        // Buttons
        val exportBtn = Button("Export")
        val cancelBtn = Button("Cancel")
        val btnBox = HBox(8.0, exportBtn, cancelBtn)
        btnBox.alignment = Pos.CENTER_RIGHT

        root.children.addAll(methodBox, formatBox, optionsBox, canvasDimsBox, customDimsBox, btnBox)

        // Event handlers
        canvasRadio.setOnAction {
            method = "Canvas"
            canvasDimsBox.isVisible = true; canvasDimsBox.isManaged = true
            customDimsBox.isVisible = false; customDimsBox.isManaged = false
            clearOverlay()
        }
        customRadio.setOnAction {
            method = "Custom"
            canvasDimsBox.isVisible = false; canvasDimsBox.isManaged = false
            customDimsBox.isVisible = true; customDimsBox.isManaged = true
            updateOverlay()
        }
        pngRadio.setOnAction { format = "PNG" }
        svgRadio.setOnAction { format = "SVG" }
        transparentCb.setOnAction { transparent = transparentCb.isSelected }
        gridCb.setOnAction { includeGrid = gridCb.isSelected }
        backFaceCb.setOnAction { includeBackFace = backFaceCb.isSelected }

        widthField.textProperty().addListener { _, _, v ->
            v.toIntOrNull()?.let {
                customWidth = it
                if (lockAspect) {
                    customHeight = it
                    heightField.text = it.toString()
                }
                updateOverlay()
            }
        }
        heightField.textProperty().addListener { _, _, v ->
            v.toIntOrNull()?.let {
                customHeight = it
                if (lockAspect) {
                    customWidth = it
                    widthField.text = it.toString()
                }
                updateOverlay()
            }
        }
        lockToggle.setOnAction {
            lockAspect = lockToggle.isSelected
            if (lockAspect) {
                val maxDim = maxOf(customWidth, customHeight)
                customWidth = maxDim; customHeight = maxDim
                widthField.text = maxDim.toString()
                heightField.text = maxDim.toString()
                updateOverlay()
            }
        }
        scaleSlider.valueProperty().addListener { _, _, v ->
            scalePercent = v.toDouble()
            scaleField.text = "${v.toInt()}%"
            updateOverlay()
        }

        cancelBtn.setOnAction {
            clearOverlay()
            stage.close()
        }

        exportBtn.setOnAction { doExport() }

        stage.setOnCloseRequest { clearOverlay() }

        val scene = Scene(root, 450.0, 280.0)
        stage.scene = scene
        stage.show()
    }

    private fun updateCanvasDiameter(field: TextField) {
        val diameter = (canvas.displayScale * 2).toInt()
        field.text = "${diameter}px"
    }

    private fun doExport() {
        val chooser = FileChooser()
        chooser.title = "Export"
        val ext = if (format == "PNG") "png" else "svg"
        chooser.extensionFilters.add(FileChooser.ExtensionFilter("${format} Files", "*.$ext"))
        val file = chooser.showSaveDialog(stage) ?: return

        val viewMatrix = canvas.getViewMatrix()

        if (method == "Canvas") {
            val diameter = (canvas.displayScale * 2).toInt()
            val centerX = diameter / 2.0
            val centerY = diameter / 2.0
            val scale = diameter / 2.0

            if (format == "PNG") {
                ExportRenderer.exportPng(file, manager, viewMatrix, scale, centerX, centerY, diameter, diameter, transparent, includeGrid, includeBackFace)
            } else {
                ExportRenderer.exportSvg(file, manager, viewMatrix, scale, centerX, centerY, diameter, diameter, transparent, includeGrid, includeBackFace)
            }
        } else {
            // Custom method
            val scaleFactor = scalePercent / 100.0
            val diskDiameter = canvas.displayScale * 2
            val cropWidth = customWidth.toDouble()
            val cropHeight = customHeight.toDouble()

            // The crop rectangle spans a fraction of the disk
            val displayScale = (diskDiameter / scaleFactor) / 2.0 * (min(customWidth, customHeight).toDouble() / diskDiameter)
            val exportScale = min(cropWidth, cropHeight) / 2.0 / scaleFactor
            val centerX = cropWidth / 2.0
            val centerY = cropHeight / 2.0

            if (format == "PNG") {
                ExportRenderer.exportPng(file, manager, viewMatrix, exportScale, centerX, centerY, customWidth, customHeight, transparent, includeGrid, includeBackFace)
            } else {
                ExportRenderer.exportSvg(file, manager, viewMatrix, exportScale, centerX, centerY, customWidth, customHeight, transparent, includeGrid, includeBackFace)
            }
        }

        clearOverlay()
        stage.close()
    }

    private fun updateOverlay() {
        clearOverlay()
        if (method != "Custom") return

        val diskRadius = canvas.displayScale
        val centerX = canvas.diskCenterXValue
        val centerY = canvas.diskCenterYValue
        val scaleFactor = scalePercent / 100.0

        // Crop rectangle dimensions in screen pixels
        val aspectRatio = customWidth.toDouble() / customHeight.toDouble()
        val cropScreenSize = diskRadius * 2.0 * scaleFactor
        val cropW: Double
        val cropH: Double
        if (aspectRatio >= 1.0) {
            cropW = cropScreenSize
            cropH = cropScreenSize / aspectRatio
        } else {
            cropH = cropScreenSize
            cropW = cropScreenSize * aspectRatio
        }

        val left = centerX - cropW / 2.0
        val top = centerY - cropH / 2.0

        val paneWidth = canvas.width
        val paneHeight = canvas.height

        // Letterbox rectangles
        val topRect = Rectangle(0.0, 0.0, paneWidth, top.coerceAtLeast(0.0))
        topRect.fill = Color.color(0.0, 0.0, 0.0, 0.3)
        topRect.isMouseTransparent = true

        val bottomRect = Rectangle(0.0, (top + cropH).coerceAtLeast(0.0), paneWidth, (paneHeight - top - cropH).coerceAtLeast(0.0))
        bottomRect.fill = Color.color(0.0, 0.0, 0.0, 0.3)
        bottomRect.isMouseTransparent = true

        val leftRect = Rectangle(0.0, top.coerceAtLeast(0.0), left.coerceAtLeast(0.0), cropH)
        leftRect.fill = Color.color(0.0, 0.0, 0.0, 0.3)
        leftRect.isMouseTransparent = true

        val rightRect = Rectangle((left + cropW).coerceAtLeast(0.0), top.coerceAtLeast(0.0), (paneWidth - left - cropW).coerceAtLeast(0.0), cropH)
        rightRect.fill = Color.color(0.0, 0.0, 0.0, 0.3)
        rightRect.isMouseTransparent = true

        // Orange border
        val border = Rectangle(left, top, cropW, cropH)
        border.fill = Color.TRANSPARENT
        border.stroke = Color.web("#FF5722")
        border.strokeWidth = 2.0
        border.isMouseTransparent = true

        // Scale label
        val label = javafx.scene.text.Text(left + 4, top + 14, "Scale: ${scalePercent.toInt()}%")
        label.fill = Color.web("#FF5722")
        label.isMouseTransparent = true

        overlayShapes.addAll(listOf(topRect, bottomRect, leftRect, rightRect, border, label))
        canvas.exportOverlayGroup.children.addAll(overlayShapes)
    }

    private fun clearOverlay() {
        canvas.exportOverlayGroup.children.clear()
        overlayShapes.clear()
    }
}
