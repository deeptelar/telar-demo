package dev.deeptelar.telar.demo

import dev.deeptelar.telar.demo.kitchen.text
import dev.deeptelar.telar.demo.ui.KitchenLink
import dev.deeptelar.telar.demo.ui.Rush
import dev.deeptelar.telar.demo.ui.pageAddress
import dev.deeptelar.telar.demo.ui.pageFragment
import kotlinx.browser.window
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Runs in a real browser: a link to a board is read from the address of the page. */
class PageLinkTest {
    @Test
    fun theBoardOfALinkIsReadFromTheAddressOfThePage() {
        window.location.hash = ""
        assertNull(pageFragment())
        assertNull(KitchenLink.of(pageFragment()))

        // A link is made for the page the game runs in, wherever that is.
        val page = assertNotNull(pageAddress())
        assertFalse("#" in page)
        val link = KitchenLink(Rush.Dinner, DINNER_BOARD)
        assertEquals("$page#kitchen=dinner.plxbo-413k0k2", link.address(page))

        window.location.hash = link.fragment
        assertEquals("kitchen=dinner.plxbo-413k0k2", pageFragment())
        val opened = assertNotNull(KitchenLink.of(pageFragment()))
        assertEquals(Rush.Dinner to DINNER_BOARD.text(), opened.rush to opened.board.text())
        window.location.hash = ""
    }
}
