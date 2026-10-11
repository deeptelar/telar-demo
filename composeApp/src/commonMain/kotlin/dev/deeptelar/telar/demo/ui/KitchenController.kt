package dev.deeptelar.telar.demo.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.deeptelar.telar.GraphConfig
import dev.deeptelar.telar.GraphEvent
import dev.deeptelar.telar.NodeExecutionException
import dev.deeptelar.telar.agent.ChatModel
import dev.deeptelar.telar.demo.kitchen.BOARD_STEPS
import dev.deeptelar.telar.demo.kitchen.Blueprint
import dev.deeptelar.telar.demo.kitchen.BoardShift
import dev.deeptelar.telar.demo.kitchen.Exit
import dev.deeptelar.telar.demo.kitchen.Kitchen
import dev.deeptelar.telar.demo.kitchen.Outcome
import dev.deeptelar.telar.demo.kitchen.PieceKind
import dev.deeptelar.telar.demo.kitchen.Score
import dev.deeptelar.telar.demo.kitchen.Service
import dev.deeptelar.telar.demo.kitchen.dinnerRush
import dev.deeptelar.telar.demo.kitchen.firstOrders
import dev.deeptelar.telar.demo.kitchen.firstThingsFirst
import dev.deeptelar.telar.demo.kitchen.kitchen
import dev.deeptelar.telar.demo.kitchen.lookingBack
import dev.deeptelar.telar.demo.kitchen.lunchRush
import dev.deeptelar.telar.demo.kitchen.scriptedStations
import dev.deeptelar.telar.demo.kitchen.sorting
import dev.deeptelar.telar.demo.kitchen.specialWishes
import dev.deeptelar.telar.demo.kitchen.theBook
import dev.deeptelar.telar.demo.kitchen.twoOvens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A shift that was played to its end, to compare with the next one.
 *
 * @property rush the orders it had.
 * @property board the board it was played on.
 * @property scripted `true` when no model was asked, so that its tokens are a guess.
 */
data class PlayedShift(val rush: Rush, val board: Blueprint, val model: String, val scripted: Boolean, val shift: Service) {
    /** `true` when every order was served right and on time, which every medal asks for. */
    val right: Boolean
        get() = shift.kitchen.score().let { it.points == it.orders * Score.ON_TIME_POINTS } && shift.kitchen.standing.none { it.outcome == Outcome.OPEN }

    /** The stations on its board. */
    val stations: Int get() = board.pieces.size
}

/**
 * What a shift can win. A board that serves every order has [Right]. The other two pull against each
 * other: a board that reads for few tokens sorts its orders, and sorting takes stations.
 */
enum class Medal(val title: String) { Right("Right"), Cheap("Cheap"), Small("Small") }

/**
 * A medal of a shift and what it takes.
 *
 * [Medal.Cheap] is set for the scripted chefs, whose tokens are the same every time. A real model
 * counts its own, so its shifts do not win it.
 */
class Goal(val medal: Medal, val text: String, private val reached: (PlayedShift) -> Boolean) {
    fun metBy(played: PlayedShift): Boolean = played.right && (played.scripted || medal != Medal.Cheap) && reached(played)
}

/**
 * The orders of a shift, which are the same every time it is played. The first seven are short, and
 * each needs one station that the shifts before it did not have. The last two are the rushes, with
 * every station on offer.
 *
 * @property orders the kitchen at the start of the shift.
 * @property teaches the station that is new in a short shift. `null` for a rush.
 * @property cheap the tokens a shift has to stay under for [Medal.Cheap], where there is that medal.
 * @property small the most stations a board may have for [Medal.Small], where there is that medal.
 */
