package dev.deeptelar.telar.demo.ui

import dev.deeptelar.telar.demo.kitchen.Blueprint
import dev.deeptelar.telar.demo.kitchen.blueprintOf
import dev.deeptelar.telar.demo.kitchen.text

/** Where the game is played in a browser. A link that an app copies leads there. */
const val DEMO_ADDRESS: String = "https://deeptelar.github.io/telar-demo/"

/**
 * What a link to the bonus stage carries: a shift and a board for it. Whoever opens the link finds
 * that board drawn, and can play the shift on it or draw a better one.
 */
data class KitchenLink(val rush: Rush, val board: Blueprint) {
    /** What follows `#` in the link, like `kitchen=dinner.plxbo-413k0k2`. */
    val fragment: String get() = "$KEY=${rush.name.lowercase()}.${board.text()}"

    /** The link, to the page at [page]. */
    fun address(page: String = DEMO_ADDRESS): String = "$page#$fragment"

    companion object {
        private const val KEY = "kitchen"

        /** The shift and the board in what follows `#` in an address, or `null` when that is not a link to a board. */
        fun of(fragment: String?): KitchenLink? {
            val carried = fragment?.removePrefix("#")?.takeIf { it.startsWith("$KEY=") }?.removePrefix("$KEY=") ?: return null
            val rush = Rush.entries.firstOrNull { it.name.lowercase() == carried.substringBefore('.') } ?: return null
            val board = blueprintOf(carried.substringAfter('.', "")) ?: return null
            return KitchenLink(rush, board)
        }
    }
}

/**
 * This shift in a sentence to post, with a link to its board: what it scored, and what it took.
 * Its points come first, because a board that is cheap or small and serves the wrong pizza is no result.
 */
fun PlayedShift.told(page: String = DEMO_ADDRESS): String {
    val tokens = shift.spent.toString().reversed().chunked(3).joinToString(",").reversed()
    val stations = if (this.stations == 1) "1 station" else "${this.stations} stations"
    val counted = if (scripted) "$tokens tokens" else "$tokens tokens of $model"
    return "Pixel Pizza, ${rush.title.lowercase()}: ${shift.kitchen.score().points} of ${rush.points} points for $counted on $stations. " +
        "Can you beat this board? ${KitchenLink(rush, board).address(page)}"
}
