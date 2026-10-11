package dev.deeptelar.telar.demo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.deeptelar.telar.END
import dev.deeptelar.telar.START
import dev.deeptelar.telar.demo.game.Cell
import kotlin.math.max

// The board's measures in dp. A tile is one node; arrows run through the gaps between tiles.
private const val TILE_WIDTH = 92f
private const val TILE_HEIGHT = 34f
private const val GAP_X = 40f
private const val GAP_Y = 28f
private const val MARGIN = 28f

/** How far below a row an arrow that goes back to an earlier column runs. */
private const val LANE = 14f

/** The width of the fade at the side of a board that scrolls. */
private const val FADE = 44f

/** The smallest size the board is drawn at, as a part of its full size. Smaller, and its labels cannot be read. */
private const val MIN_SCALE = 0.6f

private enum class TileLook { Idle, Done, Running, Waiting, Failed }

private fun Cell.tile(): Rect =
    Rect(Offset(MARGIN + column * (TILE_WIDTH + GAP_X), MARGIN + row * (TILE_HEIGHT + GAP_Y)), Size(TILE_WIDTH, TILE_HEIGHT))

/**
 * The corners of the arrow from one tile to another, in dp.
 *
 * An arrow to a later column leaves on the right and turns just before its target. An arrow to an
 * earlier column is a way back: it leaves at the bottom and runs under the row.
 */
internal fun route(from: Cell, to: Cell): List<Offset> {
    val a = from.tile()
    val b = to.tile()
    return when {
        to.column > from.column && to.row == from.row -> listOf(a.centerRight, b.centerLeft)
        to.column > from.column -> {
            val turn = b.left - GAP_X / 2
            listOf(a.centerRight, Offset(turn, a.center.y), Offset(turn, b.center.y), b.centerLeft)
        }
        to.column == from.column && to.row > from.row -> listOf(a.bottomCenter, b.topCenter)
        to.column == from.column -> listOf(a.topCenter, b.bottomCenter)
        else -> {
            val lane = max(a.bottom, b.bottom) + LANE
            listOf(a.bottomCenter, Offset(a.center.x, lane), Offset(b.center.x, lane), b.bottomCenter)
        }
    }
}

/**
 * The stage's graph as a game board. Tiles are placed by the stage, arrows are read from the
 * graph's topology, and both light up as the run moves.
 *
 * A tile can be pressed. [selected] is the node whose tile is marked, and [onSelect] is called with
 * the node of the tile that was pressed.
 */
@Composable
fun Board(controller: StageController, modifier: Modifier = Modifier, selected: String? = null, onSelect: (String) -> Unit = {}) {
    val colors = Theme.colors
    val cells = controller.stage.board
    val edges = controller.topology.edges.filter { it.from in cells && it.to in cells }
    val routers = remember(controller) { controller.topology.edges.filter { it.isConditional }.map { it.from }.toSet() }
    val hasWayBack = edges.any { cells.getValue(it.to).column < cells.getValue(it.from).column }
    val width = MARGIN * 2 + cells.values.maxOf { it.column + 1 } * (TILE_WIDTH + GAP_X) - GAP_X
    val height = MARGIN * 2 + cells.values.maxOf { it.row + 1 } * (TILE_HEIGHT + GAP_Y) - GAP_Y + if (hasWayBack) LANE else 0f

    val trail = controller.trail
    val description = "Graph: " + edges.joinToString(", ") { "${it.from.trim('_')} to ${it.to.trim('_')}" }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // On a narrow screen the board is drawn smaller, so that all of it is in view. A board that is
        // still too wide at its smallest size scrolls sideways.
        val scale = (maxWidth.value / width).coerceIn(MIN_SCALE, 1f)
        val density = LocalDensity.current
        val scroll = rememberScrollState()
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            Box(Modifier.horizontalScroll(scroll)) {
                Box(Modifier.size(width.dp, height.dp).semantics { contentDescription = description }) {
                    Canvas(Modifier.size(width.dp, height.dp)) {
                        // Unlit arrows first, so that a lit one is never drawn over.
                        edges.sortedBy { (it.from to it.to) in trail }.forEach { edge ->
                            val lit = (edge.from to edge.to) in trail
                            arrow(route(cells.getValue(edge.from), cells.getValue(edge.to)), if (lit) colors.ink else colors.dim.copy(alpha = 0.6f), lit)
                        }
                    }
                    cells.forEach { (node, cell) ->
                        val tile = cell.tile()
                        val look = when {
                            node in controller.active -> TileLook.Running
                            node == controller.failedNode -> TileLook.Failed
                            !controller.running && node in controller.waitingAt -> TileLook.Waiting
                            node in controller.visited -> TileLook.Done
                            else -> TileLook.Idle
                        }
                        Tile(node, look, router = node in routers, selected = node == selected, onClick = { onSelect(node) }, Modifier.offset(tile.left.dp, tile.top.dp))
                        if (node in controller.stage.pauseBefore) {
                            Label(
                                "save point",
                                Modifier.offset(tile.left.dp, (tile.top - 18).dp),
                                color = if (look == TileLook.Waiting) colors.yellow else colors.dim,
                            )
                        }
                    }
                }
            }
        }

        // A board that is wider than the screen fades out on the side where more of it can be scrolled into view.
        val ground = colors.ground
        Box(
            Modifier.matchParentSize().drawBehind {
                val fade = FADE.dp.toPx()
                if (scroll.canScrollForward) {
                    val start = size.width - fade
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, ground), start, size.width), Offset(start, 0f), Size(fade, size.height))
                }
                if (scroll.canScrollBackward) {
                    drawRect(Brush.horizontalGradient(listOf(ground, Color.Transparent), 0f, fade), Offset.Zero, Size(fade, size.height))
                }
            },
        )
    }
}

