package dev.deeptelar.telar.demo

import kotlinx.coroutines.test.runTest
import dev.deeptelar.telar.END
import dev.deeptelar.telar.GraphConfig
import dev.deeptelar.telar.GraphResult
import dev.deeptelar.telar.MemoryCheckpointer
import dev.deeptelar.telar.NodeExecutionException
import dev.deeptelar.telar.START
import dev.deeptelar.telar.agent.ChatMessage
import dev.deeptelar.telar.demo.game.Agent
import dev.deeptelar.telar.demo.game.Desk
import dev.deeptelar.telar.demo.game.HelpDesks
import dev.deeptelar.telar.demo.game.Ticket
import dev.deeptelar.telar.demo.game.changesSince
import dev.deeptelar.telar.demo.game.scriptedModel
import dev.deeptelar.telar.demo.game.stages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The graph of every stage, run the way the tutorial runs its levels. */
class StagesTest {
    private val desk = Desk(scriptedModel())
    private val ana = Ticket("Ana", "Where is my pizza?")

    private fun pausingBeforePay() =
        GraphConfig(threadId = "t", checkpointer = MemoryCheckpointer<Ticket>(), interruptBefore = setOf(HelpDesks.PAY))

    @Test
    fun everyBoardPlacesEachNodeOfItsGraphOnACellOfItsOwn() {
        stages.forEach { stage ->
            val nodes = stage.graph(desk).topology.nodes
            assertEquals((nodes + START + END).toSet(), stage.board.keys, "stage ${stage.number}")
            assertEquals(stage.board.size, stage.board.values.toSet().size, "stage ${stage.number} puts two nodes on one cell")
            assertTrue(nodes.containsAll(stage.pauseBefore), "stage ${stage.number}")
        }
    }

    @Test
    fun aLineReadsTheTopicAndAnswers() = runTest {
        val ticket = HelpDesks.line(desk).invoke(ana).state

        assertEquals("delivery", ticket.topic)
        assertEquals("Hi Ana, thanks for writing to Pixel Pizza. We filed this under: delivery.", ticket.reply)
    }

    @Test
    fun choicesSendEachTopicDownItsOwnPath() = runTest {
        val graph = HelpDesks.choices(desk)

        assertEquals("Hi Ana, your pizza left the oven and is on its way.", graph.invoke(ana).state.reply)
        assertEquals("We are sorry, Ben. Your money is on its way back.", graph.invoke(Ticket("Ben", "I want a refund")).state.reply)
        assertEquals("Thanks for your message, Cleo. A colleague will reply soon.", graph.invoke(Ticket("Cleo", "Do you sell salad?")).state.reply)
    }

    @Test
    fun aStageSaysWhetherItsDeskReadsTheMessage() = runTest {
        stages.forEach { stage ->
            val graph = stage.graph(desk)

            // The phone of stage 7 is busy on two calls out of three, so a run that failed is run again.
            suspend fun replyTo(message: String): String {
                val ticket = Ticket("Ana", message)
                return runCatching { graph.invoke(ticket) }.getOrElse { graph.invoke(ticket) }.state.reply
            }

            val same = replyTo(stage.inbox.first().text) == replyTo("Thanks for the pizza, it was delicious")
            assertEquals(stage.readsMessage, !same, "stage ${stage.number}")
        }
    }

    @Test
    fun theLoopRewritesTheReplyUntilItPassesTheCheck() = runTest {
        val ticket = HelpDesks.loops(desk).invoke(Ticket("Ana", "My pizza is late!")).state

        assertEquals(3, ticket.attempts)
        assertEquals("", ticket.problem)
        assertEquals("Sorry Ana, your pizza is late. It arrives in 10 minutes.", ticket.reply)
    }

    @Test
    fun theWriterOfTheLoopAnswersTheMessageItGot() = runTest {
        val graph = HelpDesks.loops(desk)
        suspend fun replyTo(message: String) = graph.invoke(Ticket("Ben", message)).state.reply

        assertEquals("Sorry Ben, your pizza is late. It arrives in 10 minutes.", replyTo("Still no pizza. It has been an hour."))

        // Anything else gets a reply that says why, and still goes through the loop.
        val thanks = graph.invoke(Ticket("Ben", "Thanks for the pizza, it was delicious")).state
        assertEquals("Sorry Ben, this desk only knows about late pizzas. A colleague will reply soon.", thanks.reply)
        assertEquals(3, thanks.attempts)

        // "late" counts as a word, not as a part of one.
        assertEquals(thanks.reply, replyTo("I loved the chocolate pizza"))
    }