enum class Rush(
    val title: String,
    val detail: String,
    val orders: () -> Kitchen,
    val teaches: PieceKind? = null,
    val cheap: Int? = null,
    val small: Int? = null,
) {
    Opening(
        "First orders",
        "Four guests, four pizzas from the menu. Put the menu lookup on a place of the board. Then draw two arrows: from the rail to the " +
            "lookup, and from the lookup to the kitchen.",
        ::firstOrders,
        teaches = PieceKind.LOOKUP,
    ),
    Wishes(
        "Wishes",
        "Three of four guests want their pizza changed. The lookup reads the name of a pizza and nothing else. The reader is a model: it " +
            "reads what the guest wants, for tokens and a minute an order.",
        ::specialWishes,
        teaches = PieceKind.READER,
    ),
    Sorting(
        "Sorting",
        "Four of six orders are a pizza of the menu and nothing more, and a model is wasted on them. A sorter sends an order one of two " +
            "ways: it has an arrow for yes and an arrow for no.",
        ::sorting,
        teaches = PieceKind.ONLY_MENU,
        cheap = 1_000,
    ),
    Book(
        "The book",
        "Two orders only make sense with what was said before: one table wants what another had, and an allergy holds for every pizza of " +
            "its table. The reader with the book is told everything the guests said.",
        ::theBook,
        teaches = PieceKind.BOOK_READER,
    ),
    LookingBack(
        "Looking back",
        "The book gets longer with every order, and every order that goes to its reader pays for all of it. The second sorter finds the " +
            "orders that look back. The others can go to a station that costs less.",
        ::lookingBack,
        teaches = PieceKind.LOOKS_BACK,
        cheap = 2_800,
    ),
    Urgent(
        "Urgent",
        "Four orders at once, and the two that came last are due first. The kitchen takes its tickets as they come. The expediter writes " +
            "on a ticket when it is due, and the kitchen takes the most urgent one first.",
        ::firstThingsFirst,
        teaches = PieceKind.EXPEDITER,
    ),
    TwoOvens(
        "Two ovens",
        "Six orders at once, all due at the same minute, and the oven has two places. A second oven bakes the pizzas of the orders that " +
            "pass it.",
        ::twoOvens,
        teaches = PieceKind.SECOND_OVEN,
    ),

    /** About reading. One reader with the book is the small board, and the cheap one needs two sorters. */
    Lunch(
        "Lunch rush",
        "Twelve orders in 95 minutes. It is about reading: guests change their mind, one table has an allergy, and one order only makes " +
            "sense if you know what another table had.",
        ::lunchRush,
        cheap = 4_000,
        small = 1,
    ),

    /**
     * About the oven. Every order on time needs the expediter and the second oven. With one reader that
     * is a board of three stations, and the cheap one has five: a sorter, so that not every order is read by a model.
     */
    Dinner(
        "Dinner rush",
        "Twelve orders in 75 minutes, most of them in two waves, and not due in the order they come. It is about the oven: one oven that " +
            "takes the tickets as they come is too slow, and the board has only six places.",
        ::dinnerRush,
        cheap = 4_000,
        small = 3,
    ),
    ;

    /** Where the shift stands in the menu, from 1. */
    val number: Int get() = ordinal + 1

    /** The shift after this one. */
    val next: Rush? get() = entries.getOrNull(ordinal + 1)

    /** The stations a board of this shift may have: the ones the short shifts taught up to here, and all of them in a rush. */
    val offers: Set<PieceKind>
        get() = if (teaches == null) PieceKind.entries.toSet() else entries.take(ordinal + 1).mapNotNull { it.teaches }.toSet()

    /** The points of a shift in which every order that was not cancelled is served right and on time. */
    val points: Int get() = orders().let { it.at(it.closesAt).score().orders * Score.ON_TIME_POINTS }

    /** The medals of this shift. A short shift has no medal for what it does not teach. */
    val goals: List<Goal> =
        listOfNotNull(
            Goal(Medal.Right, "Serve every order right and on time.") { true },
            cheap?.let { tokens -> Goal(Medal.Cheap, "Do it for fewer than ${tokens.grouped()} tokens.") { it.shift.spent < tokens } },
            small?.let { stations ->
                Goal(Medal.Small, if (stations == 1) "Do it with one station." else "Do it with $stations stations or fewer.") { it.stations <= stations }
            },
        )
}

/** 12345 as "12,345". */
internal fun Int.grouped(): String =
    toString()
        .reversed()
        .chunked(3)
        .joinToString(",")
        .reversed()

/**
 * The bonus stage: holds the board the player draws, runs a shift on it, and exposes both as Compose state.
 *
 * @param realModel the model of the service the player chose under Options, or `null` when the
 * scripted chefs do the work.
 * @param modelLabel what that model is called.
 * @param minuteMillis how long a minute of the kitchen is shown. The scripted chefs answer at once,
 * so without it a shift would be over before anyone saw it.
 * @param stepMillis how long an order is shown at each station of the board it goes through.
 * @param played where the shifts that ran to their end are kept.
 * @param drawn where the board is kept.
 * @param chosen where the shift the player chose is kept.
 * @param earned where the medals the player has won are kept, each with its shift.
 * @param onEarned told all of [earned] when a shift wins a medal that was not won before, to save it.
 */
