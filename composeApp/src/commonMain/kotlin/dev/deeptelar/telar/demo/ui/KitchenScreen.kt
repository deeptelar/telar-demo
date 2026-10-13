package dev.deeptelar.telar.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.deeptelar.telar.demo.kitchen.Blueprint
import dev.deeptelar.telar.demo.kitchen.BoardShift
import dev.deeptelar.telar.demo.kitchen.Desk
import dev.deeptelar.telar.demo.kitchen.Exit
import dev.deeptelar.telar.demo.kitchen.Kitchen
import dev.deeptelar.telar.demo.kitchen.MENU
import dev.deeptelar.telar.demo.kitchen.PieceKind
import dev.deeptelar.telar.demo.kitchen.Order
import dev.deeptelar.telar.demo.kitchen.Outcome
import dev.deeptelar.telar.demo.kitchen.Pizza
import dev.deeptelar.telar.demo.kitchen.Place
import dev.deeptelar.telar.demo.kitchen.Score
import dev.deeptelar.telar.demo.kitchen.Service
import dev.deeptelar.telar.demo.kitchen.Touch
import dev.deeptelar.telar.demo.kitchen.Way
import dev.deeptelar.telar.demo.kitchen.arrow
import dev.deeptelar.telar.demo.kitchen.called
import dev.deeptelar.telar.demo.kitchen.codeOf
import dev.deeptelar.telar.demo.kitchen.heard
import dev.deeptelar.telar.demo.kitchen.report
import kotlinx.coroutines.flow.drop

/**
 * The bonus stage: one shift of a pizzeria. The player draws how an order gets from the rail to the
 * kitchen, as stations and arrows, and the shift is measured on the orders it serves and the tokens
 * its models use.
 */
@Composable
fun KitchenScreen(controller: KitchenController, scroll: ScrollState? = null, onStages: () -> Unit) {
    val compact = LocalCompact.current
    if (!controller.inShift) {
        ShiftSelect(controller, onStages)
        return
    }
    val rush = controller.rush
    // A shift of the other orders is not shown while the player plans this one. It stays under "Your shifts".
    val shift = controller.shift?.takeIf { controller.running || controller.playingRush == rush }
    // Where the head of the page ends. On a narrow screen the first panel of every phase begins under it.
    var headEnd by remember { mutableStateOf(0f) }

    // The shifts are a page of their own, like the stages, so a shift has the screen to itself.
    Column(Modifier.onGloballyPositioned { headEnd = it.positionInParent().y + it.size.height }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("< All shifts", controller::leave, Modifier.offset(x = (-10).dp), emphasis = Emphasis.Quiet, enabled = !controller.running)
        Label("kitchen rush / shift ${rush.number} of ${Rush.entries.size}", color = Theme.colors.red)
        DotMatrix(rush.title, pitch = if (compact) 3.dp else 5.dp)
    }

    if (compact) {
        Narrow(controller, shift, scroll) { headEnd }
        return
    }
    // A short shift has nothing beside the board until it was played.
    if (shift == null && rush.short) {
        Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Builder(controller, Modifier.fillMaxWidth())
            Shifts(controller, Modifier.fillMaxWidth())
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Builder(controller, Modifier.fillMaxWidth())
            if (!rush.short) Goals(controller, Modifier.fillMaxWidth())
            Shifts(controller, Modifier.fillMaxWidth())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (shift != null) ShiftPanels(controller, shift) else OrdersAhead(controller)
        }
    }
}

/**
 * The stage on a narrow screen, where the panels are one under the other. What matters at the moment
 * comes first and fits the screen: the board while the player draws, the shift while it runs, and
 * its result when it is over. The page moves there when a shift starts and when it ends.
 *
 * @param headEnd where the head of the page ends.
 */
@Composable
private fun Narrow(controller: KitchenController, shift: BoardShift?, scroll: ScrollState?, headEnd: () -> Float) {
    val gap = with(LocalDensity.current) { PAGE_LEAD.toPx() }
    if (scroll != null) {
        LaunchedEffect(scroll, controller) {
            snapshotFlow { controller.running }.drop(1).collect { scroll.animateScrollTo((headEnd() + gap).toInt()) }
        }
    }

    if (shift != null && controller.running) {
        Watch(controller, shift)
        Rail(controller, shift)
        Log(shift)
        Stations(controller, shift)
        return
    }
    if (shift != null) {
        controller.error?.let { error ->
            Panel("The shift stopped", Modifier.fillMaxWidth(), edge = Theme.colors.red) { Body(error, color = Theme.colors.red) }
        }
        if (controller.finished) Result(controller, shift) else Meters(controller, shift)
    }
    Builder(controller, Modifier.fillMaxWidth())
    // A short shift is about its board. Its orders are shown with what became of them, and its medals with its result.
    val short = controller.rush.short
    if (shift != null) Rail(controller, shift) else if (!short) OrdersAhead(controller)
    if (!short) Goals(controller, Modifier.fillMaxWidth())
    if (shift != null) {
        Floor(shift.kitchen, running = false, ovens = if (controller.playing.hasSecondOven) 2 else 1)
        Log(shift)
        Stations(controller, shift)
    }
    Shifts(controller, Modifier.fillMaxWidth())
}

