package com.maisonchahyn.app.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import com.maisonchahyn.app.Prefs
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Couleurs d'un thème de clavier. */
data class KTheme(
    val bgTop: Int, val bgBottom: Int,
    val key: Int, val special: Int, val text: Int, val shadow: Int,
    val glass: Boolean, val dark: Boolean
)

@SuppressLint("ViewConstructor")
class KeyboardView(context: Context, private val ime: MaisonKeyboardService) : View(context) {

    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity

    var mode = Mode.LETTERS
        private set
    private var shift = false
    private var caps = false
    private var lastShiftTap = 0L
    private var emojiPage = 0
    private var rows: List<List<Key>> = emptyList()
    private var bottomInset = 0

    var enterLabel = "retour"
    var enterAccent = false
    var canPaste = false

    // Barre d'outils
    private val toolbarOn get() = Prefs.kToolbar.value
    private val toolbarH get() = if (toolbarOn) 42 * dp else 10 * dp
    private val tbSettings = RectF()
    private val tbPaste = RectF()
    private val tbGlobe = RectF()
    private val tbHide = RectF()

    // Géométrie
    private val vGap = 11 * dp
    private val hGap = 6 * dp
    private val sidePad = 3 * dp
    private val topPad = 6 * dp
    private val botPad = 6 * dp
    private fun keyH() = 46 * dp * Prefs.kHeight.value / 100f
    private fun radius() = if (theme().glass) 9 * dp else 6 * dp

