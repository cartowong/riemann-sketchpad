package riemannsketchpad.model

object StyleUtils {
    val COLOR_MAP: Map<String, String> = mapOf(
        "black" to "#000000",
        "brown" to "#795548",
        "green" to "#4CAF50",
        "purple" to "#9C27B0",
        "red" to "#F44336",
        "yellow" to "#FFEB3B"
    )

    val POINT_SIZE_MAP: Map<String, Double> = mapOf(
        "small" to 3.0,
        "medium" to 4.0,
        "large" to 6.0
    )

    val ANGLE_ARC_RADIUS_MAP: Map<String, Double> = mapOf(
        "small" to 20.0,
        "medium" to 25.0,
        "large" to 30.0
    )

    val STROKE_THICKNESS_MAP: Map<String, Double> = mapOf(
        "thin" to 1.0,
        "normal" to 2.0,
        "thick" to 3.0
    )

    fun colorHex(name: String): String = COLOR_MAP[name] ?: "#000000"
    fun pointRadius(size: String): Double = POINT_SIZE_MAP[size] ?: 4.0
    fun angleArcRadius(size: String): Double = ANGLE_ARC_RADIUS_MAP[size] ?: 25.0
    fun strokeWidth(thickness: String): Double = STROKE_THICKNESS_MAP[thickness] ?: 2.0
}
