package dev.deeptelar.telar.demo.kitchen

import dev.deeptelar.telar.demo.kitchen.Topping.MOZZARELLA
import dev.deeptelar.telar.demo.kitchen.Topping.MUSHROOMS
import dev.deeptelar.telar.demo.kitchen.Topping.ONIONS
import dev.deeptelar.telar.demo.kitchen.Topping.PEPPERONI
import dev.deeptelar.telar.demo.kitchen.Topping.PEPPERS
import dev.deeptelar.telar.demo.kitchen.Topping.TOMATO

// The short shifts of the game, each of which needs one station that the shifts before it did not.
// They are not in the library's sample.

private val PLENTY: Map<Topping, Int> = Topping.entries.associateWith { 16 }

private fun lesson(closesAt: Int, vararg orders: Order): Kitchen = Kitchen(orders.toList(), dough = 16, stock = PLENTY, closesAt = closesAt)

private fun plain(number: Int, placedAt: Int, dueAt: Int, text: String): Order =
    Order(number, table = number, placedAt, dueAt, text, wanted = MENU.entries.first { it.key in text.lowercase() }.value)

/** Four pizzas of the menu, one after the other. The menu lookup reads all of them. */
fun firstOrders(): Kitchen =
    lesson(
        closesAt = 45,
        plain(1, placedAt = 0, dueAt = 16, "One margherita."),
        plain(2, placedAt = 0, dueAt = 22, "A pepperoni."),
        plain(3, placedAt = 8, dueAt = 28, "A funghi."),
        plain(4, placedAt = 14, dueAt = 36, "One veggie."),
    )

/** Three of four guests want their pizza changed, which the menu lookup does not read. A reader does. */
fun specialWishes(): Kitchen =
    lesson(
        closesAt = 45,
        plain(1, placedAt = 0, dueAt = 18, "A margherita."),
        Order(2, table = 2, placedAt = 0, dueAt = 24, text = "A pepperoni with extra cheese.", wanted = listOf(TOMATO, MOZZARELLA, MOZZARELLA, PEPPERONI)),
        Order(3, table = 3, placedAt = 8, dueAt = 30, text = "A veggie without the onions.", wanted = listOf(TOMATO, MOZZARELLA, MUSHROOMS, PEPPERS)),
        Order(4, table = 4, placedAt = 14, dueAt = 36, text = "A funghi with extra mushrooms.", wanted = listOf(TOMATO, MOZZARELLA, MUSHROOMS, MUSHROOMS)),
    )

/** Four of six orders are a pizza of the menu and nothing more. A sorter keeps them away from the model. */
fun sorting(): Kitchen =
    lesson(
        closesAt = 55,
        plain(1, placedAt = 0, dueAt = 18, "One margherita."),
        plain(2, placedAt = 0, dueAt = 24, "A pepperoni."),
        Order(3, table = 3, placedAt = 6, dueAt = 30, text = "A margherita without the basil.", wanted = listOf(TOMATO, MOZZARELLA)),
        plain(4, placedAt = 12, dueAt = 34, "A funghi."),
        Order(
            5,
            table = 5,
            placedAt = 18,
            dueAt = 42,
            text = "A veggie with extra peppers instead of the onions.",
            wanted = listOf(TOMATO, MOZZARELLA, MUSHROOMS, PEPPERS, PEPPERS),
        ),
        plain(6, placedAt = 24, dueAt = 48, "One veggie."),
    )

/**
 * Two orders only make sense with what was said before: table 3 wants what table 1 had, and the
 * second pizza of table 2 must not have what the table is allergic to. A reader that is told one
 * order writes no ticket for the first and the wrong one for the second.
 */
