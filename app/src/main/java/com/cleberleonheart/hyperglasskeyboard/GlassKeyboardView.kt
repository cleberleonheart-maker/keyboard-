package com.cleberleonheart.hyperglasskeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.View.MeasureSpec
import android.view.WindowInsets

interface GlassKeyListener {
    fun onKey(key: GlassKey, altIndex: Int)
    fun onCandidate(word: String)
    fun onCommand(command: String)
}

private data class Palette(
    val bgTop: Int,
    val bgBottom: Int,
    val glowA: Int,
    val glowB: Int,
    val accent: Int,
    val keyA: Int,
    val keyB: Int,
    val specialA: Int,
    val specialB: Int,
    val keyText: Int = Color.WHITE,
    val modText: Int = 0xFFE2E8F8.toInt(),
    val isLight: Boolean = false,
    val flat: Boolean = false
)

private val PALETTES = listOf(
    Palette(0xFF0B1024.toInt(), 0xFF111A35.toInt(), 0x6638BDF8.toInt(), 0x55A78BFA.toInt(), 0xFF7DD3FC.toInt(), 0x26FFFFFF.toInt(), 0x0DFFFFFF.toInt(), 0x66438BFA.toInt(), 0x44256D9E.toInt()),
    Palette(0xFF130C26.toInt(), 0xFF1D1238.toInt(), 0x66C084FC.toInt(), 0x55F0ABFC.toInt(), 0xFFE9D5FF.toInt(), 0x26FFFFFF.toInt(), 0x0DFFFFFF.toInt(), 0x66A855F7.toInt(), 0x447E22C5.toInt()),
    Palette(0xFF06151F.toInt(), 0xFF0A2230.toInt(), 0x6622D3EE.toInt(), 0x5534D399.toInt(), 0xFF67E8F9.toInt(), 0x26FFFFFF.toInt(), 0x0DFFFFFF.toInt(), 0x660E7490.toInt(), 0x440F766E.toInt()),
    Palette(0xFF0A0A0F.toInt(), 0xFF14141C.toInt(), 0x55F472B6.toInt(), 0x55818CF8.toInt(), 0xFFFDA4AF.toInt(), 0x20FFFFFF.toInt(), 0x0AFFFFFF.toInt(), 0x665B5878.toInt(), 0x443F3E56.toInt(), 0xFFFFFFFF.toInt(), 0xFFE2E8F8.toInt(), false, true),
    Palette(0xFFF1F3F4.toInt(), 0xFFE3E6EA.toInt(), 0x0A1A73E8.toInt(), 0x0A34A853.toInt(), 0xFF1A73E8.toInt(), 0xFFFFFFFF.toInt(), 0xFFF1F3F4.toInt(), 0xFFE8EAED.toInt(), 0xFFDADCE0.toInt(), 0xFF202124.toInt(), 0xFF3C4043.toInt(), true, true),
    Palette(0xFF1A1206.toInt(), 0xFF2A1B08.toInt(), 0x66F59E0B.toInt(), 0x55FBBF24.toInt(), 0xFFFCD34D.toInt(), 0x26FFFFFF.toInt(), 0x0DFFFFFF.toInt(), 0x66B45309.toInt(), 0x447A3E0B.toInt())
)

