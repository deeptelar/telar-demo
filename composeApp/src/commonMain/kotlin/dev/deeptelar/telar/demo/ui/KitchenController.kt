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
import dev.deeptelar.telar.demo.kitchen.kitchen
import dev.deeptelar.telar.demo.kitchen.lunchRush
import dev.deeptelar.telar.demo.kitchen.scriptedStations
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
data class PlayedShift(val rush: Rush, val board: Blueprint, val model: String, val scripted: Boolean, val shift: Service)

/**
 * Something to reach in the bonus stage.
 *
 * @property countsTokens `true` when it is about the tokens of a shift. Those are set for the
 * scripted chefs, whose tokens are the same every time. A real model counts its own.
 */
class Goal(val text: String, val countsTokens: Boolean, private val reached: (Service) -> Boolean) {
    fun metBy(played: PlayedShift): Boolean = (played.scripted || !countsTokens) && reached(played.shift)
}

private fun Service.everyOrderOnTime(): Boolean = kitchen.score().let { it.points == it.orders * Score.ON_TIME_POINTS } && kitchen.standing.none { it.outcome == Outcome.OPEN }

private fun everyOrder() = Goal("Serve every order right and on time.", countsTokens = false) { it.everyOrderOnTime() }

private fun forFewerThan(tokens: Int, said: String) =
    Goal("Do it for fewer than $said tokens.", countsTokens = true) { it.everyOrderOnTime() && it.spent < tokens }

/**
 * The orders of a shift, which are the same every time it is played.
 *
 * @property orders the kitchen at the start of the shift.
 * @property goals what there is to reach in it, the easiest first.
 */
enum class Rush(val title: String, val detail: String, val orders: () -> Kitchen, val goals: List<Goal>) {
    /** About reading. Its second goal needs a sorter, and its third needs two. */
    Lunch(
        "Lunch rush",
        "Twelve orders in 95 minutes. It is about reading: guests change their mind, one table has an allergy, and one order only makes " +
            "sense if you know what another table had.",
        ::lunchRush,
        listOf(everyOrder(), forFewerThan(5_000, "5,000"), forFewerThan(4_000, "4,000")),
    ),

    /** About the oven. Every order on time needs the expediter, the second oven, and readers that work side by side. */
    Dinner(
        "Dinner rush",
        "Twelve orders in 75 minutes, most of them in two waves, and not due in the order they come. It is about the oven: one oven that " +
            "takes the tickets as they come is too slow, and the board has only six places.",
        ::dinnerRush,
        listOf(everyOrder(), forFewerThan(4_000, "4,000")),
    ),
}

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
 */
class KitchenController(
    private val realModel: ChatModel?,
    val modelLabel: String,
    private val scope: CoroutineScope,
    private val minuteMillis: Long = if (realModel == null) 250 else 40,
    private val stepMillis: Long = 0,
    played: MutableState<List<PlayedShift>> = mutableStateOf(emptyList()),
    drawn: MutableState<Blueprint> = mutableStateOf(Blueprint.FIRST),
    chosen: MutableState<Rush> = mutableStateOf(Rush.Lunch),
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

    /** What keeps a shift from starting on [board]. */
    var problems: List<String> by mutableStateOf(board.problems())
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

    /** The goals of the chosen shift, with whether a shift of this session reached each. */
    val goals: List<Pair<Goal, Boolean>> get() = rush.goals.map { goal -> goal to history.any { it.rush == rush && goal.metBy(it) } }

    /** Chooses the orders of the next shift. */
    fun choose(rush: Rush) {
        if (!running) this.rush = rush
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
        if (running) return
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
        problems = next.problems()
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
                if (finished) shift?.let { history = history + PlayedShift(orders, drawn, modelLabel, scripted, it) }
            }
        }
    }

    fun stop() {
        job?.cancel()
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
