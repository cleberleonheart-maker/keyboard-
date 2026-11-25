package com.cleberleonheart.hyperglasskeyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class DictionaryTest {

    @Test
    fun emptyPrefixUsesLanguageDefaults() {
        assertEquals(listOf("de", "que", "não"), Dictionary.suggest("", emptySet(), "PT"))
        assertEquals(listOf("the", "and", "you"), Dictionary.suggest("", emptySet(), "EN"))
    }

    @Test
    fun learnedWordsComeFirst() {
        val result = Dictionary.suggest("ca", setOf("casa"), "PT")
        assertEquals("casa", result.first())
    }

    @Test
    fun keepsPrefixCapitalization() {
        assertEquals("Can", Dictionary.suggest("Ca", emptySet(), "EN").first())
        assertEquals("Água", Dictionary.suggest("Ág", emptySet(), "PT").first())
    }
}