/** How far under the head of a narrow page the page is moved to: past the gap between two panels, less a margin above the panel. */
private val PAGE_LEAD = 36.dp

/**
 * A running shift on a narrow screen, in one panel: the clock, the board with the order that is on
 * it, what the kitchen did last, the ovens, and how every order stands.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Watch(controller: KitchenController, shift: BoardShift) {
    val colors = Theme.colors
    val kitchen = shift.kitchen
    val score = kitchen.score()
    val orders = kitchen.orders.size - score.cancelled
    val ovens = if (controller.playing.hasSecondOven) 2 else 1
    Panel("The shift / ${controller.playingRush.title}", Modifier.fillMaxWidth(), trailing = { Label("running", color = colors.red) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Meter("clock", "minute ${kitchen.minute} of ${kitchen.closesAt}", kitchen.minute.toFloat() / kitchen.closesAt, colors.ink)
                // The two wrap where a long count of tokens leaves no room for both in a line.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Label("${score.points} of ${orders * Score.ON_TIME_POINTS} points", color = colors.yellow)
                    Label("${shift.spent.grouped()} tokens", color = colors.ink)
                }
            }
            PillButton("Stop", controller::stop)
        }
        KitchenBoard(controller, Modifier.fillMaxWidth())
        Now(shift)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(ovens) { oven -> PixelOven(kitchen, lit = true, Modifier.weight(1f), oven, small = true) }
        }
        Label("counter: ${kitchen.made.count { it.place == Place.COUNTER }} raw / pass: ${kitchen.made.count { it.place == Place.PASS }} baked")
        OrderChips(controller, shift)
    }
}

/** What the kitchen did last, in the two lines it always takes, so that a look says what is going on. */
@Composable
private fun Now(shift: Service) {
    val colors = Theme.colors
    // A station logs what it does with the minute it is done, which can be ahead of the clock.
    val last = shift.log.filter { it.minute <= shift.kitchen.minute }.sortedBy { it.minute }.lastOrNull()
    Row(
        Modifier.fillMaxWidth().height(58.dp).semantics(mergeDescendants = true) { contentDescription = "Now: ${last?.text ?: "nothing yet"}" },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Who did it, as the picture its tile has on the board.
        PixelArt(last?.let { stationSprite(it.station) } ?: Sprites.chefHat, Modifier.padding(top = 2.dp), pixel = 2.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(if (last == null) "the kitchen is open" else "minute ${last.minute} / ${last.station}", color = if (last?.refused == true) colors.red else colors.ink)
            BasicText(
                last?.text ?: "No order has reached a station yet.",
                style = Theme.body.copy(color = if (last?.refused == true) colors.red else colors.dim),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val CHIPS_IN_A_ROW = 4
private val CHIP_GAP = 6.dp

/** Every order of the shift as a small tile with a word for how it stands, which is how a narrow screen shows them all at once. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderChips(controller: KitchenController, shift: BoardShift) {
    val colors = Theme.colors
    val minute = shift.kitchen.minute
    val shape = RoundedCornerShape(4.dp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = (maxWidth - CHIP_GAP * (CHIPS_IN_A_ROW - 1)) / CHIPS_IN_A_ROW
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CHIP_GAP), verticalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
            shift.kitchen.standing.forEach { order ->
                val onBoard = controller.reading == order.number
                val (word, color) = if (onBoard) "reading" to colors.onRed else order.standing(minute, order.number in shift.tickets, colors)
                val here = order.placedAt <= minute
                Column(
                    Modifier
                        .width(width)
                        .background(if (onBoard) colors.red else colors.ground, shape)
                        .border(1.dp, if (onBoard) colors.red else if (here && order.outcome == Outcome.OPEN) colors.dim else colors.line, shape)
                        .semantics(mergeDescendants = true) { contentDescription = "Order ${order.number}: $word" }
                        .padding(horizontal = 4.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Label("#${order.number}", color = if (onBoard) colors.onRed else if (here) colors.ink else colors.dim)
                    Label(word, color = color)
                }
            }
        }
    }
}

/** How an order stands at [minute], in a word short enough for a tile. [ticket] says whether the kitchen has one for it. */
private fun Order.standing(minute: Int, ticket: Boolean, colors: Palette): Pair<String, Color> =
    when {
        placedAt > minute -> "min $placedAt" to colors.dim
        outcome == Outcome.OPEN && minute > dueAt -> "overdue" to colors.red
        outcome == Outcome.OPEN -> if (ticket) "cooking" to colors.ink else "waiting" to colors.dim
        outcome == Outcome.ON_TIME -> "on time" to colors.ink
        outcome == Outcome.LATE -> "late" to colors.yellow
        outcome == Outcome.WRONG -> "wrong" to colors.red
        outcome == Outcome.ALLERGY -> "allergen" to colors.red
        else -> "cancelled" to colors.dim
    }

/**
 * What puts a text on the clipboard. `null` stands for the clipboard of the platform, and a test
 * gives its own to see what was copied.
 */
val LocalCopyText = staticCompositionLocalOf<((String) -> Unit)?> { null }

/** A button that copies what [text] returns, and says that it did until the text is another one. */
@Composable
private fun CopyButton(label: String, text: () -> String, modifier: Modifier = Modifier) {
    // The new clipboard of Compose has no way to make an entry from a text that is the same on every platform.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val copy = LocalCopyText.current
    var copied by remember { mutableStateOf<String?>(null) }
    val now = text()
    PillButton(
        if (copied == now) "Copied" else label,
        {
            // A browser may refuse the clipboard. The game goes on without it.
            try {
                @Suppress("DEPRECATION")
                if (copy != null) copy(now) else clipboard.setText(AnnotatedString(now))
                copied = now
            } catch (_: Exception) {
                copied = null
            }
        },
        modifier,
        emphasis = Emphasis.Quiet,
    )
}

/**
 * The shifts of the stage, in the order they are meant to be played: a card for each, with the
 * station it brings and its medals. None is locked, so that a link to a board of a later shift
 * opens for everyone. Pressing a card goes into its shift, and the list is put away.
 */
@Composable
private fun ShiftSelect(controller: KitchenController, onStages: () -> Unit) {
    val compact = LocalCompact.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("< All stages", onStages, Modifier.offset(x = (-10).dp), emphasis = Emphasis.Quiet)
        Label("bonus stage / nine shifts", color = Theme.colors.red)
        DotMatrix("Kitchen rush", pitch = if (compact) 3.dp else 5.dp)
        Body(
            if (compact) {
                "Draw the way an order takes from the rail to the kitchen: stations, and arrows between them."
            } else {
                "Orders come in until the kitchen closes, and the kitchen makes exactly what the ticket says. You draw how an order becomes " +
                    "a ticket and reaches the oven: stations, and arrows between them. Every order is one run of the graph on your board."
            },
            Modifier.widthIn(max = 720.dp),
            color = Theme.colors.dim,
        )
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns =
            when {
                maxWidth >= 900.dp -> 3
                maxWidth >= 520.dp -> 2
                else -> 1
            }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Rush.entries.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { rush -> ShiftCard(controller, rush, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A shift in the list: its number and name, the station it brings, and its medals. The one that was played last has a bright edge. */
@Composable
private fun ShiftCard(controller: KitchenController, rush: Rush, modifier: Modifier = Modifier) {
    val colors = Theme.colors
    val shape = RoundedCornerShape(4.dp)
    val last = controller.rush == rush
    val medals = controller.medals(rush)
    Row(
        modifier
            .background(colors.ground, shape)
            .border(if (last) 2.dp else 1.dp, if (last) colors.ink else colors.line, shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics(mergeDescendants = true) {
                contentDescription = "Shift ${rush.number}: ${rush.title}, ${medals.count { it.second }} of ${medals.size} medals" + if (last) ", chosen" else ""
            }
            .clickable(role = Role.Button) { controller.enter(rush) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A rush brings no station: it has them all, and the kitchen's hat stands for that.
        PixelArt(rush.teaches?.sprite ?: Sprites.chefHat, pixel = 2.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(rush.number.toString(), color = colors.red)
                Label(rush.title, color = colors.ink)
            }
            // The name of a sorter is long, and takes a second line where the card is narrow.
            BasicText((rush.teaches?.let { "new: ${it.title}" } ?: "every station").uppercase(), style = Theme.label.copy(color = colors.dim))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            medals.forEach { (_, won) -> PixelArt(Sprites.medal, pixel = 2.dp, tint = if (won) Color.Unspecified else colors.line) }
        }
    }
}

/** Where the player draws the board and starts the shift. */
@Composable
private fun Builder(controller: KitchenController, modifier: Modifier = Modifier) {
    val colors = Theme.colors
    val compact = LocalCompact.current

    @Composable
    fun code() {
        PillButton(if (controller.showCode) "Hide the code" else "Show the code", { controller.showCode = !controller.showCode }, Modifier.offset(x = (-10).dp), emphasis = Emphasis.Quiet)
        if (controller.showCode) {
            // The code behind the tile that is selected, as a stage shows the code of the tile that was pressed. Without one, the whole graph.
            val board = controller.board
            val tile = controller.selected?.takeIf { !controller.running }
            val what = if (tile == null) "the board" else board.called(tile)
            val parts = remember(board, tile) { board.codeOf(tile) }
            Column(Modifier.semantics { contentDescription = "The code of $what" }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("code of $what", color = colors.ink)
                parts.forEach { part ->
                    if (tile != null) Label(part.title)
                    // On a phone a long line goes on in the next one: what a node calls is at the end of its line.
                    Code(part.code, wrap = compact)
                }
            }
            if (tile == null) Body("This is the whole graph. Press a tile to see the code behind it.", color = colors.dim)
        }
    }

    @Composable
    fun model() {
        if (controller.scripted) {
            Spec("model", "scripted. No model is asked, and the tokens are a guess. Choose a real model under Options.")
        } else {
            Spec("model", "${controller.modelLabel}. A shift uses your key: a reader asks the model once for each order it gets.")
            Field(
                controller.budget.toString(),
                { text -> text.filter(Char::isDigit).take(7).toIntOrNull()?.let(controller::budget) },
                "Token budget. The shift stops when it is spent.",
                enabled = !controller.running,
            )
        }
        if (controller.problems.isEmpty()) {
            val page = remember { pageAddress() ?: DEMO_ADDRESS }
            CopyButton("Copy a link to this board", { KitchenLink(controller.rush, controller.board).address(page) }, Modifier.offset(x = (-10).dp))
        }
    }

    @Composable
    fun buttons() {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (controller.running) {
                PillButton("Stop", controller::stop)
            } else {
                PillButton(
                    // "New" only after a shift of these orders: one of another shift is not shown here.
                    if (controller.shift == null || controller.playingRush != controller.rush) "Start the shift" else "Start a new shift",
                    controller::start,
                    Modifier.beckon(controller.step?.touch == Touch.Start),
                    emphasis = Emphasis.Primary,
                    enabled = controller.problems.isEmpty(),
                )
                // A shift that shows its steps goes back to the board it opened with, which is what its steps start from.
                val opening = controller.rush.guide?.start?.takeIf { controller.rush.guided }
                if (opening == null) {
                    PillButton("Clear the board", { controller.load(Blueprint()) }, emphasis = Emphasis.Quiet)
                } else {
                    PillButton("Start over", { controller.load(opening) }, emphasis = Emphasis.Quiet)
                }
            }
        }
    }

    Panel("Draw the kitchen", modifier, trailing = { Label("${controller.board.pieces.size} of ${Blueprint.SLOTS} places") }) {
        val rush = controller.rush
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            rush.teaches?.let { PixelArt(it.sprite, pixel = 2.dp) }
            Label(
                // A narrow screen has no room for more than the name of a sorter.
                rush.teaches?.let { (if (compact) "new: " else "new station: ") + it.title } ?: "every station",
                color = colors.red,
            )
        }
        Body(rush.detail, color = colors.dim)
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        NextStep(controller)
        KitchenBoard(controller, Modifier.fillMaxWidth())
        Hand(controller)
        // A step says what to do about a board that cannot run yet. Without one, the board says what is wrong with it.
        // An empty board has nothing wrong with it yet: the line above says where to begin.
        if (controller.step == null && controller.board.pieces.isNotEmpty()) controller.problems.forEach { problem -> Body(problem, color = colors.red) }

        if (compact) {
            // On a narrow screen the button that starts the shift is under the board, before what is seldom needed.
            buttons()
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
            model()
            code()
        } else {
            code()
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
            model()
            buttons()
        }
    }
}

/**
 * The step of a short shift, above the board it is about: what to press next, and how many steps
 * there are. A short shift that does not show its steps by itself has a button here that asks for them.
 */
@Composable
private fun NextStep(controller: KitchenController) {
    val colors = Theme.colors
    val step = controller.step
    val guide = controller.rush.guide
    if (step == null || guide == null) {
        if (controller.canHint) Box { PillButton("Show me the next step", { controller.hint(true) }) }
        return
    }
    val count =
        when {
            // The steps of the lesson are counted as long as the player is on them.
            controller.rush.guided && !controller.hinting && step.left <= guide.steps -> "step ${guide.steps - step.left + 1} of ${guide.steps}"
            step.left == 1 -> "last step"
            else -> "next step / ${step.left - 1} more"
        }
    // A step takes two lines on a narrow screen. With room for both, the board under it stays where it is from step to step.
    Column(
        Modifier.fillMaxWidth().heightIn(min = 66.dp).semantics(mergeDescendants = true) { contentDescription = "Step: ${step.text}" },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Label(count, color = colors.yellow)
            if (controller.hinting) Label("hide the steps", Modifier.clickable(role = Role.Button) { controller.hint(false) })
        }
        Body(step.text)
    }
}

/** The menu of the pizzeria: each pizza with its toppings. It is shown with a station that looks at it. */
@Composable
private fun MenuCard() {
    val colors = Theme.colors
    Column(Modifier.semantics { contentDescription = "The menu" }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Label("the menu")
        MENU.forEach { (pizza, toppings) ->
            val line =
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.ink)) { append(pizza) }
                    append(": ${toppings.joinToString { it.word }}")
                }
            BasicText(line, style = Theme.body.copy(color = colors.dim))
        }
    }
}

/** What the player can do next on the board: choose a station for a place, or draw the arrows of the tile that is selected. */
@Composable
private fun Hand(controller: KitchenController) {
    val colors = Theme.colors
    val board = controller.board
    val picking = controller.picking
    val selected = controller.selected
    val touch = controller.step?.touch
    when {
        controller.running -> Body("The board cannot be changed while a shift runs.", color = colors.dim)
        picking != null -> {
            Label("a station for place ${picking + 1}", color = colors.ink)
            // A short shift offers the stations up to the one it teaches. A station that a board can have only once is not offered again.
            val teaches = controller.rush.teaches
            controller.rush.offers.filterNot { kind -> kind.once && board.pieces.any { it.kind == kind } }.forEach { kind ->
                val asked = (touch as? Touch.Choose)?.kind == kind
                Choice(
                    asked,
                    kind.title,
                    if (kind == teaches) "New in this shift. ${kind.job}" else kind.job,
                    onClick = { controller.put(kind) },
                    Modifier.beckon(asked, corner = 4.dp),
                    role = Role.Button,
                ) {
                    PixelArt(kind.sprite, pixel = 2.dp)
                }
            }
        }
        selected != null -> {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                board.sprite(selected)?.let { PixelArt(it, pixel = 2.dp) }
                Label(board.title(selected), color = colors.ink)
            }
            Body(board.job(selected), color = colors.dim)
            if (board.piece(selected)?.kind?.readsMenu == true) MenuCard()
            board.exits(selected).forEach { exit ->
                val target = board.target(selected, exit)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val asked = Modifier.beckon((touch as? Touch.Redraw)?.exit == exit)
                    Label(exit.arrow, Modifier.width(88.dp))
                    when {
                        controller.armed == exit -> Body("press the tile it goes to", color = colors.yellow)
                        target != null -> {
                            // By the word on its tile, which is short enough to leave room for the button on a narrow screen.
                            Body(board.called(target))
                            PillButton("Change", { controller.redraw(exit) }, asked, emphasis = Emphasis.Quiet)
                        }
                        else -> PillButton("Draw", { controller.redraw(exit) }, asked, emphasis = Emphasis.Quiet)
                    }
                }
            }
            if (board.piece(selected) != null) Box { PillButton("Remove the station", controller::remove, Modifier.beckon(touch == Touch.Remove)) }
        }
        // A step says what to press next.
        touch != null -> Unit
        // Nothing is selected: what to press next, as far as the board has got.
        board.pieces.isEmpty() -> Body("Press a + to put a station on the board.")
        board.target(Blueprint.RAIL, Exit.NEXT) == null -> Body("Press the rail, and then the station that gets every order first.")
        controller.problems.isNotEmpty() -> Body("Press a station, and then the tile its arrow goes to.")
        else -> Body("This board can run. Press a station to change its arrows, or a + to add a station.", color = colors.dim)
    }
}

