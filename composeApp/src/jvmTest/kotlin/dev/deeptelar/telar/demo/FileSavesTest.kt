package dev.deeptelar.telar.demo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import dev.deeptelar.telar.demo.game.Mail
import dev.deeptelar.telar.demo.game.scriptedModel
import dev.deeptelar.telar.demo.storage.MemoryStore
import dev.deeptelar.telar.demo.storage.fileCheckpointer
import dev.deeptelar.telar.demo.ui.Game
import dev.deeptelar.telar.demo.ui.Phase
import dev.deeptelar.telar.demo.ui.StageController
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** On the desktop and on Android a paused stage is a file, which the next start of the app finds. */
class FileSavesTest {
    private val directory = Files.createTempDirectory("pixelpizza-saves").toFile()

    /** A game as the app creates it: with a checkpointer on the directory. Each call is a new start. */
    private fun TestScope.game() = Game(scriptedModel(), MemoryStore(), fileCheckpointer(directory), this, workMillis = 0)

    /** Files are written on real threads, so the test waits in real time and not in the time of the test. */
    private suspend fun StageController.awaitSavePoint() = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (phase != Phase.SavePoint) delay(10) }
    }

    /** Waits for [wanted], like [awaitSavePoint]. */
    private suspend fun StageController.await(wanted: Phase) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (running || phase != wanted) delay(10) }
    }

    /** Puts the file of a run that the game saved with Telar 0.1.0-alpha12, as a player who updates the app has it. */
    private fun oldSave(stage: Int, name: String = "stage-$stage") {
        directory.resolve("stage-$stage.json").writeBytes(checkNotNull(javaClass.getResourceAsStream("/saves/alpha12/$name.json")).readBytes())
    }

    @Test
    fun aRunPausedByAnOlderVersionOfTheLibraryGoesOn() = runTest {
        oldSave(5)
        val stage = game().controller(5)
        stage.awaitSavePoint()
        assertEquals(12, stage.ticket?.refund)

        stage.decide(approved = true)
        stage.await(Phase.Clear)

        assertEquals("Sorry Ben! We sent you 12 euros.", stage.ticket?.reply)
        directory.deleteRecursively()
    }

    @Test
    fun aRefundPaidBeforeTheAppEndedIsNotPaidAgainByANewerVersionOfTheLibrary() = runTest {
        // The save is from the pause after the transfer and the failed mail. It holds the transfer as an effect.
        oldSave(5, name = "stage-5-paid")
        val stage = game().controller(5)
        stage.awaitSavePoint()
        assertEquals(true, stage.ticket?.approved)
        assertEquals("", stage.ticket?.transfer)

        stage.decide(approved = true)
        stage.await(Phase.Clear)

        assertEquals("T-1", stage.ticket?.transfer)
        assertTrue(stage.log.none { it.tag == "OUT" }, "the bank was not called")
        directory.deleteRecursively()
    }

    @Test
    fun aConversationSavedByAnOlderVersionOfTheLibraryGoesOn() = runTest {
        oldSave(6)
        val stage = game().controller(6)
        stage.await(Phase.Unfinished)
        assertEquals(listOf("tools"), stage.waitingAt)
        assertEquals(2, stage.ticket?.chat?.size)

        // The tool call of the model is still to run, and runs as an effect now.
        stage.retry()
        stage.await(Phase.Clear)

        assertEquals("Hi Ana! The pizza for Ana left the oven and the driver is 5 minutes away.", stage.ticket?.reply)
        directory.deleteRecursively()
    }

    @Test
    fun aStagePausedInOneSessionWaitsInTheNext() = runTest {
        val first = game().controller(5)
        first.play(Mail("Ben", "My pizza arrived cold. I want a refund."))
        first.awaitSavePoint()
        assertTrue(directory.listFiles().orEmpty().any { it.name == "stage-5.json" }, "the run is a file")

        val restarted = game().controller(5)
        restarted.awaitSavePoint()

        assertTrue(restarted.restored)
        assertEquals(12, restarted.ticket?.refund)
        directory.deleteRecursively()
    }
}
