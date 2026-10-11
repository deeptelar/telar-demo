package dev.deeptelar.telar.demo.kitchen

// A board as a short text, so that it fits in a link. It is not in the library's sample.

/** The letter of each station in the text of a board. */
private val LETTERS: Map<PieceKind, Char> =
    mapOf(
        PieceKind.LOOKUP to 'l',
        PieceKind.ONLY_MENU to 'p',
        PieceKind.LOOKS_BACK to 'e',
        PieceKind.READER to 'r',
        PieceKind.BOOK_READER to 'b',
        PieceKind.EXPEDITER to 'x',
        PieceKind.SECOND_OVEN to 'o',
    )

private const val NOTHING = '-'
private const val TO_KITCHEN = 'k'

/**
 * This board as a text of at most fifteen characters. First the six places, each with the letter of
 * its station or `-`. Then where the arrows go: the one of the rail, and after it those of each
 * station in the order of the places, two for a sorter (yes, then no). An arrow is the number of the
 * place it goes to, `k` for the kitchen, or `-` when it is not drawn.
 *
 * The board that sends every order to the menu lookup is `l-----0k`.
 */
fun Blueprint.text(): String {
    fun to(from: String, exit: Exit): Char =
        when (val target = target(from, exit)) {
            null -> NOTHING
            Blueprint.KITCHEN -> TO_KITCHEN
            else -> piece(target)?.slot?.digitToChar() ?: NOTHING
        }
    val places = (0 until Blueprint.SLOTS).map { slot -> at(slot)?.let { LETTERS.getValue(it.kind) } ?: NOTHING }
    val arrows = listOf(to(Blueprint.RAIL, Exit.NEXT)) + pieces.sortedBy { it.slot }.flatMap { piece -> exits(piece.id).map { to(piece.id, it) } }
    return (places + arrows).joinToString("")
}

/**
 * The board that [text] describes, or `null` when it describes none: a text that [Blueprint.text]
 * does not write. A board that cannot run yet, because an arrow is missing, is a board all the same.
 */
fun blueprintOf(text: String): Blueprint? {
    if (text.length < Blueprint.SLOTS + 1) return null
    var board = Blueprint()
    text.take(Blueprint.SLOTS).forEachIndexed { slot, letter ->
        if (letter == NOTHING) return@forEachIndexed
        val kind = LETTERS.entries.firstOrNull { it.value == letter }?.key ?: return null
        // A second station of a kind that a board has once is not placed.
        board = board.place(slot, kind).takeIf { it != board } ?: return null
    }
    val exits = listOf(Blueprint.RAIL to Exit.NEXT) + board.pieces.flatMap { piece -> board.exits(piece.id).map { piece.id to it } }
    val arrows = text.drop(Blueprint.SLOTS)
    if (arrows.length != exits.size) return null
    exits.zip(arrows.toList()).forEach { (exit, letter) ->
        val (from, way) = exit
        val to =
            when {
                letter == NOTHING -> return@forEach
                letter == TO_KITCHEN -> Blueprint.KITCHEN
                letter.isDigit() -> board.at(letter.digitToInt())?.id ?: return null
                else -> return null
            }
        board = board.connect(from, way, to).takeIf { it != board } ?: return null
    }
    return board
}
