package dev.deeptelar.telar.demo

import dev.deeptelar.telar.END
import dev.deeptelar.telar.GraphConfig
import dev.deeptelar.telar.GraphEdge
import dev.deeptelar.telar.START
import dev.deeptelar.telar.agent.ChatMessage
import dev.deeptelar.telar.agent.ChatModel
import dev.deeptelar.telar.agent.ChatRequest
import dev.deeptelar.telar.agent.ChatResponse
import dev.deeptelar.telar.demo.kitchen.BOARD_STEPS
import dev.deeptelar.telar.demo.kitchen.Blueprint
import dev.deeptelar.telar.demo.kitchen.Blueprint.Companion.KITCHEN
import dev.deeptelar.telar.demo.kitchen.Blueprint.Companion.RAIL
import dev.deeptelar.telar.demo.kitchen.BoardShift
import dev.deeptelar.telar.demo.kitchen.Exit
import dev.deeptelar.telar.demo.kitchen.MENU
import dev.deeptelar.telar.demo.kitchen.Outcome
import dev.deeptelar.telar.demo.kitchen.PieceKind
import dev.deeptelar.telar.demo.kitchen.READER_ALONE
import dev.deeptelar.telar.demo.kitchen.READER_WITH_BOOK
import dev.deeptelar.telar.demo.kitchen.Slip
import dev.deeptelar.telar.demo.kitchen.Topping
import dev.deeptelar.telar.demo.kitchen.dinnerRush
import dev.deeptelar.telar.demo.kitchen.graph
import dev.deeptelar.telar.demo.kitchen.kitchen
import dev.deeptelar.telar.demo.kitchen.lunchRush
import dev.deeptelar.telar.demo.kitchen.report
import dev.deeptelar.telar.demo.kitchen.scriptedStations
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The kitchen that the player draws: a board of stations and arrows, and the graph it becomes. */
class KitchenBoardTest {
    private val config = GraphConfig<BoardShift>(maxIterations = BOARD_STEPS)

    private suspend fun play(board: Blueprint, models: (PieceKind) -> ChatModel = scriptedStations(), budget: Int = 300_000): BoardShift =
        board.kitchen(models).invoke(BoardShift(lunchRush(), budget), config).state

    /** The scripted stations, with every request they get added to [requests]. */
    private fun recording(requests: MutableList<ChatRequest>): (PieceKind) -> ChatModel {
        val scripted = scriptedStations()
        return { kind ->
            ChatModel { request ->
                requests += request
                scripted(kind).chat(request)
            }
        }
    }

    @Test
    fun theBoardAPlayerStartsWithCostsNothingAndGetsMostOrdersWrong() = runTest {
        val end = play(Blueprint.FIRST)

        assertEquals(
            listOf(
                "orders   5 of 11 on time, 4 wrong, 1 with an allergen, 1 not served",
                "points   400 of 1100",
                "tokens   0 (0 read, 0 written) in 0 answers",
                "stations prep 0 tokens in 0 answers, oven 0 tokens in 0 answers",
                "kitchen  closed at minute 95, 5 pizzas wasted",
            ),
            end.report().lines(),
        )
        // The lookup reads "pepperoni" and nothing of the extra cheese.
        assertEquals(MENU.getValue("pepperoni"), end.tickets[2])
        assertEquals("Ticket #2: tomato, mozzarella, pepperoni. It read \"pepperoni\".", end.log.first { "#2" in it.text }.text)
        // "The same as table 2 had" names no pizza.
        assertNull(end.tickets[9])
        assertTrue(end.log.any { it.refused && it.station == "lookup" && it.text == "No ticket for #9: no pizza of the menu is named in it." })
        assertEquals(listOf("lookup"), end.routes[9])
    }

