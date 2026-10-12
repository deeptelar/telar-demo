package dev.deeptelar.telar.demo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.deeptelar.telar.demo.game.Cell
import dev.deeptelar.telar.demo.kitchen.Arrow
import dev.deeptelar.telar.demo.kitchen.Blueprint
import dev.deeptelar.telar.demo.kitchen.Exit
import kotlin.math.max

// The measures of the kitchen's board in dp. Lying, the rail is on the left, the kitchen on the right,
// and between them are two columns of places for stations. Upright, which is how a narrow screen
// shows it, the rail is at the top, the kitchen at the bottom, and the places are two rows between.
private const val TILE_WIDTH = 84f
private const val TILE_HEIGHT = 48f
private const val GAP_X = 38f
private const val GAP_Y = 30f
private const val GAP_UPRIGHT = 24f
private const val MARGIN_X = 6f
private const val MARGIN_Y = 18f
private const val SPRITE_PIXEL = 2f
private const val COLUMNS = 4
private const val SLOT_COLUMNS = 2
private const val ROWS = Blueprint.SLOTS / SLOT_COLUMNS

/** How far from the middle of a side the yes arrow and the no arrow of a sorter leave, so that they do not lie on each other. */
private const val SPREAD = 9f

/** The smallest size the board is drawn at, as a part of its full size. */
private const val MIN_SCALE = 0.6f

// A cell says how far along the way of an order a tile is, as its column, and where across it, as its row.
private val RAIL_CELL = Cell(0, ROWS / 2)
private val KITCHEN_CELL = Cell(COLUMNS - 1, ROWS / 2)

private fun slotCell(slot: Int): Cell = Cell(1 + slot % SLOT_COLUMNS, slot / SLOT_COLUMNS)

/** Where the tile of [id] is on the board. */
internal fun Blueprint.cell(id: String): Cell? =
    when (id) {
        Blueprint.RAIL -> RAIL_CELL
        Blueprint.KITCHEN -> KITCHEN_CELL
        else -> piece(id)?.let { slotCell(it.slot) }
    }

/**
 * Where the tiles and the arrows of the board are, in dp. The arrows are laid out for a board that
 * lies, and an [upright] one is the same board with left and top swapped.
 */
internal class BoardLayout(private val upright: Boolean) {
    private val along = if (upright) TILE_HEIGHT else TILE_WIDTH
    private val across = if (upright) TILE_WIDTH else TILE_HEIGHT
    private val gapAlong = if (upright) GAP_UPRIGHT else GAP_X
    private val marginAlong = if (upright) MARGIN_Y else MARGIN_X
    private val marginAcross = if (upright) MARGIN_X else MARGIN_Y

    // Above an upright board is the number of the order that is on it. Under it nothing needs room.
    private val length = marginAlong + MARGIN_X + COLUMNS * (along + gapAlong) - gapAlong
    private val breadth = marginAcross * 2 + ROWS * (across + GAP_Y) - GAP_Y

    val width: Float = if (upright) breadth else length
    val height: Float = if (upright) length else breadth

    private fun lying(cell: Cell): Rect =
        Rect(Offset(marginAlong + cell.column * (along + gapAlong), marginAcross + cell.row * (across + GAP_Y)), Size(along, across))

    private fun Offset.turned(): Offset = if (upright) Offset(y, x) else this

    fun tile(cell: Cell): Rect = lying(cell).let { if (upright) Rect(it.top, it.left, it.bottom, it.right) else it }

