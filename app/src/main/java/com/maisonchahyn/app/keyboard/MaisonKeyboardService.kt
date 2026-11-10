package com.maisonchahyn.app.keyboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.icu.text.BreakIterator
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.maisonchahyn.app.Prefs
import com.maisonchahyn.app.system.SystemTools

/** Le service de clavier « Maison » : AZERTY façon iPhone, thèmes en verre. */
class MaisonKeyboardService : InputMethodService() {

    private var kv: KeyboardView? = null
    private var lastSpace = 0L
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { updatePaste() }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        try {
            getSystemService(ClipboardManager::class.java)?.addPrimaryClipChangedListener(clipListener)
        } catch (_: Throwable) {
        }
    }

    override fun onDestroy() {
        try {
            getSystemService(ClipboardManager::class.java)?.removePrimaryClipChangedListener(clipListener)
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val v = KeyboardView(this, this)
        kv = v
        return v
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applyWindowStyle()
        val v = kv ?: return
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val start = when (cls) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> Mode.NUMBERS
            else -> Mode.LETTERS
        }
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnter = (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val (label, accent) = if (noEnter) "retour" to false else when (action) {
            EditorInfo.IME_ACTION_GO -> "aller" to true
            EditorInfo.IME_ACTION_SEARCH -> "rechercher" to true
            EditorInfo.IME_ACTION_SEND -> "envoyer" to true
            EditorInfo.IME_ACTION_NEXT -> "suivant" to false
            EditorInfo.IME_ACTION_DONE -> "OK" to true
            EditorInfo.IME_ACTION_PREVIOUS -> "précédent" to false
            else -> "retour" to false
        }
        v.enterLabel = label
        v.enterAccent = accent
        updatePaste()
        v.resetFor(start)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        kv?.autoShift()
    }

    private fun applyWindowStyle() {
        val w = window?.window ?: return
        val glass = kv?.theme()?.glass == true
        try {
            if (glass) {
                w.setFormat(PixelFormat.TRANSLUCENT)
                w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                w.setBackgroundBlurRadius(if (glass && Prefs.kRealBlur.value) 70 else 0)
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                @Suppress("DEPRECATION")
                w.navigationBarColor = kv?.theme()?.bgBottom ?: Color.BLACK
            }
        } catch (_: Throwable) {
        }
    }

    // ------------------------------------------------------------------ Actions
    fun commit(t: String) {
        currentInputConnection?.commitText(t, 1)
    }

    fun deleteBackward() {
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)
        if (!sel.isNullOrEmpty()) {
            ic.commitText("", 1); return
        }
        val before = ic.getTextBeforeCursor(12, 0)
        if (before.isNullOrEmpty()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); return
        }
        val s = before.toString()
        val n = try {
            val it = BreakIterator.getCharacterInstance()
            it.setText(s)
            val end = it.last()
            val prev = it.previous()
            if (prev == BreakIterator.DONE) 1 else end - prev
        } catch (e: Throwable) {
            1
        }
        ic.deleteSurroundingText(n.coerceAtLeast(1), 0)
    }

    fun handleSpace() {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        if (Prefs.kDoubleSpace.value && now - lastSpace < 700) {
            val b = ic.getTextBeforeCursor(2, 0)
            if (b != null && b.length == 2 && b[1] == ' ' && b[0].isLetterOrDigit()) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                lastSpace = 0
                return
            }
        }
        ic.commitText(" ", 1)
        lastSpace = now
    }

    fun handleEnter() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnter = (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnter && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    fun moveCursor(steps: Int) {
        val code = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(steps)) { sendDownUpKeyEvents(code) }
    }

    fun shouldAutoCap(): Boolean {
        if (!Prefs.kAutoCap.value) return false
        val ei = currentInputEditorInfo ?: return false
        val type = ei.inputType
        if (type and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        val v = type and InputType.TYPE_MASK_VARIATION
        val noCap = setOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_FILTER
        )
        if (v in noCap) return false
        val ic = currentInputConnection ?: return false
        val b = ic.getTextBeforeCursor(3, 0)?.toString() ?: return false
        if (b.isEmpty()) return true
        if (b.last() == '\n') return true
        val t = b.trimEnd(' ')
        if (t.isEmpty()) return true
        return b.endsWith(" ") && t.last() in ".!?…"
    }

    private fun updatePaste() {
        val cm = getSystemService(ClipboardManager::class.java)
        val has = try {
            cm != null && cm.hasPrimaryClip() &&
                (cm.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true ||
                    cm.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) == true)
        } catch (e: Throwable) {
            false
        }
        kv?.let {
            it.canPaste = has; it.invalidate()
        }
    }

    fun paste() {
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        val t = try {
            cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        } catch (e: Throwable) {
            null
        }
        if (!t.isNullOrEmpty()) commit(t)
    }

    fun openSettings() = SystemTools.openMaison(this, "clavier")

    fun showPicker() {
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    fun hide() = requestHideSelf(0)

    fun feedback(type: KType) {
        if (Prefs.kVibrate.value) {
            try {
                val vib: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
                }
                if (vib != null && vib.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    } else {
                        vib.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                }
            } catch (_: Throwable) {
            }
        }
        if (Prefs.kSound.value) {
            val fx = when (type) {
                KType.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                KType.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                KType.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            try {
                getSystemService(AudioManager::class.java)?.playSoundEffect(fx, -1f)
            } catch (_: Throwable) {
            }
        }
    }
}
