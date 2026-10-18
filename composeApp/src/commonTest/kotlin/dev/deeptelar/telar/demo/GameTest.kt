package dev.deeptelar.telar.demo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import dev.deeptelar.telar.Checkpoint
import dev.deeptelar.telar.Checkpointer
import dev.deeptelar.telar.END
import dev.deeptelar.telar.MemoryCheckpointer
import dev.deeptelar.telar.START
import dev.deeptelar.telar.agent.ChatModel
import dev.deeptelar.telar.agent.ChatModelException
import dev.deeptelar.telar.demo.game.Agent
import dev.deeptelar.telar.demo.game.HelpDesks
import dev.deeptelar.telar.demo.game.Mail
import dev.deeptelar.telar.demo.game.Ticket
import dev.deeptelar.telar.demo.game.jsonLines
import dev.deeptelar.telar.demo.game.toJson
import dev.deeptelar.telar.demo.game.scriptedModel
import dev.deeptelar.telar.demo.kitchen.Blueprint
import dev.deeptelar.telar.demo.storage.MemoryStore
import dev.deeptelar.telar.demo.ui.Game
import dev.deeptelar.telar.demo.ui.LogLine
import dev.deeptelar.telar.demo.ui.Medal
import dev.deeptelar.telar.demo.ui.Phase
import dev.deeptelar.telar.demo.ui.Rush
import dev.deeptelar.telar.demo.ui.Tone
import dev.deeptelar.telar.serialization.CheckpointCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The tag of the log's line for what a conditional edge picked. */
private const val CHOICE = " ->"

