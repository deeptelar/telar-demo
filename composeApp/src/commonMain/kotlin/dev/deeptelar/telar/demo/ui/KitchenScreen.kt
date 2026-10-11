package dev.deeptelar.telar.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.deeptelar.telar.demo.kitchen.Kitchen
import dev.deeptelar.telar.demo.kitchen.LineShift
import dev.deeptelar.telar.demo.kitchen.Order
import dev.deeptelar.telar.demo.kitchen.Outcome
import dev.deeptelar.telar.demo.kitchen.Pizza
import dev.deeptelar.telar.demo.kitchen.Place
import dev.deeptelar.telar.demo.kitchen.Score
import dev.deeptelar.telar.demo.kitchen.Service
import dev.deeptelar.telar.demo.kitchen.lunchRush
import dev.deeptelar.telar.demo.kitchen.report

/**
 * The bonus stage: one shift of a pizzeria whose chefs are graphs. The player builds the kitchen,
 * and the shift is measured on the orders it serves and the tokens its models use.
 */
@Composable
fun KitchenScreen(controller: KitchenController, onStages: () -> Unit) {
    val compact = LocalCompact.current
    val shift = controller.shift

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("< All stages", onStages, Modifier.offset(x = (-10).dp), emphasis = Emphasis.Quiet)
        Label("bonus stage / one shift, twelve orders", color = Theme.colors.red)
        DotMatrix("Kitchen rush", pitch = if (compact) 3.dp else 5.dp)
        Body(
            "The chefs of this kitchen are Telar graphs. Orders come in, change and get cancelled, the oven has two places, and " +
                "the kitchen closes at minute $CLOSES_AT. You build the kitchen. The shift is measured on the orders it " +
                "serves in time and on the tokens its model uses.",
            Modifier.widthIn(max = 720.dp),
            color = Theme.colors.dim,
        )
    }

    if (compact) {
        Setup(controller, Modifier.fillMaxWidth())
        if (shift != null) ShiftPanels(controller, shift)
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Setup(controller, Modifier.fillMaxWidth())
                Shifts(controller, Modifier.fillMaxWidth())
            }
            Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (shift != null) ShiftPanels(controller, shift) else Waiting()
            }
        }
    }
    if (compact) Shifts(controller, Modifier.fillMaxWidth())
}

private val CLOSES_AT = lunchRush().closesAt

/** Where the player builds the kitchen and starts the shift. */
@Composable
private fun Setup(controller: KitchenController, modifier: Modifier = Modifier) {
    val colors = Theme.colors
    Panel("Build the kitchen", modifier) {
        KitchenLayout.entries.forEach { layout ->
            Choice(controller.layout == layout, layout.title, layout.detail, onClick = { controller.choose(layout) })
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        if (controller.scripted) {
            Spec("model", "scripted. No model is asked, and the tokens are a guess. Choose a real model under Options.")
        } else {
            Spec("model", "${controller.modelLabel}. A shift uses your key: about 10,000 tokens on the line and up to 150,000 for the solo chef.")
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
                PillButton(if (controller.shift == null) "Start the shift" else "Start a new shift", controller::start, emphasis = Emphasis.Primary)
            }
        }
    }
}

@Composable
private fun Waiting() {
    Panel("The shift", Modifier.fillMaxWidth()) {
        Body("The kitchen is closed. Pick how it is built and start the shift.", color = Theme.colors.dim)
    }
}

/** Everything about the shift that runs or just ended. */
@Composable
private fun ShiftPanels(controller: KitchenController, shift: Service) {
    val colors = Theme.colors
    controller.error?.let { error ->
        Panel("The shift stopped", Modifier.fillMaxWidth(), edge = colors.red) { Body(error, color = colors.red) }
    }
    if (controller.finished) Result(controller, shift)
    Meters(controller, shift)
    Stations(controller, shift)
    Floor(shift.kitchen)
    Log(shift)
    Rail(shift)
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

/** Who works in the kitchen, whether each is at work right now, and what each cost. */
@Composable
private fun Stations(controller: KitchenController, shift: Service) {
    val colors = Theme.colors
    val line = shift is LineShift
    Panel("Stations", Modifier.fillMaxWidth(), trailing = { Label(if (line) "three nodes in parallel" else "one agent") }) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            shift.desks.forEach { (name, desk) ->
                // On the line, a chef is at work until the minute it is done. The solo chef works whenever the shift runs.
                val busy = controller.running && (!line || desk.busyUntil > shift.kitchen.minute)
                val byRules = line && name != "reader" && controller.playing == KitchenLayout.Line
                Column(
                    Modifier.weight(1f).fillMaxHeight().border(1.dp, if (busy) colors.red else colors.line, PanelShape).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Label(name, color = if (busy) colors.red else colors.ink)
                    Label(STATION_JOBS.getValue(name))
                    Body(
                        if (byRules) "plain code, no tokens" else "${(desk.tokens.inputTokens + desk.tokens.outputTokens).grouped()} tokens in ${desk.answers} answers",
                        color = colors.dim,
                    )
                }
            }
        }
    }
}

