package dev.deeptelar.telar.demo.game

import dev.deeptelar.telar.CompiledGraph
import dev.deeptelar.telar.END
import dev.deeptelar.telar.NodeRef
import dev.deeptelar.telar.START
import dev.deeptelar.telar.StateGraph
import dev.deeptelar.telar.agent.ChatMessage
import dev.deeptelar.telar.agent.chatWithProgress
import dev.deeptelar.telar.agent.toolLoop
import dev.deeptelar.telar.demo.llm.Responder

/**
 * The graphs of the stages, one function each. They are the help desks of the Telar
 * tutorial: every stage adds one move to the one before.
 */
object HelpDesks {
    const val PAY: String = "pay"
    const val KITCHEN: String = "kitchen"

    /** A real help desk would ask a model. Looking for keywords is enough to learn the moves. */
    fun topicOf(message: String): String = when {
        "refund" in message.lowercase() -> "refund"
        "where" in message.lowercase() -> "delivery"
        "sell" in message.lowercase() || "cost" in message.lowercase() -> "menu"
        else -> "other"
    }

    /** Stage 1: two nodes in a row, each adding to the ticket. */
    fun line(desk: Desk): CompiledGraph<Ticket> = StateGraph<Ticket> {
        val read = node("read") { ticket ->
            desk.work()
            ticket.copy(topic = topicOf(ticket.message))
        }
        val answer = node("answer") { ticket ->
            desk.work()
            ticket.copy(reply = "Hi ${ticket.customer}, thanks for writing to Pixel Pizza. We filed this under: ${ticket.topic}.")
        }

        START then read then answer then END
    }.compile()

    /** Stage 2: a conditional edge picks one of three paths. */
    fun choices(desk: Desk): CompiledGraph<Ticket> = StateGraph<Ticket> {
        val read = node("read") { ticket ->
            desk.work()
            ticket.copy(topic = topicOf(ticket.message))
        }
        val track = node("track") { ticket ->
            desk.work()
            ticket.copy(reply = "Hi ${ticket.customer}, your pizza left the oven and is on its way.")
        }
        val refund = node("refund") { ticket ->
            desk.work()
            ticket.copy(reply = "We are sorry, ${ticket.customer}. Your money is on its way back.")
        }
        val answer = node("answer") { ticket ->
            desk.work()
            ticket.copy(reply = "Thanks for your message, ${ticket.customer}. A colleague will reply soon.")
        }

        START then read
        conditionalEdge(read, targets = setOf(track, refund, answer)) { ticket ->
            when (ticket.topic) {
                "delivery" -> track
                "refund" -> refund
                else -> answer
            }
        }
        track then END
        refund then END
        answer then END
    }.compile()

    const val LOOP_ATTEMPTS: Int = 5

    /**
     * Pretends to be a writer whose reply gets better with every attempt. A late pizza is all it
     * knows about, and it says so when the message is about something else.
     */
    private fun sloppyReply(ticket: Ticket): String {
        val late = Regex("\\b(late|where|no pizza)\\b").containsMatchIn(ticket.message.lowercase())
        val news = if (late) "your pizza is late" else "this desk only knows about late pizzas"
        val next = if (late) "It arrives in 10 minutes." else "A colleague will reply soon."
        return when (ticket.attempts) {
            0 -> "${news.replaceFirstChar { it.uppercase() }}."
            1 -> "Sorry, $news."
            else -> "Sorry ${ticket.customer}, $news. $next"
        }
    }

    /** Stage 3: an edge that goes back, so the reply is rewritten until it passes the check. */
    fun loops(desk: Desk): CompiledGraph<Ticket> = StateGraph<Ticket> {
        val write = node("write") { ticket ->
            desk.work()
            ticket.copy(reply = sloppyReply(ticket), attempts = ticket.attempts + 1)
        }
        val check = node("check") { ticket ->
            desk.work()
            val problem = when {
                "sorry" !in ticket.reply.lowercase() -> "say sorry"
                ticket.customer !in ticket.reply -> "use the customer's name"
                else -> ""
            }
            ticket.copy(problem = problem)
        }

        START then write then check
        conditionalEdge(check, targets = setOf(write, NodeRef.END)) { ticket ->
            if (ticket.problem.isEmpty() || ticket.attempts >= LOOP_ATTEMPTS) NodeRef.END else write
        }
    }.compile()

    /** A slow call to the kitchen. */
    private suspend fun askKitchen(desk: Desk): String {
        desk.work()
        return "your pizza left the oven"
    }

    /** A slow call to the driver. */
    private suspend fun askDriver(desk: Desk): String {
        desk.work()
        return "the driver is 5 minutes away"
    }

    /** Stage 4: two lookups run at the same time, and each then writes its fact into the ticket. */
    fun parallel(desk: Desk): CompiledGraph<Ticket> = StateGraph<Ticket> {
        // `work` asks and returns what it found. The block after it writes that fact into the ticket.
        val kitchen = node(KITCHEN, work = { askKitchen(desk) }) { ticket, fact -> ticket.copy(facts = ticket.facts + fact) }
        val driver = node("driver", work = { askDriver(desk) }) { ticket, fact -> ticket.copy(facts = ticket.facts + fact) }
        val answer = node("answer") { ticket ->
            desk.work()
            ticket.copy(reply = "Hi ${ticket.customer}, ${ticket.facts.joinToString(" and ")}.")
        }

        START then kitchen then answer
        START then driver then answer
        answer then END
    }.compile()

    private fun payOut(ticket: Ticket): Ticket = if (ticket.approved) {
        ticket.copy(reply = "Sorry ${ticket.customer}! We sent you ${ticket.refund} euros.")
    } else {
        ticket.copy(reply = "Sorry ${ticket.customer}, we cannot refund this order.")
    }

