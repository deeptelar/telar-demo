package dev.deeptelar.telar.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import dev.deeptelar.telar.demo.resources.GeistMono_Medium
import dev.deeptelar.telar.demo.resources.GeistMono_Regular
import dev.deeptelar.telar.demo.resources.Res

/**
 * The colours of the game: black, white, a ramp of greys and one red, after nothing.tech.
 * Yellow is kept for the cheese and for a run that is waiting at a save point.
 */
@Immutable
class Palette(
    /** The page. */
    val ground: Color,
    /** A raised surface: a selected row, a text field. */
    val panel: Color,
    /** Borders, idle tiles and the dots of an unlit display. */
    val line: Color,
    /** Main text and everything that is lit. */
    val ink: Color,
    /** Secondary text. */
    val dim: Color,
    /** The one accent: the node that runs now, errors, the primary action. */
    val red: Color,
    /** A run waiting for the player. */
    val yellow: Color,
) {
    /** Text on a [red] or [ink] fill. */
    val onRed: Color = Color.White
}

private val Dark = Palette(
    ground = Color(0xFF040404),
    panel = Color(0xFF181818),
    line = Color(0xFF3C3C3C),
    ink = Color(0xFFFFFFFF),
    dim = Color(0xFF9B9D9D),
    red = Color(0xFFE5243B),
    yellow = Color(0xFFFFC700),
)

private val Light = Palette(
    ground = Color(0xFFFFFFFF),
    panel = Color(0xFFF4F4F4),
    line = Color(0xFFCBCCCC),
    ink = Color(0xFF040404),
    dim = Color(0xFF6F7070),
    red = Color(0xFFC8102E),
    yellow = Color(0xFFB88A00),
)

/** Colours of the pixel art. They are the same on a dark and on a light page. */
object SpriteColors {
    val cheese: Color = Color(0xFFFFC700)
    val crust: Color = Color(0xFFC98A1B)
    val pepperoni: Color = Color(0xFFC8102E)
    val paper: Color = Color(0xFFFFFFFF)

    /** What is red in a sprite, when the sprite stands on red. */
    val shadow: Color = Color(0xFF040404)
}

private val LocalPalette = staticCompositionLocalOf { Dark }
private val LocalMono = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

object Theme {
    val colors: Palette
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current

    /** Running text. */
    val body: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(fontFamily = LocalMono.current, fontSize = 14.sp, lineHeight = 21.sp)

    /** Small capitals for labels, buttons and tiles. Pass the text in upper case. */
    val label: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(
            fontFamily = LocalMono.current,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.1.em,
        )
}

@Composable
fun PizzaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val mono = FontFamily(
        Font(Res.font.GeistMono_Regular, FontWeight.Normal),
        Font(Res.font.GeistMono_Medium, FontWeight.Medium),
    )
    val palette = if (dark) Dark else Light
    CompositionLocalProvider(LocalPalette provides palette, LocalMono provides mono) {
        Box(Modifier.fillMaxSize().background(palette.ground)) { content() }
    }
}

/** Running text in the game's typeface. */
@Composable
fun Body(text: String, modifier: Modifier = Modifier, color: Color = Theme.colors.ink) {
    BasicText(text, modifier, Theme.body.copy(color = color))
}

/** A small upper-case label. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Theme.colors.dim) {
    BasicText(text.uppercase(), modifier, Theme.label.copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis)
}
