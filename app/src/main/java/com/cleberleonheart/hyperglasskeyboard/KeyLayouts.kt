package com.cleberleonheart.hyperglasskeyboard

enum class KeyType {
    CHAR, SHIFT, BACKSPACE, ENTER, SPACE, PAGE, EMOJI, MIC, SETTINGS, CLIPBOARD, LANGUAGE, SPACER
}

data class GlassKey(
    val label: String,
    val x: Float,
    val y: Int,
    val w: Float,
    val type: KeyType = KeyType.CHAR,
    val alt: List<String> = emptyList(),
    val value: String = label,
    val hint: String = ""
)

private data class Part(
    val label: String,
    val w: Float = 1f,
    val type: KeyType = KeyType.CHAR,
    val alt: List<String> = emptyList(),
    val value: String = label,
    val hint: String = ""
)

private fun p(
    label: String,
    w: Float = 1f,
    type: KeyType = KeyType.CHAR,
    alt: List<String> = emptyList(),
    value: String = label,
    hint: String = ""
) = Part(label, w, type, alt, value, hint)

private val ACCENTS = mapOf(
    "a" to listOf("á", "à", "â", "ã", "ä"),
    "e" to listOf("é", "è", "ê", "ë"),
    "i" to listOf("í", "ì", "î", "ï"),
    "o" to listOf("ó", "ò", "ô", "õ", "ö"),
    "u" to listOf("ú", "ù", "û", "ü"),
    "c" to listOf("ç", "ć", "č"),
    "n" to listOf("ñ", "ń"),
    "s" to listOf("ś", "ß"),
    "y" to listOf("ý", "ÿ"),
    "," to listOf(";", ":", "…"),
    "." to listOf("?", "!", "…"),
    "!" to listOf("¡", "1"),
    "?" to listOf("¿", "?"),
    "'" to listOf("`", "´", "^", "~"),
    "\"" to listOf("“", "”", "«", "»")
)

private fun line(y: Int, vararg parts: Part): List<GlassKey> {
    var x = 0f
    return parts.map { part ->
        val key = GlassKey(
            label = part.label,
            x = x,
            y = y,
            w = part.w,
            type = part.type,
            alt = if (part.alt.isNotEmpty()) part.alt else ACCENTS[part.label.lowercase()] ?: emptyList(),
            value = part.value,
            hint = part.hint
        )
        x += part.w
        key
    }
}

private val bottomRow = line(
    3,
    p("?123", 1.5f, KeyType.PAGE),
    p("🎤", 1f, KeyType.MIC),
    p("espaço", 6.5f, KeyType.SPACE),
    p("PT", 1f, KeyType.LANGUAGE),
    p("↵", 2f, KeyType.ENTER)
)

object Layouts {
    const val UNITS = 12f

    val LETTERS: List<GlassKey> = listOf(
        line(0, p("", 1f, KeyType.SPACER), *"qwertyuiop".mapIndexed { i, c -> p(c.toString(), hint = if (i < 9) (i + 1).toString() else "0") }.toTypedArray(), p("", 1f, KeyType.SPACER)),
        line(1, p("", 1f, KeyType.SPACER), *"asdfghjklç".map { p(it.toString()) }.toTypedArray(), p("", 1f, KeyType.SPACER)),
        line(
            2,
            p("⇧", 1.5f, KeyType.SHIFT),
            *"zxcvbnm".map { p(it.toString()) }.toTypedArray(),
            p(","), p("."),
            p("⌫", 1.5f, KeyType.BACKSPACE)
        ),
        bottomRow
    ).flatten()

    val NUMBERS: List<GlassKey> = listOf(
        line(0, p("", 1f, KeyType.SPACER), *"1234567890".map { p(it.toString()) }.toTypedArray(), p("", 1f, KeyType.SPACER)),
        line(
            1,
            p("", 1f, KeyType.SPACER),
            *listOf("-", "/", ":", ";", "(", ")", "R$", "&", "@", "?").map { p(it) }.toTypedArray(),
            p("", 1f, KeyType.SPACER)
        ),
        line(2, p("#+=", 1.5f, KeyType.PAGE), *listOf(".", ",", "?", "!", "'", "\"", "(", ")", "#").map { p(it) }.toTypedArray(), p("⌫", 1.5f, KeyType.BACKSPACE)),
        bottomRow
    ).flatten()

    val SYMBOLS: List<GlassKey> = listOf(
        line(0, p("", 1f, KeyType.SPACER), *listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "=").map { p(it) }.toTypedArray(), p("", 1f, KeyType.SPACER)),
        line(1, p("", 1f, KeyType.SPACER), *listOf("~", "`", "|", "<", ">", "€", "£", "¥", "¢", "«").map { p(it) }.toTypedArray(), p("", 1f, KeyType.SPACER)),
        line(2, p("123", 1.5f, KeyType.PAGE), *listOf("•", "°", "™", "©", "®", "✓", "÷", "×", "→").map { p(it) }.toTypedArray(), p("⌫", 1.5f, KeyType.BACKSPACE)),
        bottomRow
    ).flatten()

    private val EMOJI_PAGES = listOf(
        listOf("😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😍", "🥰", "😘", "😋", "😎", "🤩", "🥳", "😏", "😒", "😞", "😢", "😭", "😤", "😡", "🤯", "😱", "🥺", "🤔", "💡", "❤️", "🧡", "💛", "💚"),
        listOf("👍", "👎", "👌", "✌️", "🤞", "🤟", "👋", "🙌", "🤝", "💪", "🙏", "👀", "🔥", "✨", "⭐", "🌈", "☀️", "🌙", "⚡", "🌊", "🍀", "🎉", "🎂", "🎁", "🚀", "✈️", "🏠", "🎵", "🎮", "📱", "💻", "☕", "🍕", "🍺", "🏆", "✅")
    )

    fun emoji(page: Int): List<GlassKey> {
        val items = EMOJI_PAGES[page % EMOJI_PAGES.size]
        val keys = mutableListOf<GlassKey>()
        for (i in 0 until 18) {
            keys += GlassKey(items[i], (i % 6) * 2f, i / 6, 2f, KeyType.CHAR, value = items[i])
        }
        keys += line(
            3,
            p("ABC", 1.5f, KeyType.PAGE),
            p("😊", 1.5f, KeyType.EMOJI),
            p("espaço", 5f, KeyType.SPACE),
            p("⌫", 2f, KeyType.BACKSPACE),
            p("↵", 2f, KeyType.ENTER)
        )
        return keys
    }
}