private val STATION_JOBS = mapOf("chef" to "does everything", "reader" to "writes the tickets", "prep" to "makes the pizzas", "oven" to "bakes and serves")

/** The pizzas that are in the kitchen: raw on the counter, in the oven, and baked on the pass. */
@Composable
private fun Floor(kitchen: Kitchen) {
    val colors = Theme.colors
    val counter = kitchen.made.filter { it.place == Place.COUNTER }
    val pass = kitchen.made.filter { it.place == Place.PASS }
    Panel("Counter, oven and pass", Modifier.fillMaxWidth()) {
        Area("counter", if (counter.isEmpty()) "nothing" else "${counter.size} raw") {
            counter.forEach { PizzaRow(it, "raw", colors.dim) }
        }
        Area("oven", "${kitchen.oven.size} of ${Kitchen.OVEN_SLOTS} places") {
            kitchen.oven.forEach { pizza ->
                val minutes = kitchen.minute - pizza.inOvenSince!!
                when {
                    minutes > Kitchen.BURNT_AFTER -> PizzaRow(pizza, "burnt", colors.red)
                    minutes >= Kitchen.BAKED_AFTER -> PizzaRow(pizza, "baked, take it out", colors.yellow)
                    else -> PizzaRow(pizza, "$minutes of ${Kitchen.BAKED_AFTER} minutes", colors.ink)
                }
            }
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

/** The orders the kitchen has heard of, each with how it ended, and on the line with the ticket the reader wrote for it. */
@Composable
private fun Rail(shift: Service) {
    val colors = Theme.colors
    val kitchen = shift.kitchen
    val heard = kitchen.standing.filter { it.placedAt <= kitchen.minute }
    val tickets = (shift as? LineShift)?.tickets
    Panel("Orders", Modifier.fillMaxWidth(), trailing = { Label("${heard.size} of ${kitchen.orders.size} placed") }) {
        if (heard.isEmpty()) Body("No order yet.", color = colors.dim)
        heard.forEach { order ->
            val (status, color) = order.status(kitchen.minute, colors)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Label("#${order.number} / table ${order.table} / due ${order.dueAt}", color = colors.ink)
                    Label(status, color = color)
                }
                Body(order.text, color = if (order.outcome == Outcome.CANCELLED) colors.dim else colors.ink)
                tickets?.get(order.number)?.let { toppings -> Body("ticket: ${toppings.joinToString { it.word }}", color = colors.dim) }
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
        if (shift.log.isEmpty()) Body("Nothing has happened yet.", color = colors.dim)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            shift.log.takeLast(LOG_LINES).forEach { event ->
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
private fun Result(controller: KitchenController, shift: Service) {
    Panel("Shift over / ${controller.playing.title}", Modifier.fillMaxWidth().semantics { contentDescription = "Shift over" }, edge = Theme.colors.yellow) {
        shift.report().lines().forEach { line -> Spec(line.substringBefore(' '), line.substringAfter(' ').trim()) }
    }
}

/** The shifts of this session side by side, which is what the stage is for: the same orders, another kitchen or another model. */
@Composable
private fun Shifts(controller: KitchenController, modifier: Modifier = Modifier) {
    if (controller.history.isEmpty()) return
    val colors = Theme.colors
    Panel("Your shifts", modifier, trailing = { Label("same orders every time") }) {
        controller.history.forEachIndexed { index, played ->
            val score = played.shift.kitchen.score()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label("${index + 1} / ${played.layout.title} / ${played.model}", color = colors.ink)
                Body(
                    "${score.points} points, ${played.shift.spent.grouped()} tokens, minute ${played.shift.kitchen.minute}",
                    color = colors.dim,
                )
            }
        }
        Body("Try the same orders with another kitchen, or with another model under Options.", color = colors.dim)
    }
}

/** 12345 as "12,345". */
private fun Int.grouped(): String =
    toString()
        .reversed()
        .chunked(3)
        .joinToString(",")
        .reversed()
