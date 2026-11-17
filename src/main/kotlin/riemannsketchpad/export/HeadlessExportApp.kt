package riemannsketchpad.export

import javafx.application.Application
import javafx.application.Platform
import javafx.stage.Stage
import riemannsketchpad.geometry.SphericalUtils
import riemannsketchpad.model.GeometricObjectManager
import riemannsketchpad.model.ManagerState
import riemannsketchpad.persistence.DiagramSerializer
import java.io.File

object HeadlessExportApp {

    fun run(args: Array<String>) {
        if (args.size < 2) {
            System.err.println("Usage: --export <diagram.json> [outputDir]")
            System.exit(1)
            return
        }

        val diagramPath = args[1]
        val outputDir = if (args.size > 2) args[2] else "exports"

        val diagramFile = File(diagramPath)
        if (!diagramFile.exists()) {
            System.err.println("File not found: $diagramPath")
            System.exit(1)
            return
        }

        // JavaFX must be initialized for snapshot
        // Launch a minimal JavaFX app that does the export then exits
        HeadlessApp.diagramFile = diagramFile
        HeadlessApp.outputDir = File(outputDir)
        Application.launch(HeadlessApp::class.java)
    }
}

class HeadlessApp : Application() {
    companion object {
        lateinit var diagramFile: File
        lateinit var outputDir: File
    }

    override fun start(stage: Stage) {
        try {
            outputDir.mkdirs()

            val json = diagramFile.readText()
            val manager = GeometricObjectManager()
            DiagramSerializer.deserialize(json, manager)

            val viewMatrix = SphericalUtils.viewMatrix(0.0, 0.0)
            val baseName = diagramFile.nameWithoutExtension

            // Canvas export: 700px diameter
            val canvasDiam = 700
            val canvasScale = canvasDiam / 2.0
            val canvasCenter = canvasDiam / 2.0

            ExportRenderer.exportPng(
                File(outputDir, "${baseName}_canvas.png"),
                manager, viewMatrix, canvasScale, canvasCenter, canvasCenter,
                canvasDiam, canvasDiam, false, false
            )
            ExportRenderer.exportSvg(
                File(outputDir, "${baseName}_canvas.svg"),
                manager, viewMatrix, canvasScale, canvasCenter, canvasCenter,
                canvasDiam, canvasDiam, false, false
            )

            // Custom export: 1920px diameter
            val customDiam = 1920
            val customScale = customDiam / 2.0
            val customCenter = customDiam / 2.0

            ExportRenderer.exportPng(
                File(outputDir, "${baseName}_custom.png"),
                manager, viewMatrix, customScale, customCenter, customCenter,
                customDiam, customDiam, false, false
            )
            ExportRenderer.exportSvg(
                File(outputDir, "${baseName}_custom.svg"),
                manager, viewMatrix, customScale, customCenter, customCenter,
                customDiam, customDiam, false, false
            )

            println("Export complete: ${outputDir.absolutePath}")
        } catch (e: Exception) {
            System.err.println("Export failed: ${e.message}")
            e.printStackTrace()
        } finally {
            Platform.exit()
        }
    }
}