class GlassKeyboardView(
    context: Context,
    private val listener: GlassKeyListener
) : View(context) {

    var keys: List<GlassKey> = Layouts.LETTERS
    var candidates: List<String> = emptyList()
    var shiftState: Int = 0
    var enterLabel: String = context.getString(R.string.enter_default)
    var language: String = "PT"
    var paletteIndex: Int = 0
    var suggestionsEnabled: Boolean = true
    var heightPercent: Int = 100
    var vibrateEnabled: Boolean = true
    var soundEnabled: Boolean = false

    private val density = resources.displayMetrics.density
    private val scaledDensity = density * resources.configuration.fontScale

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1f) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val handler = Handler(Looper.getMainLooper())

    private var pressed: GlassKey? = null
    private var altIndex = -1
    private var popupVisible = false
    private var backspaceHeld = false
    private var pressedCandidate = -1
    private var popupLeft = 0f
    private var popupCell = 0f
    private var bottomInset = 0
    private var downX = 0f
    private var downY = 0f

    private val longPressRunnable = Runnable {
        val key = pressed ?: return@Runnable
        when {
            key.type == KeyType.BACKSPACE -> {
                backspaceHeld = true
                listener.onKey(key, -1)
                handler.postDelayed(backspaceRepeat, 90)
            }
            key.alt.isNotEmpty() -> {
                popupVisible = true
                altIndex = -1
                performFeedback()
                invalidate()
            }
            key.type == KeyType.SHIFT -> {
                listener.onKey(key, -2)
                pressed = null
                invalidate()
            }
        }
    }

    private val backspaceRepeat = object : Runnable {
        override fun run() {
            if (backspaceHeld) {
                pressed?.let { listener.onKey(it, -1) }
                handler.postDelayed(this, 70)
            }
        }
    }

    private val clearCandidatePress = Runnable {
        pressedCandidate = -1
        invalidate()
    }

    init {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }
        isClickable = true
        setOnApplyWindowInsetsListener { v, insets ->
            bottomInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            v.requestLayout()
            insets
        }
    }

    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = v * scaledDensity
    private fun Paint.baseline(centerY: Float) = centerY - (this.ascent() + this.descent()) / 2f

    private fun ensureBottomInset() {
        if (bottomInset == 0) {
            val name = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                "navigation_bar_height_landscape"
            } else {
                "navigation_bar_height"
            }
            val id = resources.getIdentifier(name, "dimen", "android")
            if (id > 0) bottomInset = resources.getDimensionPixelSize(id)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (landscape) bottomInset = 0 else ensureBottomInset()
        val baseDp = if (landscape) 260f else 340f
        val desired = (baseDp * heightPercent / 100f * density).toInt() + bottomInset
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> minOf(desired, MeasureSpec.getSize(heightMeasureSpec))
            else -> desired
        }
        setMeasuredDimension(width, maxOf(height, 1))
    }

    private fun candidateHeight(): Float = minOf(dp(44f), maxOf(height * 0.16f, dp(32f)))

    private fun toolbarStart(): Float = maxOf(width - dp(170f), dp(190f))

    private fun keyRect(key: GlassKey): RectF {
        val gap = dp(3f)
        val inset = minOf(bottomInset, (height * 0.2f).toInt())
        val rowH = maxOf((height - inset - candidateHeight() - gap) / 4f, dp(22f))
        val left = key.x / Layouts.UNITS * width + gap
        val right = (key.x + key.w) / Layouts.UNITS * width - gap
        val top = candidateHeight() + key.y * rowH + gap
        val bottom = candidateHeight() + (key.y + 1) * rowH - gap
        return RectF(left, top, right, bottom)
    }

    private fun findKey(x: Float, y: Float): GlassKey? =
        keys.firstOrNull { it.type != KeyType.SPACER && keyRect(it).contains(x, y) }

    private fun candidateSlots(): IntRange = 0 until 3

    private fun candidateRect(index: Int): RectF {
        val h = candidateHeight()
        val start = dp(10f)
        val slotW = (toolbarStart() - dp(10f) - start) / 3f
        return RectF(start + index * slotW, dp(5f), start + (index + 1) * slotW, h - dp(5f))
    }

    private fun iconRect(index: Int): RectF {
        val size = dp(38f)
        val start = toolbarStart()
        return RectF(start + index * (size + dp(4f)), dp(6f), start + index * (size + dp(4f)) + size, candidateHeight() - dp(6f))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pal = PALETTES[Math.floorMod(paletteIndex, PALETTES.size)]

        bgPaint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), pal.bgTop, pal.bgBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        glowPaint.shader = RadialGradient(width * 0.1f, 0f, width * 0.62f, pal.glowA, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)
        glowPaint.shader = RadialGradient(width * 0.92f, height * 0.95f, width * 0.7f, pal.glowB, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)

        drawCandidateBar(canvas, pal)

        for (key in keys) {
            if (key.type == KeyType.SPACER) continue
            drawKey(canvas, key, pal)
        }

        pressed?.let { drawPopup(canvas, it, pal) }
    }

    private fun drawCandidateBar(canvas: Canvas, pal: Palette) {
        val bar = RectF(dp(6f), dp(4f), width - dp(6f), candidateHeight() - dp(2f))
        val fillTop = if (pal.isLight) 0x66FFFFFF.toInt() else 0x18FFFFFF.toInt()
        val fillBottom = if (pal.isLight) 0x33FFFFFF.toInt() else 0x08FFFFFF.toInt()
        keyPaint.shader = LinearGradient(bar.left, bar.top, bar.right, bar.bottom, fillTop, fillBottom, Shader.TileMode.CLAMP)
        keyPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        canvas.drawRoundRect(bar, dp(14f), dp(14f), keyPaint)
        edgePaint.color = if (pal.isLight) 0x22000000 else 0x33FFFFFF.toInt()
        canvas.drawRoundRect(bar, dp(14f), dp(14f), edgePaint)

        if (suggestionsEnabled && candidates.isNotEmpty()) {
            smallPaint.color = pal.keyText
            smallPaint.textSize = sp(16f)
            for (i in candidateSlots()) {
                if (i >= candidates.size) break
                val r = candidateRect(i)
                if (i == pressedCandidate) {
                    keyPaint.shader = null
                    keyPaint.color = if (pal.isLight) 0x22000000 else 0x33FFFFFF.toInt()
                    canvas.drawRoundRect(
                        RectF(r.left + dp(2f), r.top + dp(2f), r.right - dp(2f), r.bottom - dp(2f)),
                        dp(10f), dp(10f), keyPaint
                    )
                }
                smallPaint.color = if (i == pressedCandidate) pal.accent else pal.keyText
                canvas.drawText(candidates[i], r.centerX(), smallPaint.baseline(r.centerY()), smallPaint)
            }
        } else {
            smallPaint.color = if (pal.isLight) 0x99000000.toInt() else 0x99FFFFFF.toInt()
            smallPaint.textSize = sp(11f)
            canvas.drawText(context.getString(R.string.brand), dp(14f), smallPaint.baseline(candidateHeight() / 2f), smallPaint)
        }

        val icons = listOf("⚙", "🌐", "◐", "📋")
        smallPaint.textSize = sp(16f)
        smallPaint.color = pal.keyText
        icons.forEachIndexed { i, icon ->
            val r = iconRect(i)
            canvas.drawText(icon, r.centerX(), smallPaint.baseline(r.centerY()), smallPaint)
        }
    }

    private fun drawKey(canvas: Canvas, key: GlassKey, pal: Palette) {
        val base = keyRect(key)
        val isPressed = key === pressed
        val r = if (isPressed) {
            RectF(base.left + dp(2f), base.top + dp(2f), base.right - dp(2f), base.bottom - dp(2f))
        } else {
            base
        }
        val isModifier = key.type != KeyType.CHAR
        val active = isPressed ||
            (key.type == KeyType.SHIFT && shiftState > 0) ||
            (key.type == KeyType.LANGUAGE && language == "EN") ||
            (key.type == KeyType.EMOJI)

        val topColor = if (isModifier) pal.specialA else pal.keyA
        val bottomColor = if (isModifier) pal.specialB else pal.keyB
        val radius = if (pal.flat) dp(6f) else dp(12f)
        if (pal.flat) {
            keyPaint.shader = null
            keyPaint.color = topColor
            keyPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            canvas.drawRoundRect(r, radius, radius, keyPaint)
            edgePaint.color = if (active) pal.accent else if (pal.isLight) 0x1A000000.toInt() else 0x1AFFFFFF.toInt()
            canvas.drawRoundRect(r, radius, radius, edgePaint)
        } else {
            keyPaint.shader = LinearGradient(r.left, r.top, r.right, r.bottom, topColor, bottomColor, Shader.TileMode.CLAMP)
            keyPaint.setShadowLayer(if (isPressed) dp(10f) else 0f, 0f, 0f, pal.accent)
            canvas.drawRoundRect(r, radius, radius, keyPaint)
            keyPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)

            edgePaint.color = if (active) pal.accent else if (pal.isLight) 0x1A000000 else 0x3DFFFFFF.toInt()
            canvas.drawRoundRect(r, radius, radius, edgePaint)

            val highlight = RectF(r.left + dp(2f), r.top + dp(2f), r.right - dp(2f), r.top + dp(3f))
            keyPaint.shader = null
            keyPaint.color = if (pal.isLight) 0xFFFFFFFF.toInt() else 0x55FFFFFF.toInt()
            canvas.drawRoundRect(highlight, dp(5f), dp(5f), keyPaint)
        }

        val label = when (key.type) {
            KeyType.ENTER -> enterLabel
            KeyType.LANGUAGE -> language
            else -> key.label
        }
        val isEmoji = key.type == KeyType.CHAR && key.value.isNotEmpty() && key.value.codePointAt(0) > 0xFFFF
        textPaint.textSize = when {
            isEmoji -> sp(26f)
            key.type == KeyType.SPACE -> sp(13f)
            label.length > 2 -> sp(13f)
            isModifier -> sp(20f)
            else -> sp(24f)
        }
        textPaint.color = when {
            key.type == KeyType.CHAR -> pal.keyText
            active -> pal.accent
            isModifier -> pal.modText
            else -> pal.keyText
        }
        if (key.type == KeyType.SPACE) {
            if (!pal.flat) {
                smallPaint.color = if (pal.isLight) 0x99000000.toInt() else 0x99FFFFFF.toInt()
                smallPaint.textSize = sp(13f)
                canvas.drawText(context.getString(R.string.space_label), r.centerX(), smallPaint.baseline(r.centerY()), smallPaint)
            }
        } else {
            canvas.drawText(label, r.centerX(), textPaint.baseline(r.centerY()), textPaint)
        }
        if (key.hint.isNotEmpty()) {
            smallPaint.color = when {
                pal.flat && pal.isLight -> 0x993C4043.toInt()
                pal.flat -> 0xB0E0E0E7.toInt()
                pal.isLight -> 0x993C4043.toInt()
                else -> 0x8094A3B8.toInt()
            }
            smallPaint.textSize = sp(10f)
            smallPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(key.hint, r.right - dp(3f), smallPaint.baseline(r.top + sp(12f)), smallPaint)
            smallPaint.textAlign = Paint.Align.CENTER
        }
    }

    private fun drawPopup(canvas: Canvas, key: GlassKey, pal: Palette) {
        val r = keyRect(key)
        val labels = if (popupVisible && key.alt.isNotEmpty()) key.alt else listOf(key.label)
        popupCell = dp(46f)
        val totalW = labels.size * popupCell
        popupLeft = (r.centerX() - totalW / 2f)
            .coerceAtLeast(dp(6f))
            .coerceAtMost(maxOf(width - totalW - dp(6f), dp(6f)))
        val topY = (r.top - dp(58f)).coerceAtLeast(candidateHeight())
        val box = RectF(popupLeft, topY, popupLeft + totalW, topY + dp(50f))

        val popTop = if (pal.isLight) 0xEEFFFFFF.toInt() else 0x44FFFFFF.toInt()
        val popBottom = if (pal.isLight) 0xCCFFFFFF.toInt() else 0x22FFFFFF.toInt()
        keyPaint.shader = LinearGradient(box.left, box.top, box.right, box.bottom, popTop, popBottom, Shader.TileMode.CLAMP)
        keyPaint.setShadowLayer(dp(12f), 0f, 0f, pal.accent)
        canvas.drawRoundRect(box, dp(14f), dp(14f), keyPaint)
        keyPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        edgePaint.color = pal.accent
        canvas.drawRoundRect(box, dp(14f), dp(14f), edgePaint)

        labels.forEachIndexed { i, label ->
            if (popupVisible && i == altIndex) {
                keyPaint.shader = null
                keyPaint.color = pal.accent
                canvas.drawRoundRect(
                    RectF(popupLeft + i * popupCell + dp(3f), box.top + dp(3f), popupLeft + (i + 1) * popupCell - dp(3f), box.bottom - dp(3f)),
                    dp(10f), dp(10f), keyPaint
                )
            }
            textPaint.textSize = sp(24f)
            textPaint.color = if (popupVisible && i == altIndex) Color.WHITE else pal.keyText
            canvas.drawText(label, popupLeft + i * popupCell + popupCell / 2f, textPaint.baseline(box.centerY()), textPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (y < candidateHeight()) {
                    handleCandidateTap(x)
                    return true
                }
                downX = x
                downY = y
                pressed = findKey(x, y)
                if (pressed != null) {
                    altIndex = -1
                    popupVisible = false
                    performFeedback()
                    if (pressed?.type == KeyType.BACKSPACE) {
                        backspaceHeld = true
                        listener.onKey(pressed!!, -1)
                        handler.postDelayed(backspaceRepeat, 300)
                    } else {
                        handler.postDelayed(longPressRunnable, 350)
                    }
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (popupVisible && pressed?.alt?.isNotEmpty() == true) {
                    altIndex = ((x - popupLeft) / popupCell).toInt().coerceIn(0, pressed!!.alt.size - 1)
                    invalidate()
                    return true
                }
                if (y >= candidateHeight()) {
                    val moved = kotlin.math.hypot(x - downX, y - downY) > dp(10f)
                    val hit = findKey(x, y)
                    if (hit !== pressed && moved) {
                        val previous = pressed
                        if (previous != null && previous.type == KeyType.CHAR && !popupVisible && !backspaceHeld) {
                            listener.onKey(previous, -1)
                        }
                        cancelTimers()
                        pressed = hit
                        altIndex = -1
                        popupVisible = false
                        if (hit != null) {
                            performFeedback()
                            if (hit.type == KeyType.BACKSPACE) {
                                backspaceHeld = true
                                listener.onKey(hit, -1)
                                handler.postDelayed(backspaceRepeat, 300)
                            } else {
                                handler.postDelayed(longPressRunnable, 350)
                            }
                        }
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val key = pressed
                if (key != null && !backspaceHeld) {
                    val alt = if (popupVisible && altIndex in key.alt.indices) altIndex else -1
                    listener.onKey(key, alt)
                }
                cancelTimers()
                pressed = null
                popupVisible = false
                altIndex = -1
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelTimers()
                pressed = null
                popupVisible = false
                altIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleCandidateTap(x: Float) {
        if (suggestionsEnabled && candidates.isNotEmpty()) {
            for (i in candidateSlots()) {
                if (i >= candidates.size) break
                if (candidateRect(i).contains(x, candidateHeight() / 2f)) {
                    pressedCandidate = i
                    handler.removeCallbacks(clearCandidatePress)
                    handler.postDelayed(clearCandidatePress, 140)
                    performFeedback()
                    listener.onCandidate(candidates[i])
                    invalidate()
                    return
                }
            }
        }
        listOf("settings", "translate", "theme", "clipboard").forEachIndexed { i, command ->
            if (iconRect(i).contains(x, candidateHeight() / 2f)) {
                performFeedback()
                listener.onCommand(command)
                return@forEachIndexed
            }
        }
    }

    private fun performFeedback() {
        if (vibrateEnabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (soundEnabled) playSoundEffect(SoundEffectConstants.CLICK)
    }

    private fun cancelTimers() {
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(backspaceRepeat)
        backspaceHeld = false
    }

    fun refresh() = invalidate()
}
