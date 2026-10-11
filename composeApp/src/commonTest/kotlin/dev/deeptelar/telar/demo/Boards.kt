package dev.deeptelar.telar.demo

import dev.deeptelar.telar.demo.kitchen.Blueprint
import dev.deeptelar.telar.demo.kitchen.Blueprint.Companion.KITCHEN
import dev.deeptelar.telar.demo.kitchen.Blueprint.Companion.RAIL
import dev.deeptelar.telar.demo.kitchen.Exit
import dev.deeptelar.telar.demo.kitchen.PieceKind

// Boards a player can draw, for the tests of the bonus stage.

/** Every order goes to one station of [kind]. */
fun boardWith(kind: PieceKind): Blueprint = Blueprint().place(0, kind).connect(RAIL, Exit.NEXT, kind.word).connect(kind.word, Exit.NEXT, KITCHEN)

/** The orders that are only a menu pizza go to the lookup, and the reader with the book gets the rest. */
val SORTED_BOARD: Blueprint =
    Blueprint()
        .place(0, PieceKind.ONLY_MENU)
        .place(1, PieceKind.LOOKUP)
        .place(3, PieceKind.BOOK_READER)
        .connect(RAIL, Exit.NEXT, "onlyMenu")
        .connect("onlyMenu", Exit.YES, "lookup")
        .connect("onlyMenu", Exit.NO, "bookReader")
        .connect("lookup", Exit.NEXT, KITCHEN)
        .connect("bookReader", Exit.NEXT, KITCHEN)

/** Two sorters, so that every order goes to the cheapest station that can read it. */
val BEST_BOARD: Blueprint =
    Blueprint()
        .place(0, PieceKind.ONLY_MENU)
        .place(1, PieceKind.LOOKUP)
        .place(2, PieceKind.LOOKS_BACK)
        .place(3, PieceKind.BOOK_READER)
        .place(5, PieceKind.READER)
        .connect(RAIL, Exit.NEXT, "onlyMenu")
        .connect("onlyMenu", Exit.YES, "lookup")
        .connect("onlyMenu", Exit.NO, "looksBack")
        .connect("looksBack", Exit.YES, "bookReader")
        .connect("looksBack", Exit.NO, "reader")
        .connect("lookup", Exit.NEXT, KITCHEN)
        .connect("bookReader", Exit.NEXT, KITCHEN)
        .connect("reader", Exit.NEXT, KITCHEN)

/**
 * This board with stations of [kinds] between the rail and the station that got every order first,
 * on places that are free: the way a player adds an expediter or a second oven in front of everything.
 */
fun Blueprint.behindTheRail(vararg kinds: PieceKind): Blueprint {
    var board = this
    kinds.forEach { kind ->
        val first = checkNotNull(board.target(RAIL, Exit.NEXT))
        val slot = (0 until Blueprint.SLOTS).first { board.at(it) == null }
        board = board.place(slot, kind).connect(kind.word, Exit.NEXT, first).connect(RAIL, Exit.NEXT, kind.word)
    }
    return board
}

/** The board that serves the dinner rush: both stations for the oven, and with the places that are left, one sorter. */
val DINNER_BOARD: Blueprint = SORTED_BOARD.behindTheRail(PieceKind.EXPEDITER, PieceKind.SECOND_OVEN)

/** The smallest board that serves the dinner rush: one reader that is told everything, and both stations for the oven. */
val SMALL_DINNER_BOARD: Blueprint = boardWith(PieceKind.BOOK_READER).behindTheRail(PieceKind.EXPEDITER, PieceKind.SECOND_OVEN)
