package dev.deeptelar.telar.demo.ui

import kotlinx.browser.window

actual fun pageAddress(): String? = window.location.origin + window.location.pathname

actual fun pageFragment(): String? = window.location.hash.removePrefix("#").ifEmpty { null }