    @Test
    fun bothLookupsWriteTheirFactIntoTheTicket() = runTest {
        val ticket = HelpDesks.parallel(desk).invoke(ana).state

        assertEquals(listOf("your pizza left the oven", "the driver is 5 minutes away"), ticket.facts)
        assertEquals("Hi Ana, your pizza left the oven and the driver is 5 minutes away.", ticket.reply)
    }

    @Test
    fun theKitchenLooksUpEachDishAndWritesWhatItFoundInTheOrderOfTheMessage() = runTest {
        val ticket = HelpDesks.parallel(desk).invoke(Ticket("Ben", "Is my order close? A salad, a pepperoni and a margherita.")).state

        assertEquals(
            listOf("your salad is being made", "your pepperoni left the oven", "your margherita is in the oven", "the driver is 5 minutes away"),
            ticket.facts,
        )
        assertEquals(
            "Hi Ben, your salad is being made, your pepperoni left the oven, your margherita is in the oven and the driver is 5 minutes away.",
            ticket.reply,
        )
    }

    @Test
    fun theRunStopsBeforePayAndPaysWhatThePlayerDecides() = runTest {
        val graph = HelpDesks.savePoints(desk)
        val refund = Ticket("Ben", "My pizza arrived cold. I want a refund.")

        val approving = pausingBeforePay()
        val paused = graph.invoke(refund, approving)
        assertIs<GraphResult.Interrupted<Ticket>>(paused)
        assertEquals(12, paused.state.refund)
        assertEquals("", paused.state.reply)
        assertEquals("Sorry Ben! We sent you 12 euros.", graph.resume(approving) { it.copy(approved = true) }.state.reply)

        val denying = pausingBeforePay()
        graph.invoke(refund, denying)
        assertEquals("Sorry Ben, we cannot refund this order.", graph.resume(denying) { it.copy(approved = false) }.state.reply)
    }

    @Test
    fun theBankPaysARefundOnceAlthoughPayRunsTwice() = runTest {
        val bank = HelpDesks.Bank()
        val graph = HelpDesks.savePoints(desk, bank)
        val config = pausingBeforePay()

        graph.invoke(Ticket("Ben", "My pizza arrived cold. I want a refund."), config)
        val paid = graph.resume(config) { it.copy(approved = true) }.state

        // The receipt mail failed after the transfer, so `pay` ran again and got the saved transfer back.
        assertEquals("T-1", paid.transfer)
        assertEquals(1, bank.transfers)
    }

    @Test
    fun theAgentAsksAToolAndAnswersWithItsResult() = runTest {
        val ticket = Agent.graph(desk).invoke(ana).state

        assertEquals("Hi Ana! The pizza for Ana left the oven and the driver is 5 minutes away.", ticket.reply)
        assertEquals(4, ticket.chat.size)
        assertEquals("order_status", (ticket.chat[1] as ChatMessage.Assistant).toolCalls.single().name)
    }

    @Test
    fun theAgentRunsSeveralToolsInOneTurn() = runTest {
        val ticket = Agent.graph(desk).invoke(Ticket("Ben", "How much is a margherita and a cola?")).state

        assertEquals(listOf("One margherita costs 9 euros.", "One cola costs 2 euros."), ticket.facts)
        assertEquals("Hi Ben! One margherita costs 9 euros. One cola costs 2 euros.", ticket.reply)
    }

    @Test
    fun aFailingToolIsReportedToTheModelInsteadOfFailingTheRun() = runTest {
        val ticket = Agent.graph(desk).invoke(Ticket("Cleo", "Do you sell sushi?")).state

        assertTrue((ticket.chat[2] as ChatMessage.ToolResult).isError)
        assertEquals("Hi Cleo! Pixel Pizza does not sell sushi.", ticket.reply)
    }