    /**
     * The corners of an arrow from one tile to another.
     *
     * On a board that lies, an arrow to the next column leaves on the right and turns in the gap
     * before its target. One that skips a column runs through the gap between two rows, so that it
     * does not pass behind a tile and look as if it ended there. An arrow in its own column goes
     * straight up or down, and an arrow to an earlier column runs back under its row.
     */
    fun wire(from: Cell, to: Cell, exit: Exit): List<Offset> {
        val a = lying(from)
        val b = lying(to)
        val shift =
            when (exit) {
                Exit.YES -> -SPREAD
                Exit.NO -> SPREAD
                Exit.NEXT -> 0f
            }
        val corners =
            when {
                to.column > from.column -> {
                    val start = Offset(a.right, a.center.y + shift)
                    // Arrows from different rows arrive at different heights of the tile.
                    val end = Offset(b.left, b.center.y + ((from.row - to.row) * SPREAD).coerceIn(-SPREAD, SPREAD))
                    val turn = b.left - gapAlong / 2 + shift / 2
                    when {
                        to.column == from.column + 1 && start.y == end.y -> listOf(start, end)
                        to.column == from.column + 1 -> listOf(start, Offset(turn, start.y), Offset(turn, end.y), end)
                        else -> {
                            val out = a.right + gapAlong / 2 + shift / 2
                            val lane = if (to.row < from.row) a.top - GAP_Y / 2 else a.bottom + GAP_Y / 2
                            listOf(start, Offset(out, start.y), Offset(out, lane), Offset(turn, lane), Offset(turn, end.y), end)
                        }
                    }
                }
                to.column == from.column -> {
                    val x = a.center.x + shift
                    if (to.row > from.row) listOf(Offset(x, a.bottom), Offset(x, b.top)) else listOf(Offset(x, a.top), Offset(x, b.bottom))
                }
                else -> {
                    val lane = max(a.bottom, b.bottom) + GAP_Y / 2
                    listOf(Offset(a.center.x + shift, a.bottom), Offset(a.center.x + shift, lane), Offset(b.center.x, lane), Offset(b.center.x, b.bottom))
                }
            }
        return corners.map { it.turned() }
    }
}

/** How wide the word of a sorter's arrow is at most. */
private const val WORD_WIDTH = 24f

/** Where the word of a sorter's arrow stands: beside the place the arrow leaves the tile. */
private fun label(corners: List<Offset>, exit: Exit): Offset {
    val start = corners.first()
    val next = corners[1]
    return if (next.y == start.y) {
        Offset(if (next.x > start.x) start.x + 3 else start.x - 3 - WORD_WIDTH, if (exit == Exit.YES) start.y - 14 else start.y + 1)
    } else {
        Offset(if (exit == Exit.YES) start.x - 2 - WORD_WIDTH else start.x + 5, if (next.y > start.y) start.y + 2 else start.y - 15)
    }
}

/**
 * The board of the bonus stage: the tiles of the stations the player placed, the empty places, and
 * the arrows. Pressing a tile or a place tells the controller, which decides what that means.
 */
