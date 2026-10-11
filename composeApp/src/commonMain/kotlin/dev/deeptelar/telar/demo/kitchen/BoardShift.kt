package dev.deeptelar.telar.demo.kitchen

import dev.deeptelar.telar.CompiledGraph
import dev.deeptelar.telar.NodeRef
import dev.deeptelar.telar.START
import dev.deeptelar.telar.StateGraph
import dev.deeptelar.telar.agent.ChatMessage
import dev.deeptelar.telar.agent.ChatModel
import dev.deeptelar.telar.agent.ChatRequest
import dev.deeptelar.telar.agent.Tool
import dev.deeptelar.telar.agent.execute

/**
 * One order on its way across the board, or one thing a guest said about an order afterwards. It is
 * the state of the graph the player drew: every order is one run of that graph.
 *
 * @property words the order as it stands now, with what the guest said later after "Then:".
 * @property said what the guest said this time. It is the line the book keeps.
 * @property book what the guests said before, the oldest first. Every station has it, and only the
 * reader with the book sends it to its model.
 * @property minute the minute the slip has got to. A model takes a minute for an answer and reads
 * one slip at a time, so a slip also waits where others are before it.
 * @property desks the stations with a model: what each cost, and until when it is busy.
 * @property ticket the toppings a station wrote down, once one has.
 * @property path the stations the slip went through.
 * @property events what the stations did with it.
 */
data class Slip(
    val order: Int,
    val table: Int,
    val words: String,
    val said: String = words,
    val book: List<String> = emptyList(),
    val minute: Int = 0,
    val desks: Map<String, Desk> = emptyMap(),
    val ticket: List<Topping>? = null,
    val path: List<String> = emptyList(),
    val events: List<Event> = emptyList(),
) {
    val isOnlyAMenuPizza: Boolean get() = PieceKind.ONLY_MENU.says(words)
    val isAboutEarlier: Boolean get() = PieceKind.LOOKS_BACK.says(words)

    /** The slip as a reader is told it. */
    val line: String get() = "Order #$order, table $table: $words"

    /** The line the book keeps of it. */
    val entry: String get() = "#$order, table $table: $said"
}

/** A ticket that a station wrote, which reaches the kitchen at minute [at]. */
data class Written(
    val order: Int,
    val toppings: List<Topping>,
    val at: Int,
)

/**
 * A shift in the kitchen of a [Blueprint].
 *
 * @property floor the kitchen behind the board: the tickets that reached it, the two stations that
 * make and bake by rules, and the log.
 * @property book what the guests said so far, a line for each order and each change.
 * @property heardUntil the minute up to which the rail has taken in what the guests said.
 * @property waiting the slips of this minute that have not been on the board yet.
 * @property slip the slip that is on the board now.
 * @property written the tickets that are still with a model: they reach the kitchen when its answer is done.
 * @property coming what the stations did that the kitchen's clock has not got to.
 * @property stations the stations of the board that ask a model, by the id of their piece.
 * @property routes the stations each order went through, by its number.
 */
data class BoardShift(
    val floor: LineShift,
    val book: List<String> = emptyList(),
    val heardUntil: Int = floor.kitchen.minute - 1,
    val waiting: List<Slip> = emptyList(),
    val slip: Slip? = null,
    val written: List<Written> = emptyList(),
    val coming: List<Event> = emptyList(),
    val stations: Map<String, Desk> = emptyMap(),
    val routes: Map<Int, List<String>> = emptyMap(),
) : Service {
    constructor(kitchen: Kitchen, tokenBudget: Int = DEFAULT_TOKEN_BUDGET) : this(LineShift(kitchen, tokenBudget))

    override val kitchen: Kitchen get() = floor.kitchen
    override val tokenBudget: Int get() = floor.tokenBudget
    override val desks: Map<String, Desk> get() = stations + mapOf("prep" to floor.prep, "oven" to floor.oven)
    override val log: List<Event> get() = floor.log

    /** The tickets that reached the kitchen: the toppings of each order, by its number. */
    val tickets: Map<Int, List<Topping>> get() = floor.tickets
}

/** The steps a shift on a board may take: `GraphConfig.maxIterations`. A minute is four steps, and every slip two more. */
const val BOARD_STEPS: Int = 600

private val READER_RULES: List<String> =
    listOf(
        "You read one order of a pizzeria and write the ticket for the kitchen. The kitchen makes exactly what the ticket says and never sees the guest's words.",
        "",
        MENU_RULES,
        "",
        "Call write_ticket once, with every topping the pizza must have. What the guest said after ordering follows \"Then:\", " +
            "and the ticket is the pizza as it is wanted now.",
    )

