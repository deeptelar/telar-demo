package dev.deeptelar.telar.demo.kitchen

import dev.deeptelar.telar.demo.kitchen.Blueprint.Companion.KITCHEN
import dev.deeptelar.telar.demo.kitchen.Blueprint.Companion.RAIL

// How a short shift is taught: a board that is partly drawn, and the next thing to press on it.
// It is not in the library's sample.

/**
 * What to press next.
 *
 * @property text the step in words.
 * @property left how many steps lead from here to a shift that runs, this one and the start of the shift counted.
 */
data class Step(val touch: Touch, val text: String, val left: Int = 1)

/**
 * An arrow the player decides: the guide asks where it goes and does not say.
 *
 * @property text the question.
 * @property tiles the tiles it can go to. The first is where the board of the guide has it.
 */
data class Ask(val text: String, val tiles: List<String>)

/**
 * The lesson of a short shift.
 *
 * @property start the board the shift opens with.
 * @property boards boards that win every medal of the shift. The steps lead to the one that takes the fewest from the board as it is drawn.
 * @property asks the arrows that the player decides, by where they start. They are on the first of [boards].
 */
class Guide(
    val start: Blueprint,
    val boards: List<Blueprint>,
    private val asks: Map<Pair<String, Exit>, Ask> = emptyMap(),
) {
    /** How many steps the lesson has, from the board it opens with. */
    val steps: Int = next(Sketch(start)).left

    /**
     * The next step from [sketch]. An arrow that the player decides is asked for, and whatever the
     * player draws is taken, unless [strict]: then every step leads to one of [boards].
     */
    fun next(sketch: Sketch, strict: Boolean = false): Step {
        if (!strict && asks.isNotEmpty()) {
            val asked = led(boards.first(), sketch, open = true)
            // Arrows of the player's own can leave a board that does not run. From there the steps lead to one that does.
            if (asked.touch != Touch.Start || sketch.board.problems().isEmpty()) return asked
        }
        return boards.map { led(it, sketch, open = false) }.minBy { it.left }
    }

    /** The step that [toward] gives, with how many steps follow it when every one is taken. */
    private fun led(goal: Blueprint, sketch: Sketch, open: Boolean): Step {
        val first = toward(goal, sketch, open)
        var step = first
        var now = sketch
        var left = 1
        while (step.touch != Touch.Start && left < MOST_STEPS) {
            now = now.after(step.touch)
            step = toward(goal, now, open)
            left++
        }
        return first.copy(left = left)
    }

    /** The next press that brings the board of [sketch] closer to [goal]. With [open], an arrow that is asked for may go anywhere. */
    private fun toward(goal: Blueprint, sketch: Sketch, open: Boolean): Step {
        val board = sketch.board
        val selected = sketch.selected
        val armed = sketch.armed

        fun ask(arrow: Arrow): Ask? = asks[arrow.from to arrow.exit].takeIf { open }

        fun drawn(arrow: Arrow): Boolean = board.target(arrow.from, arrow.exit).let { if (ask(arrow) != null) it != null else it == arrow.to }

        // Pressing a tile with an arrow in hand draws the arrow there. A tile that is only to be selected needs the hand empty.
        fun select(id: String, text: String): Step =
            if (selected != null && selected != id && armed != null) {
                Step(Touch.Press(selected), "Press ${board.called(selected)} again. That lets its arrow go.")
            } else {
                Step(Touch.Press(id), text)
            }

        val missing = goal.pieces.filter { board.piece(it.id) == null }
        val extra = board.pieces.filter { goal.piece(it.id) == null }

        // The list of a place is open: the station that is missing is chosen there.
        if (sketch.picking != null && missing.isNotEmpty()) {
            return Step(Touch.Choose(missing.first().kind), "Choose \"${missing.first().kind.title}\" in the list under the board.")
        }

        // An arrow is in hand: it goes to the tile it ends at.
        goal.arrows.firstOrNull { it.from == selected && it.exit == armed }?.let { arrow ->
            ask(arrow)?.takeIf { asked -> asked.tiles.all { board.piece(it) != null } }?.let { return Step(Touch.Press(it.tiles), it.text) }
            if (arrow.to == KITCHEN || board.piece(arrow.to) != null) {
                val which = if (arrow.exit == Exit.NEXT) "the arrow from ${board.called(arrow.from)}" else "the ${arrow.exit.word} arrow of ${board.called(arrow.from)}"
                return Step(Touch.Press(arrow.to), "Press ${board.called(arrow.to)}. That draws $which to it.")
            }
        }

        // A station that the board does not need is taken off first, which makes room for the one that is missing.
        (extra.firstOrNull { it.id == selected } ?: extra.firstOrNull())?.let { piece ->
            if (selected == piece.id) return Step(Touch.Remove, "Press \"Remove the station\" under the board.")
            return select(piece.id, "Press ${board.called(piece.id)}. This board does not need it.")
        }

        // A station is missing: it goes on the place the guide has it on, or on the first that is free.
        missing.firstOrNull()?.let { piece ->
            val slot = piece.slot.takeIf { board.at(it) == null } ?: (0 until Blueprint.SLOTS).firstOrNull { board.at(it) == null }
            if (slot != null) return Step(Touch.Place(slot), "Press the + that blinks, to put a station there.")
        }

        // An arrow is missing, or goes somewhere else.
        goal.arrows.firstOrNull { !drawn(it) }?.let { arrow ->
            if (selected != arrow.from) return select(arrow.from, "Press ${board.called(arrow.from)}.")
            val button = if (board.target(arrow.from, arrow.exit) == null) "Draw" else "Change"
            return Step(Touch.Redraw(arrow.exit), "Press \"$button\" beside \"${arrow.exit.arrow}\", under the board.")
        }
        return Step(Touch.Start, "The board is ready. Start the shift.")
    }

    companion object {
        /** More steps than any board takes, so that counting them ends. */
        private const val MOST_STEPS = 80

        /** Every order goes to one station of [kind], which stands between the rail and the kitchen. */
        fun through(kind: PieceKind): Blueprint = Blueprint().place(MIDDLE, kind).connect(RAIL, Exit.NEXT, kind.word).connect(kind.word, Exit.NEXT, KITCHEN)

        /** The place in front of the rail. */
        private const val MIDDLE = 2

        private val LOOKUP = PieceKind.LOOKUP.word
        private val READER = PieceKind.READER.word
        private val BOOK = PieceKind.BOOK_READER.word
        private val PLAIN = PieceKind.ONLY_MENU.word
        private val EARLIER = PieceKind.LOOKS_BACK.word

        /** The sorter with the lookup and a reader of [kind], which both hand on to the kitchen. The arrows of the sorter are not drawn. */
        private fun unsorted(kind: PieceKind): Blueprint =
            Blueprint()
                .place(MIDDLE, PieceKind.ONLY_MENU)
                .place(1, PieceKind.LOOKUP)
                .place(if (kind == PieceKind.READER) 5 else 3, kind)
                .connect(RAIL, Exit.NEXT, PLAIN)
                .connect(LOOKUP, Exit.NEXT, KITCHEN)
                .connect(kind.word, Exit.NEXT, KITCHEN)

        /** An order that is only a menu pizza goes to the lookup, and a reader of [kind] gets the others. */
        private fun sorted(kind: PieceKind): Blueprint = unsorted(kind).connect(PLAIN, Exit.YES, LOOKUP).connect(PLAIN, Exit.NO, kind.word)

        /** Two sorters, so that every order goes to the cheapest station that can read it. */
        private val TWO_SORTERS: Blueprint =
            sorted(PieceKind.BOOK_READER)
                .place(4, PieceKind.LOOKS_BACK)
                .place(5, PieceKind.READER)
                .connect(READER, Exit.NEXT, KITCHEN)
                .connect(PLAIN, Exit.NO, EARLIER)
                .connect(EARLIER, Exit.YES, BOOK)
                .connect(EARLIER, Exit.NO, READER)

        /** This board with a station of [kind] between the rail and the station that got every order first. */
        private fun Blueprint.behindTheRail(kind: PieceKind): Blueprint {
            val first = checkNotNull(target(RAIL, Exit.NEXT))
            val slot = (0 until Blueprint.SLOTS).first { at(it) == null }
            return place(slot, kind).connect(kind.word, Exit.NEXT, first).connect(RAIL, Exit.NEXT, kind.word)
        }

        /** One arrow is missing: from the lookup to the kitchen. */
        val FIRST_ORDERS: Guide =
            Guide(
                start = Blueprint().place(MIDDLE, PieceKind.LOOKUP).connect(RAIL, Exit.NEXT, LOOKUP),
                boards = listOf(through(PieceKind.LOOKUP)),
            )

        /** The board that won the first shift: the lookup comes off, and the player puts the reader in its place and draws its two arrows. */
        val WISHES: Guide = Guide(start = FIRST_ORDERS.boards.first(), boards = listOf(through(PieceKind.READER)))

        /** The stations are there, and the player decides where the two arrows of the sorter go. */
        val SORTING: Guide =
            Guide(
                start = unsorted(PieceKind.READER),
                boards = listOf(sorted(PieceKind.READER)),
                asks =
                    mapOf(
                        (PLAIN to Exit.YES) to Ask("Its yes arrow: press the station for an order that is only a menu pizza.", listOf(LOOKUP, READER)),
                        (PLAIN to Exit.NO) to Ask("Its no arrow: press the station for every other order.", listOf(READER, LOOKUP)),
                    ),
            )

        /** From here on a shift opens with a board that won the one before, and the steps are shown when the player asks. */
        val THE_BOOK: Guide = Guide(start = SORTING.boards.first(), boards = listOf(sorted(PieceKind.BOOK_READER), through(PieceKind.BOOK_READER)))

        val LOOKING_BACK: Guide = Guide(start = THE_BOOK.boards.first(), boards = listOf(TWO_SORTERS))

        val URGENT: Guide =
            Guide(
                start = TWO_SORTERS,
                boards = listOf(TWO_SORTERS.behindTheRail(PieceKind.EXPEDITER), through(PieceKind.LOOKUP).behindTheRail(PieceKind.EXPEDITER)),
            )

        val TWO_OVENS: Guide =
            Guide(
                start = URGENT.boards.first(),
                boards = listOf(TWO_SORTERS.behindTheRail(PieceKind.SECOND_OVEN), through(PieceKind.LOOKUP).behindTheRail(PieceKind.SECOND_OVEN)),
            )
    }
}

/** What a step calls the tile of [id]: by the word that stands on it. */
fun Blueprint.called(id: String): String =
    when (id) {
        RAIL -> "the rail"
        KITCHEN -> "the kitchen"
        else -> "the ${piece(id)?.kind?.tile ?: id} tile"
    }
