package dev.deeptelar.telar.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.deeptelar.telar.Checkpoint
import dev.deeptelar.telar.demo.game.HelpDesks
import dev.deeptelar.telar.demo.game.Mail
import dev.deeptelar.telar.demo.game.Stage
import dev.deeptelar.telar.demo.game.Ticket
import dev.deeptelar.telar.demo.game.jsonLines
import dev.deeptelar.telar.demo.game.stages

private const val TUTORIAL = "https://github.com/deeptelar/telar/blob/main/docs/tutorial/"

/** The page of each stage's level: stage 3 is level 3 of the tutorial. */
private val tutorialPages = mapOf(
    1 to "01-a-line-of-nodes.md",
    2 to "02-choices.md",
    3 to "03-loops.md",
    4 to "04-two-things-at-once.md",
    5 to "05-save-points.md",
    6 to "06-the-agent.md",
    7 to "07-game-over-screens.md",
    8 to "08-your-own-workflow.md",
)

/** One stage: its briefing, the board, the inbox to play from, and the ticket and log of the current run. */
@Composable
fun StageScreen(
    controller: StageController,
    realModel: String?,
    onStages: () -> Unit,
    onNext: (() -> Unit)?,
    inspecting: Boolean = false,
    onInspect: (Boolean) -> Unit = {},
) {
    val stage = controller.stage
    val compact = LocalCompact.current

    Briefing(stage, realModel, onStages)

    Panel(
        "Board",
        Modifier.fillMaxWidth(),
        trailing = { Label(if (controller.step > 0) "step ${controller.step.toString().padStart(2, '0')}" else "ready") },
        padding = 0.dp,
    ) {
        Board(controller, Modifier.fillMaxWidth().dotGrid(Theme.colors.line), selected = controller.codeOf, onSelect = controller::toggleCode)
        if (controller.codeOf == null) Label("press a tile to see its code", Modifier.padding(start = 14.dp, bottom = 10.dp))
    }

    controller.codeOf?.let { node -> CodePanel(stage, node, onClose = controller::closeCode) }

    if (!controller.running) Outcome(controller, onStages, onNext)

    if (compact) {
        Inbox(controller, Modifier.fillMaxWidth())
        TicketPanel(controller, inspecting, onInspect, Modifier.fillMaxWidth())
        if (inspecting) LivePanel(controller, Modifier.fillMaxWidth())
        LogPanel(controller, Modifier.fillMaxWidth())
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Inbox(controller, Modifier.weight(1f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                TicketPanel(controller, inspecting, onInspect, Modifier.fillMaxWidth())
                if (inspecting) LivePanel(controller, Modifier.fillMaxWidth())
                LogPanel(controller, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Briefing(stage: Stage, realModel: String?, onStages: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // The links are text only, so they are moved out by their padding to line up with the page.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PillButton("< All stages", onStages, Modifier.offset(x = (-10).dp), emphasis = Emphasis.Quiet)
            PillButton(
                "Tutorial level ${stage.number}",
                onClick = { uriHandler.openUri(TUTORIAL + tutorialPages.getValue(stage.number)) },
                modifier = Modifier.offset(x = 10.dp),
                emphasis = Emphasis.Quiet,
            )
        }
        Label("stage ${stage.number.twoDigits()} / ${stages.size.twoDigits()}", color = Theme.colors.red)
        DotMatrix(stage.title, pitch = if (LocalCompact.current) 3.dp else 5.dp)
        Body(stage.briefing, Modifier.widthIn(max = 720.dp), color = Theme.colors.dim)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Fact("new move", stage.moves)
            Fact(
                "model",
                when {
                    !stage.usesModel -> "none. This stage is scripted only."
                    realModel == null -> "scripted. Choose a real one under Options."
                    else -> realModel
                },
            )
        }
    }
}

/** The code behind the tile the player pressed: the node, its arrows and the functions it calls. */
@Composable
private fun CodePanel(stage: Stage, node: String, onClose: () -> Unit) {
    val parts = remember(stage, node) { stage.code.of(node) }
    Panel(
        "Code of ${node.trim('_')}",
        Modifier.fillMaxWidth(),
        trailing = {
            Label("close x", Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(role = Role.Button, onClick = onClose))
        },
    ) {
        parts.forEach { part ->
            Label(part.title)
            Code(part.code)
        }
        if (parts.any { GAME_PAUSE in it.code }) {
            Body("$GAME_PAUSE is only for the game: it makes a node take a moment, so that you can watch it run.", color = Theme.colors.dim)
        }
    }
}

/**
 * A block of source code. It keeps its line breaks and scrolls sideways when a line is too long.
 * With [wrap] a long line goes on in the next one instead, which is easier to read on a narrow screen.
 */
@Composable
internal fun Code(code: String, modifier: Modifier = Modifier, wrap: Boolean = false) {
    val colors = Theme.colors
    val text = remember(code, colors) {
        buildAnnotatedString {
            code.lines().forEachIndexed { index, line ->
                if (index > 0) append('\n')
                val comment = line.trimStart().let { it.startsWith("//") || it.startsWith("/*") || it.startsWith("*") }
                if (comment) withStyle(SpanStyle(color = colors.dim)) { append(line) } else append(line)
            }
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .background(colors.panel, RoundedCornerShape(4.dp))
            .then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState()))
            .padding(12.dp),
    ) {
        SelectionContainer {
            BasicText(text, style = Theme.body.copy(color = colors.ink, fontSize = 13.sp, lineHeight = 19.sp), softWrap = wrap)
        }
    }
}

private const val GAME_PAUSE = "desk.work()"

@Composable
private fun Fact(name: String, value: String) {
    // On the line of the value's first row, also when a narrow screen wraps the value.
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Label(name, Modifier.width(76.dp).alignByBaseline())
        Body(value, Modifier.alignByBaseline())
    }
}

