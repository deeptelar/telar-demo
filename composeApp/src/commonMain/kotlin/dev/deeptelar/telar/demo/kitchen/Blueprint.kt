package dev.deeptelar.telar.demo.kitchen

import dev.deeptelar.telar.GraphValidationException

// The part of the kitchen that the player builds. It is not in the library's sample.

/**
 * A station the player can put on the board. Every order walks from the rail along the arrows, from
 * station to station, until it reaches the kitchen with a ticket, or without one.
 *
 * @property word the name of its node in the graph.
 * @property tile what its tile on the board says.
 * @property job what it does, for the player.
 * @property asks `true` when it asks a model, which costs tokens and a minute for each order.
 * @property sorts `true` when it sends an order one of two ways and does nothing else.
 * @property once `true` when a board can have only one of it.
 */
enum class PieceKind(
    val word: String,
    val tile: String,
    val title: String,
    val job: String,
    val asks: Boolean = false,
    val sorts: Boolean = false,
    val once: Boolean = false,
) {
    LOOKUP(
        "lookup",
        "lookup",
        "Menu lookup",
        "Plain code. It finds the name of a menu pizza in the order and writes that pizza on the ticket. No tokens, no time. " +
            "It reads nothing else the guest said.",
    ),
    ONLY_MENU(
        "onlyMenu",
        "plain?",
        "Sorter: only a menu pizza?",
        "Plain code. Yes when the order is the name of a menu pizza and nothing more, like \"A margherita.\"",
        sorts = true,
    ),
    LOOKS_BACK(
        "looksBack",
        "earlier?",
        "Sorter: about something earlier?",
        "Plain code. Yes when the order names a table, or says \"same\", \"another\" or \"again\".",
        sorts = true,
    ),
    READER(
        "reader",
        "reader",
        "Reader",
        "A model reads the order, and only the order. One answer and one minute for each. It does not know what was said before.",
        asks = true,
    ),
    BOOK_READER(
        "bookReader",
        "book",
        "Reader with the book",
        "A model reads the order together with everything the guests said before. It knows what table 2 had, and each order costs more than the last.",
        asks = true,
    ),
    EXPEDITER(
        "expediter",
        "rush",
        "Expediter",
        "Plain code. It writes on the ticket when the order is due. The kitchen makes and bakes those tickets first, the one that is due soonest " +
            "before the others. Tickets without a due time are taken after them, in the order they came.",
    ),
    SECOND_OVEN(
        "secondOven",
        "oven 2",
        "Second oven",
        "A second oven with two places and a chef of its own. The pizza of an order that passes here may be baked in it.",
        once = true,
    ),
    ;

    /** What a sorter says to [words], the order as the guest said it. */
    fun says(words: String): Boolean =
        when (this) {
            ONLY_MENU -> ONLY_A_MENU_PIZZA.matches(words.trim())
            LOOKS_BACK -> ABOUT_EARLIER.containsMatchIn(words)
            else -> false
        }
}

private val ONLY_A_MENU_PIZZA = Regex("""(?:(?:an?|one)\s+)?(?:${MENU.keys.joinToString("|")})\.?""", RegexOption.IGNORE_CASE)
private val ABOUT_EARLIER = Regex("""\b(?:table|same|another|again)\b""", RegexOption.IGNORE_CASE)

/** A station on the board. [id] is the name of its node, and [slot] the place it stands on. */
data class Piece(
    val id: String,
    val kind: PieceKind,
    val slot: Int,
)

/** The way out of a station that an arrow starts at. A sorter has [YES] and [NO], everything else has [NEXT]. */
enum class Exit(val word: String) { NEXT("next"), YES("yes"), NO("no") }

/** An arrow from the [exit] of [from] to [to]. Each end is the id of a piece, [Blueprint.RAIL] or [Blueprint.KITCHEN]. */
data class Arrow(
    val from: String,
    val exit: Exit,
    val to: String,
)

/**
 * The way of an order across a board.
 *
 * @property stations the stations it goes through, by their ids.
 * @property arrives `false` when it does not get to the kitchen: an arrow is missing, or the arrows run in a circle.
 */
data class Way(
    val stations: List<String>,
    val arrives: Boolean,
)

/**
 * What the player drew: the stations between the rail, where the orders come in, and the kitchen,
 * which makes what the tickets say. It is a value, and [graph] turns it into a Telar graph.
 */
