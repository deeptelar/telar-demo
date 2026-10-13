package dev.deeptelar.telar.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.deeptelar.telar.MemoryCheckpointer
import dev.deeptelar.telar.demo.game.scriptedModel
import dev.deeptelar.telar.demo.storage.MemoryStore
import dev.deeptelar.telar.demo.ui.Game
import dev.deeptelar.telar.demo.ui.GameScreens
import dev.deeptelar.telar.demo.ui.LocalCopyText
import dev.deeptelar.telar.demo.ui.ModelMode
import dev.deeptelar.telar.demo.ui.Phase
import dev.deeptelar.telar.demo.ui.PizzaTheme
import dev.deeptelar.telar.demo.ui.Rush
import dev.deeptelar.telar.demo.ui.Screen
import dev.deeptelar.telar.demo.ui.Settings
import dev.deeptelar.telar.demo.ui.TELAR_LINK
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Clicks through the screens the way a player does, so the buttons are known to be wired up. */
@OptIn(ExperimentalTestApi::class)
class PlayThroughTest {
    @Test
    fun aPlayerStartsTheGameAndPlaysASavePointToTheEnd() = runComposeUiTest {
        lateinit var game: Game
        setContent {
            val scope = rememberCoroutineScope()
            game = remember { Game(scriptedModel(), MemoryStore(), MemoryCheckpointer(), scope, workMillis = 0) }
            var screen by remember { mutableStateOf<Screen>(Screen.Title) }
            PizzaTheme(dark = true) { GameScreens(game, Settings(), screen, onNavigate = { screen = it }, onSave = {}) }
        }

        onNodeWithText("PRESS START").performClick()
        onNodeWithContentDescription("Save points").performClick()
        onNodeWithText("RUN").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { game.controller(5).phase == Phase.SavePoint }

        onNodeWithText("Refund 12 euros to Ben?").assertIsDisplayed()
        onNodeWithText("APPROVE").performClick()
        waitUntil(timeoutMillis = 5_000) { game.controller(5).phase == Phase.Clear }

        onNodeWithContentDescription("Stage clear").assertIsDisplayed()
        assertEquals(setOf(5), game.cleared)

        onNodeWithText("NEXT STAGE").performClick()
        onNodeWithContentDescription("The agent").assertIsDisplayed()
    }