/** The game behind the screens, driven the way the buttons drive it. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameTest {
    private val store = MemoryStore()
    private val saves = TextSaves()
    private val delivery = Mail("Ana", "Where is my pizza?")
    private val refund = Mail("Ben", "My pizza arrived cold. I want a refund.")

    /** A game whose nodes each take a second, so a test can look at a run halfway. */
    private fun TestScope.game(workMillis: Long = 1_000) = Game(scriptedModel(delayMillis = workMillis), store, saves, this, workMillis)

    @Test
    fun aRunLightsItsPathAndClearsTheStage() = runTest {
        val game = game()
        val stage = game.controller(2)

        stage.play(delivery)
        advanceTimeBy(500)
        assertTrue(stage.running)
        assertEquals(setOf("read"), stage.active)

        advanceUntilIdle()

        assertEquals(Phase.Clear, stage.phase)
        assertEquals(setOf(START, "read", "track", END), stage.visited)
        assertEquals(setOf(START to "read", "read" to "track", "track" to END), stage.trail)
        assertEquals(
            listOf(
                "01" to "read",
                "" to "topic = \"delivery\"",
                " ->" to "the edge after read picked track",
                "02" to "track",
                "" to "reply = \"Hi Ana, your pizza left the oven and is on its way.\"",
                "END" to "reply sent",
            ),
            stage.log.map { it.tag to it.text },
        )
        assertEquals(setOf(2), game.cleared)
    }

    @Test
    fun parallelNodesAreActiveTogetherAndShareAStep() = runTest {
        val stage = game().controller(4)

        stage.play(delivery)
        advanceTimeBy(500)
        assertEquals(setOf(HelpDesks.KITCHEN, "driver"), stage.active)

        advanceUntilIdle()

        assertEquals(2, stage.step)
        assertTrue(stage.trail.containsAll(setOf(HelpDesks.KITCHEN to "answer", "driver" to "answer")))
        // Both nodes wrote into the ticket in that step, and no edge of this stage has a choice to make.
        assertEquals(
            listOf(
                "kitchen: your pizza left the oven",
                "kitchen + driver",
                "facts += \"your pizza left the oven\"",
                "facts += \"the driver is 5 minutes away\"",
            ),
            stage.log.take(4).map { it.text },
        )
        assertFalse(stage.log.any { it.tag == CHOICE })
    }

    @Test
    fun aNodeForEachItemReportsTheItemsAsTheyFinish() = runTest {
        val stage = game().controller(4)

        stage.play(Mail("Ana", "Where are my margherita and my pepperoni?"))
        advanceTimeBy(1_500)

        // The pepperoni takes one moment and the margherita three, so the second item is done first.
        assertEquals(setOf(HelpDesks.KITCHEN), stage.active)
        assertEquals(mapOf(HelpDesks.KITCHEN to 1), stage.itemsDone)
        assertEquals(LogLine(" #1", "kitchen: your pepperoni left the oven", Tone.Detail), stage.log.single())

        advanceUntilIdle()

        assertEquals(mapOf(HelpDesks.KITCHEN to 2), stage.itemsDone)
        assertEquals(listOf(" #1", " #0", "01"), stage.log.take(3).map { it.tag })
        // The update got the facts in the order of the message, not in the order they were found.
        assertEquals("Hi Ana, your margherita is in the oven, your pepperoni left the oven and the driver is 5 minutes away.", stage.ticket?.reply)
    }

    @Test
    fun aLoopLightsTheWayBack() = runTest {
        val stage = game().controller(3)

        stage.play(Mail("Ana", "My pizza is late!"))
        advanceUntilIdle()

        assertTrue("check" to "write" in stage.trail)
        assertEquals(3, stage.ticket?.attempts)
        // The edge after `check` chose three times: back twice, and then out.
        assertEquals(
            listOf("the edge after check picked write", "the edge after check picked write", "the edge after check picked END"),
            stage.log.filter { it.tag == CHOICE }.map { it.text },
        )
        assertTrue("problem = \"\"" in stage.log.map { it.text })
    }

    @Test
    fun aSavePointWaitsForThePlayerAndPaysWhatWasDecided() = runTest {
        val game = game()
        val stage = game.controller(5)

        stage.play(refund)
        advanceUntilIdle()

        assertEquals(Phase.SavePoint, stage.phase)
        assertEquals(listOf(HelpDesks.PAY), stage.waitingAt)
        assertEquals(emptySet(), game.cleared)

        stage.decide(approved = true)
        advanceUntilIdle()

        assertEquals(Phase.Clear, stage.phase)
        assertEquals("Sorry Ben! We sent you 12 euros.", stage.ticket?.reply)
        assertEquals(setOf(5), game.cleared)
        // The decision is the player's line in the log, and the step of `pay` only reports what `pay` wrote.
        // The bank sent the money once, although `pay` ran twice.
        assertEquals(
            listOf(
                "01" to "prepare",
                "" to "refund = 12",
                "SAV" to "stopped before pay",
                "YOU" to "approved",
                "" to "approved = true",
                "OUT" to "bank: 12 euros sent to Ben, transfer T-1",
                "TRY" to "pay: the mail server is busy",
                "" to "try 2 in 1s",
                "02" to "pay",
                "" to "transfer = \"T-1\"",
                "" to "reply = \"Sorry Ben! We sent you 12 euros.\"",
                "END" to "reply sent",
            ),
            stage.log.map { it.tag to it.text },
        )
    }

    @Test
    fun aNewSessionRestoresARunThatWaitsAtASavePoint() = runTest {
        game().controller(5).play(refund)
        advanceUntilIdle()

        val reloaded = game().controller(5)
        advanceUntilIdle()

        assertEquals(Phase.SavePoint, reloaded.phase)
        assertTrue(reloaded.restored)
        assertEquals(12, reloaded.ticket?.refund)

        reloaded.decide(approved = false)
        advanceUntilIdle()

        assertEquals("Sorry Ben, we cannot refund this order.", reloaded.ticket?.reply)
    }

    @Test
    fun aFailedNodeIsGameOverAndRetryPicksUpThere() = runTest {
        val game = game()
        val stage = game.controller(7)

        stage.play(delivery)
        advanceUntilIdle()

        assertEquals(Phase.GameOver, stage.phase)
        assertEquals(HelpDesks.KITCHEN, stage.failedNode)
        assertEquals("the kitchen phone is busy", stage.error)
        assertEquals(2, stage.failedTries)
        assertEquals(listOf(HelpDesks.KITCHEN), stage.waitingAt)
        assertEquals(Tone.Failed, stage.log.last().tone)
        assertEquals(emptySet(), game.cleared)

        stage.retry()
        advanceUntilIdle()

        assertEquals(Phase.Clear, stage.phase)
        assertNull(stage.failedNode)
        assertEquals("Hi Ana! Your pizza is in the oven.", stage.ticket?.reply)
        // The node tried again by itself once, and the retry of the run did not run `greet` again.
        assertEquals(listOf("01", "TRY", "ERR", "02", "END"), stage.log.map { it.tag }.filter { it.isNotEmpty() })
        assertEquals(LogLine("", "try 2 in 1s", Tone.Detail), stage.log[stage.log.indexOfFirst { it.tag == "TRY" } + 1])
    }

    @Test
    fun stopLeavesTheRunUnfinishedAndContinueFinishesIt() = runTest {
        val stage = game().controller(1)

        stage.play(delivery)
        advanceTimeBy(1_500)
        assertEquals(setOf("answer"), stage.active)

        stage.stop()
        advanceUntilIdle()

        assertFalse(stage.running)
        assertEquals(emptySet(), stage.active)
        assertEquals(Phase.Unfinished, stage.phase)
        assertEquals(listOf("answer"), stage.waitingAt)

        stage.retry()
        advanceUntilIdle()

        assertEquals(Phase.Clear, stage.phase)
    }

    @Test
    fun aModelsAnswerShowsWhileItIsWritten() = runTest {
        val stage = game().controller(6)

        stage.play(Mail("Dee", "Hello there!"))
        // The scripted model thinks for half a second and then writes for half a second.
        advanceTimeBy(800)

        assertEquals(setOf(Agent.ASSISTANT), stage.active)
        assertTrue(stage.writing.startsWith("Hi Dee, "))
        assertTrue(stage.writing.length < "Hi Dee, thanks for writing to Pixel Pizza. A colleague will reply soon.".length)
        assertEquals("", stage.ticket?.reply)

        advanceUntilIdle()

        assertEquals("", stage.writing)
        assertEquals("Hi Dee, thanks for writing to Pixel Pizza. A colleague will reply soon.", stage.ticket?.reply)
    }

    @Test
    fun textThatArrivedBeforeAStopIsThrownAway() = runTest {
        val stage = game().controller(6)

        stage.play(Mail("Dee", "Hello there!"))
        advanceTimeBy(800)
        assertTrue(stage.writing.isNotEmpty())

        stage.stop()
        advanceUntilIdle()

        assertEquals("", stage.writing)
        assertEquals(Phase.Unfinished, stage.phase)
    }

    @Test
    fun theAgentsToolCallsAndResultsAreLogged() = runTest {
        val stage = game().controller(6)

        stage.play(Mail("Cleo", "Do you sell sushi?"))
        advanceUntilIdle()

        assertEquals(listOf("01", " >>", "02", " <<", "03", "END"), stage.log.map { it.tag }.filter { it.isNotEmpty() && it != CHOICE })
        assertEquals(Tone.Failed, stage.log.single { it.tag == " <<" }.tone)
        assertEquals(
            listOf("the edge after assistant picked tools", "the edge after assistant picked END"),
            stage.log.filter { it.tag == CHOICE }.map { it.text },
        )
        assertTrue(Agent.TOOLS to Agent.ASSISTANT in stage.trail)
    }

    @Test
    fun theAgentOfTheFullDeskLightsItsLoopAndTheWayOut() = runTest {
        val stage = game().controller(8)

        stage.play(Mail("Cleo", "Do you sell salad?"))
        advanceUntilIdle()

        assertEquals(Phase.Clear, stage.phase)
        assertEquals(
            setOf(START to "read", "read" to "model", "model" to "tools", "tools" to "model", "model" to "send", "send" to END),
            stage.trail,
        )
        assertEquals(
            listOf("01", "02", " >>", "03", " <<", "04", "05", "END"),
            stage.log.map { it.tag }.filter { it.isNotEmpty() && it != CHOICE },
        )
        assertEquals("Hi Cleo! One salad costs 6 euros.", stage.ticket?.reply)
    }

    @Test
    fun aStepThatHandsTheTicketOnSaysSo() = runTest {
        val stage = game().controller(8)

        stage.play(delivery)
        advanceUntilIdle()

        val lookUp = stage.log.indexOfFirst { it.text == "look_up" }
        assertEquals("ticket unchanged", stage.log[lookUp + 1].text)
        assertEquals(Tone.Detail, stage.log[lookUp + 1].tone)
    }

    @Test
    fun aRetryDoesNotReportAChoiceTwice() = runTest {
        var calls = 0
        val flaky = ChatModel { if (++calls == 1) throw ChatModelException("offline") else scriptedModel().chat(it) }
        val stage = Game(flaky, store, saves, this, workMillis = 0).controller(8)

        stage.play(Mail("Dan", "Thanks for the pizza!"))
        advanceUntilIdle()
        assertEquals(Phase.GameOver, stage.phase)

        stage.retry()
        advanceUntilIdle()

        assertEquals(Phase.Clear, stage.phase)
        assertEquals(1, stage.log.count { it.text == "the edge after read picked write" })
    }

    @Test
    fun aFailedModelCallIsGameOverAtTheNodeThatAsked() = runTest {
        val offline = ChatModel { throw ChatModelException("Could not reach the Claude API: offline") }
        val stage = Game(offline, store, saves, this, workMillis = 0).controller(6)

        stage.play(delivery)
        advanceUntilIdle()

        assertEquals(Phase.GameOver, stage.phase)
        assertEquals(Agent.ASSISTANT, stage.failedNode)
        assertEquals("Could not reach the Claude API: offline", stage.error)
    }

    @Test
    fun aSaveOfAnOlderVersionIsThrownAway() = runTest {
        // Until the game used the library's messages, a ticket kept the model's conversation in another shape.
        val oldTicket = """{"customer":"Ana","message":"Hi","chat":[{"role":"user","content":[{"type":"text","text":"Hi"}]}]}"""
        val oldSave = buildJsonObject {
            put("version", 1)
            put("state", oldTicket)
            putJsonArray("nextNodes") { add(Agent.ASSISTANT) }
            put("step", 0)
            put("interruptedBefore", false)
        }
        saves.text["stage-6"] = oldSave.toString()

        val stage = game().controller(6)
        advanceUntilIdle()

        assertEquals(Phase.Ready, stage.phase)
        assertNull(stage.error)
        assertNull(saves.text["stage-6"])
    }

    @Test
    fun resetThrowsTheSavedRunAway() = runTest {
        val stage = game().controller(5)
        stage.play(refund)
        advanceUntilIdle()

        stage.reset()
        advanceUntilIdle()

        assertEquals(Phase.Ready, stage.phase)
        assertNull(stage.ticket)

        val reloaded = game().controller(5)
        advanceUntilIdle()
        assertEquals(Phase.Ready, reloaded.phase)
    }

    @Test
    fun theLiveViewListsEveryCheckpointAsTheRunSavesIt() = runTest {
        // The library's MemoryCheckpointer keeps every step, as the checkpointers of the game do.
        val game = Game(scriptedModel(delayMillis = 1_000), store, MemoryCheckpointer(), this, 1_000)
        val stage = game.controller(5)
        advanceUntilIdle()
        assertEquals(emptyList(), stage.checkpoints)

        stage.play(refund)
        advanceTimeBy(500)
        // The ticket is saved before the first node runs.
        assertEquals(listOf(0), stage.checkpoints.map { it.step })
        assertNull(stage.ticketBefore)

        advanceUntilIdle()
        assertEquals(listOf(0, 1), stage.checkpoints.map { it.step })
        assertEquals(listOf(HelpDesks.PAY), stage.checkpoints.last().nextNodes)
        assertTrue(stage.checkpoints.last().interruptedBefore)
        assertEquals(0, stage.ticketBefore?.refund)
        assertEquals(12, stage.ticket?.refund)

        stage.decide(approved = true)
        advanceUntilIdle()
        assertEquals(listOf(0, 1, 2), stage.checkpoints.map { it.step })
        assertTrue(stage.checkpoints.last().isComplete)
        assertTrue(stage.checkpoints.last().state.approved)

        // A new session reads them from storage, and Discard throws them away.
        val reloaded = Game(scriptedModel(), store, MemoryCheckpointer<Ticket>().also { saves -> stage.checkpoints.forEach { saves.save("stage-5", it) } }, this, 0)
            .controller(5)
        advanceUntilIdle()
        assertEquals(listOf(0, 1, 2), reloaded.checkpoints.map { it.step })
        reloaded.reset()
        advanceUntilIdle()
        assertEquals(emptyList(), reloaded.checkpoints)
    }

    @Test
    fun theJsonOfATicketMarksWhatAStepChanged() {
        val before = Ticket("Ben", "My pizza arrived cold.")
        val after = before.copy(topic = "refund", facts = listOf("ordered at 19:02"))

        val changed = after.jsonLines(before).filter { it.second }.map { it.first.trim() }

        // The empty list was `[]` on one line, so its new closing bracket is marked too.
        assertEquals(listOf("\"topic\": \"refund\",", "\"facts\": [", "\"ordered at 19:02\"", "],"), changed)
        assertTrue(after.jsonLines(null).none { it.second })
        // Every field of the data class is there, also the ones that still have their default.
        assertTrue("\"reply\": \"\"" in after.toJson())
    }

    @Test
    fun theLiveViewSwitchSurvivesANewSession() = runTest {
        val game = game()
        advanceUntilIdle()
        assertFalse(game.inspecting)

        game.inspect(true)
        advanceUntilIdle()
        assertTrue(game().also { advanceUntilIdle() }.inspecting)

        game.inspect(false)
        advanceUntilIdle()
        assertFalse(game().also { advanceUntilIdle() }.inspecting)
    }

    @Test
    fun clearedStagesSurviveANewSessionUntilTheyAreErased() = runTest {
        val game = game()
        game.controller(1).play(delivery)
        advanceUntilIdle()

        val reloaded = game()
        advanceUntilIdle()
        assertEquals(setOf(1), reloaded.cleared)

        reloaded.erase()
        advanceUntilIdle()

        assertEquals(emptySet(), game().also { advanceUntilIdle() }.cleared)
    }

    @Test
    fun theMedalsOfTheBonusStageSurviveANewSessionUntilTheyAreErased() = runTest {
        val game = game()
        advanceUntilIdle()
        assertFalse(game.hasMedals)
        assertEquals(Rush.Opening, game.kitchen.rush)
        game.kitchen.load(Blueprint.FIRST)
        game.kitchen.start()
        advanceUntilIdle()
        assertEquals(listOf(true), game.kitchen.goals.map { it.second })
        assertEquals("Opening.Right", store.get("pixelpizza.kitchen.medals"))

        val reloaded = game()
        advanceUntilIdle()
        assertTrue(reloaded.hasMedals)
        assertEquals(setOf(Rush.Opening to Medal.Right), reloaded.kitchen.earned)

        // A medal that the game no longer has is left out, and the others are kept.
        store.set("pixelpizza.kitchen.medals", "Opening.Right,Brunch.Right,Opening.Small,Lunch.Small,nonsense")
        val later = game()
        advanceUntilIdle()
        assertEquals(setOf(Rush.Opening to Medal.Right, Rush.Lunch to Medal.Small), later.kitchen.earned)

        later.erase()
        advanceUntilIdle()
        assertFalse(later.hasMedals)
        assertEquals(emptySet(), game().also { advanceUntilIdle() }.kitchen.earned)
    }
}

/**
 * Keeps each run as the text that the library's checkpointers write to a file or to `localStorage`,
 * so a test can leave a save of an older version of the game behind.
 */
private class TextSaves : Checkpointer<Ticket> {
    val text = mutableMapOf<String, String>()
    private val codec = CheckpointCodec<Ticket>()

    override suspend fun save(threadId: String, checkpoint: Checkpoint<Ticket>) {
        text[threadId] = codec.encode(checkpoint)
    }

    override suspend fun load(threadId: String): Checkpoint<Ticket>? = text[threadId]?.let { codec.decode(threadId, it) }

    override suspend fun delete(threadId: String) {
        text.remove(threadId)
    }
}
