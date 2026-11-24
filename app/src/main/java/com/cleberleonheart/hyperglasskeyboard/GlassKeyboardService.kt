package com.cleberleonheart.hyperglasskeyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GlassKeyboardService : InputMethodService(), GlassKeyListener {

    private lateinit var prefs: Prefs
    private var view: GlassKeyboardView? = null
    private var shift = 0
    private var lastShiftTap = 0L
    private var currentWord = StringBuilder()
    private var sentenceStart = true
    private var layoutKind = "letters"
    private var emojiPage = 0
    private var editorAction = EditorInfo.IME_ACTION_NONE
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreateInputView(): View {
        prefs = Prefs(this)
        val keyboardView = GlassKeyboardView(this, this)
        view = keyboardView
        syncPrefs()
        return keyboardView
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
        view?.candidates = emptyList()
        v.refresh()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        editorAction = if (info != null && info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) {
            EditorInfo.IME_ACTION_NONE
        } else {
            info?.imeOptions?.and(0xff) ?: EditorInfo.IME_ACTION_NONE
        }
        view?.enterLabel = labelFor(editorAction)
        currentWord.setLength(0)
        updateCandidates()
        view?.refresh()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        currentWord.setLength(0)
        sentenceStart = true
        shift = 0
        view?.shiftState = 0
        view?.candidates = emptyList()
        view?.refresh()
    }

    private fun labelFor(action: Int) = when (action) {
        EditorInfo.IME_ACTION_SEARCH -> "Buscar"
        EditorInfo.IME_ACTION_SEND -> "Enviar"
        EditorInfo.IME_ACTION_GO -> "Ir"
        EditorInfo.IME_ACTION_NEXT -> "Próximo"
        EditorInfo.IME_ACTION_DONE -> "OK"
        EditorInfo.IME_ACTION_PREVIOUS -> "Anterior"
        else -> "↵"
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
                emojiPage = (emojiPage + 1) % 2
                applyLayout()
            }
            KeyType.MIC -> toast("Use o botão de microfone do sistema para ditado")
            KeyType.SETTINGS -> openSettings()
            KeyType.CLIPBOARD -> pasteClipboard(ic)
            KeyType.LANGUAGE -> toggleLanguage()
            KeyType.SPACER -> Unit
        }
    }

    private fun commitChar(ic: InputConnection, key: GlassKey, altIndex: Int) {
        var text = if (altIndex >= 0 && altIndex < key.alt.size) key.alt[altIndex] else key.value
        val capitalize = shift > 0 || (prefs.autoCapitalize && sentenceStart && text.length == 1 && text[0].isLetter())
        if (capitalize && text.length == 1) text = text.uppercase()
        ic.commitText(text, 1)
        Log.d("HGKey", "commit '$text' before='${ic.getTextBeforeCursor(40, 0)}'")

        if (text.length == 1 && text[0].isLetter()) currentWord.append(text) else currentWord.setLength(0)
        if (shift == 1) {
            shift = 0
            view?.shiftState = 0
        }
        if (text.isNotBlank()) sentenceStart = text in setOf(".", "!", "?")
        updateCandidates()
    }

    private fun handleShift(altIndex: Int) {
        val now = System.currentTimeMillis()
        shift = when {
            altIndex == -2 -> 2
            now - lastShiftTap < 320 -> if (shift == 2) 0 else 2
            else -> when (shift) {
                0 -> 1
                1 -> 0
                else -> 0
            }
        }
        lastShiftTap = now
        view?.shiftState = shift
        view?.refresh()
    }

    private fun handleBackspace(ic: InputConnection) {
        val before = ic.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        Log.d("HGKey", "backspace before='$before'")
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        val after = ic.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        Log.d("HGKey", "backspace after='$after'")
        currentWord.setLength(0)
        currentWord.append(after.takeLastWhile { it.isLetter() })
        sentenceStart = false
        updateCandidates()
    }

    private fun handleSpace(ic: InputConnection) {
        if (currentWord.isNotEmpty()) prefs.learn(currentWord.toString())
        ic.commitText(" ", 1)
        currentWord.setLength(0)
        updateCandidates()
    }

    private fun handleEnter(ic: InputConnection) {
        if (editorAction != EditorInfo.IME_ACTION_NONE) {
            ic.performEditorAction(editorAction)
        } else {
            ic.commitText("\n", 1)
            sentenceStart = true
        }
        currentWord.setLength(0)
        updateCandidates()
    }

    private fun updateCandidates() {
        val v = view ?: return
        if (!prefs.suggestions) {
            v.candidates = emptyList()
        } else {
            val typed = currentWord.toString().ifEmpty {
                currentInputConnection?.getTextBeforeCursor(60, 0)?.toString()?.takeLastWhile { it.isLetter() } ?: ""
            }
            v.candidates = Dictionary.suggest(typed, prefs.learned)
        }
        v.refresh()
    }

    override fun onCandidate(word: String) {
        val ic = currentInputConnection ?: return
        if (currentWord.isNotEmpty()) ic.deleteSurroundingText(currentWord.length, 0)
        ic.commitText("$word ", 1)
        prefs.learn(word)
        currentWord.setLength(0)
        updateCandidates()
    }

    override fun onCommand(command: String) {
        when (command) {
            "settings" -> openSettings()
            "theme" -> {
                prefs.theme = (prefs.theme + 1) % 5
                view?.paletteIndex = prefs.theme
                view?.refresh()
                toast("Tema ${prefs.theme + 1}/5")
            }
            "clipboard" -> currentInputConnection?.let { pasteClipboard(it) }
            "translate" -> translateLastWord()
        }
    }

    private fun translateLastWord() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(200, 0)?.toString().orEmpty()
        val word = before.takeLastWhile { it.isLetter() }
        if (word.isEmpty()) {
            toast("Digite uma palavra para traduzir")
            return
        }
        currentWord.setLength(0)
        currentWord.append(word)
        val langPair = if (prefs.language == "PT") "pt|en" else "en|pt"

        Thread {
            val translated = try {
                val query = URLEncoder.encode(word, "UTF-8")
                val pair = URLEncoder.encode(langPair, "UTF-8")
                val conn = URL("https://api.mymemory.translated.net/get?q=$query&langpair=$pair").openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                Regex("\"translatedText\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
                    ?.replace("\\\"", "\"")
                    ?.replace("\\n", "\n")
            } catch (e: Exception) {
                null
            }
            mainHandler.post {
                if (translated.isNullOrBlank()) {
                    toast("Não foi possível traduzir agora")
                    updateCandidates()
                } else {
                    view?.candidates = listOf(translated)
                    view?.refresh()
                    toast("Toque na tradução para inserir")
                }
            }
        }.start()
    }

    private fun pasteClipboard(ic: InputConnection) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.text?.toString()
            if (!text.isNullOrEmpty()) {
                ic.commitText(text, 1)
                currentWord.setLength(0)
                updateCandidates()
            } else {
                toast("Área de transferência vazia")
            }
        } catch (e: SecurityException) {
            toast("Não foi possível acessar a área de transferência")
        }
    }

    private fun toggleLanguage() {
        prefs.language = if (prefs.language == "PT") "EN" else "PT"
        view?.language = prefs.language
        view?.refresh()
        toast("Idioma: ${prefs.language}")
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
