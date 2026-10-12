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

        // A player starts in the first shift, with an empty board and one station to put on it.
        onNodeWithText("BONUS: KITCHEN RUSH").performClick()
        onNodeWithContentDescription("Kitchen rush").assertIsDisplayed()
        onNodeWithContentDescription("Shift 1: First orders, 0 of 1 medals, chosen").assertIsDisplayed()
        onNodeWithContentDescription("Shift 9: Dinner rush, 0 of 3 medals").assertIsDisplayed()
        onNodeWithText("SHIFT 1 OF 9 / NEW STATION: MENU LOOKUP").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Board: no arrows").performScrollTo().assertIsDisplayed()
        onNodeWithText("The rail has no arrow. Draw one to the station that gets every order first.").performScrollTo().assertIsDisplayed()

        // The orders can be read before the shift, each with the way it takes across the board.
        onNodeWithText("ORDERS / FIRST ORDERS").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Order 4 goes rail > stuck").performScrollTo().assertIsDisplayed()

        onNodeWithContentDescription("Place 1").performScrollTo().performClick()
        onNodeWithText("MENU LOOKUP").performScrollTo().assertIsDisplayed()
        onNodeWithText("READER").assertDoesNotExist()
        onNodeWithText("MENU LOOKUP").performClick()
        onNodeWithText("press the tile it goes to").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("The kitchen").performScrollTo().performClick()
        onNodeWithContentDescription("The rail").performScrollTo().performClick()
        onNodeWithContentDescription("Station lookup").performScrollTo().performClick()
        onNodeWithContentDescription("Board: lookup to kitchen, rail to lookup").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Order 4 goes rail > lookup > kitchen").performScrollTo().assertIsDisplayed()

        // A board fits in a link, which opens the stage with that board drawn.
        onNodeWithText("COPY A LINK TO THIS BOARD").performScrollTo().performClick()
        assertEquals(listOf("https://deeptelar.github.io/telar-demo/#kitchen=opening.l-----0k"), copied)
        onNodeWithText("COPIED").performScrollTo().assertIsDisplayed()

        onNodeWithText("START THE SHIFT").performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { game.kitchen.finished }

        onNodeWithContentDescription("Shift over").performScrollTo().assertIsDisplayed()
        onNodeWithText("400 of 400").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Oven place 1: empty").performScrollTo().assertIsDisplayed()
        onNodeWithText("1 / FIRST ORDERS / LOOKUP / SCRIPTED").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Serve every order right and on time. Reached").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Shift 1: First orders, 1 of 1 medals, chosen").performScrollTo().assertIsDisplayed()
        onNodeWithText("COPY THIS RESULT AND A LINK TO ITS BOARD").performScrollTo().performClick()
        assertEquals(
            "Pixel Pizza, first orders: 400 of 400 points for 0 tokens on 1 station. Can you beat this board? " +
                "https://deeptelar.github.io/telar-demo/#kitchen=opening.l-----0k",
            copied.last(),
        )

        // The next shift starts with the same board, which reads the name of a pizza and nothing else.
        onNodeWithText("NEXT SHIFT: WISHES").performScrollTo().performClick()
        assertEquals(Rush.Wishes, game.kitchen.rush)
        onNodeWithText("SHIFT 2 OF 9 / NEW STATION: READER").performScrollTo().assertIsDisplayed()
        onNodeWithText("A pepperoni with extra cheese.").performScrollTo().assertIsDisplayed()
        onNodeWithText("START A NEW SHIFT").performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { game.kitchen.history.size == 2 }
        onNodeWithText("100 of 400").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Serve every order right and on time. Open").performScrollTo().assertIsDisplayed()
        onNodeWithText("NEXT SHIFT: SORTING").assertDoesNotExist()

        // The lookup goes, and the station that is new in this shift takes its place.
        onNodeWithContentDescription("Station lookup").performScrollTo().performClick()
        onNodeWithText("REMOVE THE STATION").performScrollTo().performClick()
        onNodeWithText("The rail has no arrow. Draw one to the station that gets every order first.").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Place 1").performScrollTo().performClick()
        onNodeWithText("New in this shift.", substring = true).performScrollTo().performClick()
        onNodeWithText("press the tile it goes to").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("The kitchen").performScrollTo().performClick()
        onNodeWithContentDescription("The rail").performScrollTo().performClick()
        onNodeWithContentDescription("Station reader").performScrollTo().performClick()
        onNodeWithContentDescription("Board: reader to kitchen, rail to reader").performScrollTo().assertIsDisplayed()
        // The link is of the board as it is drawn now, so the button offers it again.
        onNodeWithText("COPY A LINK TO THIS BOARD").performScrollTo().performClick()
        assertEquals("https://deeptelar.github.io/telar-demo/#kitchen=wishes.r-----0k", copied.last())
        // The orders of the shift that is over say where they would go on the new board.
        onAllNodesWithText("on the board now: rail > reader > kitchen").assertCountEquals(4)

        onNodeWithText("SHOW THE CODE OF THIS BOARD").performScrollTo().performClick()
        onNodeWithContentDescription("The code of the board").performScrollTo().assertIsDisplayed()

        onNodeWithText("START A NEW SHIFT").performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { game.kitchen.history.size == 3 }
        onNodeWithText("400 of 400").performScrollTo().assertIsDisplayed()
        onNodeWithText("3 / WISHES / READER / SCRIPTED").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Serve every order right and on time. Reached").performScrollTo().assertIsDisplayed()
        onNodeWithText("NEXT SHIFT: SORTING").performScrollTo().assertIsDisplayed()

        // A shift does not start on a board with a station that it does not have yet.
        onNodeWithContentDescription("Shift 1: First orders, 1 of 1 medals").performScrollTo().performClick()
        onNodeWithText("Reader comes in a later shift. Take it off the board, or choose that shift.").performScrollTo().assertIsDisplayed()
        // The board of an earlier shift can be taken up again.
        onNodeWithText("USE BOARD 1 AGAIN").performScrollTo().performClick()
        assertEquals("lookup", game.kitchen.board.summary)
        assertEquals(emptyList(), game.kitchen.problems)

        // No shift is locked. The rushes offer every station and have three medals.
        onNodeWithContentDescription("Shift 9: Dinner rush, 0 of 3 medals").performScrollTo().performClick()
        assertEquals(Rush.Dinner, game.kitchen.rush)
        onNodeWithText("SHIFT 9 OF 9 / EVERY STATION").performScrollTo().assertIsDisplayed()
        onNodeWithText("MEDALS / DINNER RUSH").performScrollTo().assertIsDisplayed()
        onNodeWithText("ORDERS / DINNER RUSH").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Order 12 goes rail > lookup > kitchen").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Place 2").performScrollTo().performClick()
        onNodeWithText("SECOND OVEN").performScrollTo().performClick()
        onNodeWithContentDescription("Station lookup").performScrollTo().performClick()
        onNodeWithContentDescription("The rail").performScrollTo().performClick()
        onNodeWithText("CHANGE").performScrollTo().performClick()
        onNodeWithContentDescription("Station secondOven").performScrollTo().performClick()
        onNodeWithText("START A NEW SHIFT").performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { game.kitchen.history.size == 4 }
        onNodeWithContentDescription("Oven 2 place 1: empty").performScrollTo().assertIsDisplayed()
        onNodeWithText("4 / DINNER RUSH / OVEN 2 + LOOKUP / SCRIPTED").performScrollTo().assertIsDisplayed()

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

        // The nine shifts are a row of numbers, and the board is drawn as on a wide screen.
        onNodeWithContentDescription("Shift 9: Dinner rush, 0 of 3 medals").assertIsDisplayed()
        onNodeWithText("Press a + to put a station on the board.").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Place 1").performScrollTo().performClick()
        onNodeWithText("MENU LOOKUP").performScrollTo().performClick()
        onNodeWithContentDescription("The kitchen").performScrollTo().performClick()
        onNodeWithContentDescription("The rail").performScrollTo().performClick()
        onNodeWithContentDescription("Station lookup").performScrollTo().performClick()

        // The player is at the button under the board. The shift that starts takes the screen.
        onNodeWithText("START THE SHIFT").performScrollTo().performClick()
        waitUntil(timeoutMillis = 30_000) { (game.kitchen.shift?.kitchen?.minute ?: 0) >= 8 }
        assertTrue(game.kitchen.running)
        onNodeWithText("THE SHIFT / FIRST ORDERS").assertIsDisplayed()
        onNodeWithText("STOP").assertIsDisplayed()
        onNodeWithContentDescription("Board: lookup to kitchen, rail to lookup").assertIsDisplayed()
        onNodeWithContentDescription("Oven place 1", substring = true).assertIsDisplayed()
        (1..4).forEach { order -> onNodeWithContentDescription("Order $order: ", substring = true).assertIsDisplayed() }
        onNodeWithText("START THE SHIFT").assertDoesNotExist()

        // When it is over, its result and the way to the next shift are there instead.
        waitUntil(timeoutMillis = 30_000) { game.kitchen.finished }
        waitForIdle()
        onNodeWithContentDescription("Shift over").assertIsDisplayed()
        onNodeWithText("NEXT SHIFT: WISHES").assertIsDisplayed()
        onNodeWithText("START A NEW SHIFT").assertExists()
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
