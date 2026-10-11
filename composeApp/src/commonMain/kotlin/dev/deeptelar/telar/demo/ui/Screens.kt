package dev.deeptelar.telar.demo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import dev.deeptelar.telar.demo.game.stages
import dev.deeptelar.telar.demo.llm.ClaudeModel
import dev.deeptelar.telar.demo.llm.ClaudeModels

private const val TITLE = "Pixel Pizza"

/** The library the game is built with, and shows. */
private const val TELAR = "https://github.com/deeptelar/telar"

/** What the link to it is called for a player who cannot see the mark. */
const val TELAR_LINK = "Telar on GitHub"

/** The mark of GitHub, as the path of its 16 by 16 icon. */
private const val GITHUB_MARK =
    "M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94" +
        "-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07" +
        "-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27" +
        ".68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95" +
        ".29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"

private val gitHubMark by lazy { PathParser().parsePathString(GITHUB_MARK).toPath() }

/**
 * The way from the game to the library it is built with: [text] and the mark of GitHub, which open
 * the repository of Telar.
 */
@Composable
fun TelarLink(text: String, modifier: Modifier = Modifier) {
    val colors = Theme.colors
    val uriHandler = LocalUriHandler.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val color = if (hovered || focused) colors.ink else colors.dim
    Row(
        modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interaction, indication = null, role = Role.Button) { uriHandler.openUri(TELAR) }
            .semantics { contentDescription = TELAR_LINK }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(text, color = color)
        Canvas(Modifier.size(16.dp)) {
            scale(size.width / 16f, pivot = Offset.Zero) { drawPath(gitHubMark, color) }
        }
    }
}

/** The first thing a player sees: the name on a dot-matrix display and one button. */
@Composable
fun TitleScreen(game: Game, modelLabel: String, onStart: () -> Unit, onKitchen: () -> Unit, onAbout: () -> Unit, onOptions: () -> Unit) {
    val colors = Theme.colors
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // One line when the display fits the window, two when it does not.
        val lines = if (maxWidth >= 720.dp) listOf(TITLE) else TITLE.split(' ')
        val pitch = ((maxWidth - 48.dp) / (lines.first().length * 6 - 1)).coerceAtMost(10.dp)
        val glow by pulse(low = 0.7f, periodMillis = 900)
        val roomForModel = maxWidth >= 520.dp

        Column(
            Modifier.fillMaxSize().padding(bottom = 56.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PixelArt(Sprites.slice, pixel = 6.dp)
            Spacer(Modifier.height(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(pitch)) {
                lines.forEach { DotMatrix(it, pitch = pitch, unlit = colors.line.copy(alpha = 0.55f)) }
            }
            Spacer(Modifier.height(24.dp))
            Label("help desk", color = colors.red)
            Spacer(Modifier.height(10.dp))
            BasicText(
                "Customers write in. A graph writes back. You run the desk.",
                Modifier.padding(horizontal = 24.dp),
                Theme.body.copy(color = colors.dim, textAlign = TextAlign.Center),
            )
            Spacer(Modifier.height(36.dp))
            PillButton(
                if (game.cleared.isEmpty()) "Press start" else "Continue",
                onStart,
                Modifier.alpha(glow),
                emphasis = Emphasis.Primary,
            )
            Spacer(Modifier.height(16.dp))
            Label("${stages.size} stages / no api key needed")
            Spacer(Modifier.height(20.dp))
            PillButton("Bonus: Kitchen rush", onKitchen)
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TelarLink("built with Telar")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (roomForModel) Label("model $modelLabel")
                PillButton("About", onAbout, emphasis = Emphasis.Quiet)
                PillButton("Options", onOptions, emphasis = Emphasis.Quiet)
            }
        }
    }
}

/** The bar above every screen but the title: the way home, the slices won so far, and the ways to the about page and the options. */
@Composable
fun TopBar(game: Game, modelLabel: String, onTitle: () -> Unit, onAbout: () -> Unit, onOptions: () -> Unit) {
    val compact = LocalCompact.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 28.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClickLabel = "Title screen", role = Role.Button, onClick = onTitle),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelArt(Sprites.miniSlice, pixel = 2.dp)
            if (!compact) DotMatrix(TITLE, pitch = 2.dp)
        }
        Spacer(Modifier.weight(1f))
        SliceMeter(game.cleared)
        if (!compact) Label("model $modelLabel")
        // The two are text only, so they stand closer than the other parts of the bar.
        Row {
            PillButton("About", onAbout, emphasis = Emphasis.Quiet)
            PillButton("Options", onOptions, emphasis = Emphasis.Quiet)
        }
    }
}