/** What the reader that is told one order, and nothing else, is asked to do. */
val READER_ALONE: String =
    (READER_RULES + "You know this one order and nothing else. If its words do not say which pizza is meant, write no ticket and say why in one sentence.")
        .joinToString("\n")

/** What the reader that is told the whole book is asked to do. */
val READER_WITH_BOOK: String =
    (
        READER_RULES +
            "Before the order you are told what the guests said earlier in this shift. Orders build on it: a guest may want what another " +
            "table had, and what a table said about an allergy holds for every pizza it orders. Do not explain what you do."
    ).joinToString("\n")

/**
 * The graph of this board, which one order runs through: a node for each station, an edge for each
 * arrow, and a conditional edge for each sorter. The rail is `START` and the kitchen is `END`.
 *
 * @param models the model of each station that asks one.
 * @throws dev.deeptelar.telar.GraphValidationException if the board is not a graph that can run.
 */
fun Blueprint.graph(models: (PieceKind) -> ChatModel): CompiledGraph<Slip> =
    StateGraph<Slip> {
        val nodes = pieces.associate { piece -> piece.id to node(piece.id) { slip -> slip.at(piece, models) } }

        fun ref(id: String): NodeRef = if (id == Blueprint.KITCHEN) NodeRef.END else nodes.getValue(id)

        target(Blueprint.RAIL, Exit.NEXT)?.let { START then ref(it) }
        pieces.forEach { piece ->
            val node = nodes.getValue(piece.id)
            if (!piece.kind.sorts) {
                target(piece.id, Exit.NEXT)?.let { node then ref(it) }
                return@forEach
            }
            val yes = target(piece.id, Exit.YES)?.let(::ref) ?: return@forEach
            val no = target(piece.id, Exit.NO)?.let(::ref) ?: return@forEach
            conditionalEdge(node, targets = setOf(yes, no)) { slip -> if (piece.kind.says(slip.words)) yes else no }
        }
    }.compile()

/** The slip after the station [piece] had it. */
private suspend fun Slip.at(piece: Piece, models: (PieceKind) -> ChatModel): Slip {
    val here = copy(path = path + piece.id)
    return when (piece.kind) {
        PieceKind.LOOKUP -> here.lookUp()
        PieceKind.READER, PieceKind.BOOK_READER -> here.read(piece, models(piece.kind))
        // A sorter only decides which arrow the slip takes, and its conditional edge does that.
        PieceKind.ONLY_MENU, PieceKind.LOOKS_BACK -> here
    }
}

/** Writes the ticket of the first menu pizza that the order names. It does not read what else the order says. */
private fun Slip.lookUp(): Slip {
    val pizza = MENU.entries.firstOrNull { it.key in words.lowercase() }
    val station = PieceKind.LOOKUP.tile
    return if (pizza == null) {
        copy(events = events + Event(minute, station, "No ticket for #$order: no pizza of the menu is named in it.", refused = true))
    } else {
        copy(ticket = pizza.value, events = events + Event(minute, station, "Ticket #$order: ${pizza.value.words()}. It read \"${pizza.key}\"."))
    }
}

/**
 * Asks the model of [piece] for the ticket once. The answer takes a minute, and the model starts
 * only when it is done with the slip before, so the slip leaves later than it came.
 */
private suspend fun Slip.read(piece: Piece, model: ChatModel): Slip {
    val withBook = piece.kind == PieceKind.BOOK_READER
    val desk = desks[piece.id] ?: Desk()
    val done = maxOf(minute, desk.busyUntil) + Kitchen.THINK_MINUTES
    var written: List<Topping>? = null
    val pad =
        listOf(
            Tool<Ticket>("write_ticket", "Writes the ticket of the order: the toppings its pizza must have.") { ticket ->
                if (ticket.order != order) throw KitchenException("The order you are reading is #$order.")
                if (ticket.toppings.isEmpty()) throw KitchenException("A pizza needs at least one topping.")
                written = ticket.toppings
                "Noted."
            },
        )
    val asked = if (withBook && book.isNotEmpty()) "Said earlier in this shift:\n${book.joinToString("\n")}\n\nNow read:\n$line" else line
    val response = model.chat(ChatRequest(listOf(ChatMessage.User(asked)), if (withBook) READER_WITH_BOOK else READER_ALONE, pad.map { it.spec }))
    response.message.toolCalls.forEach { pad.execute(listOf(it)) }

    val toppings = written
    val event =
        if (toppings == null) {
            val why = response.message.text.trim().take(SAID_LENGTH).ifEmpty { "it wrote none" }
            Event(done, piece.kind.tile, "No ticket for #$order: $why", refused = true)
        } else {
            Event(done, piece.kind.tile, "Ticket #$order: ${toppings.words()}.")
        }
    return copy(
        minute = done,
        ticket = toppings ?: ticket,
        desks = desks + (piece.id to desk.answered(response.usage).copy(busyUntil = done)),
        events = events + event,
    )
}

