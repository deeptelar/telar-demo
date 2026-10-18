package dev.deeptelar.telar.demo

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.deeptelar.telar.demo.ui.App
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // The loading text of index.html. Without it, the page also knows that the game has started.
    document.getElementById("boot")?.remove()
    ComposeViewport(document.body!!) {
        App()
    }
}