/** The medals of the chosen shift: what each takes, and the best the shifts of this session did on it. */
@Composable
private fun Goals(controller: KitchenController, modifier: Modifier = Modifier) {
    val colors = Theme.colors
    val goals = controller.goals
    Panel("Medals / ${controller.rush.title}", modifier, trailing = { Label("${goals.count { it.second }} of ${goals.size}") }) {
        goals.forEach { (goal, met) ->
            Row(
                Modifier.semantics(mergeDescendants = true) { contentDescription = "${goal.text} ${if (met) "Reached" else "Open"}" },
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PixelArt(Sprites.medal, Modifier.padding(top = 2.dp), pixel = 3.dp, tint = if (met) Color.Unspecified else colors.line)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Label(goal.medal.title, color = if (met) colors.yellow else colors.dim)
                    Body(goal.text, color = if (met) colors.ink else colors.dim)
                    controller.best(goal.medal)?.let { best -> Label("your best: " + goal.medal.said(best, controller.rush)) }
                }
            }
        }
        if (goals.size == Medal.entries.size) {
            Body(
                "Cheap and Small pull against each other: a board that reads for few tokens sorts its orders, and sorting takes stations.",
                color = colors.dim,
            )
        }
        if (!controller.scripted && goals.any { it.first.medal == Medal.Cheap }) {
            Body("Cheap is for the scripted chefs, whose tokens are the same every time. A real model counts its own: compare your shifts.", color = colors.dim)
        }
    }
}

