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
import dev.deeptelar.telar.demo.kitchen.Blueprint
import dev.deeptelar.telar.demo.kitchen.PieceKind

/**
 * Pixel art as text: one character per pixel, `.` is empty. `c` is crust, `y` cheese and `r` the red
 * of the pepperoni. `w` is the ink of the page and `d` its grey, so that a drawing in lines can be
 * seen on a dark and on a light page, and `p` is paper, which is white on both.
 */
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

    // The tiles of the bonus stage's board, each twelve pixels wide and high.

    /** The rail the orders hang on: `START` of the kitchen's graph. */
    val rail = Sprite(
        "dddddddddddd",
        "dddddddddddd",
        ".www.www.www",
        ".wdw.wrw.wdw",
        ".www.www.www",
        ".wdw.wdw.wdw",
        ".www.www.www",
        ".wdw.....wdw",
        ".www.....www",
        ".........wdw",
        ".........www",
        "............",
    )

    /** The hat of the chef who makes what the tickets say: `END` of the kitchen's graph. */
    val chefHat = Sprite(
        "..ww.ww.ww..",
        ".wppwppwppw.",
        "wppppppppppw",
        "wppppppppppw",
        "wppppppppppw",
        ".wppppppppw.",
        "..wppppppw..",
        "..wpdppdpw..",
        "..wpdppdpw..",
        "..wddddddw..",
        "..wppppppw..",
        "..wwwwwwww..",
    )

    /** The menu, where the lookup finds the name of a pizza. */
    val menu = Sprite(
        ".wwwwwwwwww.",
        ".w........w.",
        ".w...yy...w.",
        ".w..yryy..w.",
        ".w..yyry..w.",
        ".w...yy...w.",
        ".w........w.",
        ".w.dddddd.w.",
        ".w........w.",
        ".w.dddd...w.",
        ".w........w.",
        ".wwwwwwwwww.",
    )

    /** A sorter is the diamond of a decision. This one asks whether an order is a pizza of the menu and nothing more. */
    val sorterMenu = Sprite(
        ".....ww.....",
        "....w..w....",
        "...w....w...",
        "..w......w..",
        ".w...yy...w.",
        "w...yryy...w",
        "w...yyry...w",
        ".w...yy...w.",
        "..w......w..",
        "...w....w...",
        "....w..w....",
        ".....ww.....",
    )

    /** The sorter that asks whether an order is about something said earlier: its arrow points back. */
    val sorterEarlier = Sprite(
        ".....ww.....",
        "....w..w....",
        "...w....w...",
        "..w......w..",
        ".w..r.....w.",
        "w..rrrrrr..w",
        "w..rrrrrr..w",
        ".w..r.....w.",
        "..w......w..",
        "...w....w...",
        "....w..w....",
        ".....ww.....",
    )

    /** A model that reads an order. */
    val robot = Sprite(
        ".....rr.....",
        ".....rr.....",
        ".....dd.....",
        ".wwwwwwwwww.",
        ".w........w.",
        "dw.yy..yy.wd",
        "dw.yy..yy.wd",
        ".w........w.",
        ".w..dddd..w.",
        ".w........w.",
        ".wwwwwwwwww.",
        "............",
    )

    /** The book of everything the guests said, which the second reader reads with every order. */
    val book = Sprite(
        "............",
        ".wwww..wwww.",
        "w....ww....w",
        "w.dd.ww.dd.w",
        "w....ww....w",
        "w.dd.ww.dd.w",
        "w....ww....w",
        "w.dd.ww.dd.w",
        "w....ww....w",
        "wwwwwrrwwwww",
        ".....rr.....",
        ".....r......",
    )

    /** The watch of the expediter, who writes on a ticket when it is due. */
    val stopwatch = Sprite(
        "....rrrr....",
        ".....ww.....",
        "...wwwwww...",
        "..ww....ww..",
        ".ww..r...ww.",
        ".w...r....w.",
        ".w...rrr..w.",
        ".w........w.",
        ".ww......ww.",
        "..ww....ww..",
        "...wwwwww...",
        "............",
    )

    /** An oven with its fire. */
    val oven = Sprite(
        "....wwww....",
        "..wwwwwwww..",
        ".wwwwwwwwww.",
        ".www....www.",
        "www......www",
        "ww...y....ww",
        "ww..yry...ww",
        "ww..yrry..ww",
        "ww.yrrrry.ww",
        "wwwwwwwwwwww",
        "dddddddddddd",
        ".dd......dd.",
    )
}

/** The picture of a station of the bonus stage. */
val PieceKind.sprite: Sprite
    get() =
        when (this) {
            PieceKind.LOOKUP -> Sprites.menu
            PieceKind.ONLY_MENU -> Sprites.sorterMenu
            PieceKind.LOOKS_BACK -> Sprites.sorterEarlier
            PieceKind.READER -> Sprites.robot
            PieceKind.BOOK_READER -> Sprites.book
            PieceKind.EXPEDITER -> Sprites.stopwatch
            PieceKind.SECOND_OVEN -> Sprites.oven
        }

/** The picture of the tile of [id]: a station, the rail or the kitchen. */
fun Blueprint.sprite(id: String): Sprite? =
    when (id) {
        Blueprint.RAIL -> Sprites.rail
        Blueprint.KITCHEN -> Sprites.chefHat
        else -> piece(id)?.kind?.sprite
    }

/** The picture of who did something in a shift, by the name the log gives it: a station of the board, or the kitchen behind it. */
fun stationSprite(name: String): Sprite =
    PieceKind.entries.firstOrNull { it.tile == name }?.sprite ?: if (name.startsWith("oven")) Sprites.oven else Sprites.chefHat

/**
 * Draws [sprite] with square pixels of [pixel].
 *
 * @param tint when given, every pixel gets this colour, which turns the sprite into a silhouette.
 * @param onRed `true` when the sprite stands on a red fill. Its lines are white there, and its red is black.
 */
@Composable
fun PixelArt(sprite: Sprite, modifier: Modifier = Modifier, pixel: Dp = 4.dp, tint: Color = Color.Unspecified, onRed: Boolean = false) {
    val colors = Theme.colors
    val ink = if (onRed) colors.onRed else colors.ink
    val grey = if (onRed) colors.onRed.copy(alpha = 0.6f) else colors.dim
    Canvas(modifier.size(pixel * sprite.width, pixel * sprite.height)) {
        val side = pixel.toPx()
        sprite.rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, char ->
                val color = when {
                    char == '.' -> return@forEachIndexed
                    tint != Color.Unspecified -> tint
                    char == 'c' -> SpriteColors.crust
                    char == 'r' -> if (onRed) SpriteColors.shadow else SpriteColors.pepperoni
                    char == 'w' -> ink
                    char == 'd' -> grey
                    char == 'p' -> SpriteColors.paper
                    else -> SpriteColors.cheese
                }
                // A pixel is drawn a hair larger than its cell so that neighbours leave no seam.
                drawRect(color, Offset(x * side, y * side), Size(side + 0.5f, side + 0.5f))
            }
        }
    }
}
