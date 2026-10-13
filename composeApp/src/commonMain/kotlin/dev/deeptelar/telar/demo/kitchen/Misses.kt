package dev.deeptelar.telar.demo.kitchen

// What went wrong in a shift, order by order, for its result. It is not in the library's sample.

/**
 * An order that was not served right and on time.
 *
 * @property why what happened to it, in a sentence or two.
 */
data class Miss(val order: Order, val why: String)

/**
 * The orders of this shift that were not served right and on time, each with what happened to it:
 * the ticket that was wrong and the station that wrote it, the minute it was served, or why it got
 * no ticket. [board] is the board the shift was played on.
 */
fun BoardShift.misses(board: Blueprint): List<Miss> =
    kitchen.standing.mapNotNull { order ->
        val ticket = tickets[order.number]
        // The station that wrote the ticket is the last one on the order's way that writes.
        val writer =
            routes[order.number]
                .orEmpty()
                .lastOrNull { id -> board.piece(id)?.kind?.writes == true }
                ?.let { board.called(it).replaceFirstChar(Char::uppercase) }
                ?: "A station"
        val why =
            when (order.outcome) {
                Outcome.ON_TIME, Outcome.CANCELLED -> return@mapNotNull null
                Outcome.ALLERGY -> {
                    val bad = ticket.orEmpty().filter { it in order.never }.distinct().joinToString { it.word }
                    "$writer wrote a ticket with $bad. Table ${order.table} is allergic."
                }
                Outcome.WRONG ->
                    if (ticket != null && ticket.sorted() != order.wanted.sorted()) {
                        "$writer wrote: ${ticket.joinToString { it.word }}. The guest wanted: ${order.wanted.joinToString { it.word }}."
                    } else {
                        "The ticket was right, and the pizza came out of the oven spoilt."
                    }
                Outcome.LATE -> {
                    val served = log.lastOrNull { "for order #${order.number}." in it.text }?.minute
                    if (served == null) "Served late. It was due at minute ${order.dueAt}." else "Served at minute $served. It was due at minute ${order.dueAt}."
                }
                Outcome.OPEN -> {
                    val refused = log.lastOrNull { it.refused && it.text.startsWith("No ticket for #${order.number}:") }
                    when {
                        ticket != null -> "It had a ticket, and was not served before the shift ended."
                        refused != null -> "The ${refused.station} tile wrote no ticket: ${refused.text.substringAfter(": ")}"
                        order.number !in routes -> "It never got to a station."
                        else -> "It reached the kitchen without a ticket, so nothing was made for it."
                    }
                }
            }
        Miss(order, why)
    }
