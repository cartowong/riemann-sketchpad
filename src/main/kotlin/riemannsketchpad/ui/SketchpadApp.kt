package riemannsketchpad.ui

import atlantafx.base.theme.PrimerLight
import javafx.application.Application
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.Stage
import riemannsketchpad.APP_NAME
import riemannsketchpad.APP_VERSION
import riemannsketchpad.geometry.*
import riemannsketchpad.model.*
import riemannsketchpad.persistence.*
import riemannsketchpad.export.ExportDialog
import java.io.File

class SketchpadApp : Application() {

    private val manager = GeometricObjectManager()
    private val undoManager = UndoManager(manager)
    private val styleSettings = StyleSettings()
    private val recorder = ConstructionRecorder(manager, undoManager)
    private val applicator = ConstructionApplicator(manager, styleSettings)

    private lateinit var canvas: SphereCanvasPane
    private lateinit var toolbar: Toolbar
    private lateinit var objectListPane: ObjectListPane
    private lateinit var constructionsPane: ConstructionsPane
    private lateinit var measurementsPane: MeasurementsPane
    private lateinit var outerSplitPane: SplitPane
    private lateinit var rightSplitPane: SplitPane
    private lateinit var leftPaneWrapper: VBox
    private lateinit var rightPaneWrapper: VBox

    private var showObjectList = true
    private var showConstructions = true
    private var showMeasurements = true

    // Menu items that need state toggling
    private lateinit var gridMenuItem: MenuItem
    private lateinit var backFaceMenuItem: MenuItem
    private lateinit var objectListMenuItem: MenuItem
    private lateinit var constructionsMenuItem: MenuItem
    private lateinit var measurementsMenuItem: MenuItem

    override fun start(stage: Stage) {
        Application.setUserAgentStylesheet(PrimerLight().userAgentStylesheet)

        canvas = SphereCanvasPane(manager, undoManager, styleSettings, recorder, applicator)

        // Camera sliders
        val azLabel = Label("Azimuth: 0°")
        val azSlider = Slider(-180.0, 180.0, 0.0)
        azSlider.prefWidth = 200.0
        azSlider.valueProperty().bindBidirectional(canvas.azimuthProperty)
        azSlider.valueProperty().addListener { _, _, v -> azLabel.text = "Azimuth: ${v.toInt()}°" }

        val elLabel = Label("Elevation: 0°")
        val elSlider = Slider(-90.0, 90.0, 0.0)
        elSlider.prefWidth = 200.0
        elSlider.valueProperty().bindBidirectional(canvas.elevationProperty)
        elSlider.valueProperty().addListener { _, _, v -> elLabel.text = "Elevation: ${v.toInt()}°" }

        val sliderBox = HBox(10.0, azLabel, azSlider, elLabel, elSlider)
        sliderBox.alignment = Pos.CENTER_LEFT
        sliderBox.padding = Insets(4.0, 8.0, 4.0, 8.0)

        // Toolbar
        toolbar = Toolbar { name -> onToolSelected(name) }

        // Center column
        VBox.setVgrow(canvas, Priority.ALWAYS)
        val centerColumn = VBox(toolbar, sliderBox, canvas)

        // Object list (left pane)
        objectListPane = ObjectListPane(manager, undoManager, recorder, canvas)
        objectListPane.onClose = { toggleObjectList() }
        leftPaneWrapper = VBox(objectListPane)
        VBox.setVgrow(objectListPane, Priority.ALWAYS)

        // Right pane: constructions + measurements
        constructionsPane = ConstructionsPane(canvas)
        constructionsPane.onClose = { toggleConstructions() }
        measurementsPane = MeasurementsPane(manager)
        measurementsPane.onClose = { toggleMeasurements() }

        rightSplitPane = SplitPane(constructionsPane, measurementsPane)
        rightSplitPane.orientation = Orientation.VERTICAL
        rightSplitPane.setDividerPositions(0.5)

        rightPaneWrapper = VBox(rightSplitPane)
        VBox.setVgrow(rightSplitPane, Priority.ALWAYS)

        // Outer split pane
        outerSplitPane = SplitPane(leftPaneWrapper, centerColumn, rightPaneWrapper)
        outerSplitPane.setDividerPositions(0.30, 0.70)
        SplitPane.setResizableWithParent(leftPaneWrapper, false)
        SplitPane.setResizableWithParent(rightPaneWrapper, false)

        // Selection sync
        canvas.selectedIds.addListener(javafx.collections.SetChangeListener {
            objectListPane.syncSelectionFromCanvas()
        })

        canvas.onRender = {
            objectListPane.refresh()
            measurementsPane.refresh()
        }

        canvas.onApplyComplete = {
            objectListPane.refresh()
            measurementsPane.refresh()
        }

        // Menu bar
        val menuBar = buildMenuBar(stage)

        val root = VBox(menuBar, outerSplitPane)
        VBox.setVgrow(outerSplitPane, Priority.ALWAYS)

        val scene = Scene(root, 1440.0, 720.0)

        // Load CSS
        val cssUrl = javaClass.getResource("/styles/geometry.css")
        if (cssUrl != null) {
            scene.stylesheets.add(cssUrl.toExternalForm())
        }

        stage.title = "Riemann Sketchpad"
        val appIconStream = javaClass.getResourceAsStream("/icons/app-icon.png")
        if (appIconStream != null) {
            stage.icons.add(Image(appIconStream))
        }
        stage.scene = scene

        stage.setOnCloseRequest { e ->
            if (undoManager.hasUnsavedChanges) {
                val alert = Alert(Alert.AlertType.CONFIRMATION, "You have unsaved changes. Exit anyway?")
                val result = alert.showAndWait()
                if (result.isEmpty || result.get() != ButtonType.OK) {
                    e.consume()
                }
            }
        }

        stage.show()

        // Initial state
        undoManager.checkpoint()
        undoManager.markSaved()
        objectListPane.refresh()
        canvas.render()
        toolbar.selectTool("Select")
    }

