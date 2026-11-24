package com.cleberleonheart.hyperglasskeyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        title = "Hyper Glass Keyboard"

        val scroll = android.widget.ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        scroll.addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContentView(scroll)

        root.addView(headline("Hyper Glass Keyboard"))
        root.addView(
            info(
                "Teclado IME completo em estilo glass: QWERTY ABNT2, números, símbolos, " +
                    "emoji, acentos por toque longo, sugestões que aprendem com seu uso, " +
                    "shift/caps lock, backspace com repetição, colagem e atalhos de tema.\n\n" +
                    "1) Toque em “Ativar teclado” e habilite Hyper Glass Keyboard.\n" +
                    "2)Em Configurações do sistema › Idioma e entrada, selecione Hyper Glass Keyboard.\n" +
                    "3)Toque no ícone ◐ na barra do teclado para trocar o tema."
            )
        )

        val activate = Button(this).apply {
            text = "Ativar teclado nas configurações do sistema"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        }
        root.addView(activate, fullWidth())

        root.addView(headline("Aparência e som"))
        root.addView(
            spinnerRow(
                "Tema glass",
                listOf("Aurora", "Violeta", "Ocean", "Obsidian", "Gboard claro"),
                prefs.theme
            ) { prefs.theme = it }
        )
        root.addView(
            switchRow("Vibrar ao tocar", prefs.vibrate) { prefs.vibrate = it }
        )
        root.addView(
            switchRow("Som ao tocar", prefs.sound) { prefs.sound = it }
        )
        root.addView(
            seekRow("Altura do teclado", prefs.heightPercent, 70, 140) {
                prefs.heightPercent = it
            }
        )

        root.addView(headline("Digitação"))
        root.addView(
            switchRow("Mostrar sugestões de palavras", prefs.suggestions) {
                prefs.suggestions = it
            }
        )
        root.addView(
            switchRow("Capitalizar início de frase", prefs.autoCapitalize) {
                prefs.autoCapitalize = it
            }
        )
        root.addView(
            info(
                "Dicas: toque e segure uma letra para acentos (á, é, í, ó, ú, ç); " +
                    "toque duas vezes em ⇧ para travar maiúsculas; " +
                    "arraste o dedo sobre as teclas para digitar deslizando; " +
                    "toque e segure ⌫ para apagar várias letras."
            )
        )

        val reset = Button(this).apply {
            text = "Limpar palavras aprendidas"
            setOnClickListener {
                prefs.clearLearned()
                Toast.makeText(this@SettingsActivity, "Dicionário pessoal limpo", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(reset, fullWidth())
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun fullWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(10) }

    private fun headline(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 19f
        setTextColor(Color.parseColor("#7DD3FC"))
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        layoutParams = fullWidth().apply { topMargin = dp(24) }
    }

    private fun info(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.parseColor("#B8C4E0"))
        layoutParams = fullWidth()
    }

    private fun switchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = fullWidth()
        }
        val tv = TextView(this).apply {
            this.text = label
            textSize = 14f
            setTextColor(Color.parseColor("#E8ECFF"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sw = Switch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }
        row.addView(tv)
        row.addView(sw)
        return row
    }

    private fun spinnerRow(
        label: String,
        items: List<String>,
        selected: Int,
        onChange: (Int) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = fullWidth()
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.parseColor("#94A3C4"))
        })
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item, items).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(selected % items.size)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, pos: Int, id: Long) = onChange(pos)
                override fun onNothingSelected(parent: AdapterView<*>) = Unit
            }
        }
        row.addView(spinner, fullWidth())
        return row
    }

    private fun seekRow(
        label: String,
        value: Int,
        min: Int,
        max: Int,
        onChange: (Int) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = fullWidth()
        }
        val valueText = TextView(this).apply {
            text = "$label: $value%"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3C4"))
        }
        row.addView(valueText)
        row.addView(SeekBar(this).apply {
            this.max = max - min
            progress = value - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = progress + min
                    valueText.text = "$label: $v%"
                    if (fromUser) onChange(v)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }, fullWidth())
        return row
    }
}
