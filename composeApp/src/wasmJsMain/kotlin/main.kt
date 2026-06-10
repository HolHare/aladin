import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.aladin.app.App
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Mount into a fixed full-viewport div so the canvas gets 100vh height
    val root = document.createElement("div")
    root.setAttribute(
        "style",
        "position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; overflow: hidden;"
    )
    document.body!!.appendChild(root)
    ComposeViewport(root) {
        App()
    }
}