/** [best] in the words of what this medal measures. */
private fun Medal.said(best: Int, rush: Rush): String =
    when (this) {
        Medal.Right -> "$best of ${rush.points} points"
        Medal.Cheap -> "${best.grouped()} tokens"
        Medal.Small -> if (best == 1) "1 station" else "$best stations"
    }

/** The stations of [way] as the tiles of this board say them, from the rail to where the order ends. */
private fun Blueprint.said(way: Way): String =
    (listOf("rail") + way.stations.map { piece(it)?.kind?.tile ?: it } + if (way.arrives) "kitchen" else "stuck").joinToString(" > ")

/**
 * The orders of the chosen shift before it starts. They are the same every time, so nothing in a
 * shift is a surprise: under each order is the way it takes across the board as it is drawn now.
 */
@Composable
private fun OrdersAhead(controller: KitchenController) {
    val colors = Theme.colors
    val rush = controller.rush
    val board = controller.board
    val kitchen = remember(rush) { rush.orders() }
    val heard = remember(rush) { kitchen.heard() }
    // A narrow screen has no room for a second label beside the title of a panel.
    val compact = LocalCompact.current
    Panel("Orders / ${rush.title}", Modifier.fillMaxWidth(), trailing = { if (!compact) Label("known before the shift") }) {
        Body(
            "The kitchen is closed, and these are the orders it will get: the same every time. Under each is the way it takes across " +
                "the board as you have drawn it. Start a shift to see what the kitchen makes of them.",
            color = colors.dim,
        )
        kitchen.orders.forEach { order ->
            val first = board.way(order.text)
            Column(
                Modifier.semantics(mergeDescendants = true) { contentDescription = "Order ${order.number} goes ${board.said(first)}" },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Label("#${order.number} / table ${order.table} / minute ${order.placedAt} / due ${order.dueAt}", color = colors.ink)
                Body(order.text)
                WayLine("goes", board, first)
                kitchen.changes.filter { it.order == order.number }.forEach { change ->
                    Body("minute ${change.at}: ${change.text}", color = if (change.wanted == null) colors.dim else colors.ink)
                    // A change is read again, together with the order, and the words of both decide its way.
                    heard.firstOrNull { it.change && it.minute == change.at && it.slip.order == order.number }?.let { again ->
                        WayLine("goes again", board, board.way(again.slip.words))
                    }
                }
            }
        }
    }
}

