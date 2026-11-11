package com.maisonchahyn.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

/** Tous les réglages de Maison Chahyn, réactifs (Compose) et sauvegardés. */
object Prefs {
    private val all = mutableListOf<Pref<*>>()
    private var sp: SharedPreferences? = null

    fun init(context: Context) {
        if (sp != null) return
        sp = context.applicationContext.getSharedPreferences("maison_prefs", Context.MODE_PRIVATE)
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
            if (!p.contains(key)) {
                state.value = def; return
            }
            state.value = try {
                when (def) {
                    is Int -> p.getInt(key, def)
                    is Float -> p.getFloat(key, def)
                    is Boolean -> p.getBoolean(key, def)
                    is String -> p.getString(key, def) ?: def
                    is Set<*> -> p.getStringSet(key, emptySet())?.toSet() ?: def
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
                    is Set<*> -> e.putStringSet(key, v.map { it.toString() }.toSet())
                }
                e.apply()
            }

        fun reset() {
            value = def
        }
    }

    // ---------- Écran d'accueil ----------
    val lColumns = Pref("l_columns", 4)
    val lIconSize = Pref("l_icon_size", 60)
    val lShowLabels = Pref("l_labels", true)
    val lLabelSize = Pref("l_label_size", 12)
    val lIconShape = Pref("l_shape", "squircle")
    val lIconStyle = Pref("l_icon_style", "couleur")
    val lTintColor = Pref("l_tint_color", 0xFF0A84FF.toInt())
    val lDock = Pref("l_dock", true)
    val lDockApps = Pref("l_dock_apps", "")
    val lHidden = Pref("l_hidden", emptySet<String>())
    val lClock = Pref("l_clock", true)
    val lSearchBar = Pref("l_search", true)
    val lLibrary = Pref("l_library", true)
    val lSwipeDown = Pref("l_swipe_down", "notifications")
    val lUseSystemWallpaper = Pref("l_sys_wall", false)
    val lDimWallpaper = Pref("l_dim", 0f)

    // ---------- Verre liquide ----------
    val gBlur = Pref("g_blur", 24)
    val gTint = Pref("g_tint", 0.16f)
    val gDark = Pref("g_dark", false)
    val gBorder = Pref("g_border", 0.45f)
    val gHighlight = Pref("g_highlight", true)
    val gCorner = Pref("g_corner", 26)

    // ---------- Clavier ----------
    val kTheme = Pref("k_theme", "verre")
    val kVibrate = Pref("k_vibrate", true)
    val kSound = Pref("k_sound", false)
    val kPreview = Pref("k_preview", true)
    val kAutoCap = Pref("k_autocap", true)
    val kDoubleSpace = Pref("k_double_space", true)
    val kHeight = Pref("k_height", 100)
    val kNumberRow = Pref("k_number_row", false)
    val kRealBlur = Pref("k_real_blur", false)
    val kToolbar = Pref("k_toolbar", true)

    // ---------- Interface & modèles ----------
    val uiAccent = Pref("ui_accent", 0xFF0A84FF.toInt())
    val lIconPack = Pref("l_icon_pack", "")
    val lSystemIcons = Pref("l_system_icons", false)
    val currentModel = Pref("current_model", "")
    val customModels = Pref("custom_models", "[]")

    // ---------- Fond d'écran ----------
    val wallpaperPath = Pref("w_path", "")
    val wallpaperVersion = Pref("w_version", 0)
    val wallpaperName = Pref("w_name", "")
}
