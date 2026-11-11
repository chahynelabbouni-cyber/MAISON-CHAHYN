package com.chahyn.photo

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf

/** Réglages de Chahyn Photo, réactifs (Compose) et sauvegardés. */
object Prefs {
    private val all = mutableListOf<Pref<*>>()
    private var sp: SharedPreferences? = null

    fun init(context: Context) {
        if (sp != null) return
        sp = context.applicationContext.getSharedPreferences("chahyn_photo", Context.MODE_PRIVATE)
        all.forEach { it.load() }
    }

    class Pref<T : Any>(val key: String, val def: T) {
        private val state = mutableStateOf(def)

        init {
            all.add(this)
        }

        @Suppress("UNCHECKED_CAST")
        internal fun load() {
            val p = sp ?: return
            if (!p.contains(key)) return
            state.value = try {
                when (def) {
                    is Int -> p.getInt(key, def)
                    is Float -> p.getFloat(key, def)
                    is Boolean -> p.getBoolean(key, def)
                    is String -> p.getString(key, def) ?: def
                    else -> def
                } as T
            } catch (e: Exception) {
                def
            }
        }

        var value: T
            get() = state.value
            set(v) {
                state.value = v
                val e = sp?.edit() ?: return
                when (v) {
                    is Int -> e.putInt(key, v)
                    is Float -> e.putFloat(key, v)
                    is Boolean -> e.putBoolean(key, v)
                    is String -> e.putString(key, v)
                }
                e.apply()
            }
    }

    val cScene = Pref("c_scene", "auto")
    val cLook = Pref("c_look", "naturel")
    val cLookAmount = Pref("c_look_amount", 1f)
    val cRes = Pref("c_res", "12")
    val cRatio = Pref("c_ratio", "4:3")
    val cRaw = Pref("c_raw", false)
    val cGrid = Pref("c_grid", true)
    val cLevel = Pref("c_level", true)
    val cHisto = Pref("c_histo", false)
    val cSound = Pref("c_sound", true)
    val cSmartIso = Pref("c_smart_iso", true)
    val cTripod = Pref("c_tripod", true)
    val cFlash = Pref("c_flash", "off")
    val cTimer = Pref("c_timer", 0)
    val cTips = Pref("c_tips", true)
    val cVolumeShutter = Pref("c_volume_shutter", true)
    val cOnboarded = Pref("c_onboarded", false)
}

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
