package dev.deeptelar.telar.demo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Pixel art as text: one character per pixel, `.` is empty. */
class Sprite(vararg val rows: String) {
    val width: Int = rows.maxOf { it.length }
    val height: Int = rows.size
}

object Sprites {
    /** `c` crust, `y` cheese, `r` pepperoni. */
    val slice = Sprite(
        "ccccccccccccc",
        "ccccccccccccc",
        ".yyyyyyyyyyy.",
        ".yyrryyyyyyy.",
        "..yrryyyrry..",
        "..yyyyyyrry..",
        "...yyyyyyy...",
        "...yyrryyy...",
        "....yrryy....",
        "....yyyyy....",
        ".....yyy.....",
        ".....yyy.....",
        "......y......",
    )

    /** A whole pizza, seen from above, at the size of a status light. */
    val pie = Sprite(
        "..ccc..",
        ".cyyrc.",
        "cyryyyc",
        "cyyyryc",
        "cyryyyc",
        ".cyyyc.",
        "..ccc..",
    )

    /** A medal on its ribbon. `r` is the ribbon, and the disc is `y` with `c` in its middle. */
    val medal = Sprite(
        "rr...rr",
        ".rr.rr.",
        "..rrr..",
        "..yyy..",
        ".yyyyy.",
        ".yycyy.",
        ".yyyyy.",
        "..yyy..",
    )

    /** The slice at the size of a status light. */
    val miniSlice = Sprite(
        "ccccccc",
        "yyryyyy",
        ".yyyry.",
        ".yyyyy.",
        "..yry..",
        "..yyy..",
        "...y...",
    )
}

/**
 * Draws [sprite] with square pixels of [pixel].
 *
 * @param tint when given, every pixel gets this colour, which turns the sprite into a silhouette.
 */
@Composable
fun PixelArt(sprite: Sprite, modifier: Modifier = Modifier, pixel: Dp = 4.dp, tint: Color = Color.Unspecified) {
    Canvas(modifier.size(pixel * sprite.width, pixel * sprite.height)) {
        val side = pixel.toPx()
        sprite.rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, char ->
                val color = when {
                    char == '.' -> return@forEachIndexed
                    tint != Color.Unspecified -> tint
                    char == 'c' -> SpriteColors.crust
                    char == 'r' -> SpriteColors.pepperoni
                    else -> SpriteColors.cheese
                }
                // A pixel is drawn a hair larger than its cell so that neighbours leave no seam.
                drawRect(color, Offset(x * side, y * side), Size(side + 0.5f, side + 0.5f))
            }
        }
    }
}
