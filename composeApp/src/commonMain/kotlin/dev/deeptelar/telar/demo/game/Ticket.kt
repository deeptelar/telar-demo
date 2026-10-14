package dev.deeptelar.telar.demo.game

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import dev.deeptelar.telar.agent.ChatMessage
import dev.deeptelar.telar.agent.ChatModel
import dev.deeptelar.telar.agent.Tool

/**
 * The state of every stage: one customer message on its way to a reply.
 *
 * [customer] and [message] are the input. Everything else starts empty and is filled in by the
 * nodes; a stage only uses the fields its nodes need.
 */
@Serializable
data class Ticket(
    val customer: String,
    val message: String,
    val topic: String = "",
    val facts: List<String> = emptyList(),
    val refund: Int = 0,
    val approved: Boolean = false,
    val reply: String = "",
    val attempts: Int = 0,
    val problem: String = "",
    /** The conversation with the model, kept by the agent of stages 6 and 8. */
    val chat: List<ChatMessage> = emptyList(),
)

/**
 * What a step wrote into the ticket: a line for each field that differs from [before], written the
 * way the node that changed it would write it.
 *
 * `approved` is not among them. The player sets it at a save point, and no node does.
 */
fun Ticket.changesSince(before: Ticket): List<String> = buildList {
    if (topic != before.topic) add("topic = \"$topic\"")
    facts.drop(before.facts.size).forEach { add("facts += \"$it\"") }
    val messages = chat.size - before.chat.size
    if (messages > 0) add("chat += $messages ${if (messages == 1) "message" else "messages"}")
    if (refund != before.refund) add("refund = $refund")
    if (attempts != before.attempts) add("attempts = $attempts")
    if (problem != before.problem) add("problem = \"$problem\"")
    if (reply != before.reply) add("reply = \"$reply\"")
}

/** The JSON of a ticket as the live view of a stage shows it: every field, one per line. */
private val readable = Json {
    prettyPrint = true
    encodeDefaults = true
}

/** This ticket as JSON, the way `KotlinxStateSerializer` sees the data class, but with every field on a line of its own. */
fun Ticket.toJson(): String = readable.encodeToString(Ticket.serializer(), this)

/**
 * The lines of [toJson], each with `true` when it is not in the JSON of [before]: what a step
 * changed, line by line. Nothing is marked when there is no [before].
 */
fun Ticket.jsonLines(before: Ticket?): List<Pair<String, Boolean>> {
    val now = toJson().lines()
    if (before == null) return now.map { it to false }
    // A line that was there before counts once, so a second line with the same text is marked.
    val left = before.toJson().lines().groupingBy { it }.eachCount().toMutableMap()
    return now.map { line ->
        val count = left[line] ?: 0
        if (count > 0) left[line] = count - 1
        line to (count == 0)
    }
}

/** A message waiting in a stage's inbox. */
data class Mail(val from: String, val text: String)

/**
 * What the nodes of a stage work with.
 *
 * @property model writes the replies of the stages that use a model.
 * @property workMillis how long a node without a model pretends to work, so a player can watch
 * the run move across the board. Tests pass 0.
 */
class Desk(val model: ChatModel, private val workMillis: Long = 0) {
    suspend fun work() = delay(workMillis)

    /** Returns [tool] as a tool that takes a moment, like every other piece of work in the game. */
    fun slow(tool: Tool): Tool = Tool(tool.spec) { input ->
        work()
        tool.execute(input)
    }
}
