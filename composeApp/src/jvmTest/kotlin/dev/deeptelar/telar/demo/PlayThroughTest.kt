package dev.deeptelar.telar.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
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
import dev.deeptelar.telar.demo.ui.Screen
import dev.deeptelar.telar.demo.ui.Settings
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
}
