package dev.deeptelar.telar.demo

import androidx.compose.runtime.mutableStateOf
import dev.deeptelar.telar.GraphConfig
import dev.deeptelar.telar.agent.ChatModel
import dev.deeptelar.telar.agent.ChatModelException
import dev.deeptelar.telar.demo.kitchen.LineShift
import dev.deeptelar.telar.demo.kitchen.SHIFT_STEPS
import dev.deeptelar.telar.demo.kitchen.Shift
import dev.deeptelar.telar.demo.kitchen.Topping
import dev.deeptelar.telar.demo.kitchen.line
import dev.deeptelar.telar.demo.kitchen.lunchRush
import dev.deeptelar.telar.demo.kitchen.report
import dev.deeptelar.telar.demo.kitchen.scriptedChef
import dev.deeptelar.telar.demo.kitchen.scriptedReader
import dev.deeptelar.telar.demo.kitchen.shift
import dev.deeptelar.telar.demo.kitchen.soloChef
import dev.deeptelar.telar.demo.ui.KitchenController
import dev.deeptelar.telar.demo.ui.KitchenLayout
import dev.deeptelar.telar.demo.ui.PlayedShift
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The bonus stage. The kitchen is the sample of the library, copied, and the first two tests hold
 * the copy to the results the sample has there. The others drive the stage the way its buttons do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KitchenRushTest {
    private fun TestScope.kitchen(model: ChatModel? = null) = KitchenController(model, "scripted", this, minuteMillis = 100)

    @Test
    fun theSoloChefServesTheLunchRush() = runTest {
        val end = soloChef(scriptedChef()).invoke(shift(lunchRush()), GraphConfig<Shift>(maxIterations = SHIFT_STEPS)).state

        assertEquals(
            listOf(
                "orders   11 of 11 on time",
                "points   1100 of 1100",
                "tokens   ${end.spent} (${end.tokens.inputTokens} read, ${end.tokens.outputTokens} written) in 11 answers",
                "kitchen  every order served at minute 85, 0 pizzas wasted",
            ),
            end.report().lines(),
        )
        assertEquals("P1 is on the counter: tomato, mozzarella, basil.", end.log.first().text)
        assertEquals("chef", end.log.first().station)
        assertEquals(85, end.log.last().minute)
    }

    @Test
    fun theLineServesTheLunchRushSoonerAndForFewerTokens() = runTest {
        val end = line(scriptedReader()).invoke(LineShift(lunchRush()), GraphConfig<LineShift>(maxIterations = SHIFT_STEPS)).state
        val solo = soloChef(scriptedChef()).invoke(shift(lunchRush()), GraphConfig<Shift>(maxIterations = SHIFT_STEPS)).state

        assertEquals(1100, end.kitchen.score().points)
        assertEquals(78, end.kitchen.minute)
        assertEquals(10, end.reader.answers)
        assertEquals(0, end.prep.answers + end.oven.answers)
        assertTrue(end.spent < solo.spent / 2)
        // Table 3 said at minute 8 that it may not get onions. The ticket of its veggie has none.
        assertFalse(Topping.ONIONS in end.tickets.getValue(10))
        assertEquals("reader", end.log.first().station)
    }

    @Test
    fun aShiftIsShownMinuteByMinuteAndKeptWhenItIsOver() = runTest {
        val kitchen = kitchen()
        assertEquals(KitchenLayout.Line, kitchen.layout)
        assertNull(kitchen.shift)

        kitchen.start()
        advanceTimeBy(4_050)

        assertTrue(kitchen.running)
        assertEquals(40, kitchen.shift!!.kitchen.minute)
        assertFalse(kitchen.finished)
        // The kitchen cannot be rebuilt in the middle of a shift.
        kitchen.choose(KitchenLayout.Solo)
        assertEquals(KitchenLayout.Line, kitchen.layout)

        advanceUntilIdle()

        assertFalse(kitchen.running)
        assertTrue(kitchen.finished)
        assertEquals(1100, kitchen.shift!!.kitchen.score().points)
        assertEquals(listOf(KitchenLayout.Line), kitchen.history.map { it.layout })

        kitchen.choose(KitchenLayout.Solo)
        kitchen.start()
        advanceUntilIdle()

        assertEquals(KitchenLayout.Solo, kitchen.playing)
        assertEquals(listOf(78, 85), kitchen.history.map { it.shift.kitchen.minute })
        assertEquals(listOf("scripted", "scripted"), kitchen.history.map { it.model })
    }

    @Test
    fun everyKitchenRunsWithTheScriptedChefs() = runTest {
        val kitchen = kitchen()

        KitchenLayout.entries.forEach { layout ->
            kitchen.choose(layout)
            kitchen.start()
            advanceUntilIdle()
            assertTrue(kitchen.finished, layout.title)
        }

        assertEquals(KitchenLayout.entries, kitchen.history.map { it.layout })
        // Chefs who do one thing with each answer, at a minute an answer, are slower than plain code.
        assertTrue(kitchen.history.last().shift.kitchen.score().points < 1100)
        assertTrue(kitchen.history.last().shift.desks.getValue("oven").answers > 0)
    }

    @Test
    fun aShiftThatWasStoppedIsNotKept() = runTest {
        val kitchen = kitchen()

        kitchen.start()
        advanceTimeBy(2_000)
        kitchen.stop()
        advanceUntilIdle()

        assertFalse(kitchen.running)
        assertFalse(kitchen.finished)
        assertTrue(kitchen.shift!!.kitchen.minute in 15..25)
        assertEquals(emptyList(), kitchen.history)
    }

    @Test
    fun aModelThatFailsEndsTheShiftAndSaysWhy() = runTest {
        val kitchen = KitchenController(ChatModel { throw ChatModelException("The key is not valid.") }, "broken", this, minuteMillis = 0)

        kitchen.start()
        advanceUntilIdle()

        assertFalse(kitchen.running)
        assertFalse(kitchen.finished)
        assertEquals("The key is not valid.", kitchen.error)
        assertEquals(emptyList(), kitchen.history)

        // The next shift starts clean.
        kitchen.start()
        assertNull(kitchen.error)
        advanceUntilIdle()
    }

    @Test
    fun theTokenBudgetEndsAShiftAndStaysWithinItsLimits() = runTest {
        val kitchen = kitchen()

        kitchen.budget(10)
        assertEquals(1_000, kitchen.budget)
        kitchen.budget(Int.MAX_VALUE)
        assertEquals(KitchenController.MAX_BUDGET, kitchen.budget)

        kitchen.budget(1_500)
        kitchen.start()
        advanceUntilIdle()

        assertTrue(kitchen.finished)
        assertTrue(kitchen.shift!!.outOfTokens)
        assertTrue("out of tokens" in kitchen.shift!!.report())
    }

    @Test
    fun theShiftsThatWerePlayedOutliveTheController() = runTest {
        val played = mutableStateOf(emptyList<PlayedShift>())
        val first = KitchenController(null, "scripted", this, minuteMillis = 0, played = played)

        first.start()
        advanceUntilIdle()
        // The player chose another model under Options, which makes a new controller.
        val second = KitchenController(null, "another", this, minuteMillis = 0, played = played)
        second.start()
        advanceUntilIdle()

        assertEquals(listOf("scripted", "another"), second.history.map { it.model })
    }
}
