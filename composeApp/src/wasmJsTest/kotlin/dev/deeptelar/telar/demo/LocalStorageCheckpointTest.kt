package dev.deeptelar.telar.demo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import dev.deeptelar.telar.demo.game.Mail
import dev.deeptelar.telar.demo.game.scriptedModel
import dev.deeptelar.telar.demo.storage.MemoryStore
import dev.deeptelar.telar.demo.storage.platformCheckpointer
import dev.deeptelar.telar.demo.ui.Game
import dev.deeptelar.telar.demo.ui.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs in a real browser: a paused stage lives in `localStorage` between two sessions of the game. */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalStorageCheckpointTest {
    /** A game as the page creates it: with the checkpointer of the browser. Each call is a reload. */
    private fun TestScope.game() = Game(scriptedModel(), MemoryStore(), platformCheckpointer(), this, workMillis = 0)

    @Test
    fun aStagePausedInOneSessionWaitsInTheNext() = runTest {
        game().controller(5).play(Mail("Ben", "My pizza arrived cold. I want a refund."))
        advanceUntilIdle()

        val reloaded = game().controller(5)
        advanceUntilIdle()
        assertEquals(Phase.SavePoint, reloaded.phase)
        assertTrue(reloaded.restored)
        assertEquals(12, reloaded.ticket?.refund)

        reloaded.decide(approved = true)
        advanceUntilIdle()
        assertEquals("Sorry Ben! We sent you 12 euros.", reloaded.ticket?.reply)

        // The run is finished, so the save is gone and the next session starts with an empty desk.
        reloaded.reset()
        advanceUntilIdle()
        val afterwards = game().controller(5)
        advanceUntilIdle()
        assertEquals(Phase.Ready, afterwards.phase)
        assertNull(afterwards.ticket)
    }

    @Test
    fun theLiveViewReadsEveryCheckpointOfARunFromLocalStorage() = runTest {
        val stage = game().controller(5)
        advanceUntilIdle()
        stage.reset()
        advanceUntilIdle()
        stage.play(Mail("Ben", "My pizza arrived cold. I want a refund."))
        advanceUntilIdle()
        stage.decide(approved = true)
        advanceUntilIdle()

        // The ticket as the run started, after prepare, and after pay: also in the next session.
        assertEquals(listOf(0, 1, 2), stage.checkpoints.map { it.step })
        val reloaded = game().controller(5)
        advanceUntilIdle()
        assertEquals(listOf(0, 1, 2), reloaded.checkpoints.map { it.step })
        assertTrue(reloaded.checkpoints.last().state.approved)

        reloaded.reset()
        advanceUntilIdle()
    }
}