    @Test
    fun aReaderThatIsToldOneOrderCannotKnowWhatWasSaidBefore() = runTest {
        val requests = mutableListOf<ChatRequest>()

        val end = play(boardWith(PieceKind.READER), recording(requests))

        assertEquals(800, end.kitchen.score().points)
        // It reads what an order says itself: the extra, the swap, and what table 5 said afterwards.
        assertEquals(listOf(Topping.TOMATO, Topping.MOZZARELLA, Topping.MOZZARELLA, Topping.PEPPERONI), end.tickets[2])
        assertEquals(listOf(Topping.MOZZARELLA, Topping.PEPPERONI, Topping.MUSHROOMS), end.tickets[5])
        // It never heard what table 2 had, or that table 3 is allergic to onions.
        assertNull(end.tickets[9])
        assertTrue(end.log.any { it.refused && it.text == "No ticket for #9: The order does not say which pizza is meant." })
        assertTrue(Topping.ONIONS in end.tickets.getValue(10))
        assertEquals(1, end.kitchen.score().allergy)

        assertEquals(13, requests.size)
        assertTrue(requests.all { it.messages.size == 1 && it.system == READER_ALONE && it.tools.single().name == "write_ticket" })
        assertEquals("Order #1, table 1: One margherita.", requests.first().messages.single().text)
        assertEquals(
            "Order #5, table 5: A pepperoni without tomato. Then: Table 5 also wants mushrooms on it.",
            requests.first { "Then:" in it.messages.single().text }.messages.single().text,
        )
    }

    @Test
    fun theReaderWithTheBookGetsEveryOrderRightAndPaysForTheBookEveryTime() = runTest {
        val requests = mutableListOf<ChatRequest>()

        val end = play(boardWith(PieceKind.BOOK_READER), recording(requests))
        val alone = play(boardWith(PieceKind.READER))

        assertEquals(1100, end.kitchen.score().points)
        assertEquals(78, end.kitchen.minute)
        assertEquals(MENU.getValue("pepperoni"), end.tickets[9])
        assertFalse(Topping.ONIONS in end.tickets.getValue(10))
        assertEquals(13, end.stations.getValue("bookReader").answers)
        assertTrue(end.spent > alone.spent + 1_000, "${end.spent} and ${alone.spent}")

        assertEquals("Order #1, table 1: One margherita.", requests.first().messages.single().text)
        assertEquals(
            """
            Said earlier in this shift:
            #1, table 1: One margherita.
            #2, table 2: A pepperoni with extra cheese.

            Now read:
            Order #3, table 3: A funghi. A note for the whole shift: nobody at table 3 may get onions, they are allergic.
            """.trimIndent(),
            requests[2].messages.single().text,
        )
        assertTrue(requests.all { it.system == READER_WITH_BOOK })
        // The book keeps what a guest said afterwards as a line of its own.
        assertTrue("#5, table 5: Table 5 also wants mushrooms on it." in requests.last().messages.single().text)
    }

    @Test
    fun sortersSendEveryOrderToTheCheapestStationThatCanReadIt() = runTest {
        val book = play(boardWith(PieceKind.BOOK_READER))
        val sorted = play(SORTED_BOARD)
        val best = play(BEST_BOARD)

        assertEquals(listOf(1100, 1100), listOf(sorted, best).map { it.kitchen.score().points })
        assertTrue(best.spent < 4_000 && sorted.spent in 4_000..4_999 && book.spent > 5_000, "${best.spent}, ${sorted.spent} and ${book.spent}")

        assertEquals(listOf("onlyMenu", "lookup"), best.routes[8])
        assertEquals(listOf("onlyMenu", "looksBack", "reader"), best.routes[7])
        assertEquals(listOf("onlyMenu", "looksBack", "bookReader"), best.routes[9])
        // The order of table 5 went to the reader, and what the table said afterwards to the one with the book.
        assertEquals(listOf("onlyMenu", "looksBack", "bookReader"), best.routes[5])
        assertEquals(5, best.stations.getValue("reader").answers)
        assertEquals(4, best.stations.getValue("bookReader").answers)
        assertEquals(setOf("reader", "bookReader", "prep", "oven"), best.desks.keys)
    }

    @Test
    fun everyOrderIsOneRunOfTheGraphOfTheBoard() = runTest {
        val graph = BEST_BOARD.graph(scriptedStations())

        assertEquals(listOf("onlyMenu", "lookup", "looksBack", "bookReader", "reader"), graph.topology.nodes)
        assertTrue(GraphEdge(START, "onlyMenu") in graph.topology.edges)
        assertTrue(GraphEdge("onlyMenu", "lookup", isConditional = true) in graph.topology.edges)
        assertTrue(GraphEdge("looksBack", "reader", isConditional = true) in graph.topology.edges)
        assertTrue(GraphEdge("reader", END) in graph.topology.edges)

        val plain = graph.invoke(Slip(8, table = 8, words = "A pepperoni.")).state
        assertEquals(listOf("onlyMenu", "lookup"), plain.path)
        assertEquals(MENU.getValue("pepperoni"), plain.ticket)
        assertEquals(0, plain.minute)

        val read = graph.invoke(Slip(4, table = 4, words = "A veggie with extra peppers instead of the onions.", minute = 14)).state
        assertEquals(listOf("onlyMenu", "looksBack", "reader"), read.path)
        // The answer of a model takes a minute.
        assertEquals(15, read.minute)
        assertEquals(1, read.desks.getValue("reader").answers)
    }