    @Test
    fun aPlayerDrawsABetterBoardForTheKitchenRush() = runComposeUiTest {
        lateinit var game: Game
        val copied = mutableListOf<String>()
        setContent {
            val scope = rememberCoroutineScope()
            game = remember { Game(scriptedModel(), MemoryStore(), MemoryCheckpointer(), scope, workMillis = 0) }
            var screen by remember { mutableStateOf<Screen>(Screen.Title) }
            CompositionLocalProvider(LocalCopyText provides { copied += it }) {
                PizzaTheme(dark = true) { GameScreens(game, Settings(), screen, onNavigate = { screen = it }, onSave = {}) }
            }
        }

        // A player starts in the first shift, with its station on the board and one arrow to draw.
        onNodeWithText("BONUS: KITCHEN RUSH").performClick()
        onNodeWithContentDescription("Kitchen rush").assertIsDisplayed()
        // The stage opens with the list of its shifts. Pressing one goes into it, and the list is put away.
        onNodeWithContentDescription("Shift 9: Dinner rush, 0 of 3 medals").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("0 of 15 medals").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Shift 1: First orders, 0 of 1 medals, chosen").performScrollTo().performClick()
        assertTrue(game.kitchen.inShift)
        onNodeWithContentDescription("Shift 9: Dinner rush, 0 of 3 medals").assertDoesNotExist()
        onNodeWithText("KITCHEN RUSH / SHIFT 1 OF 9").assertIsDisplayed()
        onNodeWithContentDescription("First orders").assertIsDisplayed()
        onNodeWithText("NEW STATION: MENU LOOKUP").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Board: rail to lookup").performScrollTo().assertIsDisplayed()
        // The shift says what to press, one step at a time, in place of what is wrong with the board.
        onNodeWithText("STEP 1 OF 3").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Step: Press the lookup tile.").assertIsDisplayed()
        onNodeWithText("Menu lookup has no arrow. An order that gets there is stuck.").assertDoesNotExist()
        onNodeWithText("SHOW ME THE NEXT STEP").assertDoesNotExist()

        // A short shift is about its board: its orders and its medals are not listed before it.
        onNodeWithText("ORDERS / FIRST ORDERS").assertDoesNotExist()
        onNodeWithText("MEDALS / FIRST ORDERS").assertDoesNotExist()

        onNodeWithContentDescription("Station lookup").performScrollTo().performClick()
        onNodeWithText("STEP 2 OF 3").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Step: Press the kitchen. That draws the arrow from the lookup tile to it.").assertIsDisplayed()
        onNodeWithText("press the tile it goes to").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("The kitchen").performScrollTo().performClick()
        onNodeWithContentDescription("Board: rail to lookup, lookup to kitchen").performScrollTo().assertIsDisplayed()
        onNodeWithText("STEP 3 OF 3").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Step: The board is ready. Start the shift.").assertIsDisplayed()

        // The code is of the whole graph, and of a tile alone when one is selected: with the function its node calls.
        onNodeWithText("SHOW THE CODE").performScrollTo().performClick()
        onNodeWithText("CODE OF THE BOARD").performScrollTo().assertIsDisplayed()
        // The code scrolls sideways in its box, so the page is moved to the line under it.
        onNodeWithText("This is the whole graph. Press a tile to see the code behind it.").performScrollTo().assertIsDisplayed()
        onNodeWithText("StateGraph<Slip> {", substring = true).assertIsDisplayed()
        onNodeWithContentDescription("Station lookup").performScrollTo().performClick()
        onNodeWithText("CODE OF THE LOOKUP TILE").performScrollTo().assertIsDisplayed()
        onNodeWithText("ITS ARROWS").performScrollTo().assertIsDisplayed()
        onNodeWithText("val lookup = node(\"lookup\") { slip -> slip.lookUp() }").assertIsDisplayed()
        onNodeWithText("IT USES").performScrollTo().assertIsDisplayed()
        onNodeWithText("START then lookup\nlookup then END").assertIsDisplayed()
        onNodeWithText("private fun Slip.lookUp(): Slip {", substring = true).assertExists()
        onNodeWithText("val MENU: Map<String, List<Topping>> =", substring = true).assertExists()
        // The lookup reads the menu, so the menu is there with what the lookup does.
        onNodeWithContentDescription("The menu").performScrollTo().assertIsDisplayed()
        onNodeWithText("margherita: tomato, mozzarella, basil").assertIsDisplayed()
        onNodeWithText("veggie: tomato, mozzarella, mushrooms, peppers, onions").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("The rail").performScrollTo().performClick()
        onNodeWithText("CODE OF THE RAIL").performScrollTo().assertIsDisplayed()
        onNodeWithText("ARROWS FROM START").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("The menu").assertDoesNotExist()
        onNodeWithText("START then lookup").assertIsDisplayed()
        // The kitchen says what it does, and its code is the arrows that end there.
        onNodeWithContentDescription("The kitchen").performScrollTo().performClick()
        onNodeWithText("Every ticket ends here.", substring = true).performScrollTo().assertIsDisplayed()
        onNodeWithText("CODE OF THE KITCHEN").performScrollTo().assertIsDisplayed()
        onNodeWithText("ARROWS TO END").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("The code of the kitchen").performScrollTo().assertIsDisplayed()
        onNodeWithText("lookup then END").assertIsDisplayed()
        onNodeWithContentDescription("The kitchen").performScrollTo().performClick()
        onNodeWithText("HIDE THE CODE").performScrollTo().performClick()
        onNodeWithContentDescription("The code of the board").assertDoesNotExist()

        // A short shift with the scripted chefs shows its board and little else: nothing about the model, and no link to pass on.
        onNodeWithText("COPY A LINK TO THIS BOARD").assertDoesNotExist()
        onNodeWithText("MODEL").assertDoesNotExist()

        onNodeWithText("START THE SHIFT").performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { game.kitchen.finished }

        onNodeWithContentDescription("Shift over").performScrollTo().assertIsDisplayed()
        onNodeWithText("400 of 400").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Oven place 1: empty").performScrollTo().assertIsDisplayed()
        // The shifts that were played are listed in a rush, where one board is compared with another.
        onNodeWithText("YOUR SHIFTS / FIRST ORDERS").assertDoesNotExist()
        // The medal comes with the result.
        onNodeWithContentDescription("Right medal: won").performScrollTo().assertIsDisplayed()
        onNodeWithText("RIGHT / WON").performScrollTo().assertIsDisplayed()
        onNodeWithText("COPY THIS RESULT AND A LINK TO ITS BOARD").performScrollTo().performClick()
        assertEquals(
            "Pixel Pizza, first orders: 400 of 400 points for 0 tokens on 1 station. Can you beat this board? " +
                "https://deeptelar.github.io/telar-demo/#kitchen=opening.--l---2k",
            copied.last(),
        )

        // The next shift starts with the same board, which reads the name of a pizza and nothing else. Its steps begin with taking the lookup off.
        onNodeWithText("NEXT SHIFT: WISHES").performScrollTo().performClick()
        assertEquals(Rush.Wishes, game.kitchen.rush)
        onNodeWithText("KITCHEN RUSH / SHIFT 2 OF 9").performScrollTo().assertIsDisplayed()
        onNodeWithText("NEW STATION: READER").performScrollTo().assertIsDisplayed()
        onNodeWithText("STEP 1 OF 8").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Step: Press the lookup tile. This board does not need it.").assertIsDisplayed()
        onNodeWithText("A pepperoni with extra cheese.").assertDoesNotExist()
        // No shift of these orders was played yet, so the button does not speak of a new one.
        onNodeWithText("START A NEW SHIFT").assertDoesNotExist()
        onNodeWithText("START THE SHIFT").performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { game.kitchen.history.size == 2 }
        onNodeWithText("100 of 400").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Right medal: not won").performScrollTo().assertIsDisplayed()
        // The result says which orders went wrong and why. The steps of this shift are shown already, so it offers none.
        onNodeWithContentDescription("What went wrong").performScrollTo().assertIsDisplayed()
        onNodeWithText("#2: A pepperoni with extra cheese.").performScrollTo().assertIsDisplayed()
        onNodeWithText("The lookup tile wrote: tomato, mozzarella, pepperoni. The guest wanted: tomato, mozzarella, mozzarella, pepperoni.").assertIsDisplayed()
        onNodeWithText("SHOW ME THE NEXT STEP").assertDoesNotExist()
        // The orders of the shift are there with what became of them.
        onNodeWithText("A pepperoni with extra cheese.").performScrollTo().assertIsDisplayed()
        onAllNodesWithText("WRONG PIZZA").assertCountEquals(3)
        onNodeWithText("NEXT SHIFT: SORTING").assertDoesNotExist()

        // The lookup goes, and the station that is new in this shift takes its place.
        onNodeWithContentDescription("Station lookup").performScrollTo().performClick()
        onNodeWithContentDescription("Step: Press \"Remove the station\" under the board.").performScrollTo().assertIsDisplayed()
        onNodeWithText("REMOVE THE STATION").performScrollTo().performClick()
        onNodeWithContentDescription("Step: Press the + that blinks, to put a station there.").performScrollTo().assertIsDisplayed()
        // The player may take another place than the one that blinks, and the steps go on from there.
        onNodeWithContentDescription("Place 1").performScrollTo().performClick()
        onNodeWithContentDescription("Step: Choose \"Reader\" in the list under the board.").performScrollTo().assertIsDisplayed()
        onNodeWithText("New in this shift.", substring = true).performScrollTo().performClick()
        onNodeWithText("press the tile it goes to").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("The kitchen").performScrollTo().performClick()
        onNodeWithContentDescription("The rail").performScrollTo().performClick()
        onNodeWithContentDescription("Station reader").performScrollTo().performClick()
        onNodeWithContentDescription("Board: reader to kitchen, rail to reader").performScrollTo().assertIsDisplayed()
        // The orders of the shift that is over say where they would go on the new board.
        onAllNodesWithText("on the board now: rail > reader > kitchen").assertCountEquals(4)

        onNodeWithText("SHOW THE CODE").performScrollTo().performClick()
        onNodeWithContentDescription("The code of the board").performScrollTo().assertIsDisplayed()

        onNodeWithText("START A NEW SHIFT").performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { game.kitchen.history.size == 3 }
        onNodeWithText("400 of 400").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Right medal: won").performScrollTo().assertIsDisplayed()
        onNodeWithText("NEXT SHIFT: SORTING").performScrollTo().assertIsDisplayed()

        // A shift that shows its steps opens with its own board again when the player comes back to it.
        onNodeWithText("< ALL SHIFTS").performScrollTo().performClick()
        assertFalse(game.kitchen.inShift)
        // The list has the medals that were won, and marks the shift the player came from.
        onNodeWithContentDescription("Shift 2: Wishes, 1 of 1 medals, chosen").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Shift 1: First orders, 1 of 1 medals").performScrollTo().performClick()
        onNodeWithContentDescription("Board: rail to lookup").performScrollTo().assertIsDisplayed()
        onNodeWithText("START OVER").performScrollTo().assertIsDisplayed()

        // No shift is locked. The rushes offer every station and have three medals.
        onNodeWithText("< ALL SHIFTS").performScrollTo().performClick()
        onNodeWithContentDescription("Shift 9: Dinner rush, 0 of 3 medals").performScrollTo().performClick()
        assertEquals(Rush.Dinner, game.kitchen.rush)
        onNodeWithText("KITCHEN RUSH / SHIFT 9 OF 9").performScrollTo().assertIsDisplayed()
        onNodeWithText("EVERY STATION").performScrollTo().assertIsDisplayed()
        onNodeWithText("MEDALS / DINNER RUSH").performScrollTo().assertIsDisplayed()
        onNodeWithText("ORDERS / DINNER RUSH").performScrollTo().assertIsDisplayed()
        // A rush opens with an empty board, which says where to begin and not what is wrong with it.
        onNodeWithContentDescription("Board: no arrows").performScrollTo().assertIsDisplayed()
        onNodeWithText("Press a + to put a station on the board.").performScrollTo().assertIsDisplayed()
        onNodeWithText("The rail has no arrow.", substring = true).assertDoesNotExist()
        // Its orders are listed without a way, which they get with the first station.
        onNodeWithContentDescription("Order 12").performScrollTo().assertIsDisplayed()
        onNodeWithText("goes: rail > stuck").assertDoesNotExist()
        onNodeWithContentDescription("Place 1").performScrollTo().performClick()
        onNodeWithText("MENU LOOKUP").performScrollTo().performClick()
        onNodeWithContentDescription("The kitchen").performScrollTo().performClick()
        onNodeWithContentDescription("Place 2").performScrollTo().performClick()
        onNodeWithText("SECOND OVEN").performScrollTo().performClick()
        onNodeWithContentDescription("Station lookup").performScrollTo().performClick()
        onNodeWithContentDescription("The rail").performScrollTo().performClick()
        onNodeWithContentDescription("Station secondOven").performScrollTo().performClick()
        onNodeWithContentDescription("Order 12 goes rail > oven 2 > lookup > kitchen").performScrollTo().assertIsDisplayed()
        // A board fits in a link, which opens the stage with that board drawn.
        onNodeWithText("COPY A LINK TO THIS BOARD").performScrollTo().performClick()
        assertEquals("https://deeptelar.github.io/telar-demo/#kitchen=dinner.lo----1k0", copied.last())
        onNodeWithText("COPIED").performScrollTo().assertIsDisplayed()
        onNodeWithText("START THE SHIFT").performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { game.kitchen.history.size == 4 }
        onNodeWithContentDescription("Oven 2 place 1: empty").performScrollTo().assertIsDisplayed()
        // The list is of the shifts of these orders, and a board that was played can be taken up again.
        onNodeWithText("YOUR SHIFTS / DINNER RUSH").performScrollTo().assertIsDisplayed()
        onNodeWithText("1 / OVEN 2 + LOOKUP / SCRIPTED").performScrollTo().assertIsDisplayed()
        onNodeWithText("CLEAR THE BOARD").performScrollTo().performClick()
        onNodeWithText("USE BOARD 1 AGAIN").performScrollTo().performClick()
        assertEquals("oven 2 + lookup", game.kitchen.board.summary)
        assertEquals(emptyList(), game.kitchen.problems)
        onNodeWithContentDescription("The last shift").assertDoesNotExist()

        // A board that serves the last shift ends the stage: its result says how many medals there are, and leads to the list.
        game.kitchen.load(DINNER_BOARD)
        onNodeWithText("START A NEW SHIFT").performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { game.kitchen.history.size == 5 }
        onNodeWithText("1200 of 1200").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("The last shift").performScrollTo().assertIsDisplayed()
        onNodeWithText("The dinner rush is served. You have 4 of 15 medals, and the list of the shifts shows which are still open.").assertIsDisplayed()
        onNodeWithText("ALL SHIFTS").performScrollTo().performClick()
        assertFalse(game.kitchen.inShift)
        onNodeWithContentDescription("4 of 15 medals").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Shift 9: Dinner rush, 2 of 3 medals, chosen").performScrollTo().assertIsDisplayed()

        // The stage select screen leads here too.
        onNodeWithText("< ALL STAGES").performScrollTo().performClick()
        onNodeWithText("KITCHEN RUSH").performScrollTo().performClick()
        onNodeWithContentDescription("Kitchen rush").assertIsDisplayed()
    }

