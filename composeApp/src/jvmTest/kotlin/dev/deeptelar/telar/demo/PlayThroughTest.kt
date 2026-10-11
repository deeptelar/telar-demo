package dev.deeptelar.telar.demo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.deeptelar.telar.MemoryCheckpointer
import dev.deeptelar.telar.demo.game.scriptedModel
import dev.deeptelar.telar.demo.storage.MemoryStore
import dev.deeptelar.telar.demo.ui.Game
import dev.deeptelar.telar.demo.ui.GameScreens
import dev.deeptelar.telar.demo.ui.ModelMode
import dev.deeptelar.telar.demo.ui.Phase
import dev.deeptelar.telar.demo.ui.PizzaTheme
import dev.deeptelar.telar.demo.ui.Rush
import dev.deeptelar.telar.demo.ui.Screen
import dev.deeptelar.telar.demo.ui.Settings
import dev.deeptelar.telar.demo.ui.TELAR_LINK
import kotlin.test.Test
import kotlin.test.assertEquals

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
        setContent {
            val scope = rememberCoroutineScope()
            game = remember { Game(scriptedModel(), MemoryStore(), MemoryCheckpointer(), scope, workMillis = 0) }
            var screen by remember { mutableStateOf<Screen>(Screen.Title) }
            PizzaTheme(dark = true) { GameScreens(game, Settings(), screen, onNavigate = { screen = it }, onSave = {}) }
        }

        // The board a player finds sends every order to the menu lookup.
        onNodeWithText("BONUS: KITCHEN RUSH").performClick()
        onNodeWithContentDescription("Kitchen rush").assertIsDisplayed()
        onNodeWithContentDescription("Board: rail to lookup, lookup to kitchen").performScrollTo().assertIsDisplayed()
        onNodeWithText("START THE SHIFT").performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { game.kitchen.finished }

        onNodeWithContentDescription("Shift over").performScrollTo().assertIsDisplayed()
        onNodeWithText("400 of 1100").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Oven place 1: empty").performScrollTo().assertIsDisplayed()
        onNodeWithText("1 / LUNCH RUSH / LOOKUP / SCRIPTED").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Serve every order right and on time. Open").performScrollTo().assertIsDisplayed()

        // The lookup goes, and a reader that is told everything takes its place.
        onNodeWithContentDescription("Station lookup").performScrollTo().performClick()
        onNodeWithText("REMOVE THE STATION").performScrollTo().performClick()
        onNodeWithText("The rail has no arrow. Draw one to the station that gets every order first.").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Place 1").performScrollTo().performClick()
        onNodeWithText("READER WITH THE BOOK").performScrollTo().performClick()
        onNodeWithText("press the tile it goes to").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("The kitchen").performScrollTo().performClick()
        onNodeWithContentDescription("The rail").performScrollTo().performClick()
        onNodeWithContentDescription("Station bookReader").performScrollTo().performClick()
        onNodeWithContentDescription("Board: bookReader to kitchen, rail to bookReader").performScrollTo().assertIsDisplayed()

        onNodeWithText("SHOW THE CODE OF THIS BOARD").performScrollTo().performClick()
        onNodeWithContentDescription("The code of the board").performScrollTo().assertIsDisplayed()

        onNodeWithText("START A NEW SHIFT").performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { game.kitchen.history.size == 2 }
        onNodeWithText("1100 of 1100").performScrollTo().assertIsDisplayed()
        onNodeWithText("2 / LUNCH RUSH / BOOK / SCRIPTED").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Serve every order right and on time. Reached").performScrollTo().assertIsDisplayed()

        // The board of an earlier shift can be taken up again.
        onNodeWithText("USE BOARD 1 AGAIN").performScrollTo().performClick()
        assertEquals("lookup", game.kitchen.board.summary)

        // The other shift has other orders and goals of its own.
        onNodeWithText("DINNER RUSH").performScrollTo().performClick()
        assertEquals(Rush.Dinner, game.kitchen.rush)
        onNodeWithText("GOALS / DINNER RUSH").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Place 2").performScrollTo().performClick()
        onNodeWithText("SECOND OVEN").performScrollTo().performClick()
        onNodeWithContentDescription("Station lookup").performScrollTo().performClick()
        onNodeWithContentDescription("The rail").performScrollTo().performClick()
        onNodeWithText("CHANGE").performScrollTo().performClick()
        onNodeWithContentDescription("Station secondOven").performScrollTo().performClick()
        onNodeWithText("START A NEW SHIFT").performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { game.kitchen.history.size == 3 }
        onNodeWithContentDescription("Oven 2 place 1: empty").performScrollTo().assertIsDisplayed()
        onNodeWithText("3 / DINNER RUSH / OVEN 2 + LOOKUP / SCRIPTED").performScrollTo().assertIsDisplayed()

        // The stage select screen leads here too.
        onNodeWithText("< ALL STAGES").performScrollTo().performClick()
        onNodeWithText("KITCHEN RUSH").performScrollTo().performClick()
        onNodeWithContentDescription("Kitchen rush").assertIsDisplayed()
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
