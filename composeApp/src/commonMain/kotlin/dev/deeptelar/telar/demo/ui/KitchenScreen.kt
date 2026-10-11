package dev.deeptelar.telar.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.deeptelar.telar.demo.kitchen.Blueprint
import dev.deeptelar.telar.demo.kitchen.BoardShift
import dev.deeptelar.telar.demo.kitchen.Desk
import dev.deeptelar.telar.demo.kitchen.Exit
import dev.deeptelar.telar.demo.kitchen.Kitchen
import dev.deeptelar.telar.demo.kitchen.PieceKind
import dev.deeptelar.telar.demo.kitchen.Order
import dev.deeptelar.telar.demo.kitchen.Outcome
import dev.deeptelar.telar.demo.kitchen.Pizza
import dev.deeptelar.telar.demo.kitchen.Place
import dev.deeptelar.telar.demo.kitchen.Score
import dev.deeptelar.telar.demo.kitchen.Service
import dev.deeptelar.telar.demo.kitchen.report

/**
 * The bonus stage: one shift of a pizzeria. The player draws how an order gets from the rail to the
 * kitchen, as stations and arrows, and the shift is measured on the orders it serves and the tokens
 * its models use.
 */
@Composable
fun KitchenScreen(controller: KitchenController, onStages: () -> Unit) {
    val compact = LocalCompact.current
    val shift = controller.shift

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("< All stages", onStages, Modifier.offset(x = (-10).dp), emphasis = Emphasis.Quiet)
        Label("bonus stage / two shifts, twelve orders each", color = Theme.colors.red)
        DotMatrix("Kitchen rush", pitch = if (compact) 3.dp else 5.dp)
        Body(
            "Orders come in until the kitchen closes, and the kitchen makes exactly what the ticket says. You draw how an order becomes " +
                "a ticket and reaches the oven: stations, and arrows between them. Every order is one run of the graph on your board.",
            Modifier.widthIn(max = 720.dp),
            color = Theme.colors.dim,
        )
    }

    if (compact) {
        Builder(controller, Modifier.fillMaxWidth())
        Goals(controller, Modifier.fillMaxWidth())
        if (shift != null) ShiftPanels(controller, shift)
        Shifts(controller, Modifier.fillMaxWidth())
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Builder(controller, Modifier.fillMaxWidth())
                Goals(controller, Modifier.fillMaxWidth())
                Shifts(controller, Modifier.fillMaxWidth())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (shift != null) ShiftPanels(controller, shift) else Waiting()
            }
        }
    }
}

