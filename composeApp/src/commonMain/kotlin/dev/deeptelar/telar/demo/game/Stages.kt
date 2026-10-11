package dev.deeptelar.telar.demo.game

import dev.deeptelar.telar.CompiledGraph
import dev.deeptelar.telar.END
import dev.deeptelar.telar.START
import dev.deeptelar.telar.agent.ChatModel
import dev.deeptelar.telar.demo.llm.ScriptedChatModel

/** Where a node sits on a stage's board: [column] from the left, [row] from the top. */
data class Cell(val column: Int, val row: Int)

/**
 * One stage of the game: a help desk that knows one move more than the stage before.
 *
 * @property moves the library features the stage introduces.
 * @property board where each node of the graph is drawn. The arrows come from the graph itself.
 * @property pauseBefore the nodes the run stops before, to wait for the player.
 * @property usesModel whether a node asks the chat model.
 * @property readsMessage whether a node reads the customer's message. A desk that does one job for
 * every ticket does not, and a message a player writes changes nothing there.
 * @property function the name of the function that builds the graph, to find its source by.
 */
class Stage(
    val number: Int,
    val title: String,
    val moves: String,
    val briefing: String,
    val inbox: List<Mail>,
    val board: Map<String, Cell>,
    val pauseBefore: Set<String> = emptySet(),
    val usesModel: Boolean = false,
    val readsMessage: Boolean = true,
    val function: String,
    val graph: (Desk) -> CompiledGraph<Ticket>,
) {
    /** The code behind the tiles of the board. */
    val code: StageCode by lazy { StageCode(function, stageSources.values) }
}

private val delivery = Mail("Ana", "Where is my pizza?")
private val refund = Mail("Ben", "My pizza arrived cold. I want a refund.")
private val salad = Mail("Cleo", "Do you sell salad?")

/** A board with every node on one row, in the order given. */
private fun row(vararg nodes: String): Map<String, Cell> =
    (listOf(START) + nodes + END).mapIndexed { column, node -> node to Cell(column, 0) }.toMap()

val stages: List<Stage> = listOf(
    Stage(
        number = 1,
        title = "A line",
        moves = "node / then / invoke",
        briefing = "Two nodes in a row. READ works out what the customer wants, ANSWER writes the reply. " +
            "Each one gets the ticket, adds to it and hands it on.",
        inbox = listOf(delivery, refund, salad),
        board = row("read", "answer"),
        function = "line",
        graph = HelpDesks::line,
    ),
    Stage(
        number = 2,
        title = "Choices",
        moves = "conditionalEdge",
        briefing = "One reply does not fit every customer. After READ, a conditional edge looks at the topic " +
            "and picks one of three paths. Send each ticket and watch where it goes.",
        inbox = listOf(delivery, refund, salad),
        board = mapOf(
            START to Cell(0, 1),
            "read" to Cell(1, 1),
            "track" to Cell(2, 0),
            "refund" to Cell(2, 1),
            "answer" to Cell(2, 2),
            END to Cell(3, 1),
        ),
        function = "choices",
        graph = HelpDesks::choices,
    ),
    Stage(
        number = 3,
        title = "Loops",
        moves = "an edge that goes back",
        briefing = "The writer is sloppy. CHECK sends the reply back to WRITE until it says sorry and uses " +
            "the customer's name. After ${HelpDesks.LOOP_ATTEMPTS} attempts it goes out anyway: every loop needs a limit.",
        inbox = listOf(Mail("Ana", "My pizza is late!"), Mail("Ben", "Still no pizza. It has been an hour.")),
        board = row("write", "check"),
        function = "loops",
        graph = HelpDesks::loops,
    ),
    Stage(
        number = 4,
        title = "Two at once",
        moves = "fan-out / work and update",
        briefing = "Asking the kitchen and the driver one after the other is slow, so both run in the same step. " +
            "Their work happens at the same time, and each then writes its fact into the ticket, one after the other.",
        inbox = listOf(delivery, Mail("Ben", "Is my order close?")),
        board = mapOf(
            START to Cell(0, 1),
            HelpDesks.KITCHEN to Cell(1, 0),
            "driver" to Cell(1, 2),
            "answer" to Cell(2, 1),
            END to Cell(3, 1),
        ),
        readsMessage = false,
        function = "parallel",
        graph = HelpDesks::parallel,
    ),
    Stage(
        number = 5,
        title = "Save points",
        moves = "interruptBefore / resume",
        briefing = "Money is about to move, so the run stops before PAY and waits for you. It is saved: " +
            "reload the page or close the app, and the ticket is still waiting for your decision.",
        inbox = listOf(refund, Mail("Cleo", "Wrong toppings again. Refund, please.")),
        board = row("prepare", HelpDesks.PAY),
        pauseBefore = setOf(HelpDesks.PAY),
        readsMessage = false,
        function = "savePoints",
        graph = HelpDesks::savePoints,
    ),
    Stage(
        number = 6,
        title = "The agent",
        moves = "a model / tools / a loop",
        briefing = "No keywords this time. A model reads the ticket and decides by itself: answer now, or ask a " +
            "tool first. Two nodes and a loop are the whole agent.",
        inbox = listOf(delivery, Mail("Ben", "How much is a margherita and a cola?"), Mail("Cleo", "Do you sell sushi?")),
        board = mapOf(
            START to Cell(0, 0),
            Agent.ASSISTANT to Cell(1, 0),
            Agent.TOOLS to Cell(2, 1),
            END to Cell(3, 0),
        ),
        usesModel = true,
        function = "graph",
        graph = { desk -> Agent.graph(desk) },
    ),
    Stage(
        number = 7,
        title = "Game over",
        moves = "a failed node / resume",
        briefing = "The kitchen phone is busy and the run fails. Nothing is lost: every finished step was " +
            "saved, so a retry starts at the node that failed, not at the beginning.",
        inbox = listOf(delivery),
        board = row("greet", HelpDesks.KITCHEN),
        readsMessage = false,
        function = "gameOver",
        graph = { desk -> HelpDesks.gameOver(desk) },
    ),
    Stage(
        number = 8,
        title = "The full desk",
        moves = "every move on one board",
        briefing = "A choice, two lookups at once, a loop that rewrites, a save point before the money moves, and " +
            "the agent for questions about the menu. Four tickets, four ways through.",
        inbox = listOf(delivery, refund, salad, Mail("Dan", "Thanks for the pizza!")),
        board = mapOf(
            START to Cell(0, 2),
            "read" to Cell(1, 2),
            "look_up" to Cell(2, 0),
            HelpDesks.KITCHEN to Cell(3, 0),
            "driver" to Cell(3, 1),
            "write" to Cell(4, 2),
            "check" to Cell(5, 2),
            "prepare" to Cell(2, 3),
            HelpDesks.PAY to Cell(3, 3),
            "model" to Cell(2, 4),
            "tools" to Cell(3, 5),
            "send" to Cell(4, 4),
            END to Cell(6, 2),
        ),
        pauseBefore = setOf(HelpDesks.PAY),
        usesModel = true,
        function = "fullDesk",
        graph = HelpDesks::fullDesk,
    ),
)

/** The offline model: scripted answers for the stages that ask a model, with a delay so the board shows progress. */
fun scriptedModel(delayMillis: Long = 0): ChatModel =
    ScriptedChatModel(listOf(Agent.script, HelpDesks.writerScript), delayMillis)
