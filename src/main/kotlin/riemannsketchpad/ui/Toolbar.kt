package riemannsketchpad.ui

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox

class Toolbar(
    private val onToolSelected: (String) -> Unit
) : HBox() {

    private val toggleGroup = ToggleGroup()
    private val buttons = mutableMapOf<String, ToggleButton>()

    private val toolNames = listOf("Select", "Point", "Segment", "Line", "Circle", "Angle", "Region", "Label")
    private val iconNames = listOf("select", "point", "segment", "line", "circle", "angle", "region", "label")

    init {
        styleClass.add("toolbar")
        spacing = 4.0
        padding = Insets(4.0)
        alignment = Pos.CENTER_LEFT

        for (i in toolNames.indices) {
            val name = toolNames[i]
            val iconName = iconNames[i]
            val button = createToolButton(name, iconName)
            buttons[name] = button
            children.add(button)
        }

        // Default: Select tool active
        buttons["Select"]?.isSelected = true
    }

    private fun createToolButton(name: String, iconName: String): ToggleButton {
        val button = ToggleButton()
        button.toggleGroup = toggleGroup

        // Try to load icon
        val iconUrl = javaClass.getResource("/icons/$iconName.png")
        if (iconUrl != null) {
            val imageView = ImageView(Image(iconUrl.toExternalForm(), 24.0, 24.0, true, true))
            button.graphic = imageView
        } else {
            button.text = name.take(3)
        }

        button.prefWidth = 32.0
        button.prefHeight = 32.0
        button.styleClass.add("tool-button")

        button.selectedProperty().addListener { _, _, isSelected ->
            if (isSelected) {
                button.style = "-fx-border-color: #333333; -fx-border-width: 2.5; -fx-background-color: #E0E0E0;"
                onToolSelected(name)
            } else {
                button.style = "-fx-background-color: #E0E0E0; -fx-border-color: transparent; -fx-border-width: 2;"
            }
        }

        // Initial style
        button.style = "-fx-background-color: #E0E0E0; -fx-border-color: transparent; -fx-border-width: 2;"

        // Prevent deselection (always keep one selected)
        button.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED) { e ->
            if (button.isSelected) e.consume()
        }

        return button
    }

    fun selectTool(name: String) {
        buttons[name]?.isSelected = true
    }
}
