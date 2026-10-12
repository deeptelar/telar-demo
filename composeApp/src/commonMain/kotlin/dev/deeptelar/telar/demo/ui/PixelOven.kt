package dev.deeptelar.telar.demo.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.deeptelar.telar.demo.kitchen.Kitchen
import dev.deeptelar.telar.demo.kitchen.Pizza

/** How far a pizza in the oven is. */
internal enum class Bake { BAKING, BAKED, BURNT }

/** What [minutes] in the oven make of a pizza: it is baked after six, and burnt when it stays more than twelve. */
internal fun bake(minutes: Int): Bake =
    when {
        minutes > Kitchen.BURNT_AFTER -> Bake.BURNT
        minutes >= Kitchen.BAKED_AFTER -> Bake.BAKED
        else -> Bake.BAKING
    }

/** What is said about a pizza that has been in the oven for [minutes]. */
internal fun bakeText(minutes: Int): String =
    when (bake(minutes)) {
        Bake.BAKING -> "$minutes of ${Kitchen.BAKED_AFTER} minutes"
        Bake.BAKED -> "baked, take it out"
        Bake.BURNT -> "burnt"
    }

/**
 * Which pizza is in which place of the oven, given where the pizzas were before, in [places]. A
 * pizza stays in its place for as long as it is in the oven, and a new one takes the first place
 * that is free.
 */
internal fun ovenPlaces(places: List<String?>, inOven: List<String>): List<String?> {
    val kept = places.map { name -> name?.takeIf { it in inOven } }.toMutableList()
    inOven.filter { it !in kept }.forEach { name ->
        val free = kept.indexOf(null)
        if (free >= 0) kept[free] = name
    }
    return kept
}

// The oven is drawn on a grid of pixels: a chimney's worth of room for smoke, and below it the brick
// front with its mouth.
private const val GRID_WIDTH = 21
private const val GRID_HEIGHT = 18
private const val SMOKE_ROWS = 4
private val PIXEL = 4.dp
private val SMALL_PIXEL = 3.dp

private val MOUTH = Color(0xFF0B0908)
private val EMBER = Color(0xFF6E1D12)
private val SMOKE = Color(0xFF8A8A8A)
private val RAW_CRUST = Color(0xFFE9DCC0)
private val RAW_CHEESE = Color(0xFFF4ECC4)
private val RAW_PEPPERONI = Color(0xFFE2A39A)
private val BURNT_CRUST = Color(0xFF2B1D12)
private val BURNT_CHEESE = Color(0xFF4A3520)
private val BURNT_PEPPERONI = Color(0xFF1A100B)

/** How high each of the seven flames is, in pixels, in each frame of the fire. */
private val FLAMES =
    listOf(
        listOf(2, 3, 1, 2, 3, 1, 2),
        listOf(3, 1, 2, 3, 2, 2, 1),
        listOf(1, 2, 3, 1, 2, 3, 2),
        listOf(2, 3, 2, 2, 1, 2, 3),
    )

/** Where the smoke of a burnt pizza is, in each frame: pixels above the oven, as column and row. */
private val SMOKE_PUFFS =
    listOf(
        listOf(9 to 3, 11 to 2, 10 to 0),
        listOf(10 to 3, 9 to 1, 11 to 0),
        listOf(11 to 3, 10 to 2, 9 to 0),
        listOf(10 to 3, 11 to 1, 9 to 1),
    )

/**
 * The oven of the kitchen, in pixels: a place for each pizza it holds, with a fire under the pizza
 * that is in it. A pizza turns from pale to golden while it bakes, and the bar under its place
 * fills: six parts until it is baked, and six more, in red, until it is burnt.
 *
 * @param lit `true` while the shift runs, which is when the fire moves.
 * @param oven which oven of the kitchen this is: 0 for the first.
 * @param small `true` for an oven that two of fit side by side on a narrow screen: smaller pixels,
 *   and under a place only the name of its pizza and how far it is.
 */
@Composable
fun PixelOven(kitchen: Kitchen, lit: Boolean, modifier: Modifier = Modifier, oven: Int = 0, small: Boolean = false) {
    val seen = remember { arrayOfNulls<String>(Kitchen.OVEN_SLOTS) }
    val places = ovenPlaces(seen.toList(), kitchen.oven.filter { it.oven == oven }.map { it.name })
    val called = if (oven == 0) "Oven" else "Oven ${oven + 1}"
    places.forEachIndexed { index, name -> seen[index] = name }

    val time by rememberInfiniteTransition().animateFloat(0f, FLAMES.size.toFloat(), infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Restart))
    val frame = if (lit) time.toInt().coerceIn(0, FLAMES.lastIndex) else 0

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (small) 8.dp else 16.dp)) {
        places.forEachIndexed { index, name ->
            val pizza = kitchen.oven.firstOrNull { it.name == name }
            val minutes = pizza?.let { kitchen.minute - it.inOvenSince!! }
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) {
                    contentDescription = "$called place ${index + 1}: " + if (pizza == null) "empty" else "${pizza.name}, ${bakeText(minutes!!)}"
                },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Chamber(minutes, frame, if (small) SMALL_PIXEL else PIXEL)
                CookBar(minutes, if (small) 4.dp else 6.dp)
                if (small) SmallCaption(pizza, minutes) else Caption(pizza, minutes)
            }
        }
    }
}

