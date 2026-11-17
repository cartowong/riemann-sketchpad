package riemannsketchpad.model

data class ObjectMetadata(
    var visible: Boolean = true,
    var label: String? = null,
    var labelVisible: Boolean = false,
    var labelOffsetX: Double = 10.0,
    var labelOffsetY: Double = -10.0,
    var inMeasurementTable: Boolean = false,
    var labelAnchorAngle: Double = 0.0,
    var labelAnchorT: Double = 0.5,
    var isInput: Boolean = false,
    var isLocked: Boolean = false,
    // Style fields
    var color: String = "black",
    var size: String = "medium",
    var strokeStyle: String = "solid",
    var strokeThickness: String = "normal"
) {
    fun deepCopy(): ObjectMetadata = copy()
}
