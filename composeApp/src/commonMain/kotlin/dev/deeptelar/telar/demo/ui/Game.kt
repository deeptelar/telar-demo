package dev.deeptelar.telar.demo.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.deeptelar.telar.Checkpoint
import dev.deeptelar.telar.CheckpointCorruptedException
import dev.deeptelar.telar.Checkpointer
import dev.deeptelar.telar.END
import dev.deeptelar.telar.GraphConfig
import dev.deeptelar.telar.GraphEvent
import dev.deeptelar.telar.GraphResult
import dev.deeptelar.telar.GraphTopology
import dev.deeptelar.telar.NodeExecutionException
import dev.deeptelar.telar.START
import dev.deeptelar.telar.agent.ChatMessage
import dev.deeptelar.telar.agent.ChatModel
import dev.deeptelar.telar.agent.textDelta
import dev.deeptelar.telar.demo.game.Desk
import dev.deeptelar.telar.demo.game.Mail
import dev.deeptelar.telar.demo.game.Stage
import dev.deeptelar.telar.demo.game.Ticket
import dev.deeptelar.telar.demo.game.changesSince
import dev.deeptelar.telar.demo.game.stages
import dev.deeptelar.telar.demo.kitchen.Blueprint
import dev.deeptelar.telar.demo.kitchen.Guide
import dev.deeptelar.telar.demo.storage.KeyValueStore

enum class Phase {
    /** Nothing in progress. */
    Ready,

    /** The run stopped at a save point and waits for the player's decision. */
    SavePoint,

    /** A saved run stopped somewhere else, because the player stopped it or closed the app mid-run. */
    Unfinished,

    /** A node failed. */
    GameOver,

    /** The run reached END. */
    Clear,
}

/** What kind of line a [LogLine] is, which decides its colour. [Detail] says more about the line above it. */
enum class Tone { Plain, Detail, Saved, Failed, Done }

/** One line of a stage's run log: a short [tag] and what happened. */
data class LogLine(val tag: String, val text: String, val tone: Tone = Tone.Plain)

/** The log's line for a step whose nodes handed the ticket on as they got it. */
private const val UNCHANGED = "ticket unchanged"