@Composable
private fun WayLine(word: String, board: Blueprint, way: Way) {
    Body("$word: ${board.said(way)}", color = if (way.arrives) Theme.colors.dim else Theme.colors.red)
}

/** Everything about the shift that runs or just ended. */
@Composable
private fun ShiftPanels(controller: KitchenController, shift: BoardShift) {
    val colors = Theme.colors
    controller.error?.let { error ->
        Panel("The shift stopped", Modifier.fillMaxWidth(), edge = colors.red) { Body(error, color = colors.red) }
    }
    if (controller.finished) Result(controller, shift)
    Meters(controller, shift)
    Stations(controller, shift)
    Floor(shift.kitchen, controller.running, ovens = if (controller.playing.hasSecondOven) 2 else 1)
    Log(shift)
    Rail(controller, shift)
}

/** The two limits of a shift and what it earned: the clock, the tokens and the points. */
@Composable
private fun Meters(controller: KitchenController, shift: Service) {
    val colors = Theme.colors
    val kitchen = shift.kitchen
    val score = kitchen.score()
    val orders = kitchen.orders.size - score.cancelled
    Panel(
        "The shift",
        Modifier.fillMaxWidth(),
        trailing = { Label(if (controller.running) "running" else if (controller.finished) "over" else "stopped", color = if (controller.running) colors.red else colors.dim) },
    ) {
        Meter("clock", "minute ${kitchen.minute} of ${kitchen.closesAt}", kitchen.minute.toFloat() / kitchen.closesAt, colors.ink)
        Meter(
            "tokens",
            "${shift.spent.grouped()} of ${shift.tokenBudget.grouped()}",
            shift.spent.toFloat() / shift.tokenBudget,
            if (shift.spent > shift.tokenBudget * 0.8f) colors.red else colors.ink,
        )
        Meter("points", "${score.points} of ${orders * Score.ON_TIME_POINTS}", score.points.toFloat() / (orders * Score.ON_TIME_POINTS), colors.yellow)
    }
}

