package dev.deeptelar.telar.demo

import androidx.compose.runtime.mutableStateOf
import dev.deeptelar.telar.GraphConfig
import dev.deeptelar.telar.agent.ChatModel
import dev.deeptelar.telar.agent.ChatModelException
import dev.deeptelar.telar.demo.kitchen.Blueprint
import dev.deeptelar.telar.demo.kitchen.Exit
import dev.deeptelar.telar.demo.kitchen.Guide
import dev.deeptelar.telar.demo.kitchen.LineShift
import dev.deeptelar.telar.demo.kitchen.PieceKind
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
import dev.deeptelar.telar.demo.kitchen.text
import dev.deeptelar.telar.demo.ui.Bake
import dev.deeptelar.telar.demo.ui.KitchenController
import dev.deeptelar.telar.demo.ui.KitchenLink
import dev.deeptelar.telar.demo.ui.Medal
import dev.deeptelar.telar.demo.ui.PlayedShift
import dev.deeptelar.telar.demo.ui.Rush
import dev.deeptelar.telar.demo.ui.bake
import dev.deeptelar.telar.demo.ui.bakeText
import dev.deeptelar.telar.demo.ui.ovenPlaces
import dev.deeptelar.telar.demo.ui.told
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The bonus stage. The kitchen is the sample of the library, copied, and the first two tests hold
 * the copy to the results the sample has there. The others drive the stage the way its buttons do,
 * on the board that the player draws. [KitchenBoardTest] has the tests of the board itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KitchenRushTest {
    /** The bonus stage with the lunch rush chosen and the board that sends every order to the lookup. */
    private fun TestScope.kitchen(model: ChatModel? = null, minuteMillis: Long = 100, stepMillis: Long = 0, label: String = "scripted") =
        KitchenController(
            model,
            label,
            this,
            minuteMillis,
            stepMillis,
            drawn = mutableStateOf(Blueprint.FIRST),
            chosen = mutableStateOf(Rush.Lunch),
        )

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
        assertEquals(Blueprint.FIRST, kitchen.board)
        assertNull(kitchen.shift)
        kitchen.load(boardWith(PieceKind.BOOK_READER))

        kitchen.start()
        advanceTimeBy(4_050)

        assertTrue(kitchen.running)
        assertEquals(40, kitchen.shift!!.kitchen.minute)
        assertFalse(kitchen.finished)
        // The board cannot be changed in the middle of a shift.
        kitchen.load(Blueprint())
        kitchen.tapSlot(3)
        kitchen.tap("bookReader")
        assertEquals(boardWith(PieceKind.BOOK_READER), kitchen.board)
        assertNull(kitchen.picking)
        assertNull(kitchen.selected)

        advanceUntilIdle()

        assertFalse(kitchen.running)
        assertTrue(kitchen.finished)
        assertEquals(1100, kitchen.shift!!.kitchen.score().points)
        assertEquals(listOf(boardWith(PieceKind.BOOK_READER)), kitchen.history.map { it.board })

        // The next shift is on another board, and the shift that is shown keeps the board it ran on.
        kitchen.load(BEST_BOARD)
        kitchen.start()
        advanceUntilIdle()
        kitchen.load(Blueprint.FIRST)

        assertEquals(BEST_BOARD, kitchen.playing)
        assertEquals(listOf(78, 78), kitchen.history.map { it.shift.kitchen.minute })
        assertEquals(listOf("scripted", "scripted"), kitchen.history.map { it.model })
        assertTrue(kitchen.history.all { it.scripted })
        assertTrue(kitchen.history[1].shift.spent < kitchen.history[0].shift.spent)
    }

    @Test
    fun thePlayerDrawsTheBoardByPressingTiles() = runTest {
        val kitchen = kitchen()

        // The station that is there goes away, and its arrows with it.
        kitchen.tap("lookup")
        assertEquals("lookup", kitchen.selected)
        assertNull(kitchen.armed)
        kitchen.remove()
        assertEquals(Blueprint(), kitchen.board)
        assertNull(kitchen.selected)
        assertEquals(listOf("The rail has no arrow. Draw one to the station that gets every order first."), kitchen.problems)
        kitchen.start()
        assertFalse(kitchen.running)

        // An empty place offers the stations. The one that is put there is selected, with its arrow ready to be drawn.
        kitchen.tapSlot(2)
        assertEquals(2, kitchen.picking)
        kitchen.put(PieceKind.ONLY_MENU)
        assertNull(kitchen.picking)
        assertEquals("onlyMenu", kitchen.selected)
        assertEquals(Exit.YES, kitchen.armed)
        kitchen.tapSlot(2)
        assertNull(kitchen.picking)

        kitchen.tapSlot(3)
        kitchen.put(PieceKind.LOOKUP)
        kitchen.tap(Blueprint.KITCHEN)
        assertEquals(Blueprint.KITCHEN, kitchen.board.target("lookup", Exit.NEXT))
        assertEquals("lookup", kitchen.selected)
        assertNull(kitchen.armed)
        kitchen.tapSlot(5)
        kitchen.put(PieceKind.BOOK_READER)
        kitchen.tap(Blueprint.KITCHEN)

        // A sorter gets its yes arrow first and then its no arrow.
        kitchen.tap("onlyMenu")
        kitchen.tap("lookup")
        assertEquals(Exit.NO, kitchen.armed)
        kitchen.tap("bookReader")
        assertNull(kitchen.armed)
        kitchen.tap(Blueprint.RAIL)
        assertEquals(Exit.NEXT, kitchen.armed)
        // No arrow goes to the tile it starts at: pressing it again lets it go.
        kitchen.tap(Blueprint.RAIL)
        assertNull(kitchen.selected)
        kitchen.tap(Blueprint.RAIL)
        kitchen.tap("onlyMenu")
        assertEquals(emptyList(), kitchen.problems)
        assertEquals("plain? + lookup + book", kitchen.board.summary)

        // An arrow that is there can be drawn somewhere else.
        kitchen.tap("onlyMenu")
        kitchen.redraw(Exit.NO)
        assertEquals(Exit.NO, kitchen.armed)
        assertEquals(1, kitchen.problems.size)
        kitchen.tap(Blueprint.KITCHEN)
        assertEquals(Blueprint.KITCHEN, kitchen.board.target("onlyMenu", Exit.NO))
        kitchen.redraw(Exit.NO)
        kitchen.tap("bookReader")
        // The kitchen has no arrows, so it is never selected.
        kitchen.tap(Blueprint.KITCHEN)
        assertEquals("onlyMenu", kitchen.selected)

        kitchen.start()
        assertNull(kitchen.selected)
        advanceUntilIdle()
        assertEquals(1100, kitchen.shift!!.kitchen.score().points)
        assertEquals(listOf("onlyMenu", "lookup"), kitchen.shift!!.routes[1])
    }

    @Test
    fun theMedalsOfTheLunchRushAreWonByDifferentBoards() = runTest {
        val kitchen = kitchen()

        fun won() = kitchen.goals.map { it.second }
        assertEquals(listOf(Medal.Right, Medal.Cheap, Medal.Small), kitchen.goals.map { it.first.medal })
        assertEquals(
            listOf("Serve every order right and on time.", "Do it for fewer than 4,000 tokens.", "Do it with one station."),
            kitchen.goals.map { it.first.text },
        )
        assertEquals(1100, Rush.Lunch.points)

        assertEquals(listOf(false, false, false), won())
        assertEquals(listOf(null, null, null), Medal.entries.map(kitchen::best))
        listOf(
            // A board that gets orders wrong wins nothing, however small or cheap it is.
            Blueprint.FIRST to listOf(false, false, false),
            boardWith(PieceKind.READER) to listOf(false, false, false),
            // One reader with the book is the smallest board that serves every order, and the one that costs the most.
            boardWith(PieceKind.BOOK_READER) to listOf(true, false, true),
            // One sorter saves a third of the tokens, which is not enough.
            SORTED_BOARD to listOf(true, false, true),
            // The cheapest board has five stations.
            BEST_BOARD to listOf(true, true, true),
        ).forEach { (board, expected) ->
            kitchen.load(board)
            kitchen.start()
            advanceUntilIdle()
            assertEquals(expected, won(), board.summary)
        }
        // No board wins all three: the cheap one is not small, and the small one is not cheap.
        assertEquals(listOf(true, true, false), Rush.Lunch.goals.map { it.metBy(kitchen.history.last()) })
        assertEquals(listOf(true, false, true), Rush.Lunch.goals.map { it.metBy(kitchen.history[2]) })
        // The best of each measure comes from the shifts that served every order, and the points from all of them.
        assertEquals(listOf(1100, 3_565, 1), Medal.entries.map(kitchen::best))

        // A medal that was won stays won, whatever the next board does.
        kitchen.load(Blueprint.FIRST)
        kitchen.start()
        advanceUntilIdle()
        assertEquals(listOf(true, true, true), won())
        assertEquals(listOf(1100, 3_565, 1), Medal.entries.map(kitchen::best))
        assertEquals(6, kitchen.history.size)

        // The tokens of a real model are its own, so only the scripted chefs can win the medal for them.
        val real = PlayedShift(Rush.Lunch, BEST_BOARD, "a model", scripted = false, kitchen.history[4].shift)
        assertEquals(listOf(true, false, false), Rush.Lunch.goals.map { it.metBy(real) })
        assertEquals(5, real.stations)
    }

    @Test
    fun theDinnerRushIsAnotherShiftWithMedalsOfItsOwn() = runTest {
        val kitchen = kitchen()
        assertEquals(Rush.Lunch, kitchen.rush)
        kitchen.load(BEST_BOARD)
        kitchen.start()
        advanceUntilIdle()
        assertEquals(listOf(true, true, false), kitchen.goals.map { it.second })

        // What the lunch rush won does not count for the dinner rush.
        kitchen.choose(Rush.Dinner)
        assertEquals(listOf(false, false, false), kitchen.goals.map { it.second })
        assertEquals("Do it with 3 stations or fewer.", kitchen.goals.last().first.text)
        assertEquals(listOf(null, null, null), Medal.entries.map(kitchen::best))
        assertEquals(Rush.Lunch, kitchen.playingRush)

        kitchen.start()
        advanceTimeBy(1_000)
        // The orders cannot be changed in the middle of a shift.
        kitchen.choose(Rush.Lunch)
        assertEquals(Rush.Dinner, kitchen.rush)
        advanceUntilIdle()
        // The board that reads the lunch rush for the fewest tokens has no place left for the second station of the oven.
        assertEquals(950, kitchen.shift!!.kitchen.score().points)
        assertEquals(1200, Rush.Dinner.points)
        assertEquals(listOf(false, false, false), kitchen.goals.map { it.second })
        assertEquals(listOf(950, null, null), Medal.entries.map(kitchen::best))

        // With a sorter and both stations for the oven every order is on time, for few tokens, on five stations.
        kitchen.load(DINNER_BOARD)
        kitchen.start()
        advanceUntilIdle()
        assertEquals(1200, kitchen.shift!!.kitchen.score().points)
        assertEquals(listOf(true, true, false), kitchen.goals.map { it.second })
        assertEquals(listOf(1200, 3_564, 5), Medal.entries.map(kitchen::best))

        // The small board has one reader for everything. It serves every order too, and pays for it.
        kitchen.load(SMALL_DINNER_BOARD)
        kitchen.start()
        advanceUntilIdle()
        assertEquals(1200, kitchen.shift!!.kitchen.score().points)
        assertEquals(5_633, kitchen.shift!!.spent)
        assertEquals(listOf(true, false, true), Rush.Dinner.goals.map { it.metBy(kitchen.history.last()) })
        assertEquals(listOf(true, true, true), kitchen.goals.map { it.second })
        assertEquals(listOf(1200, 3_564, 3), Medal.entries.map(kitchen::best))
        assertEquals(listOf(Rush.Lunch, Rush.Dinner, Rush.Dinner, Rush.Dinner), kitchen.history.map { it.rush })

        kitchen.choose(Rush.Lunch)
        assertEquals(listOf(true, true, false), kitchen.goals.map { it.second })
    }

    @Test
    fun theShiftsComeInAMenuAndEachShortOneBringsAStation() = runTest {
        val saved = mutableListOf<Set<Pair<Rush, Medal>>>()
        val earned = mutableStateOf(emptySet<Pair<Rush, Medal>>())
        val kitchen = KitchenController(null, "scripted", this, minuteMillis = 0, earned = earned, onEarned = { saved += it })

        // A player starts in the first shift, with its station on the board and one arrow to draw.
        assertEquals(Rush.Opening, kitchen.rush)
        assertEquals(Guide.FIRST_ORDERS.start, kitchen.board)
        assertEquals(listOf("Menu lookup has no arrow. An order that gets there is stuck."), kitchen.problems)
        assertEquals(setOf(PieceKind.LOOKUP), kitchen.rush.offers)
        assertEquals(
            listOf(
                PieceKind.LOOKUP,
                PieceKind.READER,
                PieceKind.ONLY_MENU,
                PieceKind.BOOK_READER,
                PieceKind.LOOKS_BACK,
                PieceKind.EXPEDITER,
                PieceKind.SECOND_OVEN,
                null,
                null,
            ),
            Rush.entries.map { it.teaches },
        )
        // Every shift offers the stations of the ones before it, and the rushes offer all of them.
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 7, 7), Rush.entries.map { it.offers.size })
        assertEquals((1..9).toList(), Rush.entries.map { it.number })
        assertEquals(Rush.Wishes, Rush.Opening.next)
        assertNull(Rush.Dinner.next)
        // A short shift has a medal for tokens only where its station saves some.
        assertEquals(listOf(1, 1, 2, 1, 2, 1, 1, 3, 3), Rush.entries.map { it.goals.size })
        assertEquals("Do it for fewer than 2,800 tokens.", Rush.LookingBack.goals.last().text)
        assertEquals(listOf(400, 400, 600, 500, 800, 400, 600, 1100, 1200), Rush.entries.map { it.points })

        // A station of a later shift is not offered, and a board that has one does not start.
        kitchen.tapSlot(0)
        kitchen.put(PieceKind.READER)
        assertEquals(Guide.FIRST_ORDERS.start, kitchen.board)
        kitchen.load(boardWith(PieceKind.READER))
        assertEquals(listOf("Reader comes in a later shift. Take it off the board, or choose that shift."), kitchen.problems)
        kitchen.start()
        assertFalse(kitchen.running)
        // A rush has every station and keeps the board. The first shift opens with its own again.
        kitchen.choose(Rush.Lunch)
        assertEquals(emptyList(), kitchen.problems)
        kitchen.choose(Rush.Opening)
        assertEquals(Guide.FIRST_ORDERS.start, kitchen.board)
        assertEquals(1, kitchen.problems.size)

        // Each shift is won on the board of the one before with its new station added.
        val plainToLookup =
            Blueprint()
                .place(0, PieceKind.ONLY_MENU)
                .place(1, PieceKind.LOOKUP)
                .place(3, PieceKind.READER)
                .connect(Blueprint.RAIL, Exit.NEXT, "onlyMenu")
                .connect("onlyMenu", Exit.YES, "lookup")
                .connect("onlyMenu", Exit.NO, "reader")
                .connect("lookup", Exit.NEXT, Blueprint.KITCHEN)
                .connect("reader", Exit.NEXT, Blueprint.KITCHEN)
        listOf(
            Rush.Opening to Blueprint.FIRST,
            Rush.Wishes to boardWith(PieceKind.READER),
            Rush.Sorting to plainToLookup,
            Rush.Book to boardWith(PieceKind.BOOK_READER),
            Rush.LookingBack to BEST_BOARD,
            Rush.Urgent to Blueprint.FIRST.behindTheRail(PieceKind.EXPEDITER),
            Rush.TwoOvens to Blueprint.FIRST.behindTheRail(PieceKind.SECOND_OVEN),
        ).forEach { (rush, board) ->
            kitchen.choose(rush)
            assertTrue(kitchen.goals.none { it.second }, rush.title)
            kitchen.load(board)
            assertEquals(emptyList(), kitchen.problems, rush.title)
            kitchen.start()
            advanceUntilIdle()
            assertEquals(rush.points, kitchen.shift!!.kitchen.score().points, rush.title)
            assertTrue(kitchen.goals.all { it.second }, rush.title)
            // A short shift is over in less than a minute of the kitchen's clock for each order and a half.
            assertTrue(kitchen.shift!!.kitchen.minute <= 47, rush.title)
        }
        assertEquals(7, kitchen.history.size)
        assertEquals(9, kitchen.earned.size)
        assertEquals(kitchen.earned, saved.last())
        assertEquals(7, saved.size)
        assertEquals(listOf(true, true), kitchen.medals(Rush.Sorting).map { it.second })
        assertEquals(listOf(false, false, false), kitchen.medals(Rush.Lunch).map { it.second })

        // The medals are kept outside the controller, and a shift that wins nothing new is not saved again.
        val again = KitchenController(null, "scripted", this, minuteMillis = 0, earned = earned, onEarned = { saved += it }, chosen = mutableStateOf(Rush.Opening), drawn = mutableStateOf(Blueprint.FIRST))
        assertEquals(listOf(true), again.goals.map { it.second })
        again.start()
        advanceUntilIdle()
        assertEquals(7, saved.size)
        again.forget()
        assertEquals(listOf(false), again.goals.map { it.second })
    }

    @Test
    fun aLinkCarriesAShiftAndABoardForIt() = runTest {
        val link = KitchenLink(Rush.Dinner, DINNER_BOARD)
        assertEquals("kitchen=dinner.plxbo-413k0k2", link.fragment)
        assertEquals("https://deeptelar.github.io/telar-demo/#kitchen=dinner.plxbo-413k0k2", link.address())
        assertEquals("http://localhost:8080/#kitchen=lunch.l-----0k", KitchenLink(Rush.Lunch, Blueprint.FIRST).address("http://localhost:8080/"))

        // Whoever opens the link finds the shift chosen and the board drawn, and it plays as it did for whoever sent it.
        val opened = assertNotNull(KitchenLink.of("#" + link.fragment))
        assertEquals(opened, KitchenLink.of(link.fragment))
        assertEquals(Rush.Dinner to DINNER_BOARD.text(), opened.rush to opened.board.text())
        val kitchen = KitchenController(null, "scripted", this, minuteMillis = 0, drawn = mutableStateOf(opened.board), chosen = mutableStateOf(opened.rush))
        assertEquals(emptyList(), kitchen.problems)
        kitchen.start()
        advanceUntilIdle()
        assertEquals(1200, kitchen.shift!!.kitchen.score().points)
        assertEquals(
            "Pixel Pizza, dinner rush: 1200 of 1200 points for 3,564 tokens on 5 stations. Can you beat this board? " +
                "https://deeptelar.github.io/telar-demo/#kitchen=dinner.plxbo-413k0k2",
            kitchen.history.single().told(),
        )
        val real = kitchen.history.single().copy(board = boardWith(PieceKind.BOOK_READER), model = "a model", scripted = false)
        assertEquals(
            "Pixel Pizza, dinner rush: 1200 of 1200 points for 3,564 tokens of a model on 1 station. Can you beat this board? x#kitchen=dinner.b-----0k",
            real.told("x"),
        )

        // An address that is not a link to a board opens the game as always.
        listOf(null, "", "#", "stage=3", "kitchen", "kitchen=", "kitchen=lunch", "kitchen=brunch.l-----0k", "kitchen=lunch.nonsense").forEach { fragment ->
            assertNull(KitchenLink.of(fragment), fragment)
        }
    }

    @Test
    fun anOrderIsShownAtTheStationThatHasIt() = runTest {
        val kitchen = kitchen(minuteMillis = 0, stepMillis = 100)
        kitchen.load(SORTED_BOARD)

        kitchen.start()
        advanceTimeBy(50)
        assertEquals("onlyMenu" to 1, kitchen.at to kitchen.reading)
        advanceTimeBy(100)
        assertEquals("lookup" to 1, kitchen.at to kitchen.reading)
        advanceTimeBy(200)
        assertEquals("bookReader" to 2, kitchen.at to kitchen.reading)

        advanceUntilIdle()
        assertNull(kitchen.at)
        assertNull(kitchen.reading)
        assertTrue(kitchen.finished)
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
        val kitchen = kitchen(ChatModel { throw ChatModelException("The key is not valid.") }, minuteMillis = 0, label = "broken")
        assertFalse(kitchen.scripted)
        kitchen.load(boardWith(PieceKind.READER))

        kitchen.start()
        advanceUntilIdle()

        assertFalse(kitchen.running)
        assertFalse(kitchen.finished)
        assertEquals("The key is not valid.", kitchen.error)
        assertEquals(emptyList(), kitchen.history)

        // A board without a model does not ask it, and the next shift starts clean.
        kitchen.load(Blueprint.FIRST)
        kitchen.start()
        assertNull(kitchen.error)
        advanceUntilIdle()
        assertTrue(kitchen.finished)
        assertFalse(kitchen.history.single().scripted)
    }

    @Test
    fun theTokenBudgetEndsAShiftAndStaysWithinItsLimits() = runTest {
        val kitchen = kitchen()
        kitchen.load(boardWith(PieceKind.BOOK_READER))

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
    fun theShiftsAndTheBoardOutliveTheController() = runTest {
        val played = mutableStateOf(emptyList<PlayedShift>())
        val drawn = mutableStateOf(Blueprint.FIRST)
        val chosen = mutableStateOf(Rush.Lunch)
        val first = KitchenController(null, "scripted", this, minuteMillis = 0, played = played, drawn = drawn, chosen = chosen)

        first.load(SORTED_BOARD)
        first.choose(Rush.Dinner)
        first.start()
        advanceUntilIdle()
        // The player chose another model under Options, which makes a new controller.
        val second = KitchenController(null, "another", this, minuteMillis = 0, played = played, drawn = drawn, chosen = chosen)
        assertEquals(SORTED_BOARD, second.board)
        assertEquals(Rush.Dinner, second.rush)
        second.start()
        advanceUntilIdle()

        assertEquals(listOf("scripted", "another"), second.history.map { it.model })
    }

    @Test
    fun aPizzaKeepsItsPlaceInTheOvenAndIsBakedAfterSixMinutes() {
        assertEquals(listOf("P1", null), ovenPlaces(listOf(null, null), listOf("P1")))
        assertEquals(listOf("P1", "P2"), ovenPlaces(listOf("P1", null), listOf("P1", "P2")))
        // P1 is taken out. P2 stays where it is, and the next pizza gets the place that is free.
        assertEquals(listOf(null, "P2"), ovenPlaces(listOf("P1", "P2"), listOf("P2")))
        assertEquals(listOf("P3", "P2"), ovenPlaces(listOf(null, "P2"), listOf("P2", "P3")))
        assertEquals(listOf("P3", "P2"), ovenPlaces(listOf("P3", "P2"), listOf("P2", "P3", "P4")))

        assertEquals(listOf(Bake.BAKING, Bake.BAKING, Bake.BAKED, Bake.BAKED, Bake.BURNT), listOf(0, 5, 6, 12, 13).map(::bake))
        assertEquals(listOf("5 of 6 minutes", "baked, take it out", "burnt"), listOf(5, 6, 13).map(::bakeText))
    }
}