    @Test
    fun theCodeOfABoardIsTheGraphItBuilds() {
        assertEquals(
            """
            StateGraph<Slip> {
                val onlyMenu = node("onlyMenu") { it }
                val lookup = node("lookup") { slip -> slip.lookUp() }
                val looksBack = node("looksBack") { it }
                val bookReader = node("bookReader") { slip -> slip.read(model, book = true) }
                val reader = node("reader") { slip -> slip.read(model) }

                START then onlyMenu
                conditionalEdge(onlyMenu, targets = setOf(lookup, looksBack)) { slip ->
                    if (slip.isOnlyAMenuPizza) lookup else looksBack
                }
                lookup then END
                conditionalEdge(looksBack, targets = setOf(bookReader, reader)) { slip ->
                    if (slip.isAboutEarlier) bookReader else reader
                }
                bookReader then END
                reader then END
            }.compile()
            """.trimIndent(),
            BEST_BOARD.code(),
        )
        assertEquals("StateGraph<Slip> {\n}.compile()", Blueprint().code())
        assertEquals("plain? + lookup + earlier? + book + reader", BEST_BOARD.summary)
    }

    @Test
    fun aModelReadsOneOrderAtATimeAndTakesAMinuteForEach() = runTest {
        val end = play(boardWith(PieceKind.BOOK_READER))

        // Four orders come in at minute 40, and one reader reads them one after the other.
        val written = end.log.filter { it.station == "book" && it.minute in 40..45 }
        assertEquals(listOf(41 to "#7", 42 to "#8", 43 to "#9", 44 to "#10"), written.map { it.minute to it.text.substringAfter("Ticket ").substringBefore(":") })

        // On the best board three stations share them, and the lookup takes no time at all.
        val shared = play(BEST_BOARD).log.filter { "Ticket" in it.text && it.minute in 40..45 }
        assertEquals(setOf("lookup" to 40, "reader" to 41, "book" to 41, "book" to 42), shared.map { it.station to it.minute }.toSet())
    }

    @Test
    fun whatAModelSaysInsteadOfATicketIsInTheLog() = runTest {
        val talker = ChatModel { ChatResponse(ChatMessage.Assistant("I would rather not."), null) }

        val end = play(boardWith(PieceKind.READER), models = { talker })

        assertEquals(emptyMap(), end.tickets)
        assertEquals(0, end.kitchen.score().points)
        assertTrue(end.log.all { it.refused && it.station == "reader" })
        assertEquals("No ticket for #1: I would rather not.", end.log.first().text)
        assertEquals(13, end.stations.getValue("reader").answers)
    }

    @Test
    fun aShiftOnABoardStopsWhenItsTokensAreSpent() = runTest {
        val end = play(boardWith(PieceKind.BOOK_READER), budget = 1_500)

        assertTrue(end.outOfTokens)
        assertTrue(end.stations.getValue("bookReader").answers < 13)
        assertTrue("out of tokens at minute" in end.report())
    }

    @Test
    fun aBoardThatCannotRunSaysWhy() {
        assertEquals(emptyList(), Blueprint.FIRST.problems())
        assertEquals(emptyList(), BEST_BOARD.problems())
        // An order may go straight to the kitchen. It gets no ticket there.
        assertEquals(emptyList(), Blueprint().connect(RAIL, Exit.NEXT, KITCHEN).problems())

        assertEquals(listOf("The rail has no arrow. Draw one to the station that gets every order first."), Blueprint().problems())
        assertEquals(
            listOf("Sorter: only a menu pizza? needs an arrow for yes and an arrow for no.", "Menu lookup has no arrow. An order that gets there is stuck."),
            Blueprint()
                .place(0, PieceKind.ONLY_MENU)
                .place(1, PieceKind.LOOKUP)
                .connect(RAIL, Exit.NEXT, "onlyMenu")
                .connect("onlyMenu", Exit.YES, "lookup")
                .problems(),
        )
        assertEquals(
            listOf("The arrows run in a circle at Sorter: only a menu pizza?. An order would never reach the kitchen."),
            SORTED_BOARD.connect("lookup", Exit.NEXT, "onlyMenu").problems(),
        )
        // What the arrows of the game do not rule out is left to Telar, which checks the graph when it is compiled.
        assertEquals(
            listOf("Telar: Node 'reader' is not reachable from START"),
            Blueprint.FIRST.place(3, PieceKind.READER).connect("reader", Exit.NEXT, KITCHEN).problems(),
        )
    }