@Composable
private fun Meter(name: String, value: String, fraction: Float, color: Color) {
    Column(Modifier.semantics { contentDescription = "$name: $value" }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Label(name)
            Label(value, color = Theme.colors.ink)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).background(Theme.colors.line, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).background(color, RoundedCornerShape(3.dp)))
        }
    }
}

/** The stations of the board that ask a model, with what each cost, and the two behind the board that work by rules. */
@Composable
private fun Stations(controller: KitchenController, shift: BoardShift) {
    val colors = Theme.colors
    val asking = controller.playing.pieces.filter { it.kind.asks }
    val compact = LocalCompact.current
    Panel("Stations", Modifier.fillMaxWidth(), trailing = { if (!compact) Label("one run of the graph for each order") }) {
        asking.forEach { piece ->
            val desk = shift.stations[piece.id] ?: Desk()
            StationSpec(piece.kind.sprite, piece.kind.tile, "${(desk.tokens.inputTokens + desk.tokens.outputTokens).grouped()} tokens in ${desk.answers} answers")
        }
        if (asking.isEmpty()) Spec("board", "No station of this board asks a model.", colors.dim)
        StationSpec(Sprites.chefHat, "prep", "makes what the tickets say. Plain code, no tokens.", colors.dim)
        StationSpec(Sprites.oven, "oven", "bakes and serves. Plain code, no tokens.", colors.dim)
        if (controller.playing.hasSecondOven) StationSpec(Sprites.oven, "oven 2", "bakes and serves the pizzas that may go into it. Plain code, no tokens.", colors.dim)
    }
}

