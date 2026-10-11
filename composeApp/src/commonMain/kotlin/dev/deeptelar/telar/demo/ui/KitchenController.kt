package dev.deeptelar.telar.demo.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.deeptelar.telar.GraphConfig
import dev.deeptelar.telar.GraphEvent
import dev.deeptelar.telar.NodeExecutionException
import dev.deeptelar.telar.agent.ChatModel
import dev.deeptelar.telar.demo.kitchen.LineShift
import dev.deeptelar.telar.demo.kitchen.SHIFT_STEPS
import dev.deeptelar.telar.demo.kitchen.Service
import dev.deeptelar.telar.demo.kitchen.line
import dev.deeptelar.telar.demo.kitchen.lunchRush
import dev.deeptelar.telar.demo.kitchen.scriptedChef
import dev.deeptelar.telar.demo.kitchen.scriptedMaker
import dev.deeptelar.telar.demo.kitchen.scriptedOven
import dev.deeptelar.telar.demo.kitchen.scriptedReader
import dev.deeptelar.telar.demo.kitchen.shift
import dev.deeptelar.telar.demo.kitchen.soloChef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * How the kitchen of a shift is built. The orders are the same in each, so what differs in the
 * result is the kitchen.
 */
enum class KitchenLayout(val title: String, val detail: String) {
    Solo(
        "Solo chef",
        "One agent does everything. It is sent the whole conversation with every answer, so each answer costs more than the last.",
    ),
    Line(
        "The line",
        "Three stations work at the same time. The model only reads the orders. Plain code makes and bakes the pizzas, for no tokens.",
    ),
    LineOfModels(
        "The line, a model at every station",
        "The same three stations, with the model at each. A station is asked with what it needs to know now, not with a conversation.",
    ),
}

/** A shift that was played to its end, to compare with the next one. */
data class PlayedShift(val layout: KitchenLayout, val model: String, val shift: Service)

/**
 * Runs the kitchen rush and exposes the shift as Compose state.
 *
 * @param realModel the model of the service the player chose under Options, or `null` when the
 * scripted chefs do the work.
 * @param modelLabel what that model is called.
 * @param minuteMillis how long a minute of the kitchen is shown. The scripted chefs answer at once,
 * so without it a shift would be over before anyone saw it.
 * @param played where the shifts that ran to their end are kept.
 */
class KitchenController(
    private val realModel: ChatModel?,
    val modelLabel: String,
    private val scope: CoroutineScope,
    private val minuteMillis: Long = if (realModel == null) 250 else 40,
    played: MutableState<List<PlayedShift>> = mutableStateOf(emptyList()),
) {
    val scripted: Boolean = realModel == null

    var layout: KitchenLayout by mutableStateOf(KitchenLayout.Line)
        private set

    /** The most tokens the chefs of a shift may use together. */
    var budget: Int by mutableStateOf(DEFAULT_BUDGET)
        private set

    /** The shift as of its last minute that was shown, or `null` before the first shift. */
    var shift: Service? by mutableStateOf(null)
        private set

    /** The layout of [shift], which stays when the player picks another one for the next shift. */
    var playing: KitchenLayout by mutableStateOf(KitchenLayout.Line)
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

    fun choose(layout: KitchenLayout) {
        if (!running) this.layout = layout
    }

    fun budget(tokens: Int) {
        if (!running) budget = tokens.coerceIn(1_000, MAX_BUDGET)
    }

    /** Starts a new shift in the chosen kitchen. */
    fun start() {
        if (running) return
        val chosen = layout
        running = true
        finished = false
        error = null
        playing = chosen
        job = scope.launch {
            try {
                states(chosen).collect { next ->
                    // A minute of the kitchen takes a moment on the screen, so that the shift can be watched.
                    delay((next.kitchen.minute - (shift?.kitchen?.minute ?: 0)).coerceAtLeast(0) * minuteMillis)
                    shift = next
                }
                finished = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = (if (e is NodeExecutionException) e.cause?.message else null) ?: e.message ?: e.toString()
            } finally {
                running = false
                if (finished) shift?.let { history = history + PlayedShift(chosen, modelLabel, it) }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    /** The shift after each minute in which something was done, from the first one on. */
    private fun states(layout: KitchenLayout): Flow<Service> {
        val kitchen = lunchRush()
        return when (layout) {
            KitchenLayout.Solo -> {
                val begin = shift(kitchen, budget)
                shift = begin
                soloChef(realModel ?: scriptedChef(kitchen))
                    .stream(begin, GraphConfig(maxIterations = SHIFT_STEPS))
                    .mapNotNull { event -> (event as? GraphEvent.NodeCompleted)?.state }
            }
            else -> {
                val begin = LineShift(kitchen, budget)
                shift = begin
                val everywhere = layout == KitchenLayout.LineOfModels
                line(
                    reader = realModel ?: scriptedReader(kitchen),
                    prep = if (everywhere) realModel ?: scriptedMaker() else null,
                    oven = if (everywhere) realModel ?: scriptedOven() else null,
                ).stream(begin, GraphConfig(maxIterations = SHIFT_STEPS))
                    // The three stations end a minute together, so the kitchen is shown once for each minute.
                    .mapNotNull { event -> (event as? GraphEvent.NodeCompleted)?.takeIf { it.node == "clock" }?.state }
            }
        }
    }

    companion object {
        const val DEFAULT_BUDGET = 150_000
        const val MAX_BUDGET = 2_000_000
    }
}