@Composable
fun KitchenBoard(controller: KitchenController, modifier: Modifier = Modifier) {
    val colors = Theme.colors
    // A narrow screen has room for three tiles side by side, so the board stands there.
    val upright = LocalCompact.current
    val layout = remember(upright) { BoardLayout(upright) }
    val editing = !controller.running
    // While a shift runs the board is the one the shift started on.
    val board = if (editing) controller.board else controller.playing
    val width = layout.width
    val height = layout.height
    val arrows = board.arrows.mapNotNull { arrow -> layout.wired(board, arrow) }
    val description =
        "Board: " + board.arrows.joinToString(", ") { "${it.from}${if (it.exit == Exit.NEXT) "" else " ${it.exit.word}"} to ${it.to}" }.ifEmpty { "no arrows" }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val scale = (maxWidth.value / width).coerceIn(MIN_SCALE, 1f)
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            Box(Modifier.size(width.dp, height.dp).semantics { contentDescription = description }) {
                Canvas(Modifier.size(width.dp, height.dp)) {
                    arrows.forEach { (arrow, corners) ->
                        val lit = editing && arrow.from == controller.selected
                        arrow(corners, if (lit) colors.ink else colors.dim.copy(alpha = 0.7f), lit)
                    }
                }
                arrows.filter { (arrow, _) -> arrow.exit != Exit.NEXT }.forEach { (arrow, corners) ->
                    val at = label(corners, arrow.exit)
                    Label(arrow.exit.word, Modifier.offset(at.x.dp, at.y.dp), color = if (arrow.exit == Exit.YES) colors.ink else colors.dim)
                }

                val drawing = editing && controller.armed != null
                PlaceTile("rail", "The rail", look(controller, Blueprint.RAIL, drawing = false), layout.tile(RAIL_CELL), Sprites.rail, terminal = true) {
                    controller.tap(Blueprint.RAIL)
                }
                PlaceTile("kitchen", "The kitchen", look(controller, Blueprint.KITCHEN, drawing), layout.tile(KITCHEN_CELL), Sprites.chefHat, terminal = true) {
                    controller.tap(Blueprint.KITCHEN)
                }
                repeat(Blueprint.SLOTS) { slot ->
                    val piece = board.at(slot)
                    if (piece == null) {
                        val look = if (editing && controller.picking == slot) PlaceLook.Selected else PlaceLook.Empty
                        PlaceTile(if (editing) "+" else "", "Place ${slot + 1}", look, layout.tile(slotCell(slot)), enabled = editing) { controller.tapSlot(slot) }
                    } else {
                        PlaceTile(piece.kind.tile, "Station ${piece.id}", look(controller, piece.id, drawing), layout.tile(slotCell(slot)), piece.kind.sprite) {
                            controller.tap(piece.id)
                        }
                    }
                }
                if (!editing) {
                    controller.reading?.let { order -> Label("order #$order", Modifier.offset(MARGIN_X.dp, 0.dp), color = colors.red) }
                }
            }
        }
    }
}

private fun BoardLayout.wired(board: Blueprint, arrow: Arrow): Pair<Arrow, List<Offset>>? {
    val from = board.cell(arrow.from) ?: return null
    val to = board.cell(arrow.to) ?: return null
    return arrow to wire(from, to, arrow.exit)
}

private enum class PlaceLook { Idle, Empty, Selected, Target, Running }

/** How the tile of [id] looks: at work in a shift, selected, or offered as the end of the arrow that is being drawn. */
private fun look(controller: KitchenController, id: String, drawing: Boolean): PlaceLook =
    when {
        controller.running -> if (controller.at == id) PlaceLook.Running else PlaceLook.Idle
        controller.selected == id -> PlaceLook.Selected
        drawing -> PlaceLook.Target
        else -> PlaceLook.Idle
    }

@Composable
private fun PlaceTile(
    text: String,
    description: String,
    look: PlaceLook,
    tile: Rect,
    sprite: Sprite? = null,
    terminal: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = Theme.colors
    val shape = if (terminal) RoundedCornerShape(percent = 50) else RoundedCornerShape(4.dp)
    val waiting by pulse()
    val edge =
        when (look) {
            PlaceLook.Idle -> colors.dim
            PlaceLook.Empty -> colors.line
            PlaceLook.Selected -> colors.ink
            PlaceLook.Target -> colors.yellow
            PlaceLook.Running -> colors.red
        }
    val ink =
        when (look) {
            PlaceLook.Empty -> colors.dim
            PlaceLook.Running -> colors.onRed
            PlaceLook.Target -> colors.yellow
            else -> colors.ink
        }
    Column(
        Modifier
            .offset(tile.left.dp, tile.top.dp)
            .size(TILE_WIDTH.dp, TILE_HEIGHT.dp)
            .alpha(if (look == PlaceLook.Target) waiting.coerceAtLeast(0.55f) else 1f)
            .background(if (look == PlaceLook.Running) colors.red else colors.ground, shape)
            .border(if (look == PlaceLook.Selected) 2.dp else 1.dp, edge, shape)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The picture says what kind of tile it is, and the word under it which one.
        if (sprite != null) PixelArt(sprite, pixel = SPRITE_PIXEL.dp, onRed = look == PlaceLook.Running)
        Label(text, color = ink)
    }
}
