package dev.deeptelar.telar.demo

import dev.deeptelar.telar.demo.kitchen.Blueprint
import dev.deeptelar.telar.demo.kitchen.Blueprint.Companion.KITCHEN
import dev.deeptelar.telar.demo.kitchen.Blueprint.Companion.RAIL
import dev.deeptelar.telar.demo.kitchen.Sketch
import dev.deeptelar.telar.demo.kitchen.Exit
import dev.deeptelar.telar.demo.kitchen.Guide
import dev.deeptelar.telar.demo.kitchen.Touch
import dev.deeptelar.telar.demo.kitchen.PieceKind
import dev.deeptelar.telar.demo.ui.KitchenController
import dev.deeptelar.telar.demo.ui.Rush
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The steps of the short shifts: what the game tells a player to press, and where pressing it leads. */
@OptIn(ExperimentalCoroutinesApi::class)
class KitchenGuideTest {
    private fun TestScope.kitchen() = KitchenController(null, "scripted", this, minuteMillis = 0)

    /** Does what the step says, the way the buttons of the screen do. */
    private fun KitchenController.follow(touch: Touch) {
        when (touch) {
            is Touch.Place -> tapSlot(touch.slot)
            is Touch.Choose -> put(touch.kind)
            is Touch.Press -> tap(touch.ids.first())
            is Touch.Redraw -> redraw(touch.exit)
            Touch.Remove -> remove()
            Touch.Start -> start()
        }
    }

    /** Follows the steps until the shift starts, and returns what they said. Each step counts one fewer to go than the one before. */
    private fun KitchenController.followAll(): List<String> {
        val said = mutableListOf<String>()
        var left: Int? = null
        while (!running) {
            val step = assertNotNull(step, "A step after: $said")
            left?.let { assertEquals(it - 1, step.left, "After: $said") }
            left = step.left
            said += step.text
            follow(step.touch)
            assertTrue(said.size < 40, "The steps do not end: $said")
        }
        assertEquals(1, left)
        return said
    }

    @Test
    fun theFirstShiftOpensWithABoardThatLacksOneArrow() = runTest {
        val kitchen = kitchen()

        assertEquals(Rush.Opening, kitchen.rush)
        assertEquals("lookup", kitchen.board.target(RAIL, Exit.NEXT))
        assertEquals(listOf("Menu lookup has no arrow. An order that gets there is stuck."), kitchen.problems)
        assertEquals(3, Guide.FIRST_ORDERS.steps)

        // Two presses draw the arrow, and each step says how many are left.
        assertEquals(Touch.Press("lookup"), kitchen.step?.touch)
        assertEquals("Press the lookup tile.", kitchen.step?.text)
        assertEquals(3, kitchen.step?.left)
        kitchen.tap("lookup")
        assertEquals("Press the kitchen. That draws the arrow from the lookup tile to it.", kitchen.step?.text)
        assertEquals(2, kitchen.step?.left)
        kitchen.tap(KITCHEN)
        assertEquals(emptyList(), kitchen.problems)
        assertEquals(Touch.Start, kitchen.step?.touch)
        assertEquals("The board is ready. Start the shift.", kitchen.step?.text)
        assertFalse(kitchen.canHint)

        // While the shift runs there is no step, and none after it: the board was played.
        kitchen.start()
        assertNull(kitchen.step)
        advanceUntilIdle()
        assertEquals(listOf(true), kitchen.goals.map { it.second })
        assertNull(kitchen.step)
        assertFalse(kitchen.canHint)

        // A board that was changed after the shift has steps again.
        kitchen.tap("lookup")
        kitchen.remove()
        assertEquals("Press the + that blinks, to put a station there.", kitchen.step?.text)
        assertEquals(Touch.Place(2), kitchen.step?.touch)
    }

    @Test
    fun theStepsOfAShiftThatShowsThemLeadToABoardThatWinsIt() = runTest {
        val kitchen = kitchen()

        assertEquals(
            listOf("Press the lookup tile.", "Press the kitchen. That draws the arrow from the lookup tile to it.", "The board is ready. Start the shift."),
            kitchen.followAll(),
        )
        advanceUntilIdle()
        assertEquals(listOf(true), kitchen.goals.map { it.second })

        // The second shift opens with the board that won the first. The lookup comes off, and the reader takes its place.
        kitchen.choose(Rush.Wishes)
        assertEquals(Guide.through(PieceKind.LOOKUP), kitchen.board)
        assertEquals(emptyList(), kitchen.problems)
        assertEquals(8, Guide.WISHES.steps)
        assertEquals(
            listOf(
                "Press the lookup tile. This board does not need it.",
                "Press \"Remove the station\" under the board.",
                "Press the + that blinks, to put a station there.",
                "Choose \"Reader\" in the list under the board.",
                "Press the kitchen. That draws the arrow from the reader tile to it.",
                "Press the rail.",
                "Press the reader tile. That draws the arrow from the rail to it.",
                "The board is ready. Start the shift.",
            ),
            kitchen.followAll(),
        )
        // The reader stands where the lookup stood, between the rail and the kitchen.
        assertEquals(Guide.through(PieceKind.READER).pieces, kitchen.board.pieces)
        assertEquals(Guide.through(PieceKind.READER).arrows.toSet(), kitchen.board.arrows.toSet())
        advanceUntilIdle()
        assertEquals(listOf(true), kitchen.goals.map { it.second })

        // The third opens with its stations, and asks where the arrows of the sorter go.
        kitchen.choose(Rush.Sorting)
        assertEquals("plain? + lookup + reader", kitchen.board.pieces.joinToString(" + ") { it.kind.tile })
        assertEquals(listOf("Sorter: only a menu pizza? needs an arrow for yes and an arrow for no."), kitchen.problems)
        assertEquals(4, Guide.SORTING.steps)
        assertEquals(
            listOf(
                "Press the plain? tile.",
                "Its yes arrow: press the station for an order that is only a menu pizza.",
                "Its no arrow: press the station for every other order.",
                "The board is ready. Start the shift.",
            ),
            kitchen.followAll(),
        )
        advanceUntilIdle()
        assertEquals(listOf(true, true), kitchen.goals.map { it.second })
    }