/** A dotted line along [corners] with an arrowhead at its end. */
internal fun DrawScope.arrow(corners: List<Offset>, color: Color, lit: Boolean) {
    val points = corners.map { Offset(it.x.dp.toPx(), it.y.dp.toPx()) }
    val gap = 6.dp.toPx()
    val radius = (if (lit) 1.6f else 1.1f).dp.toPx()
    var next = gap
    for (index in 0 until points.lastIndex) {
        val start = points[index]
        val length = (points[index + 1] - start).getDistance()
        val direction = (points[index + 1] - start) / length
        // Stop short of the last corner, where the arrowhead goes.
        val end = if (index == points.lastIndex - 1) length - gap else length
        while (next <= end) {
            drawCircle(color, radius, start + direction * next)
            next += gap
        }
        next -= length
    }

    val tip = points.last()
    val direction = (tip - points[points.lastIndex - 1]).let { it / it.getDistance() }
    val back = tip - direction * 6.dp.toPx()
    val side = Offset(-direction.y, direction.x) * 3.5.dp.toPx()
    drawPath(
        Path().apply {
            moveTo(tip.x, tip.y)
            lineTo((back + side).x, (back + side).y)
            lineTo((back - side).x, (back - side).y)
            close()
        },
        color,
    )
}

@Composable
private fun Tile(node: String, look: TileLook, router: Boolean, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Theme.colors
    // START and END are "__START__" and "__END__" in the library.
    val terminal = node == START || node == END
    val shape = if (terminal) RoundedCornerShape(percent = 50) else RoundedCornerShape(4.dp)
    val waiting by pulse()
    val edge = when (look) {
        TileLook.Idle -> colors.line
        TileLook.Done -> colors.ink
        TileLook.Running, TileLook.Failed -> colors.red
        TileLook.Waiting -> colors.yellow
    }
    val text = when (look) {
        TileLook.Idle -> colors.dim
        TileLook.Done -> colors.ink
        TileLook.Running -> colors.onRed
        TileLook.Failed -> colors.red
        TileLook.Waiting -> colors.yellow
    }
    Row(
        modifier
            .size(TILE_WIDTH.dp, TILE_HEIGHT.dp)
            .alpha(if (look == TileLook.Waiting) waiting.coerceAtLeast(0.45f) else 1f)
            .background(if (look == TileLook.Running) colors.red else colors.ground, shape)
            // The tile whose code is shown has a heavier frame.
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.ink else edge, shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClickLabel = "Show the code", role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (look == TileLook.Running) Box(Modifier.size(6.dp).alpha(waiting).background(colors.onRed))
        Label((if (look == TileLook.Failed) "x " else "") + node.trim('_'), color = text)
        // A node that decides where the run goes next is marked with a small diamond of dots.
        if (router && look != TileLook.Running) RouterMark(edge)
    }
}

@Composable
internal fun RouterMark(color: Color) {
    Canvas(Modifier.size(7.dp)) {
        val unit = size.width / 2
        listOf(Offset(unit, 0f), Offset(0f, unit), Offset(size.width, unit), Offset(unit, size.height)).forEach {
            drawCircle(color, radius = 1.dp.toPx(), center = it)
        }
    }
}