class KitchenController(
    private val realModel: ChatModel?,
    val modelLabel: String,
    private val scope: CoroutineScope,
    private val minuteMillis: Long = if (realModel == null) 250 else 40,
    private val stepMillis: Long = 0,
    played: MutableState<List<PlayedShift>> = mutableStateOf(emptyList()),
    drawn: MutableState<Blueprint> = mutableStateOf(Blueprint()),
    chosen: MutableState<Rush> = mutableStateOf(Rush.entries.first()),
    earned: MutableState<Set<Pair<Rush, Medal>>> = mutableStateOf(emptySet()),
    private val onEarned: (Set<Pair<Rush, Medal>>) -> Unit = {},
) {
    val scripted: Boolean = realModel == null

    /** The shift the next start plays. */
    var rush: Rush by chosen
        private set

    /** The orders of [shift], which stay when the player chooses the other shift for the next one. */
    var playingRush: Rush by mutableStateOf(rush)
        private set

    /** The board as the player has drawn it. It comes from outside, like [history]. */
    var board: Blueprint by drawn
        private set

    /** What keeps a shift from starting on [board]: what is wrong with its arrows, and stations the chosen shift does not have yet. */
    var problems: List<String> by mutableStateOf(check(board, rush))
        private set

    /** The medals won so far, in this session and before it. */
    var earned: Set<Pair<Rush, Medal>> by earned
        private set

    /** The tile the player pressed last: a station or the rail. Its arrows are shown and can be drawn. */
    var selected: String? by mutableStateOf(null)
        private set

    /** The way out of [selected] that the next tile the player presses is connected to. */
    var armed: Exit? by mutableStateOf(null)
        private set

    /** The empty place the player is choosing a station for. */
    var picking: Int? by mutableStateOf(null)
        private set

    /** The most tokens the chefs of a shift may use together. */
    var budget: Int by mutableStateOf(DEFAULT_BUDGET)
        private set

    /** The shift as of its last minute that was shown, or `null` before the first shift. */
    var shift: BoardShift? by mutableStateOf(null)
        private set

    /** The board of [shift], which stays when the player draws another one for the next shift. */
    var playing: Blueprint by mutableStateOf(board)
        private set

    /** The station that has an order right now, while a shift runs. */
    var at: String? by mutableStateOf(null)
        private set

    /** The number of the order that is on the board right now. */
    var reading: Int? by mutableStateOf(null)
        private set
    var running: Boolean by mutableStateOf(false)
        private set

    /** `true` when [shift] ran to its end: the kitchen closed, every order was served, or the tokens were spent. */
    var finished: Boolean by mutableStateOf(false)
        private set

    /** What a model failed with, when one ended the shift. */
    var error: String? by mutableStateOf(null)
        private set

    /**
     * The shifts that were played to their end in this session, oldest first. The list comes from
     * outside, so that it is still there after the player chose another model under Options.
     */
    var history: List<PlayedShift> by played
        private set

    private var job: Job? = null

    /** The medals of the chosen shift, with whether each was won. */
    val goals: List<Pair<Goal, Boolean>> get() = medals(rush)

    /** The medals of [rush], with whether each was won. */
    fun medals(rush: Rush): List<Pair<Goal, Boolean>> = rush.goals.map { goal -> goal to ((rush to goal.medal) in earned) }

    /**
     * The best that the shifts of this session did on what [medal] measures, in the chosen shift: the
     * most points, or the fewest tokens or stations of a shift that served every order right and on
     * time. `null` when no shift counts yet.
     */
    fun best(medal: Medal): Int? {
        val played = history.filter { it.rush == rush }
        return when (medal) {
            Medal.Right -> played.maxOfOrNull { it.shift.kitchen.score().points }
            Medal.Cheap -> played.filter { it.right && it.scripted }.minOfOrNull { it.shift.spent }
            Medal.Small -> played.filter { it.right }.minOfOrNull { it.stations }
        }
    }

    /** Chooses the orders of the next shift. The board stays, so that a shift starts with the board of the one before. */
    fun choose(rush: Rush) {
        if (running) return
        this.rush = rush
        problems = check(board, rush)
        picking = null
    }

    /** The player pressed an empty place: the stations to choose from are offered for it. */
    fun tapSlot(slot: Int) {
        if (running || board.at(slot) != null) return
        picking = if (picking == slot) null else slot
        selected = null
        armed = null
    }

    /** Puts a station of [kind] on the place the player pressed, and selects it so that its arrow can be drawn next. */
    fun put(kind: PieceKind) {
        val slot = picking ?: return
        if (running || kind !in rush.offers) return
        draw(board.place(slot, kind))
        select(board.at(slot)?.id)
    }

    /**
     * The player pressed the tile of [id]: a station, the rail or the kitchen. With a way out armed,
     * the arrow from it is drawn to this tile. Otherwise the tile is selected, or let go when it
     * was selected already.
     */
    fun tap(id: String) {
        if (running) return
        val from = selected
        val exit = armed
        if (from != null && exit != null && id != from) {
            val connected = board.connect(from, exit, id)
            if (connected != board) {
                draw(connected)
                armed = board.freeExit(from)
                return
            }
        }
        // The kitchen has no arrows of its own to show.
        if (id != Blueprint.KITCHEN) select(id.takeIf { it != selected })
    }

    /** Arms a way out of the selected tile again, so that its arrow can be drawn somewhere else. */
    fun redraw(exit: Exit) {
        val from = selected ?: return
        if (running) return
        draw(board.disconnect(from, exit))
        armed = exit
    }

    /** Takes the selected station off the board, with its arrows. */
    fun remove() {
        val id = selected?.takeIf { board.piece(it) != null } ?: return
        if (running) return
        draw(board.remove(id))
        select(null)
    }

    /** Replaces the board with [blueprint]: an empty one, or the board of an earlier shift. */
    fun load(blueprint: Blueprint) {
        if (running) return
        draw(blueprint)
        select(null)
    }

    private fun select(id: String?) {
        selected = id
        armed = id?.let(board::freeExit)
        picking = null
    }

    private fun draw(next: Blueprint) {
        board = next
        problems = check(next, rush)
    }

    private fun check(board: Blueprint, rush: Rush): List<String> =
        board.problems() +
            board.pieces.map { it.kind }.distinct().filter { it !in rush.offers }.map { kind ->
                "${kind.title} comes in a later shift. Take it off the board, or choose that shift."
            }

    fun budget(tokens: Int) {
        if (!running) budget = tokens.coerceIn(1_000, MAX_BUDGET)
    }

    /** Starts a new shift on the board, if the board can run. */
    fun start() {
        if (running || problems.isNotEmpty()) return
        val drawn = board
        val orders = rush
        select(null)
        running = true
        finished = false
        error = null
        playing = drawn
        playingRush = orders
        job = scope.launch {
            try {
                val begin = BoardShift(orders.orders(), budget)
                shift = begin
                val models = realModel?.let { model -> { _: PieceKind -> model } } ?: scriptedStations(begin.kitchen)
                drawn.kitchen(models).stream(begin, GraphConfig(maxIterations = BOARD_STEPS)).collect { show(it) }
                finished = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.reason()
            } finally {
                running = false
                at = null
                reading = null
                if (finished) shift?.let { keep(PlayedShift(orders, drawn, modelLabel, scripted, it)) }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private fun keep(played: PlayedShift) {
        history = history + played
        val won = played.rush.goals.filter { it.metBy(played) }.map { played.rush to it.medal }.toSet() - earned
        if (won.isEmpty()) return
        earned = earned + won
        onEarned(earned)
    }

    /** Forgets the medals. */
    fun forget() {
        earned = emptySet()
    }

    /** Shows what the run of the kitchen's graph tells: where the order on the board is, and the kitchen after each minute. */
    private suspend fun show(event: GraphEvent<BoardShift>) {
        when (event) {
            // The board is a subgraph, so its nodes report inside an event of the node that runs it.
            is GraphEvent.SubgraphEvent -> (event.innermost as? GraphEvent.NodeStarted<*>)?.let { started ->
                at = started.node
                reading = event.state.slip?.order
                delay(stepMillis)
            }
            is GraphEvent.NodeCompleted -> when (event.node) {
                "board" -> {
                    at = null
                    reading = null
                }
                "clock" -> {
                    // A minute of the kitchen takes a moment on the screen, so that the shift can be watched.
                    delay((event.state.kitchen.minute - (shift?.kitchen?.minute ?: 0)).coerceAtLeast(0) * minuteMillis)
                    shift = event.state
                }
            }
            else -> Unit
        }
    }

    /** What went wrong, in the words of the model's service when a station's model failed. */
    private fun Throwable.reason(): String {
        var failure: Throwable = this
        while (failure is NodeExecutionException) failure = failure.cause ?: break
        return failure.message ?: failure.toString()
    }

    companion object {
        const val DEFAULT_BUDGET = 150_000
        const val MAX_BUDGET = 2_000_000
    }
}