/** The title of the game over panel: the node that failed, and how often it was tried. */
private fun failure(controller: StageController): String {
    val node = controller.failedNode ?: return "The run failed"
    return if (controller.failedTries > 1) "Node $node failed ${controller.failedTries} times" else "Node $node failed"
}

/** What the run came to: a decision to make, a failure to retry, or a cleared stage. */
@Composable
private fun Outcome(controller: StageController, onStages: () -> Unit, onNext: (() -> Unit)?) {
    val colors = Theme.colors
    val ticket = controller.ticket
    val next = controller.waitingAt.joinToString(" + ").uppercase()
    when (controller.phase) {
        Phase.Ready -> Unit
        Phase.SavePoint -> Panel(if (controller.restored) "Restored from storage" else "Run saved before $next", Modifier.fillMaxWidth(), colors.yellow) {
            DotMatrix("Save point", pitch = 3.dp, color = colors.yellow)
            if (ticket != null) Body("Refund ${ticket.refund} euros to ${ticket.customer}?")
            Body("Nothing is paid until you decide. The run is stored, so you can reload the page first.", color = colors.dim)
            Buttons {
                PillButton("Approve", { controller.decide(approved = true) }, emphasis = Emphasis.Primary)
                PillButton("Deny", { controller.decide(approved = false) })
                PillButton("Discard", controller::reset, emphasis = Emphasis.Quiet)
            }
        }
        Phase.Unfinished -> Panel("Run saved before $next", Modifier.fillMaxWidth(), colors.yellow) {
            DotMatrix("Paused", pitch = 3.dp, color = colors.yellow)
            Body("This run stopped halfway. Its finished steps are stored, so it can go on from $next.", color = colors.dim)
            Buttons {
                PillButton("Continue", controller::retry, emphasis = Emphasis.Primary)
                PillButton("Discard", controller::reset, emphasis = Emphasis.Quiet)
            }
        }
        Phase.GameOver -> Panel(failure(controller), Modifier.fillMaxWidth(), colors.red) {
            DotMatrix("Game over", pitch = 3.dp, color = colors.red)
            Body(controller.error.orEmpty())
            Body(
                if (next.isEmpty()) {
                    "Nothing was saved before the failure, so a retry starts from the beginning."
                } else {
                    "The steps before it are saved. A retry starts at $next, not at the beginning."
                },
                color = colors.dim,
            )
            Buttons {
                PillButton("Retry", controller::retry, emphasis = Emphasis.Primary)
                PillButton("Discard", controller::reset, emphasis = Emphasis.Quiet)
            }
        }
        Phase.Clear -> Panel("Reply sent in ${controller.step} steps", Modifier.fillMaxWidth(), colors.ink) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                PixelArt(Sprites.slice, pixel = 3.dp)
                DotMatrix("Stage clear", pitch = 3.dp)
            }
            if (ticket != null) {
                Label("to ${ticket.customer}")
                Body(typed(ticket.reply))
            }
            Buttons {
                if (onNext != null) {
                    PillButton("Next stage", onNext, emphasis = Emphasis.Primary)
                } else {
                    PillButton("All stages", onStages, emphasis = Emphasis.Primary)
                }
                PillButton("Play again", controller::reset)
            }
        }
    }
}

