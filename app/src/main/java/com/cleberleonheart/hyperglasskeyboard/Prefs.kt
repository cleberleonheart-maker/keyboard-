package com.cleberleonheart.hyperglasskeyboard

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("hyperglass_prefs", Context.MODE_PRIVATE)

    var theme: Int
        get() = sp.getInt("theme", 4)
        set(value) = sp.edit().putInt("theme", value).apply()

    var vibrate: Boolean
        get() = sp.getBoolean("vibrate", true)
        set(value) = sp.edit().putBoolean("vibrate", value).apply()

    var sound: Boolean
        get() = sp.getBoolean("sound", false)
        set(value) = sp.edit().putBoolean("sound", value).apply()

    var suggestions: Boolean
        get() = sp.getBoolean("suggestions", true)
        set(value) = sp.edit().putBoolean("suggestions", value).apply()

    var autoCapitalize: Boolean
        get() = sp.getBoolean("autoCapitalize", true)
        set(value) = sp.edit().putBoolean("autoCapitalize", value).apply()

    var heightPercent: Int
        get() = sp.getInt("heightPercent", 100)
        set(value) = sp.edit().putInt("heightPercent", value).apply()

    var language: String
        get() = sp.getString("language", "PT") ?: "PT"
        set(value) = sp.edit().putString("language", value).apply()

    var learned: Set<String>
        get() = HashSet(sp.getStringSet("learned", emptySet()) ?: emptySet())
        set(value) = sp.edit().putStringSet("learned", HashSet(value)).apply()

    fun learn(word: String) {
        if (word.length < 3 || word.any { !it.isLetter() }) return
        val current = learned.toMutableSet()
        current.add(word.lowercase())
        if (current.size > 300) {
            val extras = current.drop(150).toSet()
            extras.forEach { current.remove(it) }
        }
        learned = current
    }

    fun clearLearned() = sp.edit().remove("learned").apply()
}