    // Toucher
    private var active: Key? = null
    private var activePointer = -1
    private var downInToolbar = false
    private var downX = 0f
    private var downY = 0f
    private var accentOptions: List<String>? = null
    private var accentSel = 0
    private val accentRect = RectF()
    private var accentCell = 0f
    private var cursorMode = false
    private var lastCursorX = 0f
    private var longPressed = false

    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable { onLongPress() }
    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (active?.type == KType.DELETE) {
                ime.deleteBackward()
                ime.feedback(KType.DELETE)
                handler.postDelayed(this, 55)
            }
        }
    }

    // Peintures
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f * dp }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val tmp = RectF()
    private val path = Path()
    private val rowShaders = mutableMapOf<Int, Shader>()

    init {
        isHapticFeedbackEnabled = true
        setOnApplyWindowInsetsListener { _, insets ->
            val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION") insets.systemWindowInsetBottom
            }
            if (b != bottomInset) {
                bottomInset = b; requestLayout()
            }
            insets
        }
        build()
    }

    // ------------------------------------------------------------------ Thème
    fun theme(): KTheme {
        var id = Prefs.kTheme.value
        if (id == "auto") {
            val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            id = if (night) "verre_sombre" else "verre"
        }
        return when (id) {
            "clair" -> KTheme(0xFFD3D5DB.toInt(), 0xFFCDD0D6.toInt(), 0xFFFFFFFF.toInt(), 0xFFABB0BA.toInt(), 0xFF000000.toInt(), 0xFF898A8D.toInt(), false, false)
            "sombre" -> KTheme(0xFF2C2C2E.toInt(), 0xFF262628.toInt(), 0xFF6B6B6B.toInt(), 0xFF474749.toInt(), 0xFFFFFFFF.toInt(), 0x99000000.toInt(), false, true)
            "verre_sombre" -> KTheme(0x9E202024.toInt(), 0xC2141416.toInt(), 0x3DFFFFFF, 0x1FFFFFFF, 0xFFFFFFFF.toInt(), 0x40000000, true, true)
            "rose" -> KTheme(0xCCFFE3EE.toInt(), 0xE6F7D0E2.toInt(), 0xE6FFFFFF.toInt(), 0x80FFFFFF.toInt(), 0xFF3A1F2B.toInt(), 0x33A0305A, true, false)
            "nuit_bleue" -> KTheme(0xD90B1A3A.toInt(), 0xF2061026.toInt(), 0x330A84FF, 0x1A64D2FF, 0xFFFFFFFF.toInt(), 0x55000000, true, true)
            else -> KTheme(0xB8F2F3F7.toInt(), 0xD6E3E6EE.toInt(), 0xE0FFFFFF.toInt(), 0x73FFFFFF, 0xFF000000.toInt(), 0x30000000, true, false)
        }
    }

    // ------------------------------------------------------------------ Disposition
    fun switchMode(m: Mode) {
        mode = m
        if (m != Mode.LETTERS) {
            shift = false; caps = false
        }
        build()
    }

    fun refresh() {
        build()
    }

    private fun build() {
        rows = when (mode) {
            Mode.LETTERS -> Layouts.letters(Prefs.kNumberRow.value)
            Mode.NUMBERS -> Layouts.numbers()
            Mode.SYMBOLS -> Layouts.symbols()
            Mode.EMOJI -> Layouts.emoji(emojiPage)
        }
        rowShaders.clear()
        requestLayout()
        if (width > 0) layoutKeys()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val n = rows.size
        val h = toolbarH + topPad + n * keyH() + (n - 1) * vGap + botPad + bottomInset
        setMeasuredDimension(w, h.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutKeys()
    }

    private fun layoutKeys() {
        val W = width.toFloat()
        val avail = W - 2 * sidePad
        val unit = avail / 10f
        var y = toolbarH + topPad
        val kh = keyH()
        for (row in rows) {
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            if (total > 0f) {
                val u = if (total > 10f) avail / total else unit
                var x = sidePad + (avail - total * u) / 2f
                for (k in row) {
                    k.rect.set(x + hGap / 2, y, x + k.weight * u - hGap / 2, y + kh)
                    x += k.weight * u
                }
            }
            y += kh + vGap
        }
        val th = toolbarH
        val s = 40 * dp
        val cy = th / 2
        tbSettings.set(8 * dp, cy - s / 2, 8 * dp + s, cy + s / 2)
        tbPaste.set(tbSettings.right + 6 * dp, cy - s / 2, tbSettings.right + 6 * dp + 88 * dp, cy + s / 2)
        tbHide.set(W - 8 * dp - s, cy - s / 2, W - 8 * dp, cy + s / 2)
        tbGlobe.set(tbHide.left - 6 * dp - s, cy - s / 2, tbHide.left - 6 * dp, cy + s / 2)
    }

    // ------------------------------------------------------------------ Dessin
    override fun onDraw(canvas: Canvas) {
        val t = theme()
        val W = width.toFloat()
        val H = height.toFloat()
        fill.shader = LinearGradient(0f, 0f, 0f, H, t.bgTop, t.bgBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, W, H, fill)
        fill.shader = null
        if (t.glass) {
            // ligne de lumière en haut (bord du verre)
            fill.color = if (t.dark) 0x40FFFFFF else 0xB3FFFFFF.toInt()
            canvas.drawRect(0f, 0f, W, 1.2f * dp, fill)
        }

        if (toolbarOn) drawToolbar(canvas, t)

        val r = radius()
        val upper = shift || caps
        for ((ri, row) in rows.withIndex()) {
            for (k in row) {
                val pressed = k === active && !cursorMode
                var keyColor = when {
                    k.type == KType.ENTER && enterAccent -> 0xFF0A84FF.toInt()
                    k.type == KType.SHIFT && (shift || caps) -> if (t.dark) 0xFFFFFFFF.toInt() else 0xFFFFFFFF.toInt()
                    k.isSpecial -> t.special
                    else -> t.key
                }
                if (pressed && (k.isSpecial || !Prefs.kPreview.value || mode == Mode.EMOJI)) {
                    keyColor = if (k.isSpecial) t.key else t.special
                }
                // ombre
                fill.color = t.shadow
                tmp.set(k.rect.left, k.rect.top + 1.2f * dp, k.rect.right, k.rect.bottom + 1.2f * dp)
                if (!t.glass) canvas.drawRoundRect(tmp, r, r, fill)
                // touche
                fill.color = keyColor
                canvas.drawRoundRect(k.rect, r, r, fill)
                if (t.glass) {
                    stroke.shader = rowShaders.getOrPut(ri) {
                        LinearGradient(0f, k.rect.top, 0f, k.rect.bottom,
                            if (t.dark) 0x66FFFFFF else 0xE6FFFFFF.toInt(),
                            if (t.dark) 0x0DFFFFFF else 0x33FFFFFF,
                            Shader.TileMode.CLAMP)
                    }
                    canvas.drawRoundRect(k.rect, r, r, stroke)
                    stroke.shader = null
                }
                drawLabel(canvas, k, t, upper)
            }
        }

        val a = active
        if (a != null && accentOptions != null) drawAccentPopup(canvas, t, a)
        else if (a != null && a.type == KType.CHAR && Prefs.kPreview.value && mode != Mode.EMOJI && !cursorMode) drawPreview(canvas, t, a, upper)

        if (cursorMode) {
            fill.color = if (t.dark) 0x66000000 else 0x55FFFFFF
            canvas.drawRect(0f, toolbarH, W, H, fill)
        }
    }

    private fun labelFor(k: Key, upper: Boolean): String = when (k.type) {
        KType.CHAR -> if (upper && mode == Mode.LETTERS) k.label.uppercase(Locale.FRENCH) else k.label
        KType.ENTER -> enterLabel
        KType.SHIFT -> if (caps) "⇪" else "⇧"
        KType.SPACE -> if (mode == Mode.EMOJI) "espace" else "espace"
        else -> k.label
    }

    private fun drawLabel(canvas: Canvas, k: Key, t: KTheme, upper: Boolean) {
        val label = labelFor(k, upper)
        val isWord = label.length > 1 && k.type != KType.CHAR || (k.type == KType.CHAR && label.length > 2 && mode != Mode.EMOJI)
        text.typeface = Typeface.create("sans-serif", if (isWord) Typeface.NORMAL else Typeface.NORMAL)
        text.textSize = when {
            mode == Mode.EMOJI && k.type == KType.CHAR -> 24 * sp
            k.type == KType.SHIFT || k.type == KType.DELETE -> 22 * sp
            k.type == KType.EMOJI -> 20 * sp
            isWord -> 15.5f * sp
            k.type == KType.CHAR && mode == Mode.LETTERS && !upper -> 24 * sp
            else -> 22 * sp
        }
        text.color = when {
            k.type == KType.ENTER && enterAccent -> 0xFFFFFFFF.toInt()
            k.type == KType.SHIFT && (shift || caps) -> 0xFF000000.toInt()
            else -> t.text
        }
        val cx = k.rect.centerX()
        val cy = k.rect.centerY() - (text.descent() + text.ascent()) / 2 - if (k.type == KType.CHAR && mode == Mode.LETTERS && !upper) 2 * dp else 0f
        canvas.drawText(label, cx, cy, text)
    }

    private fun drawToolbar(canvas: Canvas, t: KTheme) {
        text.textSize = 20 * sp
        text.color = t.text
        text.typeface = Typeface.DEFAULT
        val baseline = { rect: RectF -> rect.centerY() - (text.descent() + text.ascent()) / 2 }
        canvas.drawText("⚙", tbSettings.centerX(), baseline(tbSettings), text)
        canvas.drawText("🌐", tbGlobe.centerX(), baseline(tbGlobe), text)
        // chevron « masquer »
        stroke.color = t.text
        stroke.strokeWidth = 2.2f * dp
        stroke.strokeCap = Paint.Cap.ROUND
        path.reset()
        val c = tbHide
        path.moveTo(c.centerX() - 7 * dp, c.centerY() - 3 * dp)
        path.lineTo(c.centerX(), c.centerY() + 4 * dp)
        path.lineTo(c.centerX() + 7 * dp, c.centerY() - 3 * dp)
        canvas.drawPath(path, stroke)
        stroke.strokeWidth = 1f * dp
        if (canPaste) {
            fill.color = if (t.dark) 0x26FFFFFF else 0x99FFFFFF.toInt()
            canvas.drawRoundRect(tbPaste, 20 * dp, 20 * dp, fill)
            text.textSize = 15 * sp
            canvas.drawText("📋 Coller", tbPaste.centerX(), baseline(tbPaste), text)
        }
        text.textSize = 13 * sp
        text.color = (t.text and 0x00FFFFFF) or 0x80000000.toInt()
        val mid = (tbPaste.right + tbGlobe.left) / 2
        if (!canPaste || tbGlobe.left - tbPaste.right > 120 * dp) {
            canvas.drawText("Maison", if (canPaste) mid else width / 2f, toolbarH / 2 - (text.descent() + text.ascent()) / 2, text)
        }
    }

    private fun drawPreview(canvas: Canvas, t: KTheme, k: Key, upper: Boolean) {
        val w = k.rect.width() + 2 * hGap + 8 * dp
        val h = keyH() * 1.15f
        var left = k.rect.centerX() - w / 2
        left = left.coerceIn(0f, width - w)
        val bottom = k.rect.top - 2 * dp
        val top = max(0f, bottom - h)
        tmp.set(left, top, left + w, bottom + k.rect.height())
        fill.color = 0x40000000
        canvas.drawRoundRect(tmp.left, tmp.top + 2 * dp, tmp.right, tmp.bottom + 2 * dp, radius() * 1.6f, radius() * 1.6f, fill)
        fill.color = if (t.glass && t.dark) 0xFF4A4A50.toInt() else if (t.dark) 0xFF6B6B6B.toInt() else 0xFFFFFFFF.toInt()
        canvas.drawRoundRect(tmp, radius() * 1.6f, radius() * 1.6f, fill)
        text.textSize = 34 * sp
        text.color = if (t.dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val label = labelFor(k, upper)
        canvas.drawText(label, tmp.centerX(), top + (bottom - top) / 2 - (text.descent() + text.ascent()) / 2, text)
    }

    private fun drawAccentPopup(canvas: Canvas, t: KTheme, k: Key) {
        val opts = accentOptions ?: return
        fill.color = 0x40000000
        canvas.drawRoundRect(accentRect.left, accentRect.top + 2 * dp, accentRect.right, accentRect.bottom + 2 * dp, radius() * 1.5f, radius() * 1.5f, fill)
        fill.color = if (t.dark) 0xFF4A4A50.toInt() else 0xFFFFFFFF.toInt()
        canvas.drawRoundRect(accentRect, radius() * 1.5f, radius() * 1.5f, fill)
        val upper = shift || caps
        text.textSize = 24 * sp
        for ((i, o) in opts.withIndex()) {
            val l = accentRect.left + i * accentCell
            tmp.set(l + 3 * dp, accentRect.top + 4 * dp, l + accentCell - 3 * dp, accentRect.bottom - 4 * dp)
            if (i == accentSel) {
                fill.color = 0xFF0A84FF.toInt()
                canvas.drawRoundRect(tmp, radius(), radius(), fill)
            }
            text.color = if (i == accentSel) 0xFFFFFFFF.toInt() else if (t.dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            val label = if (upper && mode == Mode.LETTERS) o.uppercase(Locale.FRENCH) else o
            canvas.drawText(label, tmp.centerX(), tmp.centerY() - (text.descent() + text.ascent()) / 2, text)
        }
    }

    // ------------------------------------------------------------------ Toucher
    private fun findKey(x: Float, y: Float): Key? {
        if (y < toolbarH) return null
        var best: Key? = null
        for ((i, row) in rows.withIndex()) {
            if (row.isEmpty()) continue
            val top = row[0].rect.top - vGap / 2
            val bottom = row[0].rect.bottom + vGap / 2
            val inBand = (y in top..bottom) || (i == 0 && y < top) || (i == rows.lastIndex && y > bottom)
            if (!inBand) continue
            var bestD = Float.MAX_VALUE
            for (k in row) {
                val d = when {
                    x < k.rect.left -> k.rect.left - x
                    x > k.rect.right -> x - k.rect.right
                    else -> 0f
                }
                if (d < bestD) {
                    bestD = d; best = k
                }
            }
            return best
        }
        return best
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = e.x
                val y = e.y
                if (toolbarOn && y < toolbarH) {
                    downInToolbar = true; return true
                }
                downInToolbar = false
                startKey(e.getPointerId(0), x, y)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = e.actionIndex
                if (active != null && accentOptions == null && !cursorMode) {
                    release(commit = true)
                }
                startKey(e.getPointerId(idx), e.getX(idx), e.getY(idx))
            }

            MotionEvent.ACTION_MOVE -> {
                val idx = e.findPointerIndex(activePointer)
                if (idx < 0) return true
                onMove(e.getX(idx), e.getY(idx))
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(e.actionIndex) == activePointer) release(commit = true)
            }

            MotionEvent.ACTION_UP -> {
                if (downInToolbar) {
                    onToolbarTap(e.x, e.y); downInToolbar = false
                } else release(commit = true)
            }

            MotionEvent.ACTION_CANCEL -> release(commit = false)
        }
        return true
    }

    private fun startKey(pointer: Int, x: Float, y: Float) {
        val k = findKey(x, y) ?: return
        active = k
        activePointer = pointer
        downX = x; downY = y
        lastCursorX = x
        cursorMode = false
        longPressed = false
        accentOptions = null
        ime.feedback(k.type)
        when (k.type) {
            KType.DELETE -> {
                ime.deleteBackward()
                handler.postDelayed(repeatRunnable, 420)
            }

            KType.SHIFT -> onShift()
            KType.CHAR, KType.EMOJI -> handler.postDelayed(longPressRunnable, 380)
            else -> {}
        }
        invalidate()
    }

    private fun onMove(x: Float, y: Float) {
        val a = active ?: return
        if (accentOptions != null) {
            val opts = accentOptions!!
            val i = ((x - accentRect.left) / accentCell).toInt().coerceIn(0, opts.size - 1)
            if (i != accentSel) {
                accentSel = i; invalidate()
            }
            return
        }
        if (a.type == KType.SPACE) {
            if (!cursorMode && abs(x - downX) > 14 * dp) {
                cursorMode = true; lastCursorX = x; invalidate()
            }
            if (cursorMode) {
                val step = 9 * dp
                val n = ((x - lastCursorX) / step).toInt()
                if (n != 0) {
                    ime.moveCursor(n)
                    lastCursorX += n * step
                }
            }
            return
        }
        if (a.type == KType.CHAR) {
            val k = findKey(x, y)
            if (k != null && k !== a && k.type == KType.CHAR) {
                active = k
                handler.removeCallbacks(longPressRunnable)
                handler.postDelayed(longPressRunnable, 380)
                invalidate()
            }
        }
    }

    private fun onLongPress() {
        val a = active ?: return
        if (a.type == KType.EMOJI) {
            longPressed = true
            ime.feedback(KType.SHIFT)
            ime.showPicker()
            return
        }
        if (a.type != KType.CHAR || mode == Mode.EMOJI) return
        val opts = Layouts.accents[a.label.lowercase(Locale.FRENCH)] ?: return
        longPressed = true
        accentOptions = opts
        accentCell = max(a.rect.width(), 34 * dp)
        val w = accentCell * opts.size
        var left = a.rect.left
        if (left + w > width - 2 * dp) left = width - 2 * dp - w
        left = max(2 * dp, left)
        val h = keyH() + 6 * dp
        val top = max(0f, a.rect.top - h - 6 * dp)
        accentRect.set(left, top, left + w, top + h)
        accentSel = min(opts.size - 1, ((downX - left) / accentCell).toInt().coerceAtLeast(0))
        if (accentSel == 0 && opts.size > 1) accentSel = 1
        ime.feedback(KType.SHIFT)
        invalidate()
    }

    private fun release(commit: Boolean) {
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(repeatRunnable)
        val a = active
        val opts = accentOptions
        active = null
        activePointer = -1
        accentOptions = null
        val wasCursor = cursorMode
        cursorMode = false
        if (a == null || !commit) {
            invalidate(); return
        }
        when (a.type) {
            KType.CHAR -> {
                val raw = if (opts != null) opts[accentSel.coerceIn(0, opts.size - 1)] else a.label
                typeChar(raw)
            }

            KType.SPACE -> if (!wasCursor) {
                ime.handleSpace()
                if (mode == Mode.NUMBERS || mode == Mode.SYMBOLS) switchMode(Mode.LETTERS)
                autoShift()
            }

            KType.ENTER -> {
                ime.handleEnter(); autoShift()
            }

            KType.NUM -> switchMode(Mode.NUMBERS)
            KType.SYM -> switchMode(Mode.SYMBOLS)
            KType.ABC -> {
                switchMode(Mode.LETTERS); autoShift()
            }

            KType.EMOJI -> if (!longPressed) {
                emojiPage = 0; switchMode(Mode.EMOJI)
            }

            KType.EMOJI_PREV -> {
                emojiPage = (emojiPage - 1 + Layouts.emojiPages) % Layouts.emojiPages; build()
            }

            KType.EMOJI_NEXT -> {
                emojiPage = (emojiPage + 1) % Layouts.emojiPages; build()
            }

            KType.DELETE -> autoShift()
            KType.SHIFT -> {}
        }
        invalidate()
    }

    private fun typeChar(raw: String) {
        val upper = (shift || caps) && mode == Mode.LETTERS
        ime.commit(if (upper) raw.uppercase(Locale.FRENCH) else raw)
        if (shift && !caps) shift = false
        if ((mode == Mode.NUMBERS || mode == Mode.SYMBOLS) && (raw == "'" || raw == "’")) switchMode(Mode.LETTERS)
        autoShift()
    }

    private fun onShift() {
        val now = SystemClock.uptimeMillis()
        if (caps) {
            caps = false; shift = false
        } else if (shift && now - lastShiftTap < 350) {
            caps = true
        } else {
            shift = !shift
        }
        lastShiftTap = now
        invalidate()
    }

    fun autoShift() {
        if (mode != Mode.LETTERS || caps) return
        val want = ime.shouldAutoCap()
        if (want != shift) {
            shift = want; invalidate()
        }
    }

    private fun onToolbarTap(x: Float, y: Float) {
        when {
            tbSettings.contains(x, y) -> ime.openSettings()
            tbGlobe.contains(x, y) -> ime.showPicker()
            tbHide.contains(x, y) -> ime.hide()
            canPaste && tbPaste.contains(x, y) -> ime.paste()
        }
    }

    fun resetFor(startMode: Mode) {
        caps = false; shift = false; emojiPage = 0
        mode = startMode
        build()
        autoShift()
    }
}
