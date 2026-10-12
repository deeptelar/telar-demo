package dev.deeptelar.telar.demo

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import org.jetbrains.skia.EncodedImageFormat
import dev.deeptelar.telar.demo.game.Mail
import dev.deeptelar.telar.MemoryCheckpointer
import dev.deeptelar.telar.demo.game.scriptedModel
import dev.deeptelar.telar.demo.kitchen.Blueprint
import dev.deeptelar.telar.demo.kitchen.Exit
import dev.deeptelar.telar.demo.storage.MemoryStore
import dev.deeptelar.telar.demo.ui.Game
import dev.deeptelar.telar.demo.ui.GameScreens
import dev.deeptelar.telar.demo.ui.ModelMode
import dev.deeptelar.telar.demo.ui.PizzaTheme
import dev.deeptelar.telar.demo.ui.Rush
import dev.deeptelar.telar.demo.ui.Screen
import dev.deeptelar.telar.demo.ui.Settings
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Draws every screen without a window and writes it to `build/screenshots`. It fails if a screen
 * cannot be drawn, and the pictures are what the README shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenshotTest {
    private val scope = TestScope()
    private val game = Game(scriptedModel(delayMillis = 1_000), MemoryStore(), MemoryCheckpointer(), scope, workMillis = 1_000)
    private val delivery = Mail("Ana", "Where is my pizza?")
    private val refund = Mail("Ben", "My pizza arrived cold. I want a refund.")

    private fun shoot(
        name: String,
        screen: Screen,
        width: Int = 1280,
        height: Int = 860,
        dark: Boolean = true,
        settings: Settings = Settings(),
    ) = shoot(name, width, height, dark) { GameScreens(game, settings, screen, onNavigate = {}, onSave = {}) }

    private fun shoot(name: String, width: Int, height: Int, dark: Boolean = true, content: @Composable () -> Unit) {
        ImageComposeScene(width * 2, height * 2, Density(2f)) {
            PizzaTheme(dark) { content() }
        }.use { scene ->
            // Text that is typed out letter by letter needs a second of real time to be complete.
            scene.render().close()
            Thread.sleep(1_200)
            scene.render(nanoTime = 1_200_000_000L).close()
            val image = scene.render(nanoTime = 1_216_000_000L)
            val file = File("build/screenshots/$name.png")
            file.parentFile.mkdirs()
            file.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            image.close()
            assertTrue(file.length() > 10_000, "$name looks empty")
        }
    }

    @Test
    fun everyScreenCanBeDrawn() {
        shoot("title", Screen.Title)
        shoot("title-light", Screen.Title, dark = false)
        shoot("title-phone", Screen.Title, width = 390, height = 780)

        // Stage 8, halfway: the kitchen and the driver are both at work.
        game.controller(8).play(delivery)
        scope.advanceTimeBy(1_500)
        shoot("stage-8-running", Screen.Play(8), height = 1000)
        scope.advanceUntilIdle()
        shoot("stage-8-clear", Screen.Play(8), height = 2000)
        shoot("stage-8-phone", Screen.Play(8), width = 390, height = 2400)

        // Stage 8 again, with a question about the menu: the model asked for a price, and the tool is at work.
        game.controller(8).play(Mail("Cleo", "Do you sell salad?"))
        scope.advanceTimeBy(2_500)
        shoot("stage-8-agent", Screen.Play(8), height = 1100)
        scope.advanceUntilIdle()
        shoot("stage-8-agent-clear", Screen.Play(8), height = 1900)

        game.controller(3).play(Mail("Ana", "My pizza is late!"))
        scope.advanceUntilIdle()
        shoot("stage-3-clear", Screen.Play(3), height = 1700)

        game.controller(5).play(refund)
        scope.advanceUntilIdle()
        shoot("stage-5-save-point", Screen.Play(5), height = 1000)

        // Stage 6, after the tools ran: the model is halfway through writing its answer.
        game.controller(6).play(Mail("Ben", "How much is a margherita and a cola?"))
        scope.advanceTimeBy(2_800)
        shoot("stage-6-writing", Screen.Play(6), height = 1100)
        scope.advanceUntilIdle()
        shoot("stage-6-clear", Screen.Play(6), height = 1700)

        game.controller(7).play(delivery)
        scope.advanceUntilIdle()
        shoot("stage-7-game-over", Screen.Play(7))

        shoot("stage-2-ready", Screen.Play(2), dark = false)

        // A tile was pressed: its code is shown under the board.
        game.controller(2).toggleCode("read")
        shoot("stage-2-code", Screen.Play(2), height = 1500, dark = false)
        game.controller(6).toggleCode("tools")
        shoot("stage-6-code", Screen.Play(6), height = 1900)
        game.controller(4).toggleCode("kitchen")
        shoot("stage-4-code-phone", Screen.Play(4), width = 390, height = 1700)
        shoot("stages", Screen.Stages)
        shoot("stages-phone", Screen.Stages, width = 390, height = 1100)
        shoot("about", Screen.About)
        shoot("about-phone", Screen.About, width = 390, height = 900)
        shoot("options", Screen.Options)
        shoot("options-claude", Screen.Options, height = 1200, settings = Settings(ModelMode.Claude).withKey("sk-ant-example"))
        shoot("options-gemini", Screen.Options, height = 1000, settings = Settings(ModelMode.Gemini).withKey("example"))
        shoot("options-other", Screen.Options, height = 1100, settings = Settings(ModelMode.Other).withModel("llama3.2"))
        shoot("options-other-phone", Screen.Options, width = 390, height = 1500, settings = Settings(ModelMode.Other))
    }

    @Test
    fun theKitchenRushCanBeDrawn() {
        // The stage opens with the list of its shifts.
        shoot("kitchen-shifts", Screen.Kitchen, height = 900)
        shoot("kitchen-shifts-phone", Screen.Kitchen, width = 390, height = 1100)
        game.kitchen.enter(Rush.Opening)
        // The first shift as a player finds it: its station on the board, and above the board the first thing to press.
        shoot("kitchen", Screen.Kitchen, height = 1300)
        // A phone has one panel under the other, and the one that matters at the moment comes first.
        shoot("kitchen-phone", Screen.Kitchen, width = 390, height = 1900)
        // The arrow is in hand, and the step names the tile it goes to.
        game.kitchen.tap("lookup")
        shoot("kitchen-step-phone", Screen.Kitchen, width = 390, height = 1900)
        game.kitchen.tap(Blueprint.KITCHEN)
        shoot("kitchen-ready-phone", Screen.Kitchen, width = 390, height = 1900)
        // The code of the station that is selected, in place of the whole graph.
        game.kitchen.tap("lookup")
        shoot("kitchen-selected-phone", Screen.Kitchen, width = 390, height = 1900)
        game.kitchen.tap("lookup")
        game.kitchen.start()
        scope.advanceTimeBy(6_000)
        shoot("kitchen-phone-running", Screen.Kitchen, width = 390, height = 2300)
        scope.advanceUntilIdle()
        shoot("kitchen-phone-over", Screen.Kitchen, width = 390, height = 3900)
        shoot("kitchen-first-over", Screen.Kitchen, height = 1900)

        // The second shift: the lookup is off the board, and the list of a place marks the station the step names.
        game.kitchen.choose(Rush.Wishes)
        shoot("kitchen-wishes-phone", Screen.Kitchen, width = 390, height = 1900)
        game.kitchen.tap("lookup")
        shoot("kitchen-remove-phone", Screen.Kitchen, width = 390, height = 1900)
        game.kitchen.remove()
        game.kitchen.tapSlot(2)
        shoot("kitchen-choose-phone", Screen.Kitchen, width = 390, height = 1900)

        // The third shift asks where the arrows of the sorter go, and both stations they can go to blink.
        game.kitchen.choose(Rush.Sorting)
        shoot("kitchen-sorting-phone", Screen.Kitchen, width = 390, height = 1900)
        game.kitchen.tap("onlyMenu")
        shoot("kitchen-ask-phone", Screen.Kitchen, width = 390, height = 1900)
        shoot("kitchen-ask", Screen.Kitchen, height = 1300)
        // With the arrows the wrong way round, the steps the player asks for lead to the button that draws an arrow again.
        game.kitchen.tap("reader")
        game.kitchen.tap("lookup")
        shoot("kitchen-ready", Screen.Kitchen, height = 1300)
        game.kitchen.hint(true)
        game.kitchen.tap("onlyMenu")
        shoot("kitchen-change-phone", Screen.Kitchen, width = 390, height = 1900)
        shoot("kitchen-change", Screen.Kitchen, height = 1300)

        // A later short shift opens with a board that runs, and shows its steps when the player asks.
        game.kitchen.choose(Rush.LookingBack)
        shoot("kitchen-hint-phone", Screen.Kitchen, width = 390, height = 1900)
        game.kitchen.hint(true)
        shoot("kitchen-hinted-phone", Screen.Kitchen, width = 390, height = 1900)
        game.kitchen.load(Blueprint.FIRST)

        // The lunch rush, and the stations an empty place offers in it.
        game.kitchen.choose(Rush.Lunch)
        shoot("kitchen-lunch", Screen.Kitchen, height = 2100)
        game.kitchen.tapSlot(3)
        shoot("kitchen-picking", Screen.Kitchen, height = 1500)

        // A board with two sorters, with the arrows of one of them being drawn.
        game.kitchen.load(BEST_BOARD)
        game.kitchen.tap("looksBack")
        game.kitchen.redraw(Exit.NO)
        shoot("kitchen-drawing", Screen.Kitchen, height = 1200)
        game.kitchen.tap("reader")

        // In the rush: four orders came in together.
        game.kitchen.start()
        // An order is on the board: the tile of the station that has it is red, and so its picture is drawn for red.
        var waited = 0L
        while (game.kitchen.at == null && waited < 16_000) {
            scope.advanceTimeBy(40)
            waited += 40
        }
        shoot("kitchen-reading-phone", Screen.Kitchen, width = 390, height = 1100)
        shoot("kitchen-reading-light", Screen.Kitchen, height = 1300, dark = false)
        scope.advanceTimeBy(16_000 - waited)
        shoot("kitchen-running", Screen.Kitchen, height = 2000)
        scope.advanceUntilIdle()
        shoot("kitchen-over", Screen.Kitchen, height = 2300)

        // The dinner rush, with both ovens at work in the second wave.
        game.kitchen.choose(Rush.Dinner)
        game.kitchen.load(DINNER_BOARD)
        game.kitchen.start()
        scope.advanceTimeBy(21_500)
        shoot("kitchen-dinner", Screen.Kitchen, height = 2300)
        shoot("kitchen-dinner-phone", Screen.Kitchen, width = 390, height = 3600)
        scope.advanceUntilIdle()
        shoot("kitchen-dinner-over", Screen.Kitchen, height = 2300)
    }

    /** The picture of a link to the game. `wasmJsMain/resources/social-preview.png` is a copy of it. */
    @Test
    fun theSocialCardCanBeDrawn() {
        // Stage 4, halfway: the kitchen and the driver are both at work.
        game.controller(4).play(delivery)
        scope.advanceTimeBy(500)
        shoot("social-preview", SOCIAL_WIDTH, SOCIAL_HEIGHT) { SocialCard(game.controller(4)) }
    }
}