    /** A phone shows one panel after the other, so the one that matters now has to be the one on the screen. */
    @Test
    fun onAPhoneTheShiftAndItsResultAreOnTheScreenWithoutScrolling() = runComposeUiTest {
        lateinit var game: Game
        setContent {
            val scope = rememberCoroutineScope()
            game = remember { Game(scriptedModel(), MemoryStore(), MemoryCheckpointer(), scope, workMillis = 1_000) }
            Box(Modifier.requiredSize(390.dp, 700.dp)) {
                PizzaTheme(dark = true) { GameScreens(game, Settings(), Screen.Kitchen, onNavigate = {}, onSave = {}) }
            }
        }

        // The nine shifts are a list, one under the other. In a shift, the board is on the first screen with its step.
        onNodeWithContentDescription("Shift 9: Dinner rush, 0 of 3 medals").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Shift 1: First orders, 0 of 1 medals, chosen").performScrollTo().performClick()
        onNodeWithContentDescription("Step: Press the lookup tile.").assertIsDisplayed()
        onNodeWithContentDescription("Station lookup").assertIsDisplayed()
        // The step is right above the board, and both fit the screen as it opens: the last tile of the board is on it whole,
        // so no step of the shift asks for a tile that is not there.
        assertTrue(onNodeWithContentDescription("The kitchen").assertIsDisplayed().getBoundsInRoot().bottom <= 700.dp)
        onNodeWithContentDescription("Station lookup").performClick()
        onNodeWithContentDescription("Step: Press the kitchen. That draws the arrow from the lookup tile to it.").assertIsDisplayed()
        onNodeWithContentDescription("The kitchen").assertIsDisplayed().performClick()
        onNodeWithContentDescription("Step: The board is ready. Start the shift.").assertIsDisplayed()

        // The player is at the button under the board. The shift that starts takes the screen.
        onNodeWithText("START THE SHIFT").performScrollTo().performClick()
        waitUntil(timeoutMillis = 30_000) { (game.kitchen.shift?.kitchen?.minute ?: 0) >= 8 }
        assertTrue(game.kitchen.running)
        onNodeWithText("THE SHIFT / FIRST ORDERS").assertIsDisplayed()
        onNodeWithText("STOP").assertIsDisplayed()
        onNodeWithContentDescription("Board: rail to lookup, lookup to kitchen").assertIsDisplayed()
        onNodeWithContentDescription("Oven place 1", substring = true).assertIsDisplayed()
        (1..4).forEach { order -> onNodeWithContentDescription("Order $order: ", substring = true).assertIsDisplayed() }
        onNodeWithText("START THE SHIFT").assertDoesNotExist()

        // The rest of the shift need not be watched. When it is over, its result and the way to the next shift are there instead.
        onNodeWithText("SKIP TO THE END").assertIsDisplayed().performClick()
        assertTrue(game.kitchen.hurried)
        waitUntil(timeoutMillis = 30_000) { game.kitchen.finished }
        waitForIdle()
        onNodeWithContentDescription("Shift over").assertIsDisplayed()
        onNodeWithText("NEXT SHIFT: WISHES").assertIsDisplayed()
        onNodeWithText("START A NEW SHIFT").assertExists()
    }

