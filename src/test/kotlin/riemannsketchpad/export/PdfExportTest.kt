package riemannsketchpad.export

import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import riemannsketchpad.APP_VERSION
import riemannsketchpad.geometry.FreePoint
import riemannsketchpad.geometry.SphericalUtils
import riemannsketchpad.model.GeometricObjectManager
import riemannsketchpad.model.ObjectMetadata
import java.awt.image.BufferedImage
import java.io.File

class PdfExportTest {

    @TempDir
    lateinit var tempDir: File

    private fun export(case: ExportCase, name: String = case.name): Pair<File, Set<String>> {
        val file = File(tempDir, "$name.pdf")
        val unsupported = ExportRenderer.exportPdf(
            file, case.manager, case.viewMatrix, case.displayScale, case.centerX, case.centerY,
            case.width, case.height, case.transparent, case.includeGrid, case.includeBackFace
        )
        return file to unsupported
    }

    private fun case(name: String) = ExportTestDiagrams.cases().first { it.name == name }

    private fun render(file: File): BufferedImage =
        Loader.loadPDF(file).use { PDFRenderer(it).renderImage(0, 1f, ImageType.ARGB) }

    @Test
    fun `every test diagram exports to a single page of exactly W x H points`() {
        for (case in ExportTestDiagrams.cases()) {
            val (file, unsupported) = export(case)
            assertTrue(unsupported.isEmpty())
            Loader.loadPDF(file).use { doc ->
                assertEquals(1, doc.numberOfPages, case.name)
                val box = doc.getPage(0).mediaBox
                assertEquals(0f, box.lowerLeftX, case.name)
                assertEquals(0f, box.lowerLeftY, case.name)
                assertEquals(case.width.toFloat(), box.width, case.name)
                assertEquals(case.height.toFloat(), box.height, case.name)
            }
        }
    }

    @Test
    fun `metadata records creator and title`() {
        val (file, _) = export(case("basic_front_grid_back"), "my diagram")
        Loader.loadPDF(file).use { doc ->
            assertEquals("Riemann Sketchpad $APP_VERSION", doc.documentInformation.creator)
            assertEquals("my diagram", doc.documentInformation.title)
        }
    }

    @Test
    fun `visible front-face labels are extractable text`() {
        val (file, _) = export(case("basic_front_grid_back"))
        val text = Loader.loadPDF(file).use { PDFTextStripper().getText(it) }
        for (label in listOf("A", "B", "C&<>", "l", "c")) {
            assertTrue(text.lines().any { it.trim() == label }, "label '$label' missing from: $text")
        }
        // D is on the back face, and labels are only drawn on the front face
        assertTrue(text.lines().none { it.trim() == "D" })
    }

    @Test
    fun `labels are drawn upright at their anchor`() {
        val (file, _) = export(case("basic_front_grid_back"))
        val positions = mutableListOf<org.apache.pdfbox.text.TextPosition>()
        Loader.loadPDF(file).use { doc ->
            object : PDFTextStripper() {
                override fun writeString(text: String, textPositions: MutableList<org.apache.pdfbox.text.TextPosition>) {
                    positions.addAll(textPositions)
                }
            }.getText(doc)
        }
        // SVG golden places "A" at x=393.55, y=234.30 (baseline, Y down)
        val a = positions.first { it.unicode == "A" }
        assertEquals(393.55f, a.xDirAdj, 0.05f)
        assertEquals(234.30f, a.yDirAdj, 0.05f)
        assertEquals(0f, a.dir)
    }

    private fun emptyCase(transparent: Boolean) =
        ExportCase("empty", GeometricObjectManager(), SphericalUtils.viewMatrix(0.0, 0.0), 400, 300, transparent, false, false)

    @Test
    fun `opaque export draws gray exterior and white disk`() {
        val image = render(export(emptyCase(transparent = false)).first)
        assertEquals(0xFFE8E8E8.toInt(), image.getRGB(5, 5))
        assertEquals(0xFFFFFFFF.toInt(), image.getRGB(200, 150))
    }

    @Test
    fun `transparent export draws no background`() {
        val image = render(export(emptyCase(transparent = true)).first)
        assertEquals(0, image.getRGB(5, 5) ushr 24)
        assertEquals(0, image.getRGB(200, 150) ushr 24)
    }

    @Test
    fun `back face honors includeBackFace`() {
        // The special diagram has a point only on the back face at (272.28, 227.72)
        val withBack = render(export(case("special_front_back")).first)
        val withoutBack = render(export(case("special_front_transparent_noback")).first)
        assertTrue(withBack.getRGB(272, 228) != 0xFFFFFFFF.toInt())
        assertEquals(0, withoutBack.getRGB(272, 228) ushr 24)
    }

    @Test
    fun `characters missing from the font become question marks and are reported`() {
        val manager = GeometricObjectManager()
        manager.addObject(FreePoint(0, 0.0, 0.0, 1.0), ObjectMetadata(label = "P日한", labelVisible = true))
        manager.recomputeAll()
        val case = ExportCase("cjk", manager, SphericalUtils.viewMatrix(0.0, 0.0), 300, 300, false, false, false)
        val (file, unsupported) = export(case)
        assertEquals(setOf("日", "한"), unsupported)
        val text = Loader.loadPDF(file).use { PDFTextStripper().getText(it) }
        assertEquals("P??", text.trim())
    }
}