/** One slice per stage; a slice is lit once its stage is cleared. */
@Composable
private fun SliceMeter(cleared: Set<Int>) {
    Row(
        Modifier.semantics { contentDescription = "${cleared.size} of ${stages.size} stages cleared" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        stages.forEach { stage ->
            PixelArt(Sprites.miniSlice, pixel = 2.dp, tint = if (stage.number in cleared) Color.Unspecified else Theme.colors.line)
        }
    }
}

@Composable
fun StageSelectScreen(game: Game, onKitchen: () -> Unit, onPlay: (Int) -> Unit) {
    val colors = Theme.colors
    val allClear = game.cleared.size == stages.size
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Label(if (allClear) "all clear / the pizza is whole" else "${game.cleared.size} of ${stages.size} cleared", color = colors.red)
        DotMatrix("Select stage", pitch = if (LocalCompact.current) 3.dp else 5.dp)
        Body(
            "Every stage is a help desk that knows one move more than the last. They follow the levels of the " +
                "Telar tutorial. Play them in order, or jump to the move you came for.",
            Modifier.widthIn(max = 720.dp),
            color = colors.dim,
        )
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 900.dp -> 4
            maxWidth >= 520.dp -> 2
            else -> 1
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            game.controllers.chunked(columns).forEach { row ->
                // Cards in a row are as tall as the tallest of them.
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { controller ->
                        StageCard(controller, controller.stage.number in game.cleared, Modifier.weight(1f).fillMaxHeight()) {
                            onPlay(controller.stage.number)
                        }
                    }
                    // Keep the cards of a short last row as wide as the others.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    Panel("Bonus stage", Modifier.fillMaxWidth(), edge = colors.yellow) {
        Body(
            "Kitchen rush: a pizzeria whose chefs are graphs works through one shift of twelve orders. You build the kitchen, " +
                "and with a real model under Options you see what each kitchen costs in tokens.",
        )
        PillButton("Kitchen rush", onKitchen)
    }

    HowToPlay()
}

@Composable
private fun HowToPlay() {
    Panel("How to play", Modifier.fillMaxWidth()) {
        Spec("01", "Pick a ticket from the inbox, or write a message of your own.")
        Spec("02", "Press RUN and watch the ticket cross the board. A red tile is a node at work.")
        Spec("03", "When the run stops at a save point, decide. When it fails, retry.")
        Spec("04", "A reply that reaches END clears the stage and earns a slice.")
        Spec(
            "05",
            "Stages ${stages.filter { it.usesModel }.joinToString(" and ") { it.number.toString() }} ask a model. Its answers are " +
                "scripted too, until you choose a real model under Options. The other stages are scripted only.",
        )
    }
}

private const val SCRIPTED_ONLY = "scripted only"
private const val SCRIPTED_OR_MODEL = "scripted or a model"

@Composable
private fun StageCard(controller: StageController, cleared: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = Theme.colors
    val stage = controller.stage
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val saved = controller.phase == Phase.SavePoint || controller.phase == Phase.Unfinished

    Column(
        modifier
            .border(1.dp, if (hovered || focused) colors.ink else colors.line, PanelShape)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            DotMatrix(stage.number.toString().padStart(2, '0'), pitch = 4.dp, color = if (cleared) colors.ink else colors.dim)
            when {
                saved -> Label("saved", color = colors.yellow)
                else -> PixelArt(Sprites.miniSlice, pixel = 3.dp, tint = if (cleared) Color.Unspecified else colors.line)
            }
        }
        DotMatrix(stage.title, pitch = 2.5.dp)
        Body(stage.moves, color = colors.dim)
        // Only a stage that asks a model can get its answers from a real one.
        Label(if (stage.usesModel) SCRIPTED_OR_MODEL else SCRIPTED_ONLY, color = if (stage.usesModel) colors.ink else colors.dim)
    }
}

/** The Claude models, cheapest first, with what a million tokens cost on each. */
@Composable
private fun ModelPicker(selected: ClaudeModel, onSelect: (ClaudeModel) -> Unit) {
    Column {
        Label("Claude model, cheapest first")
        ClaudeModels.all.forEach { model ->
            Choice(
                model == selected,
                model.name,
                "$${model.inputPrice} in / $${model.outputPrice} out per million tokens",
                { onSelect(model) },
            )
        }
        Body("List prices in US dollars, September 2026.", color = Theme.colors.dim)
    }
}

