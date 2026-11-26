package com.cleberleonheart.hyperglasskeyboard

import android.app.Activity
import android.content.Intent
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
    private lateinit var updateStatus: TextView
    private lateinit var updateNotes: TextView
    private lateinit var downloadButton: Button
    private var currentUpdate: UpdateInfo? = null
    private var updateCheck: UpdateCheckHandle? = null
    private var downloadHandle: UpdateDownloadHandle? = null
    private var updateCheckId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        title = getString(R.string.settings_title)

        val scroll = android.widget.ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        scroll.addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContentView(scroll)

        root.addView(headline(getString(R.string.settings_title)))
        root.addView(info(getString(R.string.settings_intro)))

        root.addView(Button(this).apply {
            text = getString(R.string.activate_keyboard)
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        }, fullWidth())

        root.addView(headline(getString(R.string.section_appearance_sound)))
        root.addView(
            spinnerRow(
                getString(R.string.theme_label),
                resources.getStringArray(R.array.theme_names).toList(),
                prefs.theme
            ) { prefs.theme = it }
        )
        root.addView(switchRow(getString(R.string.vibrate_label), prefs.vibrate) { prefs.vibrate = it })
        root.addView(switchRow(getString(R.string.sound_label), prefs.sound) { prefs.sound = it })
        root.addView(
            seekRow(getString(R.string.height_label), prefs.heightPercent, HEIGHT_MIN, HEIGHT_MAX) {
                prefs.heightPercent = it
            }
        )

        root.addView(headline(getString(R.string.section_typing)))
        root.addView(
            switchRow(getString(R.string.suggestions_label), prefs.suggestions) {
                prefs.suggestions = it
            }
        )
        root.addView(
            switchRow(getString(R.string.auto_capitalize_label), prefs.autoCapitalize) {
                prefs.autoCapitalize = it
            }
        )
        root.addView(
            switchRow(getString(R.string.enter_sends_label), prefs.enterSends) {
                prefs.enterSends = it
            }
        )
        root.addView(info(getString(R.string.typing_tips)))

        root.addView(Button(this).apply {
            text = getString(R.string.clear_learned)
            setOnClickListener {
                prefs.clearLearned()
                Toast.makeText(this@SettingsActivity, R.string.learned_cleared, Toast.LENGTH_SHORT).show()
            }
        }, fullWidth())

        root.addView(headline(getString(R.string.section_updates)))
        updateStatus = TextView(this).apply {
            text = getString(R.string.current_version, BuildConfig.VERSION_NAME)
            textSize = 14f
            setTextColor(color(R.color.settings_text))
            layoutParams = fullWidth()
        }
        root.addView(updateStatus)

        root.addView(Button(this).apply {
            text = getString(R.string.check_updates)
            setOnClickListener { checkUpdates() }
        }, fullWidth())

        updateNotes = TextView(this).apply {
            textSize = 13f
            setTextColor(color(R.color.settings_text_secondary))
            visibility = android.view.View.GONE
            layoutParams = fullWidth()
        }
        root.addView(updateNotes)

        downloadButton = Button(this).apply {
            text = getString(R.string.download_update)
            visibility = android.view.View.GONE
            setOnClickListener {
                val update = currentUpdate ?: return@setOnClickListener
                if (update.apkUrl.isBlank()) {
                    UpdateChecker.open(this@SettingsActivity, update)
                    return@setOnClickListener
                }
                isEnabled = false
                text = getString(R.string.downloading_update, 0)
                downloadHandle?.cancel()
                downloadHandle = UpdateChecker.download(this@SettingsActivity, update) { state ->
                    if (isFinishing || isDestroyed) return@download
                    when (state) {
                        is UpdateDownloadState.Progress -> text = getString(
                            R.string.downloading_update,
                            state.percent
                        )
                        UpdateDownloadState.Ready -> {
                            isEnabled = true
                            text = getString(R.string.install_update)
                            downloadHandle = null
                            if (!UpdateChecker.install(this@SettingsActivity)) {
                                Toast.makeText(
                                    this@SettingsActivity,
                                    R.string.install_permission_needed,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        UpdateDownloadState.Failed -> {
                            isEnabled = true
                            text = getString(R.string.download_update)
                            downloadHandle = null
                            Toast.makeText(
                                this@SettingsActivity,
                                R.string.update_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
        root.addView(downloadButton, fullWidth())

        checkUpdates()
    }

    override fun onDestroy() {
        updateCheckId++
        updateCheck?.cancel()
        downloadHandle?.cancel()
        super.onDestroy()
    }

    private fun checkUpdates() {
        updateCheck?.cancel()
        downloadHandle?.cancel()
        val id = ++updateCheckId
        updateStatus.text = getString(R.string.checking_updates)
        updateNotes.visibility = android.view.View.GONE
        downloadButton.visibility = android.view.View.GONE
        downloadButton.isEnabled = true
        currentUpdate = null
        updateCheck = UpdateChecker.check { state ->
            if (id != updateCheckId || isFinishing || isDestroyed) return@check
            updateCheck = null
            when (state) {
                is UpdateState.Available -> {
                    currentUpdate = state.info
                    updateStatus.text = getString(
                        R.string.update_available,
                        state.info.version,
                        BuildConfig.VERSION_NAME
                    )
                    val notes = state.info.notes.trim()
                    if (notes.isNotEmpty()) {
                        updateNotes.text = notes.take(500)
                        updateNotes.visibility = android.view.View.VISIBLE
                    }
                    downloadButton.text = getString(R.string.open_update, state.info.version)
                    downloadButton.visibility = android.view.View.VISIBLE
                }
                UpdateState.UpToDate -> updateStatus.text = getString(
                    R.string.up_to_date,
                    BuildConfig.VERSION_NAME
                )
                UpdateState.Error -> updateStatus.text = getString(R.string.update_error)
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun color(id: Int) = resources.getColor(id, theme)

    private fun fullWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(10) }

    private fun headline(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 19f
        setTextColor(color(R.color.settings_accent))
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        layoutParams = fullWidth().apply { topMargin = dp(24) }
    }

    private fun info(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(color(R.color.settings_text_secondary))
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
            setTextColor(color(R.color.settings_text))
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
            setTextColor(color(R.color.settings_muted))
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
            text = getString(R.string.height_value, label, value)
            textSize = 13f
            setTextColor(color(R.color.settings_muted))
        }
        row.addView(valueText)
        row.addView(SeekBar(this).apply {
            this.max = max - min
            progress = value - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = progress + min
                    valueText.text = getString(R.string.height_value, label, v)
                    if (fromUser) onChange(v)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }, fullWidth())
        return row
    }
}