    @Test
    fun theArrowsOfTheSorterAreThePlayersToDecide() = runTest {
        val kitchen = kitchen()
        kitchen.choose(Rush.Sorting)

        // Both stations are offered for the yes arrow, and the wrong one is taken as well.
        kitchen.tap("onlyMenu")
        assertEquals(Touch.Press(listOf("lookup", "reader")), kitchen.step?.touch)
        kitchen.tap("reader")
        assertEquals(Touch.Press(listOf("reader", "lookup")), kitchen.step?.touch)
        kitchen.tap("lookup")
        assertEquals(Touch.Start, kitchen.step?.touch)
        assertFalse(kitchen.canHint)

        // The shift runs, and the lookup gets what it cannot read.
        kitchen.start()
        advanceUntilIdle()
        assertEquals(listOf(false, false), kitchen.goals.map { it.second })
        assertNull(kitchen.step)

        // Now the player can ask, and the steps lead to the board that wins.
        assertTrue(kitchen.canHint)
        kitchen.hint(true)
        assertFalse(kitchen.canHint)
        assertEquals(
            listOf(
                "Press the plain? tile.",
                "Press \"Change\" beside \"yes goes to\", under the board.",
                "Press the lookup tile. That draws the yes arrow of the plain? tile to it.",
                "Press \"Change\" beside \"no goes to\", under the board.",
                "Press the reader tile. That draws the no arrow of the plain? tile to it.",
                "The board is ready. Start the shift.",
            ),
            kitchen.followAll(),
        )
        advanceUntilIdle()
        assertEquals(listOf(true, true), kitchen.goals.map { it.second })
        kitchen.hint(false)
        assertNull(kitchen.step)
    }

    @Test
    fun arrowsThatLeaveABoardUnableToRunAreLedToOneThatRuns() = runTest {
        val kitchen = kitchen()
        kitchen.choose(Rush.Sorting)

        // Both arrows to the lookup: no order gets to the reader, which Telar does not let pass.
        kitchen.tap("onlyMenu")
        kitchen.tap("lookup")
        kitchen.tap("lookup")
        assertEquals(listOf("Telar: Node 'reader' is not reachable from START"), kitchen.problems)
        assertEquals("Press \"Change\" beside \"no goes to\", under the board.", kitchen.step?.text)
        // With the arrow in hand again, where it goes is asked again.
        kitchen.redraw(Exit.NO)
        assertEquals("Its no arrow: press the station for every other order.", kitchen.step?.text)
        kitchen.tap("reader")
        assertEquals(Touch.Start, kitchen.step?.touch)
    }

    @Test
    fun aStepNeverDrawsAnArrowThePlayerDidNotMean() {
        val guide = Guide.WISHES
        // The reader was put on the board and its arrow is in hand. The lookup beside it is not needed.
        val sketch = Sketch().tapSlot(0).put(PieceKind.LOOKUP).tap(KITCHEN).tapSlot(2).put(PieceKind.READER)
        assertEquals("reader", sketch.selected)
        assertEquals(Exit.NEXT, sketch.armed)

        // Its own arrow comes first, and then the hand is empty when the lookup is pressed.
        val first = guide.next(sketch)
        assertEquals(Touch.Press(KITCHEN), first.touch)
        val second = guide.next(sketch.after(first.touch))
        assertEquals("Press the lookup tile. This board does not need it.", second.text)
        val third = guide.next(sketch.after(first.touch).after(second.touch))
        assertEquals(Touch.Remove, third.touch)
        assertEquals("Press \"Remove the station\" under the board.", third.text)

        // An arrow in hand that has nowhere to go yet is let go before another tile is pressed: the oven is not on the board.
        val held = Sketch(Guide.URGENT.boards.first()).tap(RAIL).redraw(Exit.NEXT)
        val letGo = Guide.TWO_OVENS.next(held, strict = true)
        assertEquals(Touch.Press(RAIL), letGo.touch)
        assertEquals("Press the rail again. That lets its arrow go.", letGo.text)
        assertEquals("Press the rush tile. This board does not need it.", Guide.TWO_OVENS.next(held.after(letGo.touch), strict = true).text)
    }

