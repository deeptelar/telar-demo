package dev.deeptelar.telar.demo.kitchen

import dev.deeptelar.telar.demo.kitchen.Topping.MOZZARELLA
import dev.deeptelar.telar.demo.kitchen.Topping.MUSHROOMS
import dev.deeptelar.telar.demo.kitchen.Topping.ONIONS
import dev.deeptelar.telar.demo.kitchen.Topping.PEPPERONI
import dev.deeptelar.telar.demo.kitchen.Topping.PEPPERS
import dev.deeptelar.telar.demo.kitchen.Topping.TOMATO

// The second shift of the game. It is not in the library's sample.

/**
 * A shift that is about the oven: twelve orders in 75 minutes, most of them in two waves. Five come
 * in at once when the kitchen opens and six more half an hour later, and they are not due in the
 * order they come. One oven with two places, taking the tickets as they arrive, is too slow for it.
 *
 * The orders are still to be read: table 3 has an allergy and orders again, two tables want what
 * another table had, and table 1 changes its mind.
 */
fun dinnerRush(): Kitchen =
    Kitchen(
        orders =
            listOf(
                Order(1, table = 1, placedAt = 0, dueAt = 34, text = "A veggie.", wanted = MENU.getValue("veggie")),
                Order(2, table = 2, placedAt = 0, dueAt = 15, text = "One margherita.", wanted = MENU.getValue("margherita")),
                Order(
                    3,
                    table = 3,
                    placedAt = 0,
                    dueAt = 30,
                    text = "A funghi. Nobody at table 3 may get peppers tonight, one of us is allergic.",
                    wanted = MENU.getValue("funghi"),
                    never = setOf(PEPPERS),
                ),
                Order(4, table = 4, placedAt = 0, dueAt = 19, text = "A pepperoni.", wanted = MENU.getValue("pepperoni")),
                Order(
                    5,
                    table = 5,
                    placedAt = 0,
                    dueAt = 24,
                    text = "A margherita without the basil.",
                    wanted = listOf(TOMATO, MOZZARELLA),
                ),
                Order(6, table = 6, placedAt = 12, dueAt = 40, text = "The same as table 4.", wanted = MENU.getValue("pepperoni")),
                Order(
                    7,
                    table = 7,
                    placedAt = 30,
                    dueAt = 64,
                    text = "A veggie with extra onions.",
                    wanted = listOf(TOMATO, MOZZARELLA, MUSHROOMS, PEPPERS, ONIONS, ONIONS),
                ),
                Order(
                    8,
                    table = 3,
                    placedAt = 30,
                    dueAt = 44,
                    text = "Another pizza for table 3: a veggie.",
                    wanted = listOf(TOMATO, MOZZARELLA, MUSHROOMS, ONIONS),
                    never = setOf(PEPPERS),
                ),
                Order(9, table = 9, placedAt = 30, dueAt = 48, text = "A pepperoni.", wanted = MENU.getValue("pepperoni")),
                Order(10, table = 10, placedAt = 30, dueAt = 58, text = "One funghi.", wanted = MENU.getValue("funghi")),
                Order(
                    11,
                    table = 11,
                    placedAt = 30,
                    dueAt = 52,
                    text = "A pepperoni with extra cheese and no tomato.",
                    wanted = listOf(MOZZARELLA, MOZZARELLA, PEPPERONI),
                ),
                Order(12, table = 12, placedAt = 30, dueAt = 46, text = "The same as table 5 had.", wanted = listOf(TOMATO, MOZZARELLA)),
            ),
        changes =
            listOf(
                Change(
                    at = 3,
                    order = 1,
                    text = "Table 1 wants no onions on it after all.",
                    wanted = listOf(TOMATO, MOZZARELLA, MUSHROOMS, PEPPERS),
                ),
            ),
        dough = 16,
        stock = Topping.entries.associateWith { 16 },
        closesAt = 75,
    )