fun theBook(): Kitchen =
    lesson(
        closesAt = 55,
        Order(1, table = 1, placedAt = 0, dueAt = 18, text = "A pepperoni with extra cheese.", wanted = listOf(TOMATO, MOZZARELLA, MOZZARELLA, PEPPERONI)),
        Order(
            2,
            table = 2,
            placedAt = 0,
            dueAt = 24,
            text = "A funghi. Nobody at table 2 may get onions, one of us is allergic.",
            wanted = MENU.getValue("funghi"),
            never = setOf(ONIONS),
        ),
        Order(3, table = 3, placedAt = 10, dueAt = 32, text = "The same as table 1 had.", wanted = listOf(TOMATO, MOZZARELLA, MOZZARELLA, PEPPERONI)),
        Order(
            4,
            table = 2,
            placedAt = 16,
            dueAt = 38,
            text = "Another pizza for table 2: a veggie.",
            wanted = listOf(TOMATO, MOZZARELLA, MUSHROOMS, PEPPERS),
            never = setOf(ONIONS),
        ),
        plain(5, placedAt = 22, dueAt = 46, "One margherita."),
    )

/**
 * Eight orders, of which three look back: table 1 says what nobody there may get and orders again,
 * and table 6 wants what table 3 had. Only those need the book, and the book costs more with every
 * order it has heard of. Four orders want a pizza changed, which a reader does without the book.
 */
fun lookingBack(): Kitchen =
    lesson(
        closesAt = 65,
        Order(
            1,
            table = 1,
            placedAt = 0,
            dueAt = 20,
            text = "A funghi. A note for the whole evening: nobody at table 1 may get onions, two of us are allergic.",
            wanted = MENU.getValue("funghi"),
            never = setOf(ONIONS),
        ),
        plain(2, placedAt = 0, dueAt = 26, "One margherita."),
        Order(3, table = 3, placedAt = 5, dueAt = 30, text = "A pepperoni with extra cheese.", wanted = listOf(TOMATO, MOZZARELLA, MOZZARELLA, PEPPERONI)),
        Order(
            4,
            table = 1,
            placedAt = 10,
            dueAt = 34,
            text = "Another pizza for table 1: a veggie.",
            wanted = listOf(TOMATO, MOZZARELLA, MUSHROOMS, PEPPERS),
            never = setOf(ONIONS),
        ),
        Order(
            5,
            table = 5,
            placedAt = 15,
            dueAt = 38,
            text = "A veggie with extra peppers instead of the onions.",
            wanted = listOf(TOMATO, MOZZARELLA, MUSHROOMS, PEPPERS, PEPPERS),
        ),
        Order(6, table = 6, placedAt = 20, dueAt = 44, text = "The same as table 3 had, but without the extra.", wanted = MENU.getValue("pepperoni")),
        Order(7, table = 7, placedAt = 25, dueAt = 48, text = "A margherita without the basil.", wanted = listOf(TOMATO, MOZZARELLA)),
        Order(8, table = 8, placedAt = 30, dueAt = 54, text = "A funghi with extra mushrooms.", wanted = listOf(TOMATO, MOZZARELLA, MUSHROOMS, MUSHROOMS)),
    )

/** Four orders at once, and the two that came last are due first. A kitchen that takes its tickets as they come serves those late. */
fun firstThingsFirst(): Kitchen =
    lesson(
        closesAt = 50,
        plain(1, placedAt = 0, dueAt = 40, "A veggie."),
        plain(2, placedAt = 0, dueAt = 32, "One funghi."),
        plain(3, placedAt = 0, dueAt = 14, "A margherita."),
        plain(4, placedAt = 0, dueAt = 18, "A pepperoni."),
    )

/** Six orders at once, all due at the same minute. One oven with two places bakes the last two too late, in whatever order it takes them. */
fun twoOvens(): Kitchen =
    lesson(
        closesAt = 50,
        plain(1, placedAt = 0, dueAt = 28, "A margherita."),
        plain(2, placedAt = 0, dueAt = 28, "A pepperoni."),
        plain(3, placedAt = 0, dueAt = 28, "One funghi."),
        plain(4, placedAt = 0, dueAt = 28, "A veggie."),
        plain(5, placedAt = 0, dueAt = 28, "One margherita."),
        plain(6, placedAt = 0, dueAt = 28, "A pepperoni."),
    )