@Composable
private fun Buttons(content: @Composable () -> Unit) {
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

@Composable
private fun Inbox(controller: StageController, modifier: Modifier = Modifier) {
    val inbox = controller.stage.inbox
    var selected by remember(controller) { mutableStateOf(0) }
    var message by remember(controller) { mutableStateOf(inbox.first().text) }
    val waiting = controller.phase == Phase.SavePoint || controller.phase == Phase.Unfinished
    val canRun = !controller.running && !waiting && message.isNotBlank()
    val run = { if (canRun) controller.play(Mail(inbox[selected].from, message)) }

    Panel("Inbox", modifier, trailing = { Label("${inbox.size} waiting") }) {
        Column {
            inbox.forEachIndexed { index, mail ->
                MailRow(mail, selected == index) {
                    selected = index
                    message = mail.text
                }
            }
        }
        // Where no node reads the message, the player is not asked to write one, and is told why.
        val reads = controller.stage.readsMessage
        Field(message, { message = it }, label = "Message from ${inbox[selected].from}${if (reads) ". Edit it to try your own." else ""}", onEnter = run)
        if (!reads) Body("This desk does one job for every ticket and does not read the message.", color = Theme.colors.dim)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(if (controller.running) "Running" else "Run", run, emphasis = Emphasis.Primary, enabled = canRun)
            if (controller.running) PillButton("Stop", controller::stop)
            if (waiting) Label("answer the saved run first", color = Theme.colors.yellow)
        }
    }
}

@Composable
private fun MailRow(mail: Mail, selected: Boolean, onClick: () -> Unit) {
    val colors = Theme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) colors.panel else colors.ground)
            .drawBehind { if (selected) drawRect(colors.red, size = Size(3.dp.toPx(), size.height)) }
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Label(mail.from, color = if (selected) colors.ink else colors.dim)
        Body(mail.text, color = if (selected) colors.ink else colors.dim)
    }
}

/**
 * The state of the run, field by field. Only what the nodes have filled in so far is listed. Its
 * header switches the [LivePanel] on and off.
 */
@Composable
private fun TicketPanel(controller: StageController, inspecting: Boolean, onInspect: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val ticket = controller.ticket
    Panel(
        "Ticket",
        modifier,
        trailing = {
            Label(
                if (inspecting) "hide json + saves" else "show json + saves",
                Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(role = Role.Switch) { onInspect(!inspecting) },
                color = if (inspecting) Theme.colors.ink else Theme.colors.red,
            )
        },
    ) {
        if (ticket == null) {
            Body("No ticket on the desk. Pick one from the inbox and press RUN.", color = Theme.colors.dim)
        } else {
            TicketSpecs(ticket, paid = HelpDesks.PAY in controller.visited)
        }
    }
}

@Composable
private fun TicketSpecs(ticket: Ticket, paid: Boolean) {
    Spec("customer", ticket.customer)
    Spec("message", ticket.message)
    if (ticket.topic.isNotEmpty()) Spec("topic", ticket.topic)
    if (ticket.facts.isNotEmpty()) Spec("facts", ticket.facts.joinToString("\n"))
    if (ticket.chat.isNotEmpty()) Spec("chat", "${ticket.chat.size} messages")
    if (ticket.refund > 0) Spec("refund", "${ticket.refund} euros")
    if (paid) Spec("approved", if (ticket.approved) "yes" else "no")
    if (ticket.attempts > 0) Spec("attempts", ticket.attempts.toString())
    if (ticket.problem.isNotEmpty()) Spec("problem", ticket.problem, valueColor = Theme.colors.red)
    if (ticket.reply.isNotEmpty()) Spec("reply", ticket.reply)
}

/**
 * What Telar keeps of the run, as it changes: the ticket as the JSON of its data class, and the
 * checkpoints that `history()` reads from the checkpointer. A checkpoint opens to the ticket it saved.
 */
