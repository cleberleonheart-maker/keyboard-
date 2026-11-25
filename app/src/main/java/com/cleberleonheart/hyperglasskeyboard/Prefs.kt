package com.cleberleonheart.hyperglasskeyboard

import android.content.Context
import android.content.SharedPreferences

const val THEME_COUNT = 6
const val HEIGHT_MIN = 70
const val HEIGHT_MAX = 140
private const val MAX_LEARNED = 300

object PrefKeys {
    const val THEME = "theme"
    const val VIBRATE = "vibrate"
    const val SOUND = "sound"
    const val SUGGESTIONS = "suggestions"
    const val AUTO_CAPITALIZE = "autoCapitalize"
    const val HEIGHT = "heightPercent"
    const val LANGUAGE = "language"
    const val LEARNED = "learned"
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("hyperglass_prefs", Context.MODE_PRIVATE)

    var theme: Int
        get() = sp.getInt(PrefKeys.THEME, 4)
        set(value) = sp.edit().putInt(PrefKeys.THEME, value).apply()

    var vibrate: Boolean
        get() = sp.getBoolean(PrefKeys.VIBRATE, true)
        set(value) = sp.edit().putBoolean(PrefKeys.VIBRATE, value).apply()

    var sound: Boolean
        get() = sp.getBoolean(PrefKeys.SOUND, false)
        set(value) = sp.edit().putBoolean(PrefKeys.SOUND, value).apply()

    var suggestions: Boolean
        get() = sp.getBoolean(PrefKeys.SUGGESTIONS, true)
        set(value) = sp.edit().putBoolean(PrefKeys.SUGGESTIONS, value).apply()

    var autoCapitalize: Boolean
        get() = sp.getBoolean(PrefKeys.AUTO_CAPITALIZE, true)
        set(value) = sp.edit().putBoolean(PrefKeys.AUTO_CAPITALIZE, value).apply()

    var heightPercent: Int
        get() = sp.getInt(PrefKeys.HEIGHT, 100).coerceIn(HEIGHT_MIN, HEIGHT_MAX)
        set(value) = sp.edit().putInt(PrefKeys.HEIGHT, value.coerceIn(HEIGHT_MIN, HEIGHT_MAX)).apply()

    var language: String
        get() = if (sp.getString(PrefKeys.LANGUAGE, "PT") == "EN") "EN" else "PT"
        set(value) = sp.edit().putString(PrefKeys.LANGUAGE, if (value == "EN") "EN" else "PT").apply()

    var learned: Set<String>
        get() = LinkedHashSet(sp.getStringSet(PrefKeys.LEARNED, emptySet()) ?: emptySet())
        set(value) = sp.edit().putStringSet(PrefKeys.LEARNED, LinkedHashSet(value)).apply()

    fun learn(word: String) {
        val normalized = word.trim().lowercase()
        if (normalized.length < 3 || normalized.any { !it.isLetter() }) return
        val current = learned.toMutableSet()
        current.add(normalized)
        if (current.size > MAX_LEARNED) {
            val keep = current.toList().takeLast(MAX_LEARNED)
            current.clear()
            current.addAll(keep)
        }
        learned = current
    }

    fun clearLearned() = sp.edit().remove(PrefKeys.LEARNED).apply()

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(listener)
}