    @Test
    fun aBoardIsChangedByPlacingRemovingAndConnecting() {
        val two = Blueprint().place(0, PieceKind.READER).place(1, PieceKind.READER)
        assertEquals(listOf("reader", "reader2"), two.pieces.map { it.id })
        // A place that is taken, or that is not on the board, stays as it is.
        assertEquals(two, two.place(1, PieceKind.LOOKUP).place(Blueprint.SLOTS, PieceKind.LOOKUP))

        val drawn = two.connect(RAIL, Exit.NEXT, "reader").connect("reader", Exit.NEXT, "reader2").connect("reader2", Exit.NEXT, KITCHEN)
        assertEquals("reader2", drawn.target("reader", Exit.NEXT))
        // A new arrow from the same way out replaces the old one.
        assertEquals(KITCHEN, drawn.connect("reader", Exit.NEXT, KITCHEN).target("reader", Exit.NEXT))
        assertEquals(3, drawn.connect("reader", Exit.NEXT, KITCHEN).arrows.size)
        // No arrow goes to the rail, to its own station, to a station that is not there, or from a way out the station does not have.
        listOf(
            drawn.connect("reader", Exit.NEXT, RAIL),
            drawn.connect("reader", Exit.NEXT, "reader"),
            drawn.connect("reader", Exit.NEXT, "lookup"),
            drawn.connect("reader", Exit.YES, KITCHEN),
            drawn.connect(KITCHEN, Exit.NEXT, "reader"),
        ).forEach { assertEquals(drawn, it) }

        val without = drawn.remove("reader2")
        assertEquals(listOf("reader"), without.pieces.map { it.id })
        assertEquals(listOf(RAIL), without.arrows.map { it.from })
        assertEquals(Exit.NEXT, without.freeExit("reader"))
        assertNull(without.freeExit(RAIL))
        assertEquals(listOf(Exit.YES, Exit.NO), SORTED_BOARD.exits("onlyMenu"))
        assertEquals(emptyList(), SORTED_BOARD.exits(KITCHEN))
        assertEquals("The kitchen", SORTED_BOARD.title(KITCHEN))
    }

    @Test
    fun theSortersReadTheWordsOfAnOrder() {
        val orders = lunchRush().orders.associate { it.number to it.text }

        assertEquals(setOf(1, 6, 8, 11), orders.filterValues(PieceKind.ONLY_MENU::says).keys)
        assertEquals(setOf(3, 9, 10), orders.filterValues(PieceKind.LOOKS_BACK::says).keys)
        assertTrue(PieceKind.ONLY_MENU.says(" veggie "))
        assertFalse(PieceKind.ONLY_MENU.says("A veggie, please."))
        assertFalse(PieceKind.READER.says("A veggie."))
    }

    private suspend fun dinner(board: Blueprint): BoardShift =
        board.kitchen(scriptedStations(dinnerRush())).invoke(BoardShift(dinnerRush()), config).state

    private fun BoardShift.late(): List<Int> = kitchen.standing.filter { it.outcome == Outcome.LATE }.map { it.number }

    @Test
    fun oneOvenThatTakesTheTicketsAsTheyComeIsTooSlowForTheDinnerRush() = runTest {
        val end = dinner(SORTED_BOARD)

        // Every ticket is right. The pizzas are late.
        assertEquals(950, end.kitchen.score().points)
        assertEquals(listOf(4, 5, 8, 11, 12), end.late())
        assertEquals(0, end.kitchen.score().wrong + end.kitchen.score().allergy + end.kitchen.score().missed)
        // Without an expediter a ticket has its turn when it comes: the lookup is done first, so the veggie of table 1 is made before the pizzas that are due sooner.
        assertEquals("P1 is on the counter: tomato, mozzarella, mushrooms, peppers, onions.", end.log.first { it.station == "prep" }.text)
        assertEquals(setOf("bookReader", "prep", "oven"), end.desks.keys)
    }

