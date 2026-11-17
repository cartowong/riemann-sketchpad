package riemannsketchpad.ui

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import riemannsketchpad.geometry.*
import riemannsketchpad.model.GeometricObjectManager
import riemannsketchpad.model.ConstructionRecorder
import riemannsketchpad.model.UndoManager
import riemannsketchpad.persistence.LoadedConstruction

// --- Object List Table ---

class ObjectListRow(
    val objId: Int,
    val typeName: String,
    val parents: String,
    val label: String,
    val visible: Boolean,
    val isLocked: Boolean
)

class ObjectListPane(
    private val manager: GeometricObjectManager,
    private val undoManager: UndoManager,
    private val recorder: ConstructionRecorder,
    private val canvas: SphereCanvasPane
) : VBox() {

    val showHiddenProperty = SimpleBooleanProperty(false)
    private val tableView = TableView<ObjectListRow>()
    var onClose: (() -> Unit)? = null

    init {
        styleClass.add("sidebar-left")

        // Header
        val header = HBox()
        header.alignment = Pos.CENTER_LEFT
        header.padding = Insets(4.0, 8.0, 4.0, 8.0)
        val titleLabel = Label("Object List")
        titleLabel.styleClass.add("sidebar-header")
        HBox.setHgrow(titleLabel, Priority.ALWAYS)
        val closeBtn = Button("X")
        closeBtn.styleClass.add("delete-button")
        closeBtn.setOnAction { onClose?.invoke() }
        header.children.addAll(titleLabel, closeBtn)

        // Show hidden checkbox
        val showHiddenBox = CheckBox("Show hidden objects")
        showHiddenBox.padding = Insets(4.0, 8.0, 4.0, 8.0)
        showHiddenBox.selectedProperty().bindBidirectional(showHiddenProperty)
        showHiddenBox.selectedProperty().addListener { _, _, _ -> refresh() }

        // Table
        val idCol = TableColumn<ObjectListRow, Int>("ID")
        idCol.setCellValueFactory { javafx.beans.property.SimpleObjectProperty(it.value.objId) }
        idCol.prefWidth = 40.0
        idCol.isResizable = false

        val typeCol = TableColumn<ObjectListRow, String>("Type")
        typeCol.setCellValueFactory { SimpleStringProperty(it.value.typeName) }
        typeCol.prefWidth = 120.0
        typeCol.isResizable = false

        val parentsCol = TableColumn<ObjectListRow, String>("Parents")
        parentsCol.setCellValueFactory { SimpleStringProperty(it.value.parents) }

        val labelCol = TableColumn<ObjectListRow, String>("Label")
        labelCol.setCellValueFactory { SimpleStringProperty(it.value.label) }
        labelCol.setCellFactory {
            object : TableCell<ObjectListRow, String>() {
                override fun updateItem(item: String?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty) null else item
                    graphic = null
                }
            }
        }
        // Enable editing on double-click
        labelCol.setCellFactory {
            object : TableCell<ObjectListRow, String>() {
                private val textField = TextField()
                init {
                    textField.setOnAction {
                        val row = tableView.items[index]
                        val meta = manager.getMetadata(row.objId)
                        if (meta != null) {
                            val newLabel = textField.text.trim()
                            meta.label = if (newLabel.isEmpty()) null else newLabel
                            undoManager.checkpoint()
                            refresh()
                            canvas.render()
                        }
                        cancelEdit()
                    }
                }
                override fun updateItem(item: String?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty) {
                        text = null
                        graphic = null
                    } else if (isEditing) {
                        textField.text = item ?: ""
                        graphic = textField
                        text = null
                    } else {
                        text = item ?: ""
                        graphic = null
                    }
                }
                override fun startEdit() {
                    super.startEdit()
                    textField.text = item ?: ""
                    graphic = textField
                    text = null
                    textField.requestFocus()
                    textField.selectAll()
                }
                override fun cancelEdit() {
                    super.cancelEdit()
                    text = item ?: ""
                    graphic = null
                }
            }
        }
        labelCol.isEditable = true
        tableView.isEditable = true

        val showCol = TableColumn<ObjectListRow, Void>("Show")
        showCol.prefWidth = 55.0
        showCol.isResizable = false
        showCol.setCellFactory {
            object : TableCell<ObjectListRow, Void>() {
                override fun updateItem(item: Void?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || index < 0 || index >= tableView.items.size) {
                        graphic = null
                        return
                    }
                    val row = tableView.items[index]
                    if (row.isLocked) {
                        graphic = Label("\uD83D\uDD12") // Lock emoji
                    } else {
                        val cb = CheckBox()
                        cb.isSelected = row.visible
                        cb.setOnAction {
                            val meta = manager.getMetadata(row.objId) ?: return@setOnAction
                            meta.visible = cb.isSelected
                            if (!meta.visible) meta.labelVisible = false
                            undoManager.checkpoint()
                            refresh()
                            canvas.hitTester.rebuildIntersectionCache()
                            canvas.render()
                        }
                        graphic = cb
                    }
                }
            }
        }

        val delCol = TableColumn<ObjectListRow, Void>("Del")
        delCol.prefWidth = 40.0
        delCol.isResizable = false
        delCol.setCellFactory {
            object : TableCell<ObjectListRow, Void>() {
                override fun updateItem(item: Void?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || index < 0 || index >= tableView.items.size) {
                        graphic = null
                        return
                    }
                    val row = tableView.items[index]
                    if (row.isLocked) {
                        graphic = Label("\uD83D\uDD12")
                    } else {
                        val btn = Button("X")
                        btn.styleClass.add("delete-button")
                        btn.setOnAction {
                            val alert = Alert(Alert.AlertType.CONFIRMATION, "Delete object ${row.objId} and its descendants?")
                            alert.showAndWait().ifPresent { result ->
                                if (result == ButtonType.OK) {
                                    canvas.selectedIds.remove(row.objId)
                                    manager.removeWithDescendants(row.objId)
                                    undoManager.checkpoint()
                                    refresh()
                                    canvas.hitTester.rebuildIntersectionCache()
                                    canvas.render()
                                }
                            }
                        }
                        graphic = btn
                    }
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        tableView.columns.addAll(idCol, typeCol, parentsCol, labelCol, showCol, delCol)
        tableView.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        tableView.selectionModel.selectionMode = SelectionMode.MULTIPLE

        // Two-way selection binding
        tableView.selectionModel.selectedItems.addListener(javafx.collections.ListChangeListener { _ ->
            if (suppressSelectionSync) return@ListChangeListener
            suppressSelectionSync = true
            canvas.selectedIds.clear()
            tableView.selectionModel.selectedItems.filterNotNull().forEach { row ->
                canvas.selectedIds.add(row.objId)
            }
            suppressSelectionSync = false
            // Defer render to avoid re-entering the table during clearAndSelect:
            // render() -> onRender -> refresh() -> setAll() would clear selectedCells
            // while clearAndSelect is still firing events, causing IndexOutOfBoundsException.
            Platform.runLater { canvas.render() }
        })

        VBox.setVgrow(tableView, Priority.ALWAYS)
        children.addAll(header, showHiddenBox, tableView)
    }

    private var suppressSelectionSync = false

    fun syncSelectionFromCanvas() {
        if (suppressSelectionSync) return
        suppressSelectionSync = true
        tableView.selectionModel.clearSelection()
        for (i in tableView.items.indices) {
            if (tableView.items[i].objId in canvas.selectedIds) {
                tableView.selectionModel.select(i)
            }
        }
        suppressSelectionSync = false
    }

    fun refresh() {
        val objects = manager.getObjects()
        val metadata = manager.getMetadata()
        val showHidden = showHiddenProperty.get()

        val rows = mutableListOf<ObjectListRow>()
        for ((id, obj) in objects) {
            val meta = metadata[id] ?: continue
            if (!showHidden && !meta.visible) continue
            rows.add(ObjectListRow(
                objId = id,
                typeName = displayTypeName(obj),
                parents = obj.parentIds.joinToString(", "),
                label = meta.label ?: "",
                visible = meta.visible,
                isLocked = meta.isLocked
            ))
        }
        val wasSuppressed = suppressSelectionSync
        suppressSelectionSync = true
        tableView.items.setAll(rows)
        suppressSelectionSync = wasSuppressed
        syncSelectionFromCanvas()
    }

    companion object {
        fun displayTypeName(obj: GeometricObject): String {
            return when (obj) {
                is FreePoint -> "Free Point"
                is FreePointOnLine -> "Point on Line"
                is FreePointOnSegment -> "Point on Segment"
                is FreePointOnCircle -> "Point on Circle"
                is GeodesicGeodesicIntersection -> "Intersection Point"
                is GeodesicCircleIntersection -> "Intersection Point"
                is CircleCircleIntersection -> "Intersection Point"
                is Midpoint -> "Midpoint"
                is ReflectionPoint -> "Reflection Point"
                is Line -> "Line"
                is Segment -> "Segment"
                is PerpendicularLine -> "Perpendicular Line"
                is Circle -> "Circle"
                is Angle -> "Angle"
                is Region -> "Region"
            }
        }
    }
}