    /** A shift that went wrong says why where the player is looking, and its button leads to the board with the first step. */
    @Test
    fun onAPhoneAShiftThatWentWrongSaysWhyAndLeadsToItsSteps() = runComposeUiTest {
        lateinit var game: Game
        setContent {
            val scope = rememberCoroutineScope()
            game = remember { Game(scriptedModel(), MemoryStore(), MemoryCheckpointer(), scope, workMillis = 0) }
            Box(Modifier.requiredSize(390.dp, 700.dp)) {
                PizzaTheme(dark = true) { GameScreens(game, Settings(), Screen.Kitchen, onNavigate = {}, onSave = {}) }
            }
        }

        // The shift of the book opens with the board that won the one before, which runs and reads without the book.
        onNodeWithContentDescription("Shift 4: The book, 0 of 1 medals").performScrollTo().performClick()
        onNodeWithText("START THE SHIFT").performScrollTo().performClick()
        waitUntil(timeoutMillis = 30_000) { game.kitchen.finished }
        waitForIdle()

        onNodeWithContentDescription("Shift over").assertIsDisplayed()
        onNodeWithContentDescription("What went wrong").assertIsDisplayed()
        onNodeWithText("#3: The same as table 1 had.").assertIsDisplayed()
        onNodeWithText("The reader tile wrote no ticket: The order does not say which pizza is meant.").assertIsDisplayed()
        onNodeWithText("The reader tile wrote a ticket with onions. Table 2 is allergic.").assertIsDisplayed()
        onNodeWithText("NEXT SHIFT: LOOKING BACK").assertDoesNotExist()

        // The button of the result is on the screen, and the step it asks for is too once it is pressed.
        onAllNodesWithText("SHOW ME THE NEXT STEP").onFirst().assertIsDisplayed().performClick()
        waitForIdle()
        assertTrue(game.kitchen.hinting)
        onNodeWithContentDescription("Step: Press the reader tile. This board does not need it.").assertIsDisplayed()
        onNodeWithContentDescription("Station reader").assertIsDisplayed()
    }