    @Test
    fun theAgentAnswersDirectlyWhenNoToolFits() = runTest {
        val ticket = Agent.graph(desk).invoke(Ticket("Ana", "You are great")).state

        assertEquals(2, ticket.chat.size)
        assertEquals("Hi Ana, thanks for writing to Pixel Pizza. A colleague will reply soon.", ticket.reply)
    }

    @Test
    fun aBusyPhoneFailsTheRunAndResumeRetriesTheFailedNode() = runTest {
        val graph = HelpDesks.gameOver(desk)
        val config = GraphConfig(threadId = "t", checkpointer = MemoryCheckpointer<Ticket>())

        val failure = assertFailsWith<NodeExecutionException> { graph.invoke(ana, config) }
        assertEquals(HelpDesks.KITCHEN, failure.nodeName)
        // The node tried a second time by itself before the run failed.
        assertEquals(2, failure.attempts)

        assertEquals("Hi Ana! Your pizza is in the oven.", graph.resume(config).state.reply)
    }

    @Test
    fun theFullDeskLooksUpADeliveryAndRewritesTheReplyOnce() = runTest {
        val ticket = HelpDesks.fullDesk(desk).invoke(ana, pausingBeforePay()).state

        assertEquals(2, ticket.attempts)
        assertEquals("Hi Ana, your pizza left the oven and the driver is 5 minutes away.", ticket.reply)
    }

    @Test
    fun theFullDeskStopsARefundBeforePay() = runTest {
        val graph = HelpDesks.fullDesk(desk)
        val config = pausingBeforePay()

        val paused = graph.invoke(Ticket("Ben", "My pizza arrived cold. I want a refund."), config)
        assertIs<GraphResult.Interrupted<Ticket>>(paused)
        assertEquals(listOf(HelpDesks.PAY), paused.nextNodes)

        assertEquals("Sorry Ben! We sent you 12 euros.", graph.resume(config) { it.copy(approved = true) }.state.reply)
    }

    @Test
    fun theFullDeskAsksTheAgentAboutTheMenu() = runTest {
        val ticket = HelpDesks.fullDesk(desk).invoke(Ticket("Cleo", "Do you sell salad?"), pausingBeforePay()).state

        assertEquals("menu", ticket.topic)
        assertEquals("menu_price", (ticket.chat[1] as ChatMessage.Assistant).toolCalls.single().name)
        assertEquals("Hi Cleo! One salad costs 6 euros.", ticket.reply)
        // The writer and its check are not on this path.
        assertEquals(0, ticket.attempts)
    }

    @Test
    fun theFullDeskAnswersAnythingElseWithoutALookup() = runTest {
        val ticket = HelpDesks.fullDesk(desk).invoke(Ticket("Dan", "Thanks for the pizza!"), pausingBeforePay()).state

        assertEquals(emptyList(), ticket.facts)
        assertEquals(emptyList(), ticket.chat)
        assertEquals("Hi Dan, a colleague will reply soon.", ticket.reply)
    }

    @Test
    fun theChangesOfATicketAreListedFieldByField() {
        val after = ana.copy(
            topic = "delivery",
            facts = listOf("your pizza left the oven"),
            chat = listOf(ChatMessage.User("Hi")),
            refund = 12,
            approved = true,
            transfer = "T-1",
            reply = "Hi Ana!",
            attempts = 1,
            problem = "say sorry",
        )

        assertEquals(
            listOf(
                "topic = \"delivery\"",
                "facts += \"your pizza left the oven\"",
                "chat += 1 message",
                "refund = 12",
                "transfer = \"T-1\"",
                "attempts = 1",
                "problem = \"say sorry\"",
                "reply = \"Hi Ana!\"",
            ),
            after.changesSince(ana),
        )
        // Only what is new: a fact that was there is not listed again, and a problem that was solved is.
        assertEquals(
            listOf("facts += \"the driver is 5 minutes away\"", "problem = \"\""),
            after.copy(facts = after.facts + "the driver is 5 minutes away", problem = "").changesSince(after),
        )
        assertEquals(emptyList(), after.changesSince(after))
    }
}
