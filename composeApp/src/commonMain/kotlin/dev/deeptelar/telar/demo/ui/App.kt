package dev.deeptelar.telar.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch
import dev.deeptelar.telar.demo.game.stages
import dev.deeptelar.telar.demo.kitchen.Guide
import dev.deeptelar.telar.demo.storage.platformCheckpointer
import dev.deeptelar.telar.demo.storage.platformStore

/** Where the player is. */
sealed interface Screen {
    data object Title : Screen

    data object Stages : Screen

    data class Play(val stage: Int) : Screen

    /** The bonus stage. */
    data object Kitchen : Screen

    data object About : Screen

    data object Options : Screen
}

/** True when the window is too narrow for two columns. */
val LocalCompact = staticCompositionLocalOf { false }

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val store = remember { platformStore() }
    val saves = remember { platformCheckpointer() }
    val repository = remember { SettingsRepository(store) }
    val httpClient = remember { HttpClient() }
    var loaded by remember { mutableStateOf<Settings?>(null) }
    // A link to a board of the bonus stage opens that stage, with the board drawn.
    val linked = remember { KitchenLink.of(pageFragment()) }
    var screen by remember { mutableStateOf(if (linked == null) Screen.Title else Screen.Kitchen) }
    val played = remember { mutableStateOf(emptyList<PlayedShift>()) }
    val drawn = remember { mutableStateOf(linked?.board ?: Guide.FIRST_ORDERS.start) }
    val chosen = remember { mutableStateOf(linked?.rush ?: Rush.entries.first()) }
    LaunchedEffect(Unit) { loaded = repository.load() }

    PizzaTheme {
        val settings = loaded ?: return@PizzaTheme
        // New settings mean a new model, and with it fresh graphs. Cleared stages and saved runs are kept.
        val model = remember(settings) { settings.chatModel(httpClient) }
        val game = remember(model) {
            Game(model, store, saves, scope, realModel = model.takeIf { settings.usesService }, modelLabel = settings.modelLabel, played = played, drawn = drawn, chosen = chosen)
        }
        GameScreens(game, settings, screen, onNavigate = { screen = it }) { updated ->
            loaded = updated
            scope.launch { repository.save(updated) }
        }
    }
}

/** Draws [screen]. Kept apart from [App] so that a screen can be shown without storage or a network client. */
@Composable
fun GameScreens(game: Game, settings: Settings, screen: Screen, onNavigate: (Screen) -> Unit, onSave: (Settings) -> Unit) {
    if (screen == Screen.Title) {
        TitleScreen(
            game,
            settings.modelLabel,
            onStart = { onNavigate(Screen.Stages) },
            onKitchen = { onNavigate(Screen.Kitchen) },
            onAbout = { onNavigate(Screen.About) },
            onOptions = { onNavigate(Screen.Options) },
        )
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalCompact provides (maxWidth < 760.dp)) {
            Column(Modifier.fillMaxSize()) {
                TopBar(
                    game,
                    settings.modelLabel,
                    onTitle = { onNavigate(Screen.Title) },
                    onAbout = { onNavigate(Screen.About) },
                    onOptions = { onNavigate(Screen.Options) },
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.colors.line))
                // Keyed on the screen, so each one starts scrolled to its top.
                key(screen) {
                    val scroll = rememberScrollState()
                    Box(Modifier.fillMaxSize().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
                        Column(
                            Modifier
                                .widthIn(max = 1080.dp)
                                .fillMaxWidth()
                                .padding(horizontal = if (LocalCompact.current) 16.dp else 28.dp, vertical = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            when (screen) {
                                Screen.Title -> Unit
                                Screen.Stages -> StageSelectScreen(game, onKitchen = { onNavigate(Screen.Kitchen) }) { onNavigate(Screen.Play(it)) }
                                Screen.Kitchen -> KitchenScreen(game.kitchen, scroll) { onNavigate(Screen.Stages) }
                                is Screen.Play -> StageScreen(
                                    game.controller(screen.stage),
                                    realModel = settings.modelLabel.takeIf { settings.usesService },
                                    onStages = { onNavigate(Screen.Stages) },
                                    onNext = if (screen.stage < stages.size) ({ onNavigate(Screen.Play(screen.stage + 1)) }) else null,
                                )
                                Screen.About -> AboutScreen { onNavigate(Screen.Stages) }
                                Screen.Options -> OptionsScreen(settings, game, onSave) { onNavigate(Screen.Stages) }
                            }
                        }
                    }
                }
            }
        }
    }
}
