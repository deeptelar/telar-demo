package dev.deeptelar.telar.demo.kitchen

import dev.deeptelar.telar.demo.game.CodePart
import dev.deeptelar.telar.demo.game.isComment
import dev.deeptelar.telar.demo.game.sourceBlocks

// The code behind the tiles of a board, as the stages show the code behind theirs. It is not in the library's sample.

private val DECLARATION = Regex("""^(?:(?:private|internal|suspend|const) )*(?:fun|val) (?:\w+\.)?(\w+)""")

/**
 * The functions and values of the kitchen's source files, by name. Each is the text of the real file,
 * which the build puts into the app, with the comment that stands over it.
 */
private val declarations: Map<String, String> by lazy {
    kitchenSources.values
        .flatMap { sourceBlocks(it.lines(), indent = 0) }
        .mapNotNull { block -> DECLARATION.find(block.lineSequence().first { !it.isComment() })?.let { it.groupValues[1] to block } }
        .toMap()
}

/** What the node of a station of this kind calls, and what that reads, by the names they have in the source. */
val PieceKind.uses: List<String>
    get() =
        when (this) {
            PieceKind.LOOKUP -> listOf("lookUp")
            PieceKind.ONLY_MENU -> listOf("isOnlyAMenuPizza", "ONLY_A_MENU_PIZZA")
            PieceKind.LOOKS_BACK -> listOf("isAboutEarlier", "ABOUT_EARLIER")
            PieceKind.READER -> listOf("read", "READER_ALONE")
            PieceKind.BOOK_READER -> listOf("read", "READER_WITH_BOOK")
            // These two only stamp the slip. What they use is the place in the kitchen that reads the stamp.
            PieceKind.EXPEDITER -> listOf("turnOf")
            PieceKind.SECOND_OVEN -> listOf("mayBake")
        }

private const val NO_ARROW = "// No arrow yet."

/**
 * The code to show for the tile of [id]: the node of a station, the arrows that start or end at it,
 * and the functions its node calls. The rail is `START` and the kitchen is `END`: they have no node,
 * only arrows. Without a tile it is the whole graph of the board.
 */
fun Blueprint.codeOf(id: String?): List<CodePart> {
    if (id == null) return listOf(CodePart("the graph", code()))
    val arrows = arrowCode(id).ifEmpty { NO_ARROW }

    fun source(names: List<String>): String = names.mapNotNull { declarations[it] }.joinToString("\n\n")
    return when (id) {
        Blueprint.RAIL -> listOf(CodePart("arrows from START", arrows))
        Blueprint.KITCHEN -> listOf(CodePart("arrows to END", arrows))
        else -> {
            val piece = piece(id) ?: return emptyList()
            listOf(CodePart("the node", nodeCode(id).orEmpty()), CodePart("its arrows", arrows), CodePart("it uses", source(piece.kind.uses)))
        }
    }.filter { it.code.isNotEmpty() }
}