// --- Constructions Table ---

class ConstructionsPane(
    private val canvas: SphereCanvasPane
) : VBox() {

    val constructions: ObservableList<LoadedConstruction> = FXCollections.observableArrayList()
    private val tableView = TableView<LoadedConstruction>()
    var onClose: (() -> Unit)? = null

    init {
        styleClass.add("sidebar-right")

        val header = HBox()
        header.alignment = Pos.CENTER_LEFT
        header.padding = Insets(4.0, 8.0, 4.0, 8.0)
        val titleLabel = Label("Custom Constructions")
        titleLabel.styleClass.add("sidebar-header")
        HBox.setHgrow(titleLabel, Priority.ALWAYS)
        val closeBtn = Button("X")
        closeBtn.styleClass.add("delete-button")
        closeBtn.setOnAction { onClose?.invoke() }
        header.children.addAll(titleLabel, closeBtn)

        val applyCol = TableColumn<LoadedConstruction, Void>("Apply")
        applyCol.prefWidth = 60.0
        applyCol.isResizable = false
        applyCol.setCellFactory {
            object : TableCell<LoadedConstruction, Void>() {
                override fun updateItem(item: Void?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || index < 0 || index >= tableView.items.size) {
                        graphic = null
                        return
                    }
                    val construction = tableView.items[index]
                    val btn = Button("Apply")
                    btn.styleClass.add("delete-button")
                    btn.setOnAction {
                        if (construction.inputTypes.isEmpty()) {
                            // Zero inputs: apply immediately
                            canvas.applicator.apply(construction, emptyMap())
                            canvas.undoManager.checkpoint()
                            canvas.hitTester.rebuildIntersectionCache()
                            canvas.render()
                        } else {
                            canvas.startApplyMode(construction)
                        }
                    }
                    graphic = btn
                }
            }
        }

        val fileCol = TableColumn<LoadedConstruction, String>("File")
        fileCol.setCellValueFactory { SimpleStringProperty(it.value.fileName.removeSuffix(".json")) }

        val inputsCol = TableColumn<LoadedConstruction, String>("Inputs")
        inputsCol.setCellValueFactory {
            val types = it.value.inputTypes.map { t ->
                if (t == "Point" || t in listOf("FreePoint", "FreePointOnLine", "FreePointOnSegment", "FreePointOnCircle",
                        "Midpoint", "ReflectionPoint", "GeodesicGeodesicIntersection", "GeodesicCircleIntersection", "CircleCircleIntersection")) {
                    "Point"
                } else t
            }
            SimpleStringProperty(types.joinToString(", "))
        }

        val delCol = TableColumn<LoadedConstruction, Void>("Del")
        delCol.prefWidth = 40.0
        delCol.isResizable = false
        delCol.setCellFactory {
            object : TableCell<LoadedConstruction, Void>() {
                override fun updateItem(item: Void?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || index < 0 || index >= tableView.items.size) {
                        graphic = null
                        return
                    }
                    val btn = Button("X")
                    btn.styleClass.add("delete-button")
                    btn.setOnAction { constructions.removeAt(index) }
                    graphic = btn
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        tableView.columns.addAll(applyCol, fileCol, inputsCol, delCol)
        tableView.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        tableView.items = constructions

        VBox.setVgrow(tableView, Priority.ALWAYS)
        children.addAll(header, tableView)
    }
}

// --- Measurements Table ---

class MeasurementRow(val objId: Int, val typeName: String, val label: String, val value: String)

class MeasurementsPane(
    private val manager: GeometricObjectManager
) : VBox() {

    private val tableView = TableView<MeasurementRow>()
    var onClose: (() -> Unit)? = null

    init {
        styleClass.add("sidebar-right")

        val header = HBox()
        header.alignment = Pos.CENTER_LEFT
        header.padding = Insets(4.0, 8.0, 4.0, 8.0)
        val titleLabel = Label("Measurements")
        titleLabel.styleClass.add("sidebar-header")
        HBox.setHgrow(titleLabel, Priority.ALWAYS)
        val closeBtn = Button("X")
        closeBtn.styleClass.add("delete-button")
        closeBtn.setOnAction { onClose?.invoke() }
        header.children.addAll(titleLabel, closeBtn)

        val idCol = TableColumn<MeasurementRow, Int>("ID")
        idCol.setCellValueFactory { javafx.beans.property.SimpleObjectProperty(it.value.objId) }
        idCol.prefWidth = 40.0
        idCol.isResizable = false

        val typeCol = TableColumn<MeasurementRow, String>("Type")
        typeCol.setCellValueFactory { SimpleStringProperty(it.value.typeName) }
        typeCol.prefWidth = 120.0
        typeCol.isResizable = false

        val labelCol = TableColumn<MeasurementRow, String>("Label")
        labelCol.setCellValueFactory { SimpleStringProperty(it.value.label) }

        val valueCol = TableColumn<MeasurementRow, String>("Value")
        valueCol.setCellValueFactory { SimpleStringProperty(it.value.value) }

        val delCol = TableColumn<MeasurementRow, Void>("Del")
        delCol.prefWidth = 40.0
        delCol.isResizable = false
        delCol.setCellFactory {
            object : TableCell<MeasurementRow, Void>() {
                override fun updateItem(item: Void?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || index < 0 || index >= tableView.items.size) {
                        graphic = null
                        return
                    }
                    val row = tableView.items[index]
                    val btn = Button("X")
                    btn.styleClass.add("delete-button")
                    btn.setOnAction {
                        manager.getMetadata(row.objId)?.inMeasurementTable = false
                        refresh()
                    }
                    graphic = btn
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        tableView.columns.addAll(idCol, typeCol, labelCol, valueCol, delCol)
        tableView.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY

        VBox.setVgrow(tableView, Priority.ALWAYS)
        children.addAll(header, tableView)
    }

    fun refresh() {
        val objects = manager.getObjects()
        val metadata = manager.getMetadata()
        val rows = mutableListOf<MeasurementRow>()

        for ((id, obj) in objects) {
            val meta = metadata[id] ?: continue
            if (!meta.inMeasurementTable) continue
            val value = MeasurementUtils.measureLabel(obj) ?: continue
            rows.add(MeasurementRow(
                objId = id,
                typeName = ObjectListPane.displayTypeName(obj),
                label = meta.label ?: "",
                value = value
            ))
        }
        tableView.items.setAll(rows)
    }
}