@Composable
private fun LivePanel(controller: StageController, modifier: Modifier = Modifier) {
    val colors = Theme.colors
    // The step of the checkpoint whose ticket is shown, or null when none is open.
    var open by remember(controller) { mutableStateOf<Int?>(null) }
    Panel("Live state", modifier, trailing = { Label("what telar keeps") }) {
        Label("ticket, the data class as json")
        val ticket = controller.ticket
        if (ticket == null) {
            Body("No ticket yet. Run one and watch its fields fill in.", color = colors.dim)
        } else {
            JsonLines(ticket.jsonLines(controller.ticketBefore))
            if (controller.ticketBefore != null) Body("Marked: what the last step changed.", color = colors.dim)
        }

        Label("checkpoints, graph.history(config)")
        val checkpoints = controller.checkpoints
        if (checkpoints.isEmpty()) {
            Body("Nothing saved yet. A run saves its ticket before the first node, then after every step.", color = colors.dim)
        }
        Column {
            // The latest first: that is where a resume would continue.
            checkpoints.asReversed().forEachIndexed { index, checkpoint ->
                val opened = open == checkpoint.step
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(if (opened) colors.panel else colors.ground)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { open = if (opened) null else checkpoint.step }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Label("step ${checkpoint.step.twoDigits()}", Modifier.padding(top = 4.dp).width(64.dp), color = if (index == 0) colors.ink else colors.dim)
                        Body(checkpointText(checkpoint), Modifier.weight(1f), color = if (index == 0) colors.ink else colors.dim)
                    }
                    if (opened) {
                        val earlier = checkpoints.lastOrNull { it.step < checkpoint.step }
                        JsonLines(checkpoint.state.jsonLines(earlier?.state))
                    }
                }
            }
        }
    }
}

/** What a checkpoint says about the run: where it goes on, and why it stopped there. */
private fun checkpointText(checkpoint: Checkpoint<Ticket>): String = when {
    checkpoint.isComplete -> "the end: nothing left to run"
    checkpoint.interruptedBefore -> "paused before ${checkpoint.nextNodes.joinToString(" + ")}"
    else -> "next: ${checkpoint.nextNodes.joinToString(" + ")}"
}

/** JSON, a line at a time, with the lines marked `true` highlighted. It wraps, to fit a phone. */
@Composable
private fun JsonLines(lines: List<Pair<String, Boolean>>) {
    val colors = Theme.colors
    val text = remember(lines, colors) {
        buildAnnotatedString {
            lines.forEachIndexed { index, (line, changed) ->
                if (index > 0) append('\n')
                if (changed) {
                    // The indent stays unmarked, so that the mark covers what changed.
                    val text = line.trimStart()
                    append(line.substring(0, line.length - text.length))
                    withStyle(SpanStyle(color = colors.ink, background = colors.yellow.copy(alpha = 0.3f))) { append(text) }
                } else {
                    append(line)
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().background(colors.panel, RoundedCornerShape(4.dp)).padding(12.dp)) {
        SelectionContainer {
            BasicText(text, style = Theme.body.copy(color = colors.dim, fontSize = 12.sp, lineHeight = 17.sp))
        }
    }
}

/** The events of the run as they arrived from `stream()`. */
@Composable
private fun LogPanel(controller: StageController, modifier: Modifier = Modifier) {
    val colors = Theme.colors
    Panel("Run log", modifier, trailing = { Label("stream()") }) {
        if (controller.log.isEmpty() && controller.writing.isEmpty()) Body("Nothing has run yet.", color = colors.dim)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            controller.log.forEach { line ->
                val color = when (line.tone) {
                    Tone.Plain -> colors.ink
                    Tone.Detail -> colors.dim
                    Tone.Saved -> colors.yellow
                    Tone.Failed -> colors.red
                    Tone.Done -> colors.ink
                }
                LogRow(line.tag, line.text, tagColor = if (line.tone == Tone.Plain) colors.dim else color, textColor = color)
            }
            // The answer a model is writing right now, piece by piece as it arrives.
            if (controller.writing.isNotEmpty()) LogRow(WRITING, "${controller.writing}_", tagColor = colors.dim, textColor = colors.dim)
        }
    }
}

@Composable
private fun LogRow(tag: String, text: String, tagColor: Color, textColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Label(tag, Modifier.padding(top = 4.dp).width(28.dp), color = tagColor)
        Body(text, Modifier.weight(1f), color = textColor)
    }
}

private const val WRITING = " .."

private fun Int.twoDigits(): String = toString().padStart(2, '0')