/** One place of the oven. [minutes] is how long its pizza has been in, or `null` when the place is empty. */
@Composable
private fun Chamber(minutes: Int?, frame: Int, pixel: Dp) {
    val brick = lerp(Theme.colors.line, Theme.colors.dim, 0.4f)
    val joint = Theme.colors.ground
    val glow = Theme.colors.yellow
    Canvas(Modifier.size(pixel * GRID_WIDTH, pixel * GRID_HEIGHT)) {
        val side = pixel.toPx()

        fun pixel(x: Int, y: Int, color: Color) = drawRect(color, Offset(x * side, y * side), Size(side + 0.5f, side + 0.5f))

        // The front: bricks with an arched top, and the dark mouth in it.
        val top = SMOKE_ROWS
        for (y in top until GRID_HEIGHT) {
            for (x in 0 until GRID_WIDTH) {
                val row = y - top
                val corner = (row == 0 && (x < 2 || x > GRID_WIDTH - 3)) || (row == 1 && (x < 1 || x > GRID_WIDTH - 2))
                if (corner) continue
                val inMouth = x in 4..16 && row in 3..11 && !(row == 3 && (x == 4 || x == 16))
                // Every other row of bricks is set half a brick further.
                val isJoint = !inMouth && row % 3 == 2 && (x + (row / 3) * 2) % 4 == 0
                pixel(x, y, if (inMouth) MOUTH else if (isJoint) joint else brick)
            }
        }
        // A baked pizza lights the rim of the mouth.
        if (minutes != null && bake(minutes) == Bake.BAKED) {
            for (x in 5..15) pixel(x, top + 2, glow)
        }

        if (minutes == null) {
            // An empty place keeps its embers.
            for (x in 5..15 step 2) pixel(x, top + 11, EMBER)
            return@Canvas
        }

        // The fire: seven flames, red below and yellow at the tip.
        FLAMES[frame].forEachIndexed { index, height ->
            val x = 4 + index * 2
            for (level in 0 until height) {
                pixel(x, top + 11 - level, if (level == height - 1) SpriteColors.cheese else SpriteColors.pepperoni)
            }
        }
        pie(minutes, originX = 7, originY = top + 3, side)

        if (bake(minutes) == Bake.BURNT) SMOKE_PUFFS[frame].forEach { (x, y) -> pixel(x, y, SMOKE) }
    }
}

/** The pizza in a place of the oven: pale when it goes in, golden when it is baked, and dark when it is burnt. */
private fun DrawScope.pie(minutes: Int, originX: Int, originY: Int, side: Float) {
    val done = (minutes.toFloat() / Kitchen.BAKED_AFTER).coerceIn(0f, 1f)
    val burnt = bake(minutes) == Bake.BURNT
    Sprites.pie.rows.forEachIndexed { y, row ->
        row.forEachIndexed { x, char ->
            val color =
                when (char) {
                    '.' -> return@forEachIndexed
                    'c' -> if (burnt) BURNT_CRUST else lerp(RAW_CRUST, SpriteColors.crust, done)
                    'r' -> if (burnt) BURNT_PEPPERONI else lerp(RAW_PEPPERONI, SpriteColors.pepperoni, done)
                    else -> if (burnt) BURNT_CHEESE else lerp(RAW_CHEESE, SpriteColors.cheese, done)
                }
            drawRect(color, Offset((originX + x) * side, (originY + y) * side), Size(side + 0.5f, side + 0.5f))
        }
    }
}

/** The minutes of a pizza in the oven as a bar: a part for each minute until it is baked, and a part for each minute it can still stay. */
@Composable
private fun CookBar(minutes: Int?, wide: Dp) {
    val colors = Theme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        repeat(Kitchen.BURNT_AFTER) { part ->
            val filled = minutes != null && part < minutes
            val color =
                when {
                    !filled -> colors.line
                    part < Kitchen.BAKED_AFTER -> colors.yellow
                    else -> colors.red
                }
            // The gap after the sixth part is where the pizza is baked.
            Box(Modifier.size(width = wide, height = 4.dp).background(color))
            if (part == Kitchen.BAKED_AFTER - 1) Box(Modifier.size(width = 1.dp, height = 4.dp))
        }
    }
}

@Composable
private fun bakeColor(minutes: Int): Color =
    when (bake(minutes)) {
        Bake.BAKING -> Theme.colors.ink
        Bake.BAKED -> Theme.colors.yellow
        Bake.BURNT -> Theme.colors.red
    }

/** The caption of a place of a small oven: two short lines that stay as high when the place is empty. */
@Composable
private fun SmallCaption(pizza: Pizza?, minutes: Int?) {
    Column {
        Label(pizza?.name ?: "empty", color = if (pizza == null) Theme.colors.dim else Theme.colors.ink)
        if (pizza == null || minutes == null) {
            Label("")
        } else {
            Label(if (bake(minutes) == Bake.BAKING) "$minutes of ${Kitchen.BAKED_AFTER}" else bake(minutes).name.lowercase(), color = bakeColor(minutes))
        }
    }
}

@Composable
private fun Caption(pizza: Pizza?, minutes: Int?) {
    val colors = Theme.colors
    if (pizza == null || minutes == null) {
        Label("empty")
        return
    }
    val color = bakeColor(minutes)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Label(pizza.name, color = colors.ink)
        Label(bakeText(minutes), color = color)
    }
    Body(pizza.toppings.joinToString { it.word }, color = colors.dim)
}
