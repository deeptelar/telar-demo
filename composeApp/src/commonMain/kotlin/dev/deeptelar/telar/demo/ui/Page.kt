package dev.deeptelar.telar.demo.ui

/** The address of the page the game runs in, without what follows `#`. `null` where there is no page: outside a browser. */
expect fun pageAddress(): String?

/** What follows `#` in the address of the page: what a link to the game carries. `null` when there is nothing, or no page. */
expect fun pageFragment(): String?