/** How much of what a model said instead of a ticket goes into the log. */
private const val SAID_LENGTH = 120

/**
 * The kitchen of this board as one graph. Run it with `GraphConfig(maxIterations = BOARD_STEPS)`.
 *
 * - `rail` hears what the guests said in this minute and puts one slip on the board.
 * - `board` is the graph the player drew, as a subgraph. A slip runs through it from the rail to
 *   the kitchen, and then `rail` puts the next one on, until none is left for this minute.
 * - `prep` and `oven` are the kitchen behind the board. They work at the same time, by the rules of
 *   [line], from the tickets that reached them. `clock` ends the minute.
 *
 * A slip is read at once, however many minutes its models take: nothing a reader does depends on
 * the pizzas. Its ticket reaches the kitchen in the minute the last answer is done.
 */
fun Blueprint.kitchen(models: (PieceKind) -> ChatModel): CompiledGraph<BoardShift> {
    val drawn = graph(models)
    return StateGraph<BoardShift> {
        val rail = node("rail") { it.hear() }
        val board = subgraph("board", drawn, state = { checkNotNull(it.slip) { "The board is asked only when a slip is on it" } }) { shift, slip ->
            shift.took(slip)
        }
        val bell = node("bell") { it }
        val clock = node("clock") { it.copy(floor = it.floor.copy(kitchen = it.kitchen.after(1))) }
        val make =
            node("prep", work = { shift -> shift.floor.prepTurn(null) }) { shift, turn ->
                if (turn == null) shift else shift.copy(floor = shift.floor.with("prep", turn).copy(prep = shift.floor.prep.after(turn)))
            }
        val bake =
            node("oven", work = { shift -> shift.floor.ovenTurn(null) }) { shift, turn ->
                if (turn == null) shift else shift.copy(floor = shift.floor.with("oven", turn).copy(oven = shift.floor.oven.after(turn)))
            }

        START then rail
        conditionalEdge(rail, targets = setOf(board, bell)) { shift -> if (shift.slip != null) board else bell }
        board then rail
        listOf(make, bake).forEach { bell then it then clock }
        conditionalEdge(clock, targets = setOf(rail, NodeRef.END)) { shift -> if (shift.over) NodeRef.END else rail }
    }.compile()
}

/** What the guests said after minute [since] and up to now, as slips: the orders they placed and the changes they made. */
private fun Kitchen.slips(since: Int): List<Slip> {
    val placed = orders.filter { it.placedAt in (since + 1)..minute }.map { it.placedAt to Slip(it.number, it.table, it.text) }
    val changed =
        changes.filter { it.wanted != null && it.at in (since + 1)..minute }.mapNotNull { change ->
            val order = at(change.at).standing.first { it.number == change.order }
            if (order.outcome != Outcome.OPEN) null else change.at to Slip(order.number, order.table, order.text, said = change.text)
        }
    return (placed + changed).sortedBy { it.first }.map { it.second }
}

/**
 * This shift after the rail took in the news of the minute: the tickets and the log lines whose
 * minute has come are with the kitchen, and the next slip is on the board, if there is one.
 */
private fun BoardShift.hear(): BoardShift {
    val now = kitchen.minute
    val queue = waiting + if (heardUntil < now) kitchen.slips(heardUntil) else emptyList()
    val next = queue.firstOrNull()?.copy(book = book, minute = now, desks = stations)
    val due = written.filter { it.at <= now }
    val told = coming.filter { it.minute <= now }
    return copy(
        floor = floor.copy(tickets = floor.tickets + due.associate { it.order to it.toppings }, log = floor.log + told.sortedBy { it.minute }),
        book = if (next == null) book else book + next.entry,
        heardUntil = now,
        waiting = queue.drop(1),
        slip = next,
        written = written - due.toSet(),
        coming = coming - told.toSet(),
    )
}

/** This shift after [slip] came off the board. */
private fun BoardShift.took(slip: Slip): BoardShift =
    copy(
        stations = slip.desks,
        written = written + listOfNotNull(slip.ticket?.let { Written(slip.order, it, slip.minute) }),
        coming = coming + slip.events,
        routes = routes + (slip.order to slip.path),
        slip = null,
    )
