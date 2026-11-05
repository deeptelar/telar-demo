package dev.deeptelar.telar.demo.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import dev.deeptelar.telar.CompiledGraph
import dev.deeptelar.telar.Retry
import dev.deeptelar.telar.START
import dev.deeptelar.telar.StateGraph
import dev.deeptelar.telar.agent.ChatMessage
import dev.deeptelar.telar.agent.Description
import dev.deeptelar.telar.agent.Tool
import dev.deeptelar.telar.agent.ToolCall
import dev.deeptelar.telar.agent.toolLoop
import dev.deeptelar.telar.demo.llm.Responder

@Serializable
data class OrderLookup(
    @Description("The customer's name, for example \"Ana\"") val customer: String,
)

@Serializable
data class MenuLookup(
    @Description("The item, for example \"margherita\"") val item: String,
)

/** Stage 6: a model that decides by itself whether to answer or to ask a tool first. */
object Agent {
    const val ASSISTANT: String = "assistant"
    const val TOOLS: String = "tools"

    const val SYSTEM: String =
        "You work at the help desk of Pixel Pizza. Answer the customer in one or two friendly sentences and use their name. " +
            "Use the order_status tool for questions about a delivery and the menu_price tool for questions about what " +
            "we sell or what it costs. Never guess a price."

    /** The prompt of the agent in stage 8, which only gets the questions about the menu. */
    const val MENU_SYSTEM: String =
        "You work at the help desk of Pixel Pizza and answer questions about the menu. Answer the customer in one or two " +
            "friendly sentences and use their name. Use the menu_price tool for what we sell or what it costs. Never guess a price."

    private const val CUSTOMER = "Customer: "
    private const val MESSAGE = "Message: "

    /** The conversation of a new ticket starts with the customer's message. */
    fun opening(ticket: Ticket): String = "$CUSTOMER${ticket.customer}\n$MESSAGE${ticket.message}"

    /**
     * The classic agent loop as a graph: the model answers or asks for tools, the tools run, and
     * their results go back to the model until it answers in plain text. `toolLoop` adds the two
     * nodes and the edges between them; a tool that fails reaches the model as an error result, and
     * a call to the model that fails is tried again before it fails the run.
     */
    fun graph(desk: Desk, tools: List<Tool> = deskTools): CompiledGraph<Ticket> = StateGraph<Ticket> {
        START then toolLoop(
            model = desk.model,
            tools = tools.map(desk::slow),
            messages = { it.chat },
            append = { ticket, new ->
                val answer = (new.lastOrNull() as? ChatMessage.Assistant)?.takeIf { it.toolCalls.isEmpty() }
                ticket.copy(
                    chat = ticket.chat + new,
                    facts = ticket.facts + new.filterIsInstance<ChatMessage.ToolResult>().map { it.text },
                    reply = answer?.text?.trim() ?: ticket.reply,
                )
            },
            firstMessage = ::opening,
            system = SYSTEM,
            modelNode = ASSISTANT,
            toolsNode = TOOLS,
            retry = Retry(attempts = 3),
        )
    }.compile()

    /** The input class gives the model the schema of the tool. */
    val orderStatus: Tool = Tool<OrderLookup>(
        "order_status",
        "Returns where a customer's order is right now. The data is made up for the game.",
    ) { lookup -> "The pizza for ${lookup.customer} left the oven and the driver is 5 minutes away." }

    private val menu = mapOf("margherita" to 9, "pepperoni" to 11, "salad" to 6, "cola" to 2)

    val menuPrice: Tool = Tool<MenuLookup>(
        "menu_price",
        "Returns the price of one item on the Pixel Pizza menu, or an error if we do not sell it.",
    ) { lookup ->
        val item = lookup.item.trim().lowercase()
        val price = menu[item] ?: throw IllegalArgumentException("Pixel Pizza does not sell $item.")
        "One $item costs $price euros."
    }

    val deskTools: List<Tool> = listOf(orderStatus, menuPrice)

    /** Scripted behaviour: calls a tool when the message clearly needs one, then reports what came back. */
    val script: Responder = Responder { request ->
        if (request.system != SYSTEM && request.system != MENU_SYSTEM) return@Responder null
        val ticket = request.messages.first().text.lines()
        val customer = ticket.first().removePrefix(CUSTOMER)
        val results = request.messages.takeLastWhile { it is ChatMessage.ToolResult }
        if (results.isNotEmpty()) {
            return@Responder ChatMessage.Assistant("Hi $customer! " + results.joinToString(" ") { it.text })
        }
        val message = ticket.last().removePrefix(MESSAGE).lowercase()
        val items = (menu.keys.filter { it in message } + listOfNotNull(sells.find(message)?.groupValues?.get(1))).distinct()
        val calls = buildList {
            if ("where" in message) {
                add(ToolCall("toolu_scripted_status", orderStatus.spec.name, buildJsonObject { put("customer", customer) }))
            }
            items.forEach { item ->
                add(ToolCall("toolu_scripted_price_$item", menuPrice.spec.name, buildJsonObject { put("item", item) }))
            }
        }
        if (calls.isNotEmpty()) {
            ChatMessage.Assistant(toolCalls = calls)
        } else {
            ChatMessage.Assistant("Hi $customer, thanks for writing to Pixel Pizza. A colleague will reply soon.")
        }
    }

    private val sells = Regex("""sell (\p{L}+)""")
}