/** Where the player draws the board and starts the shift. */
@Composable
private fun Builder(controller: KitchenController, modifier: Modifier = Modifier) {
    val colors = Theme.colors
    var showCode by remember { mutableStateOf(false) }
    Panel("Draw the kitchen", modifier, trailing = { Label("${controller.board.pieces.size} of ${Blueprint.SLOTS} places") }) {
        Rush.entries.forEach { rush -> Choice(controller.rush == rush, rush.title, rush.detail, onClick = { controller.choose(rush) }) }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        KitchenBoard(controller, Modifier.fillMaxWidth())
        Hand(controller)
        controller.problems.forEach { problem -> Body(problem, color = colors.red) }

        PillButton(if (showCode) "Hide the code" else "Show the code of this board", { showCode = !showCode }, Modifier.offset(x = (-10).dp), emphasis = Emphasis.Quiet)
        if (showCode) {
            Box(Modifier.fillMaxWidth().background(colors.panel, RoundedCornerShape(4.dp)).horizontalScroll(rememberScrollState()).padding(12.dp)) {
                BasicText(
                    controller.board.code(),
                    Modifier.semantics { contentDescription = "The code of the board" },
                    Theme.body.copy(color = colors.ink, fontSize = 12.sp, lineHeight = 18.sp),
                    softWrap = false,
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (controller.running) {
                PillButton("Stop", controller::stop)
            } else {
                PillButton(
                    if (controller.shift == null) "Start the shift" else "Start a new shift",
                    controller::start,
                    emphasis = Emphasis.Primary,
                    enabled = controller.problems.isEmpty(),
                )
                PillButton("Clear the board", { controller.load(Blueprint()) }, emphasis = Emphasis.Quiet)
            }
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
    when {
        controller.running -> Body("The board cannot be changed while a shift runs.", color = colors.dim)
        picking != null -> {
            Label("a station for place ${picking + 1}", color = colors.ink)
            // A station that a board can have only once is not offered again.
            PieceKind.entries.filterNot { kind -> kind.once && board.pieces.any { it.kind == kind } }.forEach { kind ->
                Choice(false, kind.title, kind.job, onClick = { controller.put(kind) }, role = Role.Button)
            }
        }
        selected != null -> {
            Label(board.title(selected), color = colors.ink)
            Body(board.piece(selected)?.kind?.job ?: "Every order and every change of an order starts here.", color = colors.dim)
            board.exits(selected).forEach { exit ->
                val target = board.target(selected, exit)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Label(if (exit == Exit.NEXT) "arrow to" else "${exit.word} goes to", Modifier.width(88.dp))
                    when {
                        controller.armed == exit -> Body("press the tile it goes to", color = colors.yellow)
                        target != null -> {
                            Body(board.title(target))
                            PillButton("Change", { controller.redraw(exit) }, emphasis = Emphasis.Quiet)
                        }
                        else -> PillButton("Draw", { controller.redraw(exit) }, emphasis = Emphasis.Quiet)
                    }
                }
            }
            if (board.piece(selected) != null) PillButton("Remove the station", controller::remove)
        }
        else -> Body("Press an empty place to add a station. Press a station or the rail, and then the tile its arrow goes to.", color = colors.dim)
    }
}

/** What there is to reach, and what the shifts of this session have reached. */
@Composable
private fun Goals(controller: KitchenController, modifier: Modifier = Modifier) {
    val colors = Theme.colors
    val goals = controller.goals
    Panel("Goals / ${controller.rush.title}", modifier, trailing = { Label("${goals.count { it.second }} of ${goals.size}") }) {
        goals.forEach { (goal, met) ->
            Row(
                Modifier.semantics(mergeDescendants = true) { contentDescription = "${goal.text} ${if (met) "Reached" else "Open"}" },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Label(if (met) "[x]" else "[ ]", Modifier.padding(top = 4.dp), color = if (met) colors.yellow else colors.dim)
                Body(goal.text, color = if (met) colors.ink else colors.dim)
            }
        }
        if (!controller.scripted) {
            Body("The token goals are for the scripted chefs, whose tokens are the same every time. A real model counts its own: compare your shifts.", color = colors.dim)
        }
    }
}

@Composable
private fun Waiting() {
    Panel("The shift", Modifier.fillMaxWidth()) {
        Body(
            "The kitchen is closed. Start a shift on the board as it is: the orders show here as they come in, each with the way it took " +
                "across the board and the ticket it got. Then draw a better board.",
            color = Theme.colors.dim,
        )
    }
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
    Panel("Stations", Modifier.fillMaxWidth(), trailing = { Label("one run of the graph for each order") }) {
        asking.forEach { piece ->
            val desk = shift.stations[piece.id] ?: Desk()
            Spec(piece.kind.tile, "${(desk.tokens.inputTokens + desk.tokens.outputTokens).grouped()} tokens in ${desk.answers} answers")
        }
        if (asking.isEmpty()) Spec("board", "No station of this board asks a model.", colors.dim)
        Spec("prep", "makes what the tickets say. Plain code, no tokens.", colors.dim)
        Spec("oven", "bakes and serves. Plain code, no tokens.", colors.dim)
        if (controller.playing.hasSecondOven) Spec("oven 2", "bakes and serves the pizzas that may go into it. Plain code, no tokens.", colors.dim)
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
            Label("baked after ${Kitchen.BAKED_AFTER} minutes, burnt after ${Kitchen.BURNT_AFTER}", Modifier.padding(start = 88.dp, bottom = 4.dp))
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

/** The orders the kitchen has heard of, each with how it ended, the way it took across the board, and the ticket it got. */
@Composable
private fun Rail(controller: KitchenController, shift: BoardShift) {
    val colors = Theme.colors
    val kitchen = shift.kitchen
    val board = controller.playing
    val heard = kitchen.standing.filter { it.placedAt <= kitchen.minute }
    Panel("Orders", Modifier.fillMaxWidth(), trailing = { Label("${heard.size} of ${kitchen.orders.size} placed") }) {
        if (heard.isEmpty()) Body("No order yet.", color = colors.dim)
        heard.forEach { order ->
            val (status, color) = order.status(kitchen.minute, colors)
            val route = shift.routes[order.number]
            val ticket = shift.tickets[order.number]
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Label("#${order.number} / table ${order.table} / due ${order.dueAt}", color = colors.ink)
                    Label(status, color = color)
                }
                Body(order.text, color = if (order.outcome == Outcome.CANCELLED) colors.dim else colors.ink)
                if (route != null) {
                    Body("went: " + (listOf("rail") + route.map { board.piece(it)?.kind?.tile ?: it } + "kitchen").joinToString(" > "), color = colors.dim)
                }
                when {
                    ticket != null -> Body("ticket: ${ticket.joinToString { it.word }}", color = colors.dim)
                    route != null && order.outcome != Outcome.CANCELLED && shift.written.none { it.order == order.number } ->
                        Body("no ticket, so the kitchen makes nothing for it", color = colors.red)
                }
            }
        }
    }
}

private fun Order.status(minute: Int, colors: Palette): Pair<String, Color> =
    when (outcome) {
        Outcome.OPEN -> if (minute > dueAt) "overdue" to colors.red else "waiting" to colors.dim
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
    Panel("Shift over / ${rush.title} / ${controller.playing.summary}", Modifier.fillMaxWidth().semantics { contentDescription = "Shift over" }, edge = Theme.colors.yellow) {
        // What each station cost is in the panel of the stations.
        shift.report().lines().filterNot { it.startsWith("stations") }.forEach { line -> Spec(line.substringBefore(' '), line.substringAfter(' ').trim()) }
        Spec("goals", "${rush.goals.count { it.metBy(played) }} of ${rush.goals.size} reached on this board")
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

/** 12345 as "12,345". */
private fun Int.grouped(): String =
    toString()
        .reversed()
        .chunked(3)
        .joinToString(",")
        .reversed()