/** Runs the graph of one stage and exposes the run as Compose state. */
class StageController(
    val stage: Stage,
    desk: Desk,
    private val checkpointer: Checkpointer<Ticket>,
    private val scope: CoroutineScope,
    private val onClear: (Stage) -> Unit = {},
) {
    private val graph = stage.graph(desk)
    private val threadId = "stage-${stage.number}"
    private val config = GraphConfig(threadId = threadId, checkpointer = checkpointer, interruptBefore = stage.pauseBefore)

    /** The graph's structure, for drawing the board. */
    val topology: GraphTopology = graph.topology

    var phase: Phase by mutableStateOf(Phase.Ready)
        private set
    var running: Boolean by mutableStateOf(false)
        private set

    /** The ticket as of the last finished step. */
    var ticket: Ticket? by mutableStateOf(null)
        private set

    /** The ticket before the last finished step, to show what that step changed. `null` before the first step. */
    var ticketBefore: Ticket? by mutableStateOf(null)
        private set

    /** The nodes that are running right now. */
    var active: Set<String> by mutableStateOf(emptySet())
        private set

    /** The nodes that finished in this run, with START, and with END once the run got there. */
    var visited: Set<String> by mutableStateOf(emptySet())
        private set

    /** The arrows this run followed, as pairs of node names. */
    var trail: Set<Pair<String, String>> by mutableStateOf(emptySet())
        private set

    /** The nodes a saved run starts with when it continues. Empty when nothing is saved. */
    var waitingAt: List<String> by mutableStateOf(emptyList())
        private set

    /** The node that failed, when the phase is [Phase.GameOver]. */
    var failedNode: String? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var log: List<LogLine> by mutableStateOf(emptyList())
        private set

    /** What a model has written so far of the answer it is working on. Empty when no model is writing. */
    var writing: String by mutableStateOf("")
        private set

    /** The node whose code is shown under the board, after the player pressed its tile. */
    var codeOf: String? by mutableStateOf(null)
        private set

    /** Shows the code of [node], or puts it away when it is the one already shown. */
    fun toggleCode(node: String) {
        codeOf = if (codeOf == node) null else node
    }

    fun closeCode() {
        codeOf = null
    }
    var step: Int by mutableStateOf(0)
        private set

    /**
     * The checkpoints of this stage's run, oldest first, as `CompiledGraph.history` reads them from the
     * checkpointer. They are read again whenever the run saves one, so a player can watch them arrive.
     */
    var checkpoints: List<Checkpoint<Ticket>> by mutableStateOf(emptyList())
        private set

    /** True when what is shown came from storage rather than from a run in this session. */
    var restored: Boolean by mutableStateOf(false)
        private set

    private var job: Job? = null
    private var lastNodes: List<String> = emptyList()

    /** The choices of conditional edges that the log already has, so a retry does not report one twice. */
    private var chosen: Set<Pair<String, String>> = emptySet()
    private var lastMail: Mail? = null

    init {
        scope.launch {
            phase = savedPhase()
            restored = phase != Phase.Ready
            checkpoints = savedCheckpoints()
        }
    }

    /** Starts a new run for [mail]. Whatever was saved for this stage is replaced. */
    fun play(mail: Mail) {
        if (running || mail.text.isBlank()) return
        lastMail = mail
        ticket = Ticket(mail.from, mail.text.trim())
        ticketBefore = null
        visited = setOf(START)
        trail = emptySet()
        log = emptyList()
        writing = ""
        step = 0
        lastNodes = listOf(START)
        chosen = emptySet()
        follow(graph.stream(ticket!!, config))
    }

    /** Answers a save point: the decision is written into the ticket and the run continues. */
    fun decide(approved: Boolean) {
        if (running) return
        log = log + LogLine("YOU", if (approved) "approved" else "denied", Tone.Saved) + LogLine("", "approved = $approved", Tone.Detail)
        follow(graph.streamResume(config) { it.copy(approved = approved) })
    }

    /** Continues after a failure or a stop: from the last save if there is one, from the start otherwise. */
    fun retry() {
        if (running) return
        if (waitingAt.isNotEmpty()) follow(graph.streamResume(config)) else lastMail?.let(::play)
    }

    fun stop() {
        job?.cancel()
    }

    /** Throws the run away, saved or not, and clears the board. */
    fun reset() {
        scope.launch {
            job?.cancelAndJoin()
            checkpointer.delete(threadId)
            phase = Phase.Ready
            ticket = null
            ticketBefore = null
            checkpoints = emptyList()
            visited = emptySet()
            trail = emptySet()
            waitingAt = emptyList()
            log = emptyList()
            writing = ""
            step = 0
            error = null
            failedNode = null
            restored = false
        }
    }

    private fun follow(events: Flow<GraphEvent<Ticket>>) {
        if (running) return
        running = true
        restored = false
        error = null
        failedNode = null
        waitingAt = emptyList()
        job = scope.launch {
            var outcome: Phase? = null
            try {
                events.collect { event ->
                    record(event)
                    // A run saves its input before its first node starts, each step before StepCompleted,
                    // and a pause before Interrupted.
                    if (event is GraphEvent.NodeStarted || event is GraphEvent.StepCompleted || event is GraphEvent.Interrupted) {
                        checkpoints = savedCheckpoints()
                    }
                    if (event is GraphEvent.Completed) outcome = Phase.Clear
                    if (event is GraphEvent.Interrupted) outcome = Phase.SavePoint
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                outcome = Phase.GameOver
                failedNode = (e as? NodeExecutionException)?.nodeName
                error = (if (e is NodeExecutionException) e.cause?.message else null) ?: e.message ?: e.toString()
                log = log + LogLine("ERR", failedNode?.let { "$it: $error" } ?: error.orEmpty(), Tone.Failed)
            } finally {
                active = emptySet()
                // Text that arrived before a failure or a stop is not an answer.
                writing = ""
                withContext(NonCancellable) {
                    // The saved run says where a retry would pick up, and after Stop it is all there is to go by.
                    val saved = savedPhase()
                    phase = outcome ?: saved
                    checkpoints = savedCheckpoints()
                }
                running = false
                if (outcome == Phase.Clear) onClear(stage)
            }
        }
    }

    private fun record(event: GraphEvent<Ticket>) {
        when (event) {
            is GraphEvent.NodeStarted -> {
                active = active + event.node
                arriveAt(event.node)
            }
            // A model node reports each piece of text while the model writes it. No stage has a graph
            // inside a graph, but the text of a model in one would be read the same way.
            is GraphEvent.NodeProgress, is GraphEvent.SubgraphEvent -> event.textDelta?.let { writing += it }
            is GraphEvent.NodeCompleted -> {
                active = active - event.node
                visited = visited + event.node
                // The answer is in the state now.
                writing = ""
            }
            is GraphEvent.StepCompleted -> {
                step = event.step
                val before = ticket ?: event.state
                log = log + LogLine(event.step.toString().padStart(2, '0'), event.nodes.joinToString(" + ")) +
                    event.state.chat.drop(before.chat.size).flatMap(::toolLines) +
                    event.state.changesSince(before).ifEmpty { listOf(UNCHANGED) }.map { LogLine("", it, Tone.Detail) }
                ticketBefore = before
                ticket = event.state
                lastNodes = event.nodes
                chosen = emptySet()
            }
            is GraphEvent.Interrupted -> {
                ticket = event.state
                waitingAt = event.nextNodes
                log = log + LogLine("SAV", "stopped before ${event.nextNodes.joinToString(" + ")}", Tone.Saved)
            }
            is GraphEvent.Completed -> {
                ticket = event.state
                arriveAt(END)
                visited = visited + END
                log = log + LogLine("END", "reply sent", Tone.Done)
            }
        }
    }

    /** Lights the arrows that led to [node], and logs the choice of each conditional edge among them. */
    private fun arriveAt(node: String) {
        val followed = topology.edges.filter { it.to == node && it.from in lastNodes }
        trail = trail + followed.map { it.from to node }
        val choices = followed.filter { it.isConditional }.map { it.from to node } - chosen
        log = log + choices.map { (from, to) -> LogLine(" ->", "the edge after $from picked ${if (to == END) "END" else to}", Tone.Detail) }
        chosen = chosen + choices
    }

    private fun toolLines(message: ChatMessage): List<LogLine> = when (message) {
        is ChatMessage.Assistant -> message.toolCalls.map { LogLine(" >>", "${it.name} ${it.input}") }
        is ChatMessage.ToolResult -> listOf(LogLine(" <<", message.text, if (message.isError) Tone.Failed else Tone.Plain))
        is ChatMessage.User -> emptyList()
    }

    /** The checkpoint is the source of truth for where a run stands, also after a reload, a failure or Stop. */
    private suspend fun savedRun(): GraphResult.Interrupted<Ticket>? = try {
        graph.lastResult(config) as? GraphResult.Interrupted
    } catch (e: CancellationException) {
        throw e
    } catch (_: CheckpointCorruptedException) {
        // A save from an older version of the game, whose tickets looked different. It cannot be continued.
        checkpointer.delete(threadId)
        null
    } catch (e: Exception) {
        error = e.message
        null
    }

    private suspend fun savedCheckpoints(): List<Checkpoint<Ticket>> = try {
        graph.history(config)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // What savedRun() reports, it reports. The list is only something to look at.
        emptyList()
    }

    private suspend fun savedPhase(): Phase {
        val saved = savedRun() ?: return Phase.Ready
        ticket = saved.state
        waitingAt = saved.nextNodes
        return if (saved.nextNodes.any { it in stage.pauseBefore }) Phase.SavePoint else Phase.Unfinished
    }
}

/**
 * The whole game: one controller per stage, and which stages the player has cleared.
 *
 * @param store remembers the cleared stages, and the medals of the bonus stage.
 * @param saves keeps the runs of the stages, so that one that paused is still there in the next session.
 * @param realModel the model of the service the player chose, or `null` when the scripted model answers.
 * [model] is never `null`: the stages always have someone to ask.
 * @param modelLabel what [realModel] is called.
 * @param played the shifts of the bonus stage that ran to their end.
 * @param drawn the board of the bonus stage.
 * @param chosen the shift of the bonus stage that the player chose.
 * @param entered whether the player is in that shift, or at the list of the shifts.
 */
class Game(
    model: ChatModel,
    private val store: KeyValueStore,
    saves: Checkpointer<Ticket>,
    private val scope: CoroutineScope,
    workMillis: Long = 650,
    realModel: ChatModel? = null,
    modelLabel: String = "scripted",
    played: MutableState<List<PlayedShift>> = mutableStateOf(emptyList()),
    drawn: MutableState<Blueprint> = mutableStateOf(Guide.FIRST_ORDERS.start),
    chosen: MutableState<Rush> = mutableStateOf(Rush.entries.first()),
    entered: MutableState<Boolean> = mutableStateOf(false),
) {
    var cleared: Set<Int> by mutableStateOf(emptySet())
        private set

    /** Whether the stages show the ticket as JSON and the checkpoints of the run, next to the ticket. */
    var inspecting: Boolean by mutableStateOf(false)
        private set

    fun inspect(on: Boolean) {
        inspecting = on
        scope.launch { if (on) store.set(INSPECT, "on") else store.remove(INSPECT) }
    }

    private val medals = mutableStateOf(emptySet<Pair<Rush, Medal>>())

    /** The bonus stage. Its chefs are scripted unless the player chose a real model. */
    val kitchen: KitchenController =
        KitchenController(
            realModel,
            modelLabel,
            scope,
            minuteMillis = if (workMillis == 0L) 0 else if (realModel == null) 250 else 40,
            // With a real model an order stays at a station for as long as the model takes to answer.
            stepMillis = if (workMillis == 0L || realModel != null) 0 else 160,
            played = played,
            drawn = drawn,
            chosen = chosen,
            earned = medals,
            entered = entered,
            onEarned = ::saveMedals,
        )

    val controllers: List<StageController> = stages.map { StageController(it, Desk(model, workMillis), saves, scope, ::markCleared) }

    init {
        scope.launch {
            cleared = store.get(CLEARED).orEmpty().split(',').mapNotNull { it.toIntOrNull() }.toSet()
            // A medal that was won before this one was read from the store stays won.
            medals.value = medals.value + store.get(MEDALS).orEmpty().split(',').mapNotNull(::medalOf)
            inspecting = store.get(INSPECT) == "on"
        }
    }

    fun controller(number: Int): StageController = controllers.first { it.stage.number == number }

    private fun markCleared(stage: Stage) {
        cleared = cleared + stage.number
        scope.launch { store.set(CLEARED, cleared.sorted().joinToString(",")) }
    }

    /** Forgets the cleared stages, every saved run, and the medals of the bonus stage. */
    fun erase() {
        controllers.forEach { it.reset() }
        cleared = emptySet()
        kitchen.forget()
        scope.launch {
            store.remove(CLEARED)
            store.remove(MEDALS)
        }
    }

    /** `true` when there is something for [erase] to forget in the bonus stage. */
    val hasMedals: Boolean get() = medals.value.isNotEmpty()

    private fun saveMedals(won: Set<Pair<Rush, Medal>>) {
        scope.launch { store.set(MEDALS, won.joinToString(",") { (rush, medal) -> "${rush.name}.${medal.name}" }) }
    }

    /** A medal as [saveMedals] writes it, or `null` for a shift or a medal that the game no longer has. */
    private fun medalOf(saved: String): Pair<Rush, Medal>? {
        val rush = Rush.entries.firstOrNull { it.name == saved.substringBefore('.') } ?: return null
        val medal = Medal.entries.firstOrNull { it.name == saved.substringAfter('.') } ?: return null
        return (rush to medal).takeIf { rush.goals.any { it.medal == medal } }
    }

    private companion object {
        const val CLEARED = "pixelpizza.cleared"
        const val MEDALS = "pixelpizza.kitchen.medals"
        const val INSPECT = "pixelpizza.inspect"
    }
}
