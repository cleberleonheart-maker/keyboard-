package com.cleberleonheart.hyperglasskeyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class GlassKeyboardService : InputMethodService(), GlassKeyListener {

    private lateinit var prefs: Prefs
    private var view: GlassKeyboardView? = null
    private var shift = 0
    private var lastShiftTap = 0L
    private var currentWord = ""
    private var sentenceStart = true
    private var layoutKind = "letters"
    private var emojiPage = 0
    private var editorAction = EditorInfo.IME_ACTION_NONE
    private var prefsListenerRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val translationRequest = AtomicInteger(0)
    private val translationExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "hyperglass-translate").apply { isDaemon = true }
    }
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in UI_PREFS) mainHandler.post { syncPrefs() }
    }

    override fun onCreateInputView(): View {
        prefs = Prefs(this)
        val keyboardView = GlassKeyboardView(this, this)
        view = keyboardView
        if (!prefsListenerRegistered) {
            prefs.registerListener(prefsListener)
            prefsListenerRegistered = true
        }
        syncPrefs()
        return keyboardView
    }

    override fun onDestroy() {
        translationRequest.incrementAndGet()
        translationExecutor.shutdownNow()
        if (prefsListenerRegistered) {
            prefs.unregisterListener(prefsListener)
            prefsListenerRegistered = false
        }
        super.onDestroy()
    }

    private fun syncPrefs() {
        val v = view ?: return
        v.paletteIndex = prefs.theme
        v.vibrateEnabled = prefs.vibrate
        v.soundEnabled = prefs.sound
        v.suggestionsEnabled = prefs.suggestions
        v.heightPercent = prefs.heightPercent
        v.language = prefs.language
        v.shiftState = shift
        applyLayout()
        updateCandidates()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        editorAction = if (info != null && info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) {
            EditorInfo.IME_ACTION_NONE
        } else {
            info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        }
        currentWord = ""
        sentenceStart = true
        syncPrefs()
        view?.enterLabel = getString(labelRes(editorAction))
        view?.refresh()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        translationRequest.incrementAndGet()
        currentWord = ""
        sentenceStart = true
        shift = 0
        view?.shiftState = 0
        view?.candidates = emptyList()
        view?.refresh()
    }

    private fun labelRes(action: Int) = when (action) {
        EditorInfo.IME_ACTION_SEARCH -> R.string.enter_search
        EditorInfo.IME_ACTION_SEND -> R.string.enter_send
        EditorInfo.IME_ACTION_GO -> R.string.enter_go
        EditorInfo.IME_ACTION_NEXT -> R.string.enter_next
        EditorInfo.IME_ACTION_DONE -> R.string.enter_done
        EditorInfo.IME_ACTION_PREVIOUS -> R.string.enter_previous
        else -> R.string.enter_default
    }

    private fun applyLayout() {
        view?.keys = when (layoutKind) {
            "numbers" -> Layouts.NUMBERS
            "symbols" -> Layouts.SYMBOLS
            "emoji" -> Layouts.emoji(emojiPage)
            else -> Layouts.LETTERS
        }
        view?.refresh()
    }

    override fun onKey(key: GlassKey, altIndex: Int) {
        val ic = currentInputConnection ?: return
        when (key.type) {
            KeyType.CHAR -> commitChar(ic, key, altIndex)
            KeyType.SHIFT -> handleShift(altIndex)
            KeyType.BACKSPACE -> handleBackspace(ic)
            KeyType.SPACE -> handleSpace(ic)
            KeyType.ENTER -> handleEnter(ic)
            KeyType.PAGE -> {
                layoutKind = when (key.label) {
                    "?123" -> "numbers"
                    "#+=", "123" -> "symbols"
                    else -> "letters"
                }
                applyLayout()
            }
            KeyType.EMOJI -> {
                emojiPage = (emojiPage + 1) % Layouts.EMOJI_PAGE_COUNT
                applyLayout()
            }
            KeyType.MIC -> toast(R.string.mic_hint)
            KeyType.SETTINGS -> openSettings()
            KeyType.CLIPBOARD -> pasteClipboard(ic)
            KeyType.LANGUAGE -> toggleLanguage()
            KeyType.SPACER -> Unit
        }
    }

    private fun commitChar(ic: InputConnection, key: GlassKey, altIndex: Int) {
        var text = if (altIndex in key.alt.indices) key.alt[altIndex] else key.value
        val capitalize = shift > 0 ||
            (prefs.autoCapitalize && sentenceStart && text.length == 1 && text[0].isLetter())
        if (capitalize && text.length == 1) text = text.uppercase()
        ic.commitText(text, 1)
        currentWord = trailingWord(ic)
        sentenceStart = when {
            text in SENTENCE_ENDERS -> true
            text.length == 1 && text[0].isLetter() -> false
            text.isBlank() -> sentenceStart
            else -> false
        }
        if (shift == 1) {
            shift = 0
            view?.shiftState = 0
        }
        updateCandidates()
    }

    private fun handleShift(altIndex: Int) {
        val now = System.currentTimeMillis()
        shift = when {
            altIndex == -2 -> 2
            now - lastShiftTap < 320 -> if (shift == 2) 0 else 2
            else -> if (shift == 0) 1 else 0
        }
        lastShiftTap = now
        view?.shiftState = shift
        view?.refresh()
    }

    private fun handleBackspace(ic: InputConnection) {
        deleteOneCharacter(ic)
        currentWord = trailingWord(ic)
        val before = ic.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        sentenceStart = before.isEmpty() || before.lastOrNull().toString() in SENTENCE_ENDERS
        updateCandidates()
    }

    private fun deleteOneCharacter(ic: InputConnection) {
        val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        val deleteCount = if (before.length >= 2 &&
            Character.isHighSurrogate(before[before.length - 2]) &&
            Character.isLowSurrogate(before.last())
        ) 2 else 1
        ic.deleteSurroundingText(deleteCount, 0)
    }

    private fun handleSpace(ic: InputConnection) {
        val typed = trailingWord(ic)
        if (typed.isNotEmpty()) prefs.learn(typed)
        ic.commitText(" ", 1)
        currentWord = ""
        updateCandidates()
    }

    private fun handleEnter(ic: InputConnection) {
        if (editorAction != EditorInfo.IME_ACTION_NONE) {
            ic.performEditorAction(editorAction)
        } else {
            ic.commitText("\n", 1)
        }
        currentWord = ""
        sentenceStart = true
        updateCandidates()
    }

    private fun trailingWord(ic: InputConnection): String =
        ic.getTextBeforeCursor(120, 0)?.toString()?.takeLastWhile { it.isLetter() }.orEmpty()

    private fun updateCandidates() {
        val v = view ?: return
        if (!prefs.suggestions) {
            v.candidates = emptyList()
        } else {
            val typed = currentWord.ifEmpty { currentInputConnection?.let(::trailingWord).orEmpty() }
            v.candidates = Dictionary.suggest(typed, prefs.learned, prefs.language)
        }
        v.refresh()
    }

    override fun onCandidate(word: String) {
        val ic = currentInputConnection ?: return
        val typed = trailingWord(ic)
        if (typed.isNotEmpty()) ic.deleteSurroundingText(typed.length, 0)
        ic.commitText("$word ", 1)
        prefs.learn(word)
        currentWord = ""
        sentenceStart = false
        updateCandidates()
    }

    override fun onCommand(command: String) {
        when (command) {
            "settings" -> openSettings()
            "theme" -> {
                val names = resources.getStringArray(R.array.theme_names)
                prefs.theme = (prefs.theme + 1) % THEME_COUNT
                syncPrefs()
                toast(
                    R.string.theme_changed,
                    prefs.theme + 1,
                    THEME_COUNT,
                    names.getOrElse(prefs.theme) { (prefs.theme + 1).toString() }
                )
            }
            "clipboard" -> currentInputConnection?.let { pasteClipboard(it) }
            "translate" -> translateLastWord()
        }
    }

    private fun translateLastWord() {
        val ic = currentInputConnection ?: return
        val word = trailingWord(ic)
        if (word.isEmpty()) {
            toast(R.string.translate_prompt)
            return
        }
        currentWord = word
        sentenceStart = false
        val request = translationRequest.incrementAndGet()
        val langPair = if (prefs.language == "PT") "pt|en" else "en|pt"

        translationExecutor.execute {
            if (request != translationRequest.get()) return@execute
            val translated = fetchTranslation(word, langPair)
            mainHandler.post {
                if (request != translationRequest.get()) return@post
                if (translated.isNullOrBlank()) {
                    toast(R.string.translate_failure)
                    updateCandidates()
                } else {
                    view?.candidates = listOf(translated)
                    view?.refresh()
                    toast(R.string.translate_hint)
                }
            }
        }
    }

    private fun fetchTranslation(word: String, langPair: String): String? {
        val query = URLEncoder.encode(word, "UTF-8")
        val pair = URLEncoder.encode(langPair, "UTF-8")
        val conn = URL("https://api.mymemory.translated.net/get?q=$query&langpair=$pair")
            .openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doInput = true
            conn.setRequestProperty("User-Agent", "HyperGlassKeyboard")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (json.optInt("responseStatus") != 200) return null
            json.optJSONObject("responseData")?.optString("translatedText")?.trim()?.ifBlank { null }
        } finally {
            conn.disconnect()
        }
    }

    private fun pasteClipboard(ic: InputConnection) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.text?.toString()
            if (!text.isNullOrEmpty()) {
                ic.commitText(text, 1)
                currentWord = trailingWord(ic)
                sentenceStart = false
                updateCandidates()
            } else {
                toast(R.string.clipboard_empty)
            }
        } catch (e: SecurityException) {
            toast(R.string.clipboard_error)
        }
    }

    private fun toggleLanguage() {
        prefs.language = if (prefs.language == "PT") "EN" else "PT"
        syncPrefs()
        toast(R.string.language_changed, prefs.language)
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun toast(id: Int, vararg args: Any?) {
        Toast.makeText(this, getString(id, *args), Toast.LENGTH_SHORT).show()
    }

    companion object {
        private val UI_PREFS = setOf(
            PrefKeys.THEME,
            PrefKeys.VIBRATE,
            PrefKeys.SOUND,
            PrefKeys.SUGGESTIONS,
            PrefKeys.HEIGHT,
            PrefKeys.LANGUAGE
        )
        private val SENTENCE_ENDERS = setOf(".", "!", "?")
    }
}
