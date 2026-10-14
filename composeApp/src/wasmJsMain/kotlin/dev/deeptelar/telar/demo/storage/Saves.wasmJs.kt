package dev.deeptelar.telar.demo.storage

import dev.deeptelar.telar.Checkpointer
import dev.deeptelar.telar.checkpoint.browser.LocalStorageCheckpointer
import dev.deeptelar.telar.demo.game.Ticket
import dev.deeptelar.telar.serialization.KotlinxStateSerializer

/**
 * The runs are entries of `localStorage`, so they survive a reload of the page. A run keeps its last
 * 30 checkpoints, which the live view of a stage lists. A run of a stage takes far fewer steps.
 */
actual fun platformCheckpointer(): Checkpointer<Ticket> =
    LocalStorageCheckpointer(KotlinxStateSerializer<Ticket>(), keyPrefix = "pixelpizza.save.", maxHistory = 30)
