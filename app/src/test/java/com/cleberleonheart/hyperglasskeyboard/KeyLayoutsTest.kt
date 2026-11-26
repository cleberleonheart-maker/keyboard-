package com.cleberleonheart.hyperglasskeyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyLayoutsTest {

    @Test
    fun emojiPagesShowEveryEmoji() {
        val pages = (0 until Layouts.EMOJI_PAGE_COUNT).map { Layouts.emoji(it) }
        assertEquals(4, Layouts.EMOJI_PAGE_COUNT)
        assertEquals(72, pages.sumOf { page -> page.count { it.type == KeyType.CHAR } })
        assertEquals(
            pages.flatMap { page -> page.filter { it.type == KeyType.CHAR }.map { it.value } }.toSet().size,
            pages.sumOf { page -> page.count { it.type == KeyType.CHAR } }
        )
    }

    @Test
    fun emojiPageWrapsAround() {
        assertEquals(Layouts.emoji(0), Layouts.emoji(Layouts.EMOJI_PAGE_COUNT * 3))
    }

    @Test
    fun numberAndSymbolLayoutsHaveRouteBackToLetters() {
        listOf(Layouts.NUMBERS, Layouts.SYMBOLS).forEach { layout ->
            assertEquals(1, layout.count { it.type == KeyType.PAGE && it.label == "ABC" })
        }
        assertEquals(1, Layouts.SYMBOLS.count { it.type == KeyType.PAGE && it.label == "123" })
    }
}