    @Test
    fun theExpediterAndTheSecondOvenEachHelpAndTheDinnerRushNeedsBoth() = runTest {
        val rushed = dinner(SORTED_BOARD.behindTheRail(PieceKind.EXPEDITER))
        val twoOvens = dinner(SORTED_BOARD.behindTheRail(PieceKind.SECOND_OVEN))
        val both = dinner(DINNER_BOARD)

        assertEquals(listOf(1000, 1100, 1200), listOf(rushed, twoOvens, both).map { it.kitchen.score().points })
        assertEquals(emptyList(), both.late())
        assertEquals(54, both.kitchen.minute)
        // The stations for the oven cost no tokens: the three boards read the orders the same way.
        assertEquals(setOf(3_564), setOf(rushed.spent, twoOvens.spent, both.spent))

        // With the due time on the tickets the kitchen starts with the pizza that is due first.
        assertEquals("P1 is on the counter: tomato, mozzarella, basil.", rushed.log.first { it.station == "prep" }.text)
        // The second oven has a chef of its own, who works in the same minutes as the first.
        val baked = both.log.filter { "is in the oven" in it.text }
        assertEquals(setOf("oven", "oven 2"), baked.map { it.station }.toSet())
        assertTrue(both.kitchen.pizzas.count { it.oven == 1 } >= 4)
        assertTrue(rushed.kitchen.pizzas.all { it.oven == 0 })
        assertEquals(setOf("bookReader", "prep", "oven", "oven 2"), both.desks.keys)
        assertEquals(listOf("secondOven", "expediter", "onlyMenu", "lookup"), both.routes[2])
    }

    @Test
    fun aReaderThatReadsEveryOrderAloneIsTooSlowForSixOrdersAtOnce() = runTest {
        val end = dinner(boardWith(PieceKind.BOOK_READER).behindTheRail(PieceKind.EXPEDITER, PieceKind.SECOND_OVEN))

        // Six orders come in at minute 30, and the last ticket is written at minute 36.
        assertEquals(1100, end.kitchen.score().points)
        assertEquals(listOf(11, 12), end.late())
    }

    @Test
    fun onlyThePizzasOfOrdersThatPassedTheSecondOvenAreBakedInIt() = runTest {
        // The orders that are only a menu pizza pass the second oven, and the others do not.
        val board =
            SORTED_BOARD
                .place(2, PieceKind.SECOND_OVEN)
                .connect("lookup", Exit.NEXT, "secondOven")
                .connect("secondOven", Exit.NEXT, KITCHEN)

        val end = dinner(board)

        // Table 1 ordered a veggie and nothing else. What it said afterwards went the other way, and the order keeps its stamp.
        assertEquals(setOf(1, 2, 4, 9, 10), end.secondOven)
        val inSecond = end.log.filter { it.station == "oven 2" && "was served" in it.text }.map { it.text.substringAfter("order #").substringBefore(".").toInt() }
        assertTrue(inSecond.isNotEmpty() && inSecond.all { it in end.secondOven }, "$inSecond")
    }

    @Test
    fun aBoardHasOneSecondOvenAndItsCodeShowsTheStationsForTheOven() {
        val board = Blueprint.FIRST.behindTheRail(PieceKind.EXPEDITER, PieceKind.SECOND_OVEN)

        assertEquals(board, board.place(5, PieceKind.SECOND_OVEN))
        assertEquals(listOf("expediter", "expediter2"), Blueprint().place(0, PieceKind.EXPEDITER).place(1, PieceKind.EXPEDITER).pieces.map { it.id })
        assertTrue(board.hasSecondOven)
        assertFalse(Blueprint.FIRST.hasSecondOven)
        assertEquals(
            """
            StateGraph<Slip> {
                val secondOven = node("secondOven") { slip -> slip.copy(secondOven = true) }
                val expediter = node("expediter") { slip -> slip.copy(rush = true) }
                val lookup = node("lookup") { slip -> slip.lookUp() }

                START then secondOven
                secondOven then expediter
                expediter then lookup
                lookup then END
            }.compile()
            """.trimIndent(),
            board.code(),
        )
        assertEquals("oven 2 + rush + lookup", board.summary)
    }
}
