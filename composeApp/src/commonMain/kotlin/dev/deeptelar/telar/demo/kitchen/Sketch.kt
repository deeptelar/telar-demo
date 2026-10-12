package dev.deeptelar.telar.demo.kitchen

// What the player does at the board, one press at a time. It is not in the library's sample.

/**
 * The board with what the player has in hand. Every press returns the sketch after it, so that the
 * steps of a [Guide] can be followed without a screen.
 *
 * @property selected the tile the player pressed last: a station or the rail. Its arrows are shown and can be drawn.
 * @property armed the way out of [selected] that the next tile the player presses is connected to.
 * @property picking the empty place the player is choosing a station for.
 */
data class Sketch(
    val board: Blueprint = Blueprint(),
    val selected: String? = null,
    val armed: Exit? = null,
    val picking: Int? = null,
) {
    /** The player pressed an empty place: the stations to choose from are offered for it, or put away when they were. */
    fun tapSlot(slot: Int): Sketch = if (board.at(slot) != null) this else Sketch(board, picking = slot.takeIf { it != picking })

    /** Puts a station of [kind] on the place the player pressed, and selects it so that its arrow can be drawn next. */
    fun put(kind: PieceKind): Sketch {
        val slot = picking ?: return this
        val placed = board.place(slot, kind)
        return Sketch(placed).select(placed.at(slot)?.id)
    }

    /**
     * The player pressed the tile of [id]: a station, the rail or the kitchen. With a way out armed,
     * the arrow from it is drawn to this tile. Otherwise the tile is selected, or let go when it
     * was selected already.
     */
    fun tap(id: String): Sketch {
        val from = selected
        val exit = armed
        if (from != null && exit != null && id != from) {
            val connected = board.connect(from, exit, id)
            if (connected != board) return copy(board = connected, armed = connected.freeExit(from))
        }
        // The kitchen has no arrows of its own to show.
        return if (id == Blueprint.KITCHEN) this else select(id.takeIf { it != selected })
    }

    /** Arms a way out of the selected tile again, so that its arrow can be drawn somewhere else. */
    fun redraw(exit: Exit): Sketch {
        val from = selected ?: return this
        return copy(board = board.disconnect(from, exit), armed = exit)
    }

    /** Takes the selected station off the board, with its arrows. */
    fun remove(): Sketch {
        val id = selected?.takeIf { board.piece(it) != null } ?: return this
        return Sketch(board.remove(id))
    }

    /** The sketch after [touch]. Starting the shift leaves it as it is. */
    fun after(touch: Touch): Sketch =
        when (touch) {
            is Touch.Place -> tapSlot(touch.slot)
            is Touch.Choose -> put(touch.kind)
            is Touch.Press -> tap(touch.ids.first())
            is Touch.Redraw -> redraw(touch.exit)
            Touch.Remove -> remove()
            Touch.Start -> this
        }

    private fun select(id: String?): Sketch = copy(selected = id, armed = id?.let(board::freeExit), picking = null)
}

/** One press of the player, as a step of a [Guide] asks for it. */
sealed interface Touch {
    /** Press the empty place [slot]. */
    data class Place(val slot: Int) : Touch

    /** Choose a station of [kind] in the list that the place offers. */
    data class Choose(val kind: PieceKind) : Touch

    /** Press a tile: a station, the rail or the kitchen. Any of [ids] is a way on, and the first is the one the guide would press. */
    data class Press(val ids: List<String>) : Touch {
        constructor(id: String) : this(listOf(id))
    }

    /** Press the button beside the arrow of [exit], which lets the arrow be drawn again. */
    data class Redraw(val exit: Exit) : Touch

    /** Press the button that takes the selected station off the board. */
    data object Remove : Touch

    /** Start the shift. */
    data object Start : Touch
}

/** What the lines under the board call the arrow of this way out. */
val Exit.arrow: String get() = if (this == Exit.NEXT) "arrow to" else "$word goes to"