    @Test
    fun aPlayerPressesATileToSeeTheCodeOfItsNode() = runComposeUiTest {
        setContent {
            val scope = rememberCoroutineScope()
            val game = remember { Game(scriptedModel(), MemoryStore(), MemoryCheckpointer(), scope, workMillis = 0) }
            PizzaTheme(dark = true) { GameScreens(game, Settings(), Screen.Play(1), onNavigate = {}, onSave = {}) }
        }

        onNodeWithText("PRESS A TILE TO SEE ITS CODE").assertIsDisplayed()
        onNodeWithText("READ").performClick()

        onNodeWithText("CODE OF READ").assertIsDisplayed()
        onNodeWithText("START then read then answer then END").assertIsDisplayed()
        onNodeWithText("fun topicOf(message: String)", substring = true).assertIsDisplayed()

        // Another tile replaces the code, and the same tile again puts it away.
        onNodeWithText("ANSWER").performClick()
        onNodeWithText("CODE OF ANSWER").assertIsDisplayed()
        onNodeWithText("ANSWER").performClick()
        assertEquals(0, onAllNodesWithText("CODE OF ANSWER").fetchSemanticsNodes().size)
        onNodeWithText("PRESS A TILE TO SEE ITS CODE").assertIsDisplayed()
    }

    @Test
    fun theGameSaysWhichStagesCanAskARealModel() = runComposeUiTest {
        var screen by mutableStateOf<Screen>(Screen.Stages)
        var settings by mutableStateOf(Settings())
        setContent {
            val scope = rememberCoroutineScope()
            val game = remember { Game(scriptedModel(), MemoryStore(), MemoryCheckpointer(), scope, workMillis = 0) }
            PizzaTheme(dark = true) { GameScreens(game, settings, screen, onNavigate = {}, onSave = {}) }
        }

        // The cards of stages 6 and 8 are marked, and the other six say that they are scripted.
        assertEquals(2, onAllNodesWithText("SCRIPTED OR A MODEL", substring = true).fetchSemanticsNodes().size)
        assertEquals(6, onAllNodesWithText("SCRIPTED ONLY", substring = true).fetchSemanticsNodes().size)

        screen = Screen.Play(7)
        onNodeWithText("none. This stage is scripted only.").assertIsDisplayed()

        screen = Screen.Play(6)
        onNodeWithText("scripted. Choose a real one under Options.").assertIsDisplayed()

        // With a service chosen, the stage names its model. A stage without a model still has none.
        settings = Settings(ModelMode.OpenAi).withKey("a key").withModel("gpt-5-mini")
        onNodeWithText("gpt-5-mini").assertIsDisplayed()
        screen = Screen.Play(7)
        onNodeWithText("none. This stage is scripted only.").assertIsDisplayed()
    }

