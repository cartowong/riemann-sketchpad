package riemannsketchpad

const val APP_NAME = "RiemannSketchpad"
const val APP_VERSION = "1.0.0"

fun main(args: Array<String>) {
    if (args.isNotEmpty() && args[0] == "--export") {
        riemannsketchpad.export.HeadlessExportApp.run(args)
    } else {
        javafx.application.Application.launch(riemannsketchpad.ui.SketchpadApp::class.java, *args)
    }
}