data class Blueprint(
    val pieces: List<Piece> = emptyList(),
    val arrows: List<Arrow> = emptyList(),
) {
    fun piece(id: String): Piece? = pieces.firstOrNull { it.id == id }

    fun at(slot: Int): Piece? = pieces.firstOrNull { it.slot == slot }

    /** What the tile of [id] is called. */
    fun title(id: String): String =
        when (id) {
            RAIL -> "The rail"
            KITCHEN -> "The kitchen"
            else -> piece(id)?.kind?.title ?: id
        }

    /** The ways out of [id]: none for the kitchen. */
    fun exits(id: String): List<Exit> =
        when {
            id == RAIL -> listOf(Exit.NEXT)
            piece(id)?.kind?.sorts == true -> listOf(Exit.YES, Exit.NO)
            piece(id) != null -> listOf(Exit.NEXT)
            else -> emptyList()
        }

    /** Where the arrow from the [exit] of [from] goes, or `null` when there is none. */
    fun target(from: String, exit: Exit): String? = arrows.firstOrNull { it.from == from && it.exit == exit }?.to

    /** The first way out of [id] that has no arrow yet. */
    fun freeExit(id: String): Exit? = exits(id).firstOrNull { target(id, it) == null }

    /**
     * The way an order with these [words] takes across the board. Only the sorters decide it, and they
     * are plain code, so it is known before a shift starts and without asking a model.
     */
    fun way(words: String): Way {
        val stations = mutableListOf<String>()
        var next = target(RAIL, Exit.NEXT)
        while (next != null && next != KITCHEN && next !in stations) {
            val kind = piece(next)?.kind ?: break
            stations += next
            next = target(next, if (!kind.sorts) Exit.NEXT else if (kind.says(words)) Exit.YES else Exit.NO)
        }
        return Way(stations, arrives = next == KITCHEN)
    }

    /** This board with a new station of [kind] on [slot]. A slot that is taken, or is not on the board, stays as it is. */
    fun place(slot: Int, kind: PieceKind): Blueprint {
        if (slot !in 0 until SLOTS || at(slot) != null || (kind.once && pieces.any { it.kind == kind })) return this
        val id = generateSequence(1) { it + 1 }.map { if (it == 1) kind.word else "${kind.word}$it" }.first { piece(it) == null }
        return copy(pieces = pieces + Piece(id, kind, slot))
    }

    /** This board without the station [id] and without its arrows. */
    fun remove(id: String): Blueprint = copy(pieces = pieces.filter { it.id != id }, arrows = arrows.filter { it.from != id && it.to != id })

    /** This board with an arrow from the [exit] of [from] to [to], in place of the one that started there. An arrow that cannot be is not drawn. */
    fun connect(from: String, exit: Exit, to: String): Blueprint {
        if (exit !in exits(from) || to == from || to == RAIL || (to != KITCHEN && piece(to) == null)) return this
        return disconnect(from, exit).let { it.copy(arrows = it.arrows + Arrow(from, exit, to)) }
    }

    fun disconnect(from: String, exit: Exit): Blueprint = copy(arrows = arrows.filterNot { it.from == from && it.exit == exit })

    /**
     * What is wrong with the board, or nothing when a shift can start. The board is checked first for
     * what a player can do wrong with arrows, in the words of the game. What is left is checked by
     * Telar when the graph is compiled, and reported in its words.
     */
    fun problems(): List<String> {
        val own = mutableListOf<String>()
        if (target(RAIL, Exit.NEXT) == null) own += "The rail has no arrow. Draw one to the station that gets every order first."
        pieces.forEach { piece ->
            val missing = exits(piece.id).filter { target(piece.id, it) == null }
            if (missing.isEmpty()) return@forEach
            own +=
                if (piece.kind.sorts) {
                    "${title(piece.id)} needs an arrow for yes and an arrow for no."
                } else {
                    "${title(piece.id)} has no arrow. An order that gets there is stuck."
                }
        }
        circle()?.let { own += "The arrows run in a circle at ${title(it)}. An order would never reach the kitchen." }
        if (own.isNotEmpty()) return own
        return try {
            graph { _ -> error("A board that is only checked asks no model.") }
            emptyList()
        } catch (e: GraphValidationException) {
            e.message
                .orEmpty()
                .lines()
                .filter { it.startsWith("- ") || !it.startsWith("Invalid graph") }
                .map { "Telar: ${it.removePrefix("- ")}" }
        }
    }

    /** A station that an order could come back to, if there is one. */
    private fun circle(): String? {
        fun reaches(from: String, to: String, seen: Set<String> = emptySet()): Boolean =
            arrows.filter { it.from == from }.any { it.to == to || (it.to !in seen && reaches(it.to, to, seen + it.to)) }
        return pieces.firstOrNull { reaches(it.id, it.id) }?.id
    }

    /** The stations in the order an order can meet them, and after them the ones no arrow leads to. */
    private fun ordered(): List<Piece> {
        val found = mutableListOf<String>()
        val queue = ArrayDeque(listOf(RAIL))
        while (queue.isNotEmpty()) {
            arrows.filter { it.from == queue.first() }.map { it.to }.filter { it != KITCHEN && it !in found }.forEach {
                found += it
                queue += it
            }
            queue.removeFirst()
        }
        return found.mapNotNull(::piece) + pieces.filter { it.id !in found }
    }

    /** A statement of the graph of the board, without indentation, and the tiles it is about. */
    private class Statement(val tiles: Set<String>, val text: String)

    /** The node of each station, in the order an order can meet them. */
    private fun nodes(): List<Statement> =
        ordered().map { piece ->
            val work =
                when (piece.kind) {
                    PieceKind.LOOKUP -> "slip -> slip.lookUp()"
                    PieceKind.READER -> "slip -> slip.read(model)"
                    PieceKind.BOOK_READER -> "slip -> slip.read(model, book = true)"
                    PieceKind.EXPEDITER -> "slip -> slip.copy(rush = true)"
                    PieceKind.SECOND_OVEN -> "slip -> slip.copy(secondOven = true)"
                    else -> "it"
                }
            Statement(setOf(piece.id), "val ${piece.id} = node(\"${piece.id}\") { $work }")
        }

    /** The arrows: an edge for each, and a conditional edge for the two of a sorter. A sorter that lacks one has none yet. */
    private fun edges(): List<Statement> {
        fun ref(id: String) = if (id == KITCHEN) "END" else id
        return listOfNotNull(target(RAIL, Exit.NEXT)?.let { Statement(setOf(RAIL, it), "START then ${ref(it)}") }) +
            ordered().mapNotNull { piece ->
                val id = piece.id
                if (!piece.kind.sorts) return@mapNotNull target(id, Exit.NEXT)?.let { Statement(setOf(id, it), "$id then ${ref(it)}") }
                val yes = target(id, Exit.YES) ?: return@mapNotNull null
                val no = target(id, Exit.NO) ?: return@mapNotNull null
                val question = if (piece.kind == PieceKind.ONLY_MENU) "isOnlyAMenuPizza" else "isAboutEarlier"
                Statement(
                    setOf(id, yes, no),
                    "conditionalEdge($id, targets = setOf(${setOf(ref(yes), ref(no)).joinToString()})) { slip ->\n" +
                        "    if (slip.$question) ${ref(yes)} else ${ref(no)}\n" +
                        "}",
                )
            }
    }

    /**
     * The board as the Kotlin that builds its graph with Telar. [graph] makes the same calls: a node
     * for each station, an edge for each arrow, and a conditional edge for each sorter.
     */
    fun code(): String {
        val nodes = nodes().map { it.text.prependIndent("    ") }
        val edges = edges().map { it.text.prependIndent("    ") }
        return (listOf("StateGraph<Slip> {") + nodes + listOf("").filter { nodes.isNotEmpty() && edges.isNotEmpty() } + edges + "}.compile()")
            .joinToString("\n")
    }

    /**
     * The part of [code] that is about the tile of [id]: the node of a station, and after it the
     * arrows that start or end there. The rail has no node, so it has only its arrow.
     */
    fun code(id: String): String {
        val node = nodes().filter { id in it.tiles }.map { it.text }
        val arrows = edges().filter { id in it.tiles }.map { it.text }
        val none = listOf("// No arrow yet.").filter { arrows.isEmpty() }
        return (node + listOf("").filter { node.isNotEmpty() } + arrows + none).joinToString("\n")
    }

    /** `true` when the kitchen of this board has a second oven. */
    val hasSecondOven: Boolean get() = pieces.any { it.kind == PieceKind.SECOND_OVEN }

    /** The stations of the board in a few words, to tell one board from another. */
    val summary: String get() = if (pieces.isEmpty()) "no station" else ordered().joinToString(" + ") { it.kind.tile }

    companion object {
        /** Where the orders come in: `START` of the graph. */
        const val RAIL: String = "rail"

        /** Where the tickets go: `END` of the graph. The kitchen makes and bakes what they say, by rules. */
        const val KITCHEN: String = "kitchen"

        /** The places of the board a station can stand on. */
        const val SLOTS: Int = 6

        /** The board of the first shift: every order goes to the menu lookup. It costs nothing, and reads the name of a pizza and nothing else. */
        val FIRST: Blueprint =
            Blueprint().place(0, PieceKind.LOOKUP).connect(RAIL, Exit.NEXT, "lookup").connect("lookup", Exit.NEXT, KITCHEN)
    }
}