    // --- Tool Selection ---

    private fun onToolSelected(name: String) {
        val tool: Tool = when (name) {
            "Select" -> SelectionTool(canvas, manager, undoManager)
            "Point" -> PointTool(canvas, manager, undoManager, styleSettings)
            "Segment" -> GeodesicTool(canvas, manager, undoManager, styleSettings, GeodesicSubMode.SEGMENT)
            "Line" -> GeodesicTool(canvas, manager, undoManager, styleSettings, GeodesicSubMode.LINE)
            "Circle" -> CircleTool(canvas, manager, undoManager, styleSettings)
            "Angle" -> AngleTool(canvas, manager, undoManager, styleSettings)
            "Region" -> RegionTool(canvas, manager, undoManager, styleSettings)
            "Label" -> LabelTool(canvas, manager, undoManager)
            else -> return
        }
        canvas.currentTool = tool
    }

    // --- Menu Bar ---

    private fun buildMenuBar(stage: Stage): MenuBar {
        val menuBar = MenuBar()

        // File menu
        val fileMenu = Menu("File")
        val newItem = MenuItem("New")
        newItem.accelerator = KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN)
        newItem.setOnAction { doNew() }

        val saveItem = MenuItem("Save")
        saveItem.accelerator = KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN)
        saveItem.setOnAction { doSave(stage) }

        val openItem = MenuItem("Open")
        openItem.accelerator = KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN)
        openItem.setOnAction { doOpen(stage) }

        val exportItem = MenuItem("Export")
        exportItem.accelerator = KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN)
        exportItem.setOnAction { doExport(stage) }

        fileMenu.items.addAll(newItem, SeparatorMenuItem(), saveItem, openItem, SeparatorMenuItem(), exportItem)

        // Disable file menu during recording
        fileMenu.setOnShowing {
            val recording = recorder.isRecording
            newItem.isDisable = recording
            saveItem.isDisable = recording
            openItem.isDisable = recording
            exportItem.isDisable = recording
        }

        // Action menu
        val actionMenu = Menu("Action")
        val selectAllItem = MenuItem("Select All")
        selectAllItem.accelerator = KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN)
        selectAllItem.setOnAction {
            canvas.selectedIds.clear()
            manager.getObjects().keys.forEach { canvas.selectedIds.add(it) }
            canvas.render()
            objectListPane.syncSelectionFromCanvas()
        }

        val deselectAllItem = MenuItem("Deselect All")
        deselectAllItem.accelerator = KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)
        deselectAllItem.setOnAction {
            canvas.selectedIds.clear()
            canvas.render()
            objectListPane.syncSelectionFromCanvas()
        }

        val selectParentsItem = MenuItem("Select Parents")
        selectParentsItem.accelerator = KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)
        selectParentsItem.setOnAction {
            val parentIds = mutableSetOf<Int>()
            for (id in canvas.selectedIds) {
                manager.getObject(id)?.parentIds?.forEach { parentIds.add(it) }
            }
            canvas.selectedIds.clear()
            canvas.selectedIds.addAll(parentIds)
            canvas.render()
            objectListPane.syncSelectionFromCanvas()
        }

        val selectChildrenItem = MenuItem("Select Children")
        selectChildrenItem.accelerator = KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)
        selectChildrenItem.setOnAction {
            val selected = canvas.selectedIds.toSet()
            val childIds = mutableSetOf<Int>()
            for ((id, obj) in manager.getObjects()) {
                if (selected.all { it in obj.parentIds }) {
                    childIds.add(id)
                }
            }
            canvas.selectedIds.clear()
            canvas.selectedIds.addAll(childIds)
            canvas.render()
            objectListPane.syncSelectionFromCanvas()
        }

        val showItem = MenuItem("Show")
        showItem.accelerator = KeyCodeCombination(KeyCode.I, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)
        showItem.setOnAction {
            canvas.selectedIds.forEach { id -> manager.getMetadata(id)?.visible = true }
            undoManager.checkpoint()
            objectListPane.refresh()
            canvas.hitTester.rebuildIntersectionCache()
            canvas.render()
        }

        val hideItem = MenuItem("Hide")
        hideItem.accelerator = KeyCodeCombination(KeyCode.I, KeyCombination.SHORTCUT_DOWN)
        hideItem.setOnAction {
            canvas.selectedIds.forEach { id ->
                val meta = manager.getMetadata(id) ?: return@forEach
                meta.visible = false
                meta.labelVisible = false
            }
            undoManager.checkpoint()
            objectListPane.refresh()
            canvas.hitTester.rebuildIntersectionCache()
            canvas.render()
        }

        val deleteItem = MenuItem("Delete")
        deleteItem.accelerator = KeyCodeCombination(KeyCode.DELETE)
        deleteItem.setOnAction { doDeleteSelected() }

        val undoItem = MenuItem("Undo")
        undoItem.accelerator = KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN)
        undoItem.setOnAction {
            if (undoManager.canUndo) {
                undoManager.undo()
                canvas.hitTester.rebuildIntersectionCache()
                canvas.render()
                objectListPane.refresh()
                measurementsPane.refresh()
            }
        }

        val redoItem = MenuItem("Redo")
        redoItem.accelerator = KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)
        redoItem.setOnAction {
            if (undoManager.canRedo) {
                undoManager.redo()
                canvas.hitTester.rebuildIntersectionCache()
                canvas.render()
                objectListPane.refresh()
                measurementsPane.refresh()
            }
        }

        val measureItem = MenuItem("Measure")
        measureItem.accelerator = KeyCodeCombination(KeyCode.M, KeyCombination.SHORTCUT_DOWN)
        measureItem.setOnAction {
            for (id in canvas.selectedIds) {
                val obj = manager.getObject(id) ?: continue
                if (MeasurementUtils.measure(obj) != null) {
                    manager.getMetadata(id)?.inMeasurementTable = true
                }
            }
            undoManager.checkpoint()
            measurementsPane.refresh()
        }

        val clearMeasurementsItem = MenuItem("Clear Measurements")
        clearMeasurementsItem.accelerator = KeyCodeCombination(KeyCode.M, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)
        clearMeasurementsItem.setOnAction {
            manager.getMetadata().values.forEach { it.inMeasurementTable = false }
            measurementsPane.refresh()
        }

        actionMenu.items.addAll(selectAllItem, deselectAllItem, selectParentsItem, selectChildrenItem,
            SeparatorMenuItem(), showItem, hideItem, deleteItem,
            SeparatorMenuItem(), undoItem, redoItem,
            SeparatorMenuItem(), measureItem, clearMeasurementsItem)

        // Construction menu
        val constructionMenu = Menu("Construction")
        val startRecordingItem = MenuItem("Start Recording")
        startRecordingItem.accelerator = KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN)
        startRecordingItem.setOnAction {
            recorder.startRecording()
            canvas.styleClass.add("recording-border")
            canvas.render()
            objectListPane.refresh()
        }

        val saveRecordingItem = MenuItem("Save Recording")
        saveRecordingItem.accelerator = KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)
        saveRecordingItem.setOnAction { doSaveRecording(stage) }

        val cancelRecordingItem = MenuItem("Cancel Recording")
        cancelRecordingItem.setOnAction {
            recorder.cancelRecording()
            canvas.styleClass.remove("recording-border")
            canvas.hitTester.rebuildIntersectionCache()
            canvas.render()
            objectListPane.refresh()
        }

        val loadConstructionItem = MenuItem("Load Construction")
        loadConstructionItem.accelerator = KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN)
        loadConstructionItem.setOnAction { doLoadConstruction(stage) }

        val loadFolderItem = MenuItem("Load from Folder...")
        loadFolderItem.accelerator = KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)
        loadFolderItem.setOnAction { doLoadFolder(stage) }

        val unloadAllItem = MenuItem("Unload All")
        unloadAllItem.accelerator = KeyCodeCombination(KeyCode.U, KeyCombination.SHORTCUT_DOWN)
        unloadAllItem.setOnAction { constructionsPane.constructions.clear() }

        // Built-in constructions
        val midpointItem = MenuItem("Midpoint")
        midpointItem.setOnAction { startBuiltInApply("Midpoint") }

        val perpItem = MenuItem("Perpendicular Line")
        perpItem.setOnAction { startBuiltInApply("PerpendicularLine") }

        val reflItem = MenuItem("Reflection Point")
        reflItem.setOnAction { startBuiltInApply("ReflectionPoint") }

        constructionMenu.items.addAll(startRecordingItem, saveRecordingItem, cancelRecordingItem,
            SeparatorMenuItem(), loadConstructionItem, loadFolderItem, unloadAllItem,
            SeparatorMenuItem(), midpointItem, perpItem, reflItem)

        constructionMenu.setOnShowing {
            val recording = recorder.isRecording
            startRecordingItem.isDisable = recording
            saveRecordingItem.isDisable = !recording
            cancelRecordingItem.isDisable = !recording
            loadConstructionItem.isDisable = recording
            loadFolderItem.isDisable = recording
            unloadAllItem.isDisable = recording
        }

        // Style menu
        val styleMenu = buildStyleMenu()

        // View menu
        val viewMenu = Menu("View")
        gridMenuItem = MenuItem(if (canvas.showGrid) "Hide Grid" else "Show Grid")
        gridMenuItem.accelerator = KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN)
        gridMenuItem.setOnAction {
            canvas.showGrid = !canvas.showGrid
            gridMenuItem.text = if (canvas.showGrid) "Hide Grid" else "Show Grid"
        }

        backFaceMenuItem = MenuItem(if (canvas.showBackFace) "Hide Back Face" else "Show Back Face")
        backFaceMenuItem.accelerator = KeyCodeCombination(KeyCode.B, KeyCombination.SHORTCUT_DOWN)
        backFaceMenuItem.setOnAction {
            canvas.showBackFace = !canvas.showBackFace
            backFaceMenuItem.text = if (canvas.showBackFace) "Hide Back Face" else "Show Back Face"
        }

        objectListMenuItem = MenuItem(if (showObjectList) "Hide Object List" else "Show Object List")
        objectListMenuItem.setOnAction { toggleObjectList() }

        constructionsMenuItem = MenuItem(if (showConstructions) "Hide Custom Constructions" else "Show Custom Constructions")
        constructionsMenuItem.setOnAction { toggleConstructions() }

        measurementsMenuItem = MenuItem(if (showMeasurements) "Hide Measurements" else "Show Measurements")
        measurementsMenuItem.setOnAction { toggleMeasurements() }

        viewMenu.items.addAll(gridMenuItem, backFaceMenuItem, SeparatorMenuItem(), objectListMenuItem, constructionsMenuItem, measurementsMenuItem)

        // About menu
        val aboutMenu = Menu("About")
        val aboutItem = MenuItem("About Riemann Sketchpad")
        aboutItem.setOnAction {
            val dialog = Dialog<Void>()
            dialog.title = "About Riemann Sketchpad"
            dialog.dialogPane.buttonTypes.add(ButtonType.CLOSE)

            val content = VBox(12.0)
            content.alignment = Pos.CENTER
            content.padding = Insets(16.0)

            val iconStream = javaClass.getResourceAsStream("/icons/app-icon.png")
            if (iconStream != null) {
                val imageView = ImageView(Image(iconStream))
                imageView.fitWidth = 96.0
                imageView.fitHeight = 96.0
                imageView.isPreserveRatio = true
                content.children.add(imageView)
            }

            content.children.addAll(
                Label("Riemann Sketchpad").apply { style = "-fx-font-size: 18px; -fx-font-weight: bold;" },
                Label("Version $APP_VERSION"),
                Label("A compass-and-straightedge construction tool\nfor elliptic geometry on the Riemann sphere."),
                Label("Copyright \u00A9 2026 Carto Wong"),
                Label("Licensed under the GNU General Public License v3.0"),
                Hyperlink("github.com/cartowong/riemann-sketchpad").apply {
                    setOnAction {
                        hostServices.showDocument("https://github.com/cartowong/riemann-sketchpad")
                    }
                },
                Hyperlink("Third-Party Licenses").apply {
                    setOnAction {
                        val noticesDialog = Dialog<Void>()
                        noticesDialog.title = "Third-Party Licenses"
                        noticesDialog.dialogPane.buttonTypes.add(ButtonType.CLOSE)
                        val noticesText = SketchpadApp::class.java.getResourceAsStream("/THIRD_PARTY_NOTICES")
                            ?.bufferedReader()?.readText() ?: "Could not load third-party notices."
                        val textArea = javafx.scene.control.TextArea(noticesText)
                        textArea.isEditable = false
                        textArea.isWrapText = true
                        textArea.prefWidth = 600.0
                        textArea.prefHeight = 400.0
                        noticesDialog.dialogPane.content = textArea
                        noticesDialog.initOwner(stage)
                        noticesDialog.showAndWait()
                    }
                }
            )

            dialog.dialogPane.content = content
            dialog.initOwner(stage)
            dialog.showAndWait()
        }
        aboutMenu.items.add(aboutItem)

        menuBar.menus.addAll(fileMenu, actionMenu, constructionMenu, styleMenu, viewMenu, aboutMenu)
        return menuBar
    }

    // --- Style Menu ---

    /**
     * Returns the IDs of currently selected objects that belong to the given category,
     * or an empty list if none match.
     */
    private fun selectedIdsForCategory(category: String): List<Int> {
        val objects = manager.getObjects()
        return canvas.selectedIds.filter { id ->
            val obj = objects[id] ?: return@filter false
            canvas.objectCategory(obj) == category
        }
    }

    /**
     * Apply a metadata mutation to selected objects of the given category.
     * If matching objects are selected, mutates them, checkpoints undo, and re-renders.
     * Returns true if applied to selection, false if no matching selection (caller should update default).
     */
    private fun applyToSelectionOrDefault(category: String, mutate: (ObjectMetadata) -> Unit, updateDefault: () -> Unit) {
        val ids = selectedIdsForCategory(category)
        if (ids.isNotEmpty()) {
            val metadata = manager.getMetadata()
            ids.forEach { id -> metadata[id]?.let(mutate) }
            undoManager.checkpoint()
            canvas.render()
        } else {
            updateDefault()
        }
    }

    private fun buildStyleMenu(): Menu {
        val styleMenu = Menu("Style")

        // Point submenu
        val pointMenu = Menu("Point")
        val pointSizeMenu = Menu("Size")
        val pointSizeGroup = ToggleGroup()
        for (size in listOf("small", "medium", "large")) {
            val item = RadioMenuItem(size.replaceFirstChar { it.uppercase() })
            item.toggleGroup = pointSizeGroup
            item.isSelected = styleSettings.pointSize == size
            item.setOnAction {
                applyToSelectionOrDefault("Point", { it.size = size }, { styleSettings.pointSize = size })
            }
            pointSizeMenu.items.add(item)
        }
        val pointColorMenu = buildColorSubMenu("Point", { styleSettings.pointColor }, { styleSettings.pointColor = it })
        pointMenu.items.addAll(pointSizeMenu, pointColorMenu)

        // Geodesic submenu
        val geodesicMenu = Menu("Geodesic")
        val geodesicColorMenu = buildColorSubMenu("Geodesic", { styleSettings.geodesicColor }, { styleSettings.geodesicColor = it })
        val geodesicStrokeMenu = Menu("Stroke Style")
        val strokeGroup = ToggleGroup()
        for (style in listOf("solid", "dashed")) {
            val item = RadioMenuItem(style.replaceFirstChar { it.uppercase() })
            item.toggleGroup = strokeGroup
            item.isSelected = styleSettings.geodesicStrokeStyle == style
            item.setOnAction {
                applyToSelectionOrDefault("Geodesic", { it.strokeStyle = style }, { styleSettings.geodesicStrokeStyle = style })
            }
            geodesicStrokeMenu.items.add(item)
        }
        val geodesicThicknessMenu = Menu("Stroke Thickness")
        val thickGroup = ToggleGroup()
        for (thickness in listOf("thin", "normal", "thick")) {
            val item = RadioMenuItem(thickness.replaceFirstChar { it.uppercase() })
            item.toggleGroup = thickGroup
            item.isSelected = styleSettings.geodesicStrokeThickness == thickness
            item.setOnAction {
                applyToSelectionOrDefault("Geodesic", { it.strokeThickness = thickness }, { styleSettings.geodesicStrokeThickness = thickness })
            }
            geodesicThicknessMenu.items.add(item)
        }
        geodesicMenu.items.addAll(geodesicColorMenu, geodesicStrokeMenu, geodesicThicknessMenu)

        // Circle submenu
        val circleMenu = Menu("Circle")
        val circleColorMenu = buildColorSubMenu("Circle", { styleSettings.circleColor }, { styleSettings.circleColor = it })
        val circleStrokeMenu = Menu("Stroke Style")
        val circStrokeGroup = ToggleGroup()
        for (style in listOf("solid", "dashed")) {
            val item = RadioMenuItem(style.replaceFirstChar { it.uppercase() })
            item.toggleGroup = circStrokeGroup
            item.isSelected = styleSettings.circleStrokeStyle == style
            item.setOnAction {
                applyToSelectionOrDefault("Circle", { it.strokeStyle = style }, { styleSettings.circleStrokeStyle = style })
            }
            circleStrokeMenu.items.add(item)
        }
        val circleThicknessMenu = Menu("Stroke Thickness")
        val circThickGroup = ToggleGroup()
        for (thickness in listOf("thin", "normal", "thick")) {
            val item = RadioMenuItem(thickness.replaceFirstChar { it.uppercase() })
            item.toggleGroup = circThickGroup
            item.isSelected = styleSettings.circleStrokeThickness == thickness
            item.setOnAction {
                applyToSelectionOrDefault("Circle", { it.strokeThickness = thickness }, { styleSettings.circleStrokeThickness = thickness })
            }
            circleThicknessMenu.items.add(item)
        }
        circleMenu.items.addAll(circleColorMenu, circleStrokeMenu, circleThicknessMenu)

        // Angle submenu
        val angleMenu = Menu("Angle")
        val angleSizeMenu = Menu("Size")
        val angleSizeGroup = ToggleGroup()
        for (size in listOf("small", "medium", "large")) {
            val item = RadioMenuItem(size.replaceFirstChar { it.uppercase() })
            item.toggleGroup = angleSizeGroup
            item.isSelected = styleSettings.angleSize == size
            item.setOnAction {
                applyToSelectionOrDefault("Angle", { it.size = size }, { styleSettings.angleSize = size })
            }
            angleSizeMenu.items.add(item)
        }
        val angleColorMenu = buildColorSubMenu("Angle", { styleSettings.angleColor }, { styleSettings.angleColor = it })
        angleMenu.items.addAll(angleSizeMenu, angleColorMenu)

        // Region submenu
        val regionMenu = Menu("Region")
        val regionColorMenu = buildColorSubMenu("Region", { styleSettings.regionColor }, { styleSettings.regionColor = it })
        regionMenu.items.add(regionColorMenu)

        styleMenu.items.addAll(pointMenu, geodesicMenu, circleMenu, angleMenu, regionMenu)
        return styleMenu
    }

    private fun buildColorSubMenu(category: String, currentGetter: () -> String, setter: (String) -> Unit): Menu {
        val menu = Menu("Color")
        val group = ToggleGroup()
        for ((name, _) in StyleUtils.COLOR_MAP) {
            val item = RadioMenuItem(name.replaceFirstChar { it.uppercase() })
            item.toggleGroup = group
            item.isSelected = currentGetter() == name
            item.setOnAction {
                applyToSelectionOrDefault(category, { it.color = name }, { setter(name) })
            }
            menu.items.add(item)
        }
        return menu
    }

    // --- Pane Visibility ---

    private fun toggleObjectList() {
        showObjectList = !showObjectList
        objectListMenuItem.text = if (showObjectList) "Hide Object List" else "Show Object List"
        rebuildSplitPane()
    }

    private fun toggleConstructions() {
        showConstructions = !showConstructions
        constructionsMenuItem.text = if (showConstructions) "Hide Custom Constructions" else "Show Custom Constructions"
        rebuildRightPane()
    }

    private fun toggleMeasurements() {
        showMeasurements = !showMeasurements
        measurementsMenuItem.text = if (showMeasurements) "Hide Measurements" else "Show Measurements"
        rebuildRightPane()
    }

    private fun rebuildRightPane() {
        rightSplitPane.items.clear()
        if (showConstructions) rightSplitPane.items.add(constructionsPane)
        if (showMeasurements) rightSplitPane.items.add(measurementsPane)
        if (rightSplitPane.items.size == 2) rightSplitPane.setDividerPositions(0.5)

        val showRight = showConstructions || showMeasurements
        rebuildSplitPane(showRight)
    }

    private fun rebuildSplitPane(forceShowRight: Boolean? = null) {
        val showRight = forceShowRight ?: (showConstructions || showMeasurements)
        val centerColumn = outerSplitPane.items.find { it !is VBox || (it != leftPaneWrapper && it != rightPaneWrapper) }
            ?: return

        outerSplitPane.items.clear()
        if (showObjectList) outerSplitPane.items.add(leftPaneWrapper)
        outerSplitPane.items.add(centerColumn)
        if (showRight) outerSplitPane.items.add(rightPaneWrapper)

        // Set divider positions
        when (outerSplitPane.items.size) {
            3 -> outerSplitPane.setDividerPositions(0.30, 0.70)
            2 -> outerSplitPane.setDividerPositions(if (showObjectList) 0.30 else 0.70)
        }
    }

    // --- File Operations ---

    private fun doNew() {
        if (undoManager.hasUnsavedChanges) {
            val alert = Alert(Alert.AlertType.CONFIRMATION, "You have unsaved changes. Create a new diagram?")
            val result = alert.showAndWait()
            if (result.isEmpty || result.get() != ButtonType.OK) return
        }
        manager.restore(ManagerState(LinkedHashMap(), HashMap(), 0))
        undoManager.reset()
        undoManager.checkpoint()
        undoManager.markSaved()
        canvas.selectedIds.clear()
        canvas.hitTester.rebuildIntersectionCache()
        canvas.render()
        objectListPane.refresh()
        measurementsPane.refresh()
    }

    private fun doSave(stage: Stage) {
        val chooser = FileChooser()
        chooser.title = "Save Diagram"
        chooser.extensionFilters.add(FileChooser.ExtensionFilter("JSON Files", "*.json"))
        val file = chooser.showSaveDialog(stage) ?: return
        val json = DiagramSerializer.serialize(manager)
        file.writeText(json)
        undoManager.markSaved()
    }

    private fun doOpen(stage: Stage) {
        if (undoManager.hasUnsavedChanges) {
            val alert = Alert(Alert.AlertType.CONFIRMATION, "You have unsaved changes. Open a new diagram?")
            val result = alert.showAndWait()
            if (result.isEmpty || result.get() != ButtonType.OK) return
        }
        val chooser = FileChooser()
        chooser.title = "Open Diagram"
        chooser.extensionFilters.add(FileChooser.ExtensionFilter("JSON Files", "*.json"))
        val file = chooser.showOpenDialog(stage) ?: return
        try {
            val json = file.readText()
            manager.restore(ManagerState(LinkedHashMap(), HashMap(), 0))
            DiagramSerializer.deserialize(json, manager)
            undoManager.reset()
            undoManager.checkpoint()
            undoManager.markSaved()
            canvas.selectedIds.clear()
            canvas.hitTester.rebuildIntersectionCache()
            canvas.render()
            objectListPane.refresh()
            measurementsPane.refresh()
        } catch (e: Exception) {
            Alert(Alert.AlertType.ERROR, "Failed to open file: ${e.message}").showAndWait()
        }
    }

    private fun doExport(stage: Stage) {
        val dialog = ExportDialog(canvas, manager, stage)
        dialog.show()
    }

    private fun doDeleteSelected() {
        if (canvas.selectedIds.isEmpty()) return
        val alert = Alert(Alert.AlertType.CONFIRMATION, "Delete ${canvas.selectedIds.size} object(s) and their descendants?")
        val result = alert.showAndWait()
        if (result.isEmpty || result.get() != ButtonType.OK) return

        val toDelete = canvas.selectedIds.toList().sortedDescending()
        canvas.selectedIds.clear()
        for (id in toDelete) {
            manager.removeWithDescendants(id)
        }
        undoManager.checkpoint()
        canvas.hitTester.rebuildIntersectionCache()
        canvas.render()
        objectListPane.refresh()
        measurementsPane.refresh()
    }

    // --- Construction Operations ---

    private fun doSaveRecording(stage: Stage) {
        if (!recorder.isRecording) return
        val chooser = FileChooser()
        chooser.title = "Save Construction"
        chooser.extensionFilters.add(FileChooser.ExtensionFilter("JSON Files", "*.json"))
        val file = chooser.showSaveDialog(stage) ?: return

        val json = ConstructionSerializer.saveConstruction(manager)
        file.writeText(json)
        recorder.endRecording()
        canvas.styleClass.remove("recording-border")
        canvas.hitTester.rebuildIntersectionCache()
        canvas.render()
        objectListPane.refresh()
    }

    private fun doLoadConstruction(stage: Stage) {
        val chooser = FileChooser()
        chooser.title = "Load Construction"
        chooser.extensionFilters.add(FileChooser.ExtensionFilter("JSON Files", "*.json"))
        val files = chooser.showOpenMultipleDialog(stage) ?: return
        loadConstructionFiles(files)
    }

    private fun doLoadFolder(stage: Stage) {
        val chooser = DirectoryChooser()
        chooser.title = "Load Constructions from Folder"
        val dir = chooser.showDialog(stage) ?: return
        val files = dir.listFiles { f -> f.extension == "json" }?.sorted() ?: return
        loadConstructionFiles(files.toList())
    }

    private fun loadConstructionFiles(files: List<File>) {
        val errors = mutableListOf<String>()
        for (file in files) {
            try {
                val json = file.readText()
                val loaded = ConstructionSerializer.loadConstruction(json, file.name)
                constructionsPane.constructions.add(loaded)
            } catch (e: Exception) {
                errors.add(file.name)
            }
        }
        if (errors.isNotEmpty()) {
            Alert(Alert.AlertType.WARNING, "Failed to load: ${errors.joinToString(", ")}").showAndWait()
        }
    }

    // --- Built-in Constructions ---

    private fun startBuiltInApply(type: String) {
        val construction = createBuiltInConstruction(type) ?: return
        canvas.startApplyMode(construction)
    }

    private fun createBuiltInConstruction(type: String): LoadedConstruction? {
        return when (type) {
            "Midpoint" -> {
                // Two point inputs -> Midpoint
                val objects = listOf(
                    SerializedObject(0, "FreePoint", x = 1.0, y = 0.0, z = 0.0),
                    SerializedObject(1, "FreePoint", x = 0.0, y = 1.0, z = 0.0),
                    SerializedObject(2, "Midpoint", parentIds = listOf(0, 1))
                )
                val metadata = mapOf(
                    "0" to SerializedMetadata(isInput = true),
                    "1" to SerializedMetadata(isInput = true),
                    "2" to SerializedMetadata(visible = true)
                )
                val diagram = SerializedDiagram(APP_NAME, APP_VERSION, "riemann", objects, metadata)
                LoadedConstruction("Midpoint", diagram, listOf("Point", "Point"), listOf(0, 1))
            }
            "PerpendicularLine" -> {
                val objects = listOf(
                    SerializedObject(0, "FreePoint", x = 1.0, y = 0.0, z = 0.0),
                    SerializedObject(1, "FreePoint", x = 0.0, y = 1.0, z = 0.0),
                    SerializedObject(2, "FreePoint", x = 0.0, y = 0.0, z = 1.0),
                    SerializedObject(3, "Line", parentIds = listOf(1, 2)),
                    SerializedObject(4, "PerpendicularLine", parentIds = listOf(0, 3))
                )
                val metadata = mapOf(
                    "0" to SerializedMetadata(isInput = true),
                    "1" to SerializedMetadata(isLocked = true),
                    "2" to SerializedMetadata(isLocked = true),
                    "3" to SerializedMetadata(isInput = true),
                    "4" to SerializedMetadata(visible = true)
                )
                val diagram = SerializedDiagram(APP_NAME, APP_VERSION, "riemann", objects, metadata)
                LoadedConstruction("Perpendicular Line", diagram, listOf("Point", "Geodesic"), listOf(0, 3))
            }
            "ReflectionPoint" -> {
                val objects = listOf(
                    SerializedObject(0, "FreePoint", x = 1.0, y = 0.0, z = 0.0),
                    SerializedObject(1, "FreePoint", x = 0.0, y = 1.0, z = 0.0),
                    SerializedObject(2, "FreePoint", x = 0.0, y = 0.0, z = 1.0),
                    SerializedObject(3, "Line", parentIds = listOf(1, 2)),
                    SerializedObject(4, "ReflectionPoint", parentIds = listOf(0, 3))
                )
                val metadata = mapOf(
                    "0" to SerializedMetadata(isInput = true),
                    "1" to SerializedMetadata(isLocked = true),
                    "2" to SerializedMetadata(isLocked = true),
                    "3" to SerializedMetadata(isInput = true),
                    "4" to SerializedMetadata(visible = true)
                )
                val diagram = SerializedDiagram(APP_NAME, APP_VERSION, "riemann", objects, metadata)
                LoadedConstruction("Reflection Point", diagram, listOf("Point", "Geodesic"), listOf(0, 3))
            }
            else -> null
        }
    }
}