/** What a service is, in a sentence under its name. */
private val ModelMode.detail: String
    get() = when (this) {
        ModelMode.Scripted -> "Fixed answers. Works offline and needs no key."
        ModelMode.Claude -> "Real answers for the stages that ask a model. Needs your Anthropic API key."
        ModelMode.OpenAi -> "The same, from a model of OpenAI. Needs your OpenAI API key."
        ModelMode.Gemini -> "The same, from a model of Google. Needs your Gemini API key."
        ModelMode.Other -> "Any other server with the API of OpenAI: Ollama on this device, Groq, Mistral, OpenRouter."
    }

/** What the game is for, and the way to the library it is built with. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val colors = Theme.colors
    DotMatrix("About", pitch = if (LocalCompact.current) 3.dp else 5.dp)

    Panel("The game", Modifier.fillMaxWidth()) {
        Body(
            "Pixel Pizza shows what an AI agent workflow is, one move at a time. Each of its ${stages.size} stages is a small help " +
                "desk that really runs, on this device: it reads a ticket, picks a path, asks two places at once, waits for " +
                "your decision, and starts again where it failed.",
        )
        Body("You watch every step on the board, and a press on a tile shows the code of its node.", color = colors.dim)
    }

    Panel("The library", Modifier.fillMaxWidth()) {
        Body(
            "The game is built with Telar, an open-source Kotlin library for AI agents and workflows. Every stage is a Telar " +
                "graph, and the board, the ticket and the run log are drawn from the events of its run.",
        )
        Body("Telar runs on the JVM, Android, iOS and the desktop, and in the browser.", color = colors.dim)
        TelarLink("github.com/deeptelar/telar")
    }

    // A link that is text only, so it is moved out by its padding to line up with the page.
    PillButton("< All stages", onBack, Modifier.offset(x = (-10).dp), emphasis = Emphasis.Quiet)
}

@Composable
fun OptionsScreen(settings: Settings, game: Game, onSave: (Settings) -> Unit, onBack: () -> Unit) {
    val colors = Theme.colors
    var draft by remember(settings) { mutableStateOf(settings) }

    DotMatrix("Options", pitch = if (LocalCompact.current) 3.dp else 5.dp)

    Panel("Model", Modifier.fillMaxWidth()) {
        ModelMode.entries.forEach { mode ->
            Choice(draft.mode == mode, mode.title, mode.detail, { draft = draft.copy(mode = mode) })
        }
        if (draft.mode != ModelMode.Scripted) {
            if (draft.mode == ModelMode.Other) {
                Field(draft.address, { draft = draft.copy(address = it) }, label = "Address, up to and including the version")
                Body("Ollama answers a web page only when it was started with OLLAMA_ORIGINS set to the address of the page.", color = colors.dim)
            }
            Field(draft.apiKey, { draft = draft.withKey(it) }, label = draft.mode.keyLabel, secret = true)
            if (draft.mode == ModelMode.Claude) {
                ModelPicker(ClaudeModels.byId(draft.modelId)) { draft = draft.withModel(it.id) }
            } else {
                Field(draft.modelId, { draft = draft.withModel(it) }, label = "Model, one that can call tools")
            }
            Choice(
                draft.rememberKey,
                "Remember the key on this device",
                "The key goes only to ${draft.host.ifEmpty { "the address above" }}, straight from this app. Unticked, it is kept in " +
                    "memory and forgotten when you close the page. Requests are billed to your account.",
                { draft = draft.copy(rememberKey = !draft.rememberKey) },
                role = Role.Checkbox,
            )
            draft.missing?.let { Body("Without $it the game keeps using the scripted model.", color = colors.yellow) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Save", { onSave(draft) }, emphasis = Emphasis.Primary, enabled = draft != settings)
            PillButton("Back", onBack, emphasis = Emphasis.Quiet)
        }
    }

    Panel("Save data", Modifier.fillMaxWidth()) {
        Body(
            "${game.cleared.size} of ${stages.size} stages cleared. Erasing also throws away every run that is waiting at a save point.",
            color = colors.dim,
        )
        Box { PillButton("Erase progress", game::erase, enabled = game.cleared.isNotEmpty() || game.controllers.any { it.phase != Phase.Ready }) }
    }
}