    @Test
    fun aShortShiftOpensWithABoardThatCanRunAndShowsItsStepsWhenAsked() = runTest {
        val kitchen = kitchen()
        val later = listOf(Rush.Book, Rush.LookingBack, Rush.Urgent, Rush.TwoOvens)

        later.forEach { rush ->
            val guide = assertNotNull(rush.guide)
            assertFalse(rush.guided, rush.title)

            // The board of the first shift cannot run here with what it lacks, so the shift opens with its own.
            kitchen.choose(Rush.Opening)
            kitchen.choose(rush)
            assertEquals(guide.start, kitchen.board, rush.title)
            assertEquals(emptyList(), kitchen.problems, rush.title)
            assertNull(kitchen.step, rush.title)
            assertTrue(kitchen.canHint, rush.title)

            // The steps lead to a board that wins every medal of the shift.
            kitchen.hint(true)
            val said = kitchen.followAll()
            assertEquals(guide.steps, said.size, rush.title)
            advanceUntilIdle()
            assertTrue(kitchen.goals.all { it.second }, "${rush.title}: $said")
            assertEquals(guide.boards.first().pieces.toSet(), kitchen.board.pieces.toSet(), rush.title)
            assertEquals(guide.boards.first().arrows.toSet(), kitchen.board.arrows.toSet(), rush.title)
        }

        // Every board the steps can lead to wins its shift.
        Rush.entries.forEach { rush ->
            rush.guide?.boards?.forEach { board ->
                kitchen.choose(Rush.Lunch)
                kitchen.forget()
                kitchen.choose(rush)
                kitchen.load(board)
                assertEquals(emptyList(), kitchen.problems, rush.title)
                kitchen.start()
                advanceUntilIdle()
                assertTrue(kitchen.goals.all { it.second }, "${rush.title}: ${board.summary}")
            }
        }
        assertNull(Rush.Lunch.guide)
        assertNull(Rush.Dinner.guide)
    }

    @Test
    fun theStepsLeadToTheBoardThatIsNearest() = runTest {
        val kitchen = kitchen()

        // A player who served the book shift with one reader keeps that board in the next one that needs no model.
        kitchen.choose(Rush.Book)
        kitchen.load(Guide.through(PieceKind.BOOK_READER))
        kitchen.choose(Rush.LookingBack)
        assertEquals(Guide.through(PieceKind.BOOK_READER), kitchen.board)

        // In the shift of the expediter the nearest board that wins is the lookup behind the expediter, not the six stations.
        kitchen.load(Guide.through(PieceKind.LOOKUP))
        kitchen.choose(Rush.Urgent)
        assertEquals(Guide.through(PieceKind.LOOKUP), kitchen.board)
        kitchen.hint(true)
        val said = kitchen.followAll()
        assertEquals(
            listOf(
                "Press the + that blinks, to put a station there.",
                "Choose \"Expediter\" in the list under the board.",
                "Press the lookup tile. That draws the arrow from the rush tile to it.",
                "Press the rail.",
                "Press \"Change\" beside \"arrow to\", under the board.",
                "Press the rush tile. That draws the arrow from the rail to it.",
                "The board is ready. Start the shift.",
            ),
            said,
        )
        advanceUntilIdle()
        assertEquals(listOf(true), kitchen.goals.map { it.second })
    }

    @Test
    fun choosingAShiftKeepsABoardThatRunsAndTheShiftsThatShowTheirStepsStartOver() = runTest {
        val kitchen = kitchen()

        // The shift that is chosen already keeps what the player drew.
        kitchen.tap("lookup")
        kitchen.tap(KITCHEN)
        kitchen.choose(Rush.Opening)
        assertEquals(Guide.through(PieceKind.LOOKUP), kitchen.board)

        // Another shift that shows its steps opens with its own board, also when the player comes back to it.
        kitchen.choose(Rush.Sorting)
        assertEquals(Guide.SORTING.start, kitchen.board)
        kitchen.choose(Rush.Opening)
        assertEquals(Guide.FIRST_ORDERS.start, kitchen.board)

        // A rush keeps every board, and says what is wrong with it.
        kitchen.choose(Rush.Lunch)
        assertEquals(Guide.FIRST_ORDERS.start, kitchen.board)
        assertEquals(1, kitchen.problems.size)
        assertNull(kitchen.step)
        assertFalse(kitchen.canHint)

        // The steps are put away when another shift is chosen.
        kitchen.choose(Rush.Book)
        kitchen.hint(true)
        assertNotNull(kitchen.step)
        kitchen.choose(Rush.LookingBack)
        assertFalse(kitchen.hinting)
        assertNull(kitchen.step)
    }
}