    @Test
    fun theTitleScreenAndTheAboutPageLinkToTheLibrary() = runComposeUiTest {
        val opened = mutableListOf<String>()
        var screen by mutableStateOf<Screen>(Screen.Title)
        setContent {
            val scope = rememberCoroutineScope()
            val game = remember { Game(scriptedModel(), MemoryStore(), MemoryCheckpointer(), scope, workMillis = 0) }
            val links = object : UriHandler {
                override fun openUri(uri: String) {
                    opened += uri
                }
            }
            CompositionLocalProvider(LocalUriHandler provides links) {
                PizzaTheme(dark = true) { GameScreens(game, Settings(), screen, onNavigate = { screen = it }, onSave = {}) }
            }
        }

        onNodeWithContentDescription(TELAR_LINK).performClick()

        // The about page is one press away from the title screen and from the bar of every other screen.
        onNodeWithText("ABOUT").performClick()
        assertEquals(Screen.About, screen)
        onNodeWithText("Pixel Pizza shows what an AI agent workflow is", substring = true).assertIsDisplayed()
        onNodeWithContentDescription(TELAR_LINK).performScrollTo().performClick()
        assertEquals(List(2) { "https://github.com/deeptelar/telar" }, opened)

        onNodeWithText("< ALL STAGES").performScrollTo().performClick()
        assertEquals(Screen.Stages, screen)
        listOf(Screen.Stages, Screen.Play(3), Screen.Options).forEach { page ->
            screen = page
            // The pages themselves carry no link: it is on the about page.
            assertEquals(0, onAllNodesWithContentDescription(TELAR_LINK).fetchSemanticsNodes().size)
            onNodeWithText("ABOUT").performClick()
            assertEquals(Screen.About, screen)
        }
    }
}