/** A station with the picture its tile has on the board, and what is said about it. */
@Composable
private fun StationSpec(sprite: Sprite, name: String, value: String, valueColor: Color = Theme.colors.ink) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PixelArt(sprite, pixel = 2.dp)
        Box(Modifier.weight(1f)) { Spec(name, value, valueColor) }
    }
}

/** The pizzas that are in the kitchen: raw on the counter, in the oven with its fire, and baked on the pass. */
@Composable
private fun Floor(kitchen: Kitchen, running: Boolean, ovens: Int) {
    val colors = Theme.colors
    val counter = kitchen.made.filter { it.place == Place.COUNTER }
    val pass = kitchen.made.filter { it.place == Place.PASS }
    Panel("Counter, oven and pass", Modifier.fillMaxWidth()) {
        Area("counter", if (counter.isEmpty()) "nothing" else "${counter.size} raw") {
            counter.forEach { PizzaRow(it, "raw", colors.dim) }
        }
        Area(if (ovens == 1) "oven" else "ovens", "${kitchen.oven.size} of ${ovens * Kitchen.OVEN_SLOTS} places") {
            repeat(ovens) { oven ->
                if (ovens > 1) Label("oven ${oven + 1}", Modifier.padding(start = 88.dp, top = 4.dp), color = colors.ink)
                PixelOven(kitchen, lit = running, Modifier.fillMaxWidth().padding(start = 88.dp, top = 4.dp), oven)
            }
            Body(
                "Baked after ${Kitchen.BAKED_AFTER} minutes, burnt after ${Kitchen.BURNT_AFTER}.",
                Modifier.padding(start = 88.dp, bottom = 4.dp),
                color = colors.dim,
            )
        }
        Area("pass", if (pass.isEmpty()) "nothing" else "${pass.size} out of the oven") {
            pass.forEach { PizzaRow(it, if (it.good) "baked" else "spoilt", if (it.good) colors.ink else colors.red) }
        }
    }
}

@Composable
private fun Area(name: String, summary: String, pizzas: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Label(name, Modifier.width(76.dp), color = Theme.colors.ink)
            Label(summary)
        }
        pizzas()
    }
}

@Composable
private fun PizzaRow(pizza: Pizza, state: String, stateColor: Color) {
    Row(Modifier.padding(start = 88.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        PixelArt(Sprites.pie, pixel = 2.dp, tint = if (pizza.baked == null && pizza.place == Place.COUNTER) Theme.colors.dim else Color.Unspecified)
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Label(pizza.name, color = Theme.colors.ink)
                Label(state, color = stateColor)
            }
            Body(pizza.toppings.joinToString { it.word }, color = Theme.colors.dim)
        }
    }
}

/**
 * The orders of the shift: those the kitchen has heard of, each with how it ended, the way it took
 * across the board and the ticket it got, and after them the ones that are still to come.
 */
@Composable
private fun Rail(controller: KitchenController, shift: BoardShift) {
    val colors = Theme.colors
    val kitchen = shift.kitchen
    val board = controller.playing
    val (heard, ahead) = kitchen.standing.partition { it.placedAt <= kitchen.minute }
    // Once the shift is over the player draws again, and an order that would go another way now says so.
    val redrawn = controller.board.takeIf { !controller.running && it != board }
    Panel("Orders", Modifier.fillMaxWidth(), trailing = { Label("${heard.size} of ${kitchen.orders.size} placed") }) {
        if (heard.isEmpty()) Body("No order yet.", color = colors.dim)
        heard.forEach { order ->
            val (status, color) = order.status(kitchen.minute, controller.finished, colors)
            val route = shift.routes[order.number]
            val ticket = shift.tickets[order.number]
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Label("#${order.number} / table ${order.table} / due ${order.dueAt}", color = colors.ink)
                    Label(status, color = color)
                }
                Body(order.text, color = if (order.outcome == Outcome.CANCELLED) colors.dim else colors.ink)
                if (route != null) Body("went: ${board.said(Way(route, arrives = true))}", color = colors.dim)
                when {
                    ticket != null -> Body("ticket: ${ticket.joinToString { it.word }}", color = colors.dim)
                    route != null && order.outcome != Outcome.CANCELLED && shift.written.none { it.order == order.number } ->
                        Body("no ticket, so the kitchen makes nothing for it", color = colors.red)
                }
                if (redrawn != null && route != null && order.outcome != Outcome.CANCELLED) {
                    val now = redrawn.way(order.text)
                    if (redrawn.said(now) != board.said(Way(route, arrives = true))) {
                        Body("on the board now: ${redrawn.said(now)}", color = if (now.arrives) colors.yellow else colors.red)
                    }
                }
            }
        }
        ahead.forEach { order ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Label("#${order.number} / table ${order.table} / due ${order.dueAt}")
                    Label("comes at minute ${order.placedAt}")
                }
                Body(order.text, color = colors.dim)
            }
        }
    }
}

