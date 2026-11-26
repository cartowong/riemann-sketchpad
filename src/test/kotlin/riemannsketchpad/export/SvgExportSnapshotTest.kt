package riemannsketchpad.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.util.Locale

/**
 * Compares SVG export against golden files in src/test/resources/export/svg.
 * To regenerate the goldens, run the tests with UPDATE_GOLDENS=true.
 */
class SvgExportSnapshotTest {

    @TestFactory
    fun `svg export matches golden files`(): List<DynamicTest> {
        // The SVG writer formats numbers with the default locale.
        Locale.setDefault(Locale.US)
        val update = System.getenv("UPDATE_GOLDENS") == "true"
        return ExportTestDiagrams.cases().map { case ->
            DynamicTest.dynamicTest(case.name) {
                // Kept under build/ (not a temp dir) so CI can upload them when a snapshot differs.
                val out = File("build/snapshot-actual/${case.name}.svg").also { it.parentFile.mkdirs() }
                ExportRenderer.exportSvg(
                    out, case.manager, case.viewMatrix, case.displayScale, case.centerX, case.centerY,
                    case.width, case.height, case.transparent, case.includeGrid, case.includeBackFace
                )
                val actual = out.readText()
                val golden = File("src/test/resources/export/svg/${case.name}.svg")
                if (update) {
                    golden.parentFile.mkdirs()
                    golden.writeText(actual)
                }
                check(golden.exists()) { "Missing golden file for ${case.name}; run with UPDATE_GOLDENS=true" }
                assertEquals(golden.readText(), actual)
            }
        }
    }
}