    /** Stage 5: run it with `interruptBefore = setOf(PAY)`, so a human decides before money moves. */
    fun savePoints(desk: Desk): CompiledGraph<Ticket> = StateGraph<Ticket> {
        val prepare = node("prepare") { ticket ->
            desk.work()
            ticket.copy(refund = 12)
        }
        val pay = node(PAY) { ticket ->
            desk.work()
            payOut(ticket)
        }

        START then prepare then pay then END
    }.compile()

    /** The kitchen's phone. It is busy on every other call, so a run fails once and a retry gets through. */
    class BusyPhone {
        private var calls = 0

        fun call(): String {
            if (++calls % 2 == 1) error("the kitchen phone is busy")
            return "Your pizza is in the oven."
        }
    }

    /** Stage 7: the second node depends on something that can fail. */
    fun gameOver(desk: Desk, callKitchen: () -> String = BusyPhone()::call): CompiledGraph<Ticket> = StateGraph<Ticket> {
        val greet = node("greet") { ticket ->
            desk.work()
            ticket.copy(reply = "Hi ${ticket.customer}!")
        }
        val kitchen = node(KITCHEN) { ticket ->
            desk.work()
            ticket.copy(reply = "${ticket.reply} ${callKitchen()}")
        }

        START then greet then kitchen then END
    }.compile()

    const val WRITE_ATTEMPTS: Int = 3

    const val WRITER_SYSTEM: String =
        "You write replies for the help desk of Pixel Pizza. Answer the customer in one or two friendly sentences. " +
            "Use only the facts you are given. Reply with the message for the customer and nothing else."

    private const val CUSTOMER = "Customer: "
    private const val MESSAGE = "Message: "
    private const val FACTS = "Facts: "
    private const val FIX = "Fix this in the previous reply: "

    private fun writerPrompt(ticket: Ticket): String = buildString {
        appendLine(CUSTOMER + ticket.customer)
        appendLine(MESSAGE + ticket.message)
        if (ticket.facts.isNotEmpty()) appendLine(FACTS + ticket.facts.joinToString("; "))
        if (ticket.problem.isNotEmpty()) appendLine(FIX + ticket.problem)
    }

    /** Stage 8: every move of the earlier stages on one board, with a model writing the reply. */
    fun fullDesk(desk: Desk): CompiledGraph<Ticket> = StateGraph<Ticket> {
        val read = node("read") { ticket ->
            desk.work()
            ticket.copy(topic = topicOf(ticket.message))
        }

        // Delivery questions: two lookups at the same time. A router returns one node, so the
        // fan-out starts at a node of its own that passes the ticket on unchanged.
        val lookUp = node("look_up") { ticket -> ticket }
        val kitchen = node(KITCHEN, work = { askKitchen(desk) }) { ticket, fact -> ticket.copy(facts = ticket.facts + fact) }
        val driver = node("driver", work = { askDriver(desk) }) { ticket, fact -> ticket.copy(facts = ticket.facts + fact) }

        // Every reply is written and checked, and rewritten if the check finds a problem.
        val write = node("write") { ticket ->
            // In a run that is watched, the model streams and the run log shows the reply while it is written.
            val reply = desk.model.chatWithProgress(writerPrompt(ticket), WRITER_SYSTEM)
            ticket.copy(reply = reply.trim(), attempts = ticket.attempts + 1)
        }
        val check = node("check") { ticket ->
            desk.work()
            ticket.copy(problem = if (ticket.customer in ticket.reply) "" else "use the customer's name")
        }

        // Refunds: a human decides before PAY runs.
        val prepare = node("prepare") { ticket ->
            desk.work()
            ticket.copy(refund = 12)
        }
        val pay = node(PAY) { ticket ->
            desk.work()
            payOut(ticket)
        }

        // The model's answer is the last message of the conversation.
        val send = node("send") { ticket ->
            desk.work()
            ticket.copy(reply = ticket.chat.last().text.trim())
        }

        // Questions about the menu: the agent of stage 6, with the price tool. `toolLoop` adds its two
        // nodes, MODEL and TOOLS, and the run goes on to SEND when the model has its answer.
        val agent = toolLoop(
            model = desk.model,
            tools = listOf(desk.slow(Agent.menuPrice)),
            messages = { it.chat },
            append = { ticket, new -> ticket.copy(chat = ticket.chat + new) },
            firstMessage = Agent::opening,
            system = Agent.MENU_SYSTEM,
            then = send,
        )

        START then read
        conditionalEdge(read, targets = setOf(lookUp, prepare, agent, write)) { ticket ->
            when (ticket.topic) {
                "delivery" -> lookUp
                "refund" -> prepare
                "menu" -> agent
                else -> write
            }
        }

        lookUp then kitchen then write
        lookUp then driver then write
        write then check
        conditionalEdge(check, targets = setOf(write, NodeRef.END)) { ticket ->
            if (ticket.problem.isEmpty() || ticket.attempts >= WRITE_ATTEMPTS) NodeRef.END else write
        }

        prepare then pay then END
        send then END
    }.compile()

    /** The scripted writer forgets the customer's name until the check asks for it. */
    val writerScript: Responder = Responder { request ->
        if (request.system != WRITER_SYSTEM) return@Responder null
        val lines = request.messages.last().text.lines()
        fun field(prefix: String) = lines.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix).orEmpty()
        val body = field(FACTS).split("; ").filter { it.isNotBlank() }.joinToString(" and ").ifEmpty { "a colleague will reply soon" }
        ChatMessage.Assistant(
            if (field(FIX).isEmpty()) "${body.replaceFirstChar { it.uppercase() }}." else "Hi ${field(CUSTOMER)}, $body.",
        )
    }
}
