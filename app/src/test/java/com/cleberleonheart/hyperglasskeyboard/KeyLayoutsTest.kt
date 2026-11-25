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
}