/** How an order stands at [minute]. Once the shift is [over], an order that is still open was not served. */
private fun Order.status(minute: Int, over: Boolean, colors: Palette): Pair<String, Color> =
    when (outcome) {
        Outcome.OPEN ->
            when {
                over -> "not served" to colors.red
                minute > dueAt -> "overdue" to colors.red
                else -> "waiting" to colors.dim
            }
        Outcome.ON_TIME -> "served on time" to colors.ink
        Outcome.LATE -> "served late" to colors.yellow
        Outcome.WRONG -> "wrong pizza" to colors.red
        Outcome.ALLERGY -> "allergen served" to colors.red
        Outcome.CANCELLED -> "cancelled" to colors.dim
    }

/** What the stations did, the latest at the bottom. */
@Composable
private fun Log(shift: Service) {
    val colors = Theme.colors
    Panel("Kitchen log", Modifier.fillMaxWidth(), trailing = { Label("stream()") }) {
        if (shift.log.none { it.minute <= shift.kitchen.minute }) Body("Nothing has happened yet.", color = colors.dim)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // A station logs what it does with the minute it is done, which can be ahead of the clock.
            shift.log.filter { it.minute <= shift.kitchen.minute }.sortedBy { it.minute }.takeLast(LOG_LINES).forEach { event ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Label(event.minute.toString().padStart(2, '0'), Modifier.padding(top = 4.dp).width(22.dp))
                    Label(event.station, Modifier.padding(top = 4.dp).width(52.dp), color = if (event.refused) colors.red else colors.ink)
                    Body(event.text, Modifier.weight(1f), color = if (event.refused) colors.red else colors.dim)
                }
            }
        }
    }
}

private const val LOG_LINES = 8

/** How the shift went, in the lines that one shift is compared with another on. */
@Composable
private fun Result(controller: KitchenController, shift: BoardShift) {
    val rush = controller.playingRush
    val played = PlayedShift(rush, controller.playing, controller.modelLabel, controller.scripted, shift)
    // A narrow screen has no room for the board in the title. It is drawn under the result.
    val title = if (LocalCompact.current) "Shift over / ${rush.title}" else "Shift over / ${rush.title} / ${controller.playing.summary}"
    Panel(title, Modifier.fillMaxWidth().semantics { contentDescription = "Shift over" }, edge = Theme.colors.yellow) {
        // What each station cost is in the panel of the stations.
        shift.report().lines().filterNot { it.startsWith("stations") }.forEach { line -> Spec(line.substringBefore(' '), line.substringAfter(' ').trim()) }
        // The medals of the shift, and which of them this board won.
        rush.goals.forEach { goal ->
            val won = goal.metBy(played)
            Row(
                Modifier.semantics(mergeDescendants = true) { contentDescription = "${goal.medal.title} medal: ${if (won) "won" else "not won"}" },
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PixelArt(Sprites.medal, Modifier.padding(top = 2.dp), pixel = 3.dp, tint = if (won) Color.Unspecified else Theme.colors.line)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Label(if (won) "${goal.medal.title} / won" else "${goal.medal.title} / not yet", color = if (won) Theme.colors.yellow else Theme.colors.dim)
                    Body(goal.text, color = if (won) Theme.colors.ink else Theme.colors.dim)
                }
            }
        }
        val page = remember { pageAddress() ?: DEMO_ADDRESS }
        CopyButton("Copy this result and a link to its board", { played.told(page) }, Modifier.offset(x = (-10).dp))
        // A shift that served every order is done with, whatever medals are left in it.
        val next = rush.next
        if (played.right && next != null && controller.rush == rush) {
            Box { PillButton("Next shift: ${next.title}", { controller.choose(next) }, emphasis = Emphasis.Primary) }
        }
    }
}

/** The shifts of this session side by side, which is what the stage is for: the same orders on another board, or with another model. */
@Composable
private fun Shifts(controller: KitchenController, modifier: Modifier = Modifier) {
    if (controller.history.isEmpty()) return
    val colors = Theme.colors
    Panel("Your shifts", modifier, trailing = { Label("same orders every time") }) {
        controller.history.forEachIndexed { index, played ->
            val score = played.shift.kitchen.score()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label("${index + 1} / ${played.rush.title} / ${played.board.summary} / ${played.model}", color = colors.ink)
                Body(
                    "${score.points} points, ${played.shift.spent.grouped()} tokens, minute ${played.shift.kitchen.minute}",
                    color = colors.dim,
                )
                if (!controller.running && played.board != controller.board) {
                    PillButton("Use board ${index + 1} again", { controller.load(played.board) }, Modifier.offset(x = (-10).dp), emphasis = Emphasis.Quiet)
                }
            }
        }
        Body("Try the same orders on another board, or with another model under Options.", color = colors.dim)
    }
}
